/**
 * Copyright (c) 2013, 2015, The Regents of the University of California, The Cytoscape Consortium
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;

import org.eclipse.jetty.server.Server;
import org.ndexbio.common.NdexClasses;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.models.dao.orientdb.GroupDocDAO;
import org.ndexbio.common.models.dao.orientdb.NetworkDocDAO;
import org.ndexbio.common.models.dao.orientdb.RequestDAO;
import org.ndexbio.common.models.dao.orientdb.UserDocDAO;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.object.Account;
import org.ndexbio.model.object.Membership;
import org.ndexbio.model.object.NdexStatus;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.Request;
import org.ndexbio.model.object.ResponseType;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.helpers.Email;
import org.ndexbio.rest.server.StandaloneServer;
import org.ndexbio.task.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;

@Path("/admin")
public class AdminService extends NdexService {
	private static Logger logger = LoggerFactory.getLogger(AdminService.class);
	
//	static final String defaultPostEdgeLimit = "800000";
	
	private static final String postElementLimitProp = "ServerPostElementLimit";

	public AdminService(@Context HttpServletRequest httpRequest)
    {
        super(httpRequest);
	}

	/**************************************************************************
	 * 
	 * Gets status for the service.
	 * @throws NdexException 
	 **************************************************************************/

	@GET
	@PermitAll
	@NdexOpenFunction
	@Path("/status")
	@Produces("application/json")
	public NdexStatus getStatus() throws NdexException	{

		logger.info("[start: Getting status]");
		
		try (ODatabaseDocumentTx db =NdexDatabase.getInstance().getAConnection()){
			
			NdexStatus status = new NdexStatus();
			status.setNetworkCount(AdminService.getClassCount(db,"network"));
			status.setUserCount(AdminService.getClassCount(db,"user"));
			status.setGroupCount(AdminService.getClassCount(db,"group")); 

			Map<String,String> props = status.getProperties();
			
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
			status.setProperties(props);
			logger.info("[end: Got status]");
			
			// testing the email notification code.
			Map<String, Map<ResponseType, Integer>> tab = getNotificationTable();
			
			String senderAddress = Configuration.getInstance().getProperty("Feedback-Email");
			String emailSubject = "NDEx Notifications - ";
			UserDocDAO userdao = new UserDocDAO(db);
			for ( Map.Entry<String, Map<ResponseType,Integer>> rec : tab.entrySet()) {
				String userUUIDStr = rec.getKey();
				User u = userdao.getUserById(UUID.fromString(userUUIDStr));
				Map<ResponseType,Integer> notifications = rec.getValue();
				if ( notifications.get(ResponseType.PENDING)!=null)	{			
				
					String emailString = "Dear " + u.getAccountName() + " account holder,\n" + 
						"You have received one or more requests to access networks or groups that you currently manage in NDEx. \n" + 
						"Please log in to your account to review and manage all pending requests.\n" + 
						"This is an automated message, please do not respond to this email. If you need help, contact us by emailing: support@ndexbio.org\n\n" +
						"Best Regards,\n" + 
						"The NDEx team\n";
					
					// send email;
					Email.sendEmailUsingLocalhost(senderAddress, u.getEmailAddress(), emailSubject + "You Have Pending Request(s)",
							emailString);
				}
				if ( notifications.get(ResponseType.ACCEPTED)!=null)	{			
					
					String emailString = "Dear " + u.getAccountName() + " account holder,\n" + 
						"Your pending requests have been reviewed by the account's administrator.\n"+
						"You can now log in to your account and access new networks and groups.\n" +
						"This is an automated message, please do not respond to this email. If you need help, contact us by emailing: support@ndexbio.org\n\n" +
						"Best Regards,\n" + 
						"The NDEx team\n";
					
					// send email;
					Email.sendEmailUsingLocalhost(senderAddress, u.getEmailAddress(), emailSubject + "You Request Has Been Reviewed",
							emailString);
				}
				
			}
			
			return status;
		} 
	}

	/**
	 * Generates a table from db that stores the user uuids and their pending/accepted/denied request within the last 24 hours.
	 * @return
	 * @throws NdexException 
	 */
	private Map<String, Map<ResponseType, Integer>> getNotificationTable() throws NdexException {
		 try (ODatabaseDocumentTx dbconn = NdexDatabase.getInstance().getAConnection()){
		  		
	  			OSQLSynchQuery<ODocument> query = new OSQLSynchQuery<>(
	  						"SELECT FROM " + NdexClasses.Request +
	  						" WHERE sysdate().asLong()  - modificationTime.asLong()  < 24*3600000 and  isDeleted=false"
	  								 );

	  			List<ODocument> records = dbconn.command(query).execute();

	  		    Map <String, Map<ResponseType, Integer>> result = new HashMap<> ();
	  			for (ODocument request : records) {
	  				Request r = RequestDAO.getRequestFromDocument(request);
	  				if ( r.getResponse() == ResponseType.PENDING ) {  // pending request need to notify the destinations side.
	  					if (r.getPermission() == Permissions.MEMBER || r.getPermission() == Permissions.GROUPADMIN ) {
	  						// group request. need to notify all admins of the group
	  						GroupDocDAO grpdao = new GroupDocDAO ( dbconn);
	  						List<Membership> members = grpdao.getGroupUserMemberships(r.getDestinationUUID(), Permissions.ADMIN, 0, 5);
	  						for ( Membership member : members){
	  							String uuidStr = member.getMemberUUID().toString();
	  							Map<ResponseType, Integer> notifications = result.get(uuidStr);
	  							if ( notifications == null) {
	  								notifications = new HashMap<>();
	  								result.put(uuidStr, notifications);
	  							}  
	  							Integer cnt = notifications.get(r.getResponse());
	  							if ( cnt == null)
	  								cnt = 1;
	  							else 
	  								cnt = cnt + 1;
	  							notifications.put(ResponseType.PENDING, cnt);
	  						}
	  					} else {
	  						// network request. need to notify all admins of the network
	  						NetworkDocDAO networkdao = new NetworkDocDAO (dbconn);
	  						Set<String> uuids = networkdao.getAdminUsersOnNetwork(r.getDestinationUUID().toString());
	  						
	  				//		UserDocDAO userdao = new UserDocDAO ( dbconn);
	  						for ( String userUUIDStr : uuids){
	  							Map<ResponseType, Integer> notifications = result.get(userUUIDStr);
	  							if ( notifications == null) {
	  								notifications = new HashMap<>();
	  								result.put(userUUIDStr, notifications);
	  							}  
	  							Integer cnt = notifications.get(r.getResponse());
	  							if ( cnt == null)
	  								cnt = 1;
	  							else 
	  								cnt = cnt + 1;
	  							notifications.put(ResponseType.PENDING, cnt);
	  						}
	  						
	  					}
	  					
	  				} else { //accepted or denied request need to notify the source 
						Map<ResponseType, Integer> notifications = result.get(r.getSourceUUID().toString());
						if ( notifications == null) {
							notifications = new HashMap<>();
						    result.put(r.getSourceUUID().toString(), notifications);						
						}
	  					Integer cnt = notifications.get(ResponseType.ACCEPTED);
	  					if ( cnt == null) 
	  						cnt = 1;
	  					else 
	  						cnt = cnt+1;
	  					notifications.put(ResponseType.ACCEPTED, cnt);
	  				}
	  				
	  			}
	  		return result;	
	  	}
	}
	
	
	
	/*
	 * Shut down the server.  Currently it only works for Jetty.  We need it for our performance benchmarking.
	 * 
	 * In future we have to 
	 *    1) add support for Tomcat
	 *    2) only allow privileged users to shut down Tomcat.
	 */
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
	        } catch (Exception e) {
	    	    ;
	        }
		}
		return;
	}

	
	private static Integer getClassCount(ODatabaseDocumentTx db, String className) {

		final List<ODocument> classCountResult = db.query(new OSQLSynchQuery<ODocument>(
						"SELECT COUNT(*) as count FROM " + className + " where isDeleted = false"));

		final Long count = classCountResult.get(0).field("count");

		Integer classCount = count != null ? count.intValue() : null;

		return classCount;

	} 
	
	
	@POST
	@PermitAll
	@Path("/accounts")
	@Produces("application/json")
	public List<Account> getAccountsByuuids(final Set<String> uuidStrs) throws NdexException	{
		List<Account> accountList = new ArrayList<> (uuidStrs.size());
		try ( UserDocDAO userdao = new UserDocDAO() ) {
			GroupDocDAO groupdao = new GroupDocDAO(userdao.getDBConnection());
			for ( String uuidStr : uuidStrs) {
				UUID uuid = UUID.fromString(uuidStr);
				try {
					User u = userdao.getUserById(uuid);
					accountList.add(u);
				} catch ( ObjectNotFoundException e) {
					accountList.add(groupdao.getGroupById(uuid));
				}
			
			}
		}
		
		return accountList;
	}
	
