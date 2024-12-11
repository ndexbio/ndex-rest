/**
 * Copyright (c) 2013, 2016, The Regents of the University of California, The Cytoscape Consortium
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */
package org.ndexbio.rest.services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;

import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;

import org.eclipse.jetty.server.Server;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.importexport.ImporterExporterEntry;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.util.Util;
import org.ndexbio.model.exceptions.BadRequestException;
import org.ndexbio.model.exceptions.ForbiddenOperationException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.NdexPropertyValuePair;
import org.ndexbio.model.object.NdexStatus;
import org.ndexbio.model.object.User;
import org.ndexbio.model.object.network.NetworkIndexLevel;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.NdexHttpServletDispatcher;
import org.ndexbio.rest.helpers.AmazonSESMailSender;
import org.ndexbio.rest.helpers.EZIDClient;
import org.ndexbio.rest.server.StandaloneServer;
import org.ndexbio.task.NdexServerQueue;
import org.ndexbio.task.SolrIndexScope;
import org.ndexbio.task.SolrTaskRebuildNetworkIdx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.v3.oas.annotations.Operation;


@Path("/v2/admin")
public class AdminServiceV2 extends NdexService {
	private static Logger logger = LoggerFactory.getLogger(AdminService.class);
	
//	static final String defaultPostEdgeLimit = "800000";
	
	private static final String postElementLimitProp = "ServerPostElementLimit";
	
//	private static final String ndexServerVersion = "2.0";

	public AdminServiceV2(@Context HttpServletRequest httpRequest)
    {
        super(httpRequest);
	}

	/**************************************************************************
	 * 
	 * Gets status for the service.
	 * @throws NdexException 
	 * @throws SQLException 
	 **************************************************************************/