/*
	@GET
	@Path("/backupdb")
	@Produces("application/json")
	public void backupDB() throws NdexException	{
		if ( !isSystemUser())
			throw new NdexException ("Only Sysetm users are allowed to backup database from API.");
		Thread t = new Thread(new Runnable() {
	         @Override
			public void run()
	         {
	        	 ODatabaseDocumentTx db = null;
	        	 try {
			
	        		 String ndexRoot = Configuration.getInstance().getNdexRoot();
	        		 SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
	        		 String strDate = sdf.format(Calendar.getInstance().getTime());
			
	        		 db = NdexDatabase.getInstance().getAConnection();
	        		 String exportFile = ndexRoot + "/dbbackups/db_"+ strDate + ".export";

	        		 logger.info("Backing up database to " + exportFile);
	        		 
	        		 try{
	        			  OCommandOutputListener listener = new OCommandOutputListener() {
	        			    @Override
	        			    public void onMessage(String iText) {
	        			      System.out.print(iText);
	        			      logger.info(iText);
	        			    }
	        			  };

	        			  ODatabaseExport export = new ODatabaseExport(db, exportFile, listener);
	        			  export.setIncludeIndexDefinitions(false);
	        			  export.exportDatabase();
	        			  export.close();
	        			} catch (IOException e) {
							e.printStackTrace();
							logger.error("IO exception when backing up database. " + e.getMessage());
						}  finally {
	        			  db.close();
	        			} 
	        		 logger.info("Database back up fininished succefully.");

	        	 } catch (NdexException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					logger.error("Failed to backup database.  " + e.getMessage()) ;
	        	 } finally {
	        		 if ( db!=null) db.close();

	        	 }
	         }
		});
		t.start();
	}
*/
	
/*	private boolean isSystemUser() throws NdexException {
	  return getLoggedInUser().getAccountName().equals(Configuration.getInstance().getSystmUserName()) ;
	}
*/
}