	@SuppressWarnings("static-method")
	@GET
	@PermitAll
	@NdexOpenFunction
	@Path("/status")
	@Operation(summary = "Get Server Status", description = "Get the current status of the server. Use this function to check if the server is running and which version it is. The default value for parameter format is 'standard'.")
	@Produces("application/json")
	public NdexStatus getStatus(
			@DefaultValue("short") @QueryParam("format") String format) throws NdexException, SQLException	{
		
		try (Connection db =NdexDatabase.getInstance().getConnection()){
			
			NdexStatus status = new NdexStatus();
			status.setNetworkCount(AdminServiceV2.getClassCount(db,"network"));
			status.setUserCount(AdminServiceV2.getClassCount(db,"ndex_user"));
			status.setGroupCount(AdminServiceV2.getClassCount(db,"ndex_group")); 

			Map<String,Object> props = status.getProperties();
			
			String edgeLimit = Configuration.getInstance().getProperty(Configuration.networkPostEdgeLimit);
			if ( edgeLimit != null ) {
				try {
					int i = Integer.parseInt(edgeLimit);
					props.put(postElementLimitProp, Integer.toString(i));
				} catch( NumberFormatException e) {
					logger.error("[Invalid value in server property {}]", Configuration.networkPostEdgeLimit);
			//		props.put("ServerPostEdgeLimit", "-1");  //defaultPostEdgeLimit);
				}
			} /* else {
				props.put(postElementLimitProp, "-1"); // defaultPostEdgeLimit);
			} */
		    
			props.put("ServerResultLimit", "10000");
			props.put("ServerVersion", NdexHttpServletDispatcher.getNdexVersion());
			props.put("Build" , NdexHttpServletDispatcher.getBuildNumber() );
			
			if (format.toLowerCase().equals("full")) {
				List<HashMap<String,Object>> impExpList = new ArrayList<>();	
				for ( ImporterExporterEntry entry: Configuration.getInstance().getImporterExporters()){
					HashMap<String,Object> importerExporter = new HashMap<>();
					importerExporter.put("name", entry.getName());
					importerExporter.put("description", entry.getDescription());
					importerExporter.put("fileExtension",  entry.getFileExtension());
					importerExporter.put("exporter", (entry.getExporterCmd()!=null && !entry.getExporterCmd().isEmpty()));
					importerExporter.put("importer", (entry.getImporterCmd()!=null && !entry.getImporterCmd().isEmpty()));
					impExpList.add(importerExporter);
				}
				if (!impExpList.isEmpty())
					props.put("ImporterExporters", impExpList);
			}
			
			status.setProperties(props);
			
			return status;
		} 
	}

	
	@POST
	@Path("/request")
	@Operation(summary = "Create a request for admins", description = "General function for creating admin related requests. The posted object has a 'type' attribute which tells the type of a request.")
	@Produces("application/json")
	public void addRequest(
			 Map<String,Object> request) throws Exception	{

		User user = this.getLoggedInUser();
		String reqType = (String)request.get("type");
		if ( reqType == null) 
			throw new ForbiddenOperationException("Attribute 'type' is missing in the request.");
		switch ( reqType ) {
		case "DOI": {
			if ( Configuration.getInstance().getDOIUser() == null)
				throw new ForbiddenOperationException("DOI creation is not enabled on this server.");
			
			String networkIdStr = (String)request.get("networkId");
			if (networkIdStr == null)
				throw new ForbiddenOperationException("Attribute 'networkId' is missing in the request.");
			
			boolean isCertified = false;
			Boolean isCertifiedObj = (Boolean)request.get("isCertified");
			if ( isCertifiedObj !=null && isCertifiedObj.booleanValue()) {
				isCertified = true;
			}
			
			UUID networkId = UUID.fromString(networkIdStr);
			String adminEmailAddress = Configuration.getInstance().getProperty("NdexSystemUserEmail");
		
			try (NetworkDAO dao = new NetworkDAO() ) {
				if (!dao.isAdmin(networkId, user.getExternalId())) 
					throw new ForbiddenOperationException("You are not the owner of this network.");
				if ( dao.hasDOI(networkId)) {
					throw new ForbiddenOperationException("This network already has a DOI or a pending DOI request.");
				}
				
				if ( request.get("properties") ==null) {
					throw new BadRequestException("Required Attributes are missing.");
				}
					
				Map<String,Object> objMap =  (Map<String,Object>)request.get("properties");

				String submitterEmail = (String)objMap.get("contactEmail");
				if ( submitterEmail == null) 
					throw new BadRequestException("contactEmail is missing in the request.");
				
				dao.requestDOI(networkId, isCertified);
				
				dao.setFlag(networkId, "iscomplete", false);
				dao.commit();
				NdexServerQueue.INSTANCE.addSystemTask(new SolrTaskRebuildNetworkIdx(networkId,SolrIndexScope.global,false,null, NetworkIndexLevel.ALL,false));
								
			/*	String name = dao.getNetworkName(networkId);
				String url = Configuration.getInstance().getHostURI() + "/viewer/networks/"+ networkId ;
				
				if ( dao.getNetworkVisibility(networkId) == VisibilityType.PRIVATE) 
					url += "?accesskey=" + key;
				
				String creationURL = Configuration.getInstance().getHostURI() + "/v3/networks/" + networkId
						+ "/DOI?key=" + URLEncoder.encode(Security.encrypt(networkId.toString()),StandardCharsets.UTF_8.toString())
							+"&email=" + 
								URLEncoder.encode(submitterEmail, StandardCharsets.UTF_8.toString());*/
				
				mintDOI(networkId, submitterEmail);
				/*
				//Reading in the email template
				String emailTemplate = Util.readFile(Configuration.getInstance().getNdexRoot() + "/conf/Server_notification_email_template.html");

				String messageBody = "Dear NDEx Administrator,<p>User " + user.getUserName() + " requests a DOI on network '" +
				        name + "' (UUID: " + networkId + "). Please follow this <a href=\""+ url + "\">link</a> to access this network. <br>";
				
				StringBuilder stringMapTable = new StringBuilder();
				stringMapTable.append("<table>");

				for (Map.Entry<String,Object> pair : objMap.entrySet()) {
					     stringMapTable.append("<tr><td><b>" + pair.getKey() + ":</b></td><td>" +pair.getValue() + "</td></tr>");
				}
				stringMapTable.append("</table>");
				messageBody += "<p>Additional information: <br>" + stringMapTable.toString() 
				 + "<p> To create DOI for this network, please click <a href=\"" + creationURL + "\">here</a>.";
				
		        String htmlEmail = emailTemplate.replaceFirst("%%____%%", 
		        		Matcher.quoteReplacement(messageBody)) ;

		        AmazonSESMailSender.getInstance().sendEmail(adminEmailAddress, 
		        		  htmlEmail, "DOI request on NDEx Network", "html");*/
			}
			break;
		}	
		case "Cancel_DOI": {
			String networkIdStr = (String)request.get("networkId");
			if (networkIdStr == null)
				throw new ForbiddenOperationException("Attribute 'networkId' is missing in the request");
			UUID networkId = UUID.fromString(networkIdStr);
			String adminEmailAddress = Configuration.getInstance().getProperty("NdexSystemUserEmail");

			try (NetworkDAO dao = new NetworkDAO() ) {

				if (!dao.isAdmin(networkId, user.getExternalId())) 
					throw new ForbiddenOperationException("You are not the owner of this network.");
				String currentDOI = dao.getNetworkDOI(networkId);
				if ( currentDOI ==null || !currentDOI.equals(NetworkDAO.PENDING)) {
					throw new ForbiddenOperationException("Only pending DOI request can be cancelled.");
				}

				dao.cancelDOI(networkId);
				//dao.setFlag(networkId, "iscomplete", false);
				dao.commit();
				//NdexServerQueue.INSTANCE.addSystemTask(new SolrTaskRebuildNetworkIdx(networkId,SolrIndexScope.global,false,null, NetworkIndexLevel.ALL));

				String name = dao.getNetworkName(networkId);
				
				//Reading in the email template
				String emailTemplate = Util.readFile(Configuration.getInstance().getNdexRoot() + "/conf/Server_notification_email_template.html");

				String messageBody = "Dear NDEx Administrator,<p>User " + user.getUserName() + " has cancelled the DOI request on network '" +
				        name + "' (UUID: " + networkId + ").<br>";
				
		        String htmlEmail = emailTemplate.replaceFirst("%%____%%", 
		        		Matcher.quoteReplacement(messageBody)) ;

		        AmazonSESMailSender.getInstance().sendEmail(adminEmailAddress, 
		        		  htmlEmail, "A DOI request is cancelled on NDEx Network", "html");
			}
			break;
		}
		default:
			throw new ForbiddenOperationException("Request type " + reqType + " is not supported.");
		}
		
	}

	
	/*
	 * Shut down the server.  Currently it only works for Jetty.  We need it for our performance benchmarking.
	 * 
	 * In future we have to 
	 *    1) add support for Tomcat
	 *    2) only allow privileged users to shut down Tomcat.
	 */
	@SuppressWarnings("static-method")
	@GET
	@PermitAll
	@NdexOpenFunction
	@Path("/shutdown")
	@Produces("application/json")
	public void shutDown()	{
		logger.info("[start: shutdown server]");
		
		Server jettyServer = StandaloneServer.getJettyServer();
		if (null != jettyServer) {			
			stopJettyServer(jettyServer);			
			// the following log entry will not log since the server will be down; but let's still have it
			logger.info("[end: shutdown server]");
			System.exit(0);
		}
	}
	
	private static void stopJettyServer(Server server) {
		if (null != server) {
	        try {
	    		NdexDatabase.close();
	        	server.stop();
	        } catch (Exception e) {/* */}
		}
		return;
	}

	
	protected static int getClassCount(Connection db,String className) throws SQLException, NdexException {

		String queryStr = "select reltuples as cnt from pg_class where relname = ?";
		try (PreparedStatement st = db.prepareStatement(queryStr)) { 
			st.setString(1, className);
			try (ResultSet rs = st.executeQuery()) {
				if ( rs.next()) {
					return rs.getInt(1);
				}
			}
		}
		
		throw new NdexException("Failed to get Ndex db statistics.");

	} 
	
	
	private static String mintDOI( 
			UUID networkUUID,
			String submitter
			) throws Exception {

	//	String uuidFromKey = Security.decrypt(key,Configuration.getInstance().getSecretKeySpec());
		String submitterEmail = submitter;
		
		/*if( !networkId.equals(uuidFromKey)) 
			throw new BadRequestException("Invalid key in the URL."); */
		
		// UUID networkUUID = UUID.fromString(networkId);
		
		try (NetworkDAO dao = new NetworkDAO() ) {
			String currentDOI = dao.getNetworkDOI(networkUUID);
			if ( currentDOI ==null || !currentDOI.equals(NetworkDAO.PENDING)) {
				throw new ForbiddenOperationException("This operation only works when a DOI is pending. The current value of DOI is: " + currentDOI );
			}
			dao.setDOI(networkUUID, "CREATING");
			dao.commit();
			
			NetworkSummary s = dao.getNetworkSummaryById(networkUUID);
			
			String author = null;
			for (NdexPropertyValuePair p : s.getProperties() ) {
				if ( p.getPredicateString().equals("author"))
					author = p.getValue();
				
			}
			if ( author == null)  {
				dao.setDOI(networkUUID, NetworkDAO.PENDING);
				dao.commit();
				throw new NdexException("Property author is missing in the network.");
			}
			
			String url = Configuration.getInstance().getHostURI() + "/viewer/networks/"+ networkUUID.toString();
			
			if ( dao.getNetworkVisibility(networkUUID) == VisibilityType.PRIVATE) {
				url += "?accesskey=" + dao.getNetworkAccessKey(networkUUID);
			}

			String id;
			try {
				id = EZIDClient.createDOI(
						url ,
						author, s.getName(),
						Configuration.getInstance().getDOIPrefix(),
						Configuration.getInstance().getDOIUser(),
						Configuration.getInstance().getDOIPswd());
			} catch (Exception e) {
				dao.setDOI(networkUUID, NetworkDAO.PENDING);
				List<String> warnings = dao.getWarnings(networkUUID);
				if (warnings == null)
					warnings = new ArrayList<>();
				warnings.add("Failed to create DOI in EZID site. Cause: " + e.getMessage());
				dao.setWarning(networkUUID, warnings);
				dao.commit();
				e.printStackTrace();
				throw new NdexException("Failed to create DOI in EZID site. Cause: " + e.getMessage());
				
			}
			
			dao.setDOI(networkUUID, id);
			dao.commit();
			
			//Send confirmation to submitter and admin
			
			//Reading in the email template
			String emailTemplate = Util.readFile(Configuration.getInstance().getNdexRoot() + "/conf/Server_notification_email_template.html");
			String adminEmailAddress = Configuration.getInstance().getProperty("NdexSystemUserEmail");

			String messageBody = "Dear NDEx user " + s.getOwner() + ",<p>"
					+ "Your DOI request for the network<br>"
					+ s.getName() + "(" + networkUUID.toString() + ")<br>"
					+ "has been processed.<p>"
					+ "You digital Object Identifier (DOI) is:<br>"
					+ id + "<p>"
					+ "Your identifier's URL form is:<br>"
					+ "https://doi.org/" + id + "<p>"
					+ "Please be advised that it can take several hours before your new DOI becomes resolvable.";
					
			
	        String htmlEmail = emailTemplate.replaceFirst("%%____%%", 
	        		Matcher.quoteReplacement(messageBody)) ;

	        AmazonSESMailSender.getInstance().sendEmail(submitterEmail, 
	        		  htmlEmail, "A DOI has been created for your NDEx Network", "html",adminEmailAddress);

			
			return "DOI " + id +" has been created on this network. Confirmation emails have been sent."; 
		}
		
	}


}
