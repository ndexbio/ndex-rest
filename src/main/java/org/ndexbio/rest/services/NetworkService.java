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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.solr.client.solrj.SolrServerException;
import org.cxio.aspects.datamodels.ATTRIBUTE_DATA_TYPE;
import org.cxio.aspects.datamodels.NetworkAttributesElement;
import org.cxio.aspects.datamodels.NodesElement;
import org.cxio.core.interfaces.AspectElement;
import org.cxio.metadata.MetaDataCollection;
import org.cxio.metadata.MetaDataElement;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.ndexbio.common.NdexClasses;
import org.ndexbio.common.cx.CXAspectFragment;
import org.ndexbio.common.cx.CXAspectWriter;
import org.ndexbio.common.cx.CXNetworkFileGenerator;
import org.ndexbio.common.cx.NdexCXNetworkWriter;
import org.ndexbio.common.models.dao.postgresql.Helper;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.models.dao.postgresql.TaskDAO;
import org.ndexbio.common.solr.NetworkGlobalIndexManager;
import org.ndexbio.common.solr.SingleNetworkSolrIdxManager;
import org.ndexbio.common.util.NdexUUIDFactory;
import org.ndexbio.model.cx.NdexNetworkStatus;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.CXSimplePathQuery;
import org.ndexbio.model.object.Membership;
import org.ndexbio.model.object.NdexPropertyValuePair;
import org.ndexbio.model.object.NdexProvenanceEventType;
import org.ndexbio.model.object.NetworkExportRequest;
import org.ndexbio.model.object.NetworkSearchResult;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.Priority;
import org.ndexbio.model.object.ProvenanceEntity;
import org.ndexbio.model.object.ProvenanceEvent;
import org.ndexbio.model.object.SimpleNetworkQuery;
import org.ndexbio.model.object.SimplePropertyValuePair;
import org.ndexbio.model.object.Status;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.TaskType;
import org.ndexbio.model.object.User;
import org.ndexbio.model.object.network.FileFormat;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.annotations.ApiDoc;
import org.ndexbio.task.CXNetworkLoadingTask;
import org.ndexbio.task.NdexServerQueue;
import org.ndexbio.task.NetworkExportTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Path("/network")
public class NetworkService extends NdexService {
	
	static Logger logger = LoggerFactory.getLogger(NetworkService.class);
	
	static private final String readOnlyParameter = "readOnly";

	public NetworkService(@Context HttpServletRequest httpRequest) {
		super(httpRequest);
	}


/* Note: will be implemented in services	
	@PermitAll
	@GET
	@Path("/{networkId}/namespaceFile/{prefix}")
	@Produces("text/plain")
    @ApiDoc("Retrieves the archived namespace file if exists. Otherwise 404 will be returned.")
	public String getBELNamespaceFile(
			@PathParam("networkId") final String networkId,
			@PathParam("prefix") final String prefix
			) throws ObjectNotFoundException, UnauthorizedOperationException, NdexException, Exception {
    	logger.info("[start: Getting namespace file for " + prefix + " in network {}]", networkId);

		if ( isReadable(networkId) ) { 
			try ( SingleNetworkDAO dao = new SingleNetworkDAO (networkId) ) {				
				String s = dao.getNamespaceFile(prefix);
				logger.info("[end: Return namespace file of {} ,in network {}]", prefix, networkId);
				return s;
			}
		}
		else
            throw new UnauthorizedOperationException("User doesn't have read access to this network.");
	} 
	
	@PermitAll
	@GET
	@Path("/{networkId}/namespace")
	@Produces("application/json")
    @ApiDoc("Retrieves a list of Namespace objects from the network specified by 'networkId'.")
	public List<Namespace> getNamespaces(
			@PathParam("networkId") final String networkId)

			throws IllegalArgumentException, NdexException {

		logger.info("[start: Getting list of namespaces for network {}, skipBlocks {}, blockSize {}]",  
				networkId);
		
		try (NetworkDocDAO daoNew = new NetworkDocDAO()){
			
			if ( !isReadable(daoNew, networkId)) {
				logger.error("[end: Network {} not readable for this user]", networkId);
				throw new UnauthorizedOperationException("Network " + networkId + " is not readable to this user.");
			}
			
			logger.info("[end: Got list of namespaces for network {}]",  networkId);
			return (List<Namespace>) daoNew.getNamespaces(networkId);
		} 

	} */

	/* Note: will be implement in service
	@POST
	@Path("/{networkId}/namespace")
	@Produces("application/json")
    @ApiDoc("Adds the POSTed Namespace object to the network specified by 'networkId'.")
	public void addNamespace(
			@PathParam("networkId") final String networkId,
			final Namespace namespace
			)
			throws IllegalArgumentException, NdexException, IOException {
		
		logger.info("[start: Adding namespace to the network {}]", networkId);

		NdexDatabase db = null; 
		NdexPersistenceService networkService = null;
		try {
			db = NdexDatabase.getInstance();
			networkService = new NdexPersistenceService(
					db,
					UUID.fromString(networkId));

            //networkService.get
			networkService.getNamespace(new RawNamespace(namespace.getPrefix(), namespace.getUri()));

            //DW: Handle provenance
            ProvenanceEntity oldProv = networkService.getNetworkProvenance();
            ProvenanceEntity newProv = new ProvenanceEntity();
            newProv.setUri( oldProv.getUri() );

            Helper.populateProvenanceEntity(newProv, networkService.getCurrentNetwork());

            Timestamp now = new Timestamp(Calendar.getInstance().getTimeInMillis());
            ProvenanceEvent event = new ProvenanceEvent(NdexProvenanceEventType.ADD_NAMESPACE, now);
            List<SimplePropertyValuePair> eventProperties = new ArrayList<>();
            Helper.addUserInfoToProvenanceEventProperties( eventProperties, this.getLoggedInUser());
            String namespaceString = namespace.getPrefix() + " : " + namespace.getUri();
            eventProperties.add( new SimplePropertyValuePair("namespace", namespaceString));
            event.setProperties(eventProperties);
            List<ProvenanceEntity> oldProvList = new ArrayList<>();
            oldProvList.add(oldProv);
            event.setInputs(oldProvList);

            newProv.setCreationEvent(event);
            networkService.setNetworkProvenance(newProv);

			networkService.commit();
			networkService.close();
		} finally {
			
			if (networkService != null) networkService.close();
			logger.info("[end: Added namespace to the network {}]", networkId);
		}
	} */

    /**************************************************************************
    * Returns network provenance.
     * @throws IOException
     * @throws JsonMappingException
     * @throws JsonParseException
     * @throws NdexException
     * @throws SQLException 
    *
    **************************************************************************/
	@PermitAll
	@GET
	@Path("/{networkid}/provenance")
	@Produces("application/json")
	@ApiDoc("This method retrieves the 'provenance' attribute of the network specified by 'networkId', if it " +
	        "exists. The returned value is a JSON ProvenanceEntity object which in turn contains a " +
	        "tree-structure of ProvenanceEvent and ProvenanceEntity objects that describe the provenance " +
	        "history of the network. See the document NDEx Provenance History for a detailed description of " +
	        "this structure and best practices for its use.")	
	public ProvenanceEntity getProvenance(
			@PathParam("networkid") final String networkId)

			throws IllegalArgumentException, JsonParseException, JsonMappingException, IOException, NdexException, SQLException {
		
		logger.info("[start: Getting provenance of network {}]", networkId);
		
		try (NetworkDAO daoNew = new NetworkDAO()) {
			String userAccountName =  (getLoggedInUser() == null ? null : getLoggedInUser().getUserName());
		/*	if ( ! daoNew.networkSummaryIsReadable(userAccountName, networkId)) {
				String userStr = this.getLoggedInUser() != null? this.getLoggedInUser().getUserName() : "anonymous";
				logger.error("[end: Network {} not readable for user {} ]", networkId,  userStr);
				throw new UnauthorizedOperationException("Network " + networkId + " is not readable to this user.");
			} */
			logger.info("[end: Got provenance of network {}]", networkId);
			return daoNew.getProvenance(UUID.fromString(networkId));

		} 
	}

    /**************************************************************************
    * Updates network provenance.
     * @throws Exception
    *
    **************************************************************************/
    @PUT
	@Path("/{networkid}/provenance")
	@Produces("application/json")
    @ApiDoc("Updates the 'provenance' field of the network specified by 'networkId' to be the " +
            "ProvenanceEntity object in the PUT data. The ProvenanceEntity object is expected to represent " +
            "the current state of the network and to contain a tree-structure of ProvenanceEvent and " +
            "ProvenanceEntity objects that describe the networks provenance history.")
    public ProvenanceEntity setProvenance(@PathParam("networkid")final String networkIdStr, final ProvenanceEntity provenance)
    		throws Exception {

    	logger.info("[start: Updating provenance of network {}]", networkIdStr);
    

		try (NetworkDAO daoNew = new NetworkDAO()){
			
			User user = getLoggedInUser();
			UUID networkId = UUID.fromString(networkIdStr);

			if ( !daoNew.isWriteable(networkId, user.getExternalId())) {
				logger.error("[end: No write permissions for user account {} on network {}]", 
						user.getUserName(), networkId);
		        throw new UnauthorizedOperationException("User doesn't have write permissions for this network.");
			}
	
			if(daoNew.isReadOnly(networkId)) {
				daoNew.close();
				logger.info("[end: Can't modify readonly network {}]", networkId);
				throw new NdexException ("Can't update readonly network.");
			} 

			if ( daoNew.networkIsLocked(networkId)) {
				daoNew.close();
				logger.info("[end: Can't update locked network {}]", networkId);
				throw new NdexException ("Can't modify locked network. The network is currently locked by another updating thread.");
			} 
			daoNew.setProvenance(networkId, provenance);
			daoNew.commit();
			return  provenance; //  daoNew.getProvenance(networkUUID);
		} catch (Exception e) {
			//if (null != daoNew) daoNew.rollback();
			logger.error("[end: Updating provenance of network {}. Exception caught:]{}", networkIdStr, e);	
			throw e;
		} finally {
			logger.info("[end: Updated provenance of network {}]", networkIdStr);
		}
    }



    /**************************************************************************
    * Sets network properties.
     * @throws Exception
    *
    **************************************************************************/
    @PUT
	@Path("/{networkid}/properties")
	@Produces("application/json")
    @ApiDoc("Updates the 'properties' field of the network specified by 'networkId' to be the list of " +
            "NdexPropertyValuePair  objects in the PUT data.")
    public int setNetworkProperties(
    		@PathParam("networkid")final String networkId,
    		final List<NdexPropertyValuePair> properties)
    		throws Exception {

    	logger.info("[start: Updating properties of network {}]", networkId);


		try (NetworkDAO daoNew = new NetworkDAO()) {
			
			User user = getLoggedInUser();
			UUID networkUUID = UUID.fromString(networkId);
			
	  	    if(daoNew.isReadOnly(networkUUID)) {
				logger.info("[end: Can't modify readonly network {}]", networkId);
				throw new NdexException ("Can't update readonly network.");				
			} 
			
			if ( !daoNew.isWriteable(networkUUID, user.getExternalId())) {
				logger.error("[end: No write permissions for user account {} on network {}]", 
						user.getUserName(), networkId);
		        throw new UnauthorizedOperationException("User doesn't have write permissions for this network.");
			} 
			
			if ( daoNew.networkIsLocked(networkUUID)) {
				logger.info("[end: Can't update locked network {}]", networkId);
				throw new NdexException ("Can't modify locked network. The network is currently locked by another updating thread.");
			} 

			int i = daoNew.setNetworkProperties(networkUUID, properties);

            //DW: Handle provenance

            ProvenanceEntity oldProv = daoNew.getProvenance(networkUUID);
            ProvenanceEntity newProv = new ProvenanceEntity();
            newProv.setUri( oldProv.getUri() );

            NetworkSummary summary = daoNew.getNetworkSummaryById(networkUUID);
            Helper.populateProvenanceEntity(newProv, summary);
            ProvenanceEvent event = new ProvenanceEvent(NdexProvenanceEventType.SET_NETWORK_PROPERTIES, summary.getModificationTime());
            List<SimplePropertyValuePair> eventProperties = new ArrayList<>();
            Helper.addUserInfoToProvenanceEventProperties( eventProperties, user);
            for( NdexPropertyValuePair vp : properties )
            {
                SimplePropertyValuePair svp = new SimplePropertyValuePair(vp.getPredicateString(), vp.getValue());
                eventProperties.add(svp);
            }
            event.setProperties(eventProperties);
            List<ProvenanceEntity> oldProvList = new ArrayList<>();
            oldProvList.add(oldProv);
            event.setInputs(oldProvList);

            newProv.setCreationEvent(event);
            daoNew.setProvenance(networkUUID, newProv); 

            NetworkSummary fullSummary = daoNew.getNetworkSummaryById(networkUUID);
			
			//update the networkProperty aspect 
			List<NetworkAttributesElement> attrs = getNetworkAttributeAspectsFromSummary(fullSummary);
			if ( attrs.size() > 0 ) {					
				try (CXAspectWriter writer = new CXAspectWriter(Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/aspects/" 
						+ NetworkAttributesElement.ASPECT_NAME) ) {
					for ( NetworkAttributesElement e : attrs) {
						writer.writeCXElement(e);	
						writer.flush();
					}
				}
			}
			
			//update metadata
			MetaDataCollection metadata = daoNew.getMetaDataCollection(networkUUID);
			MetaDataElement elmt = metadata.getMetaDataElement(NetworkAttributesElement.ASPECT_NAME);
			if ( elmt == null) {
				elmt = new MetaDataElement();
			}
			elmt.setElementCount(Long.valueOf(attrs.size()));
			daoNew.updateMetadataColleciton(networkUUID, metadata);

			//Recreate the CX file 					
			CXNetworkFileGenerator g = new CXNetworkFileGenerator(networkUUID, fullSummary, metadata);
			g.reCreateCXFile();
			
			daoNew.unlockNetwork(networkUUID);
            
   			return i;
		} catch (Exception e) {
			//logger.severe("Error occurred when update network properties: " + e.getMessage());
			//e.printStackTrace();
			//if (null != daoNew) daoNew.rollback();
			logger.error("Updating properties of network {}. Exception caught:]{}", networkId, e);
			
			throw new NdexException(e.getMessage(), e);
		} finally {
			logger.info("[end: Updated properties of network {}]", networkId);
		}
    }
  
	/*
	 *
	 * Operations returning Networks 
	 * 
	 */

	@PermitAll
	@GET
	@Path("/{networkid}")
	@Produces("application/json")
	@ApiDoc("Retrieves a NetworkSummary object based on the network specified by 'networkId'. This " +
            "method returns an error if the network is not found or if the authenticated user does not have " +
            "READ permission for the network.")
	public NetworkSummary getNetworkSummary(
			@PathParam("networkid") final String networkIdStr)

			throws IllegalArgumentException, NdexException, SQLException, JsonParseException, JsonMappingException, IOException {

    	logger.info("[start: Getting networkSummary of network {}]", networkIdStr);
		
		try (NetworkDAO dao = new NetworkDAO())  {
			UUID userId = getLoggedInUserId();
			UUID networkId = UUID.fromString(networkIdStr);
			if ( dao.isReadable(networkId, userId)) {
				NetworkSummary summary = dao.getNetworkSummaryById(networkId);
				logger.error("[end: Getting networkSummary of network {}.]", networkId);	

				return summary;
			}
				
			throw new ObjectNotFoundException ("network", networkId);
		}  
			
			
	}


	@PermitAll
	@POST
	@Path("/summaries")
	@Produces("application/json")
	@ApiDoc("Retrieves a list of NetworkSummary objects based on the network uuids POSTed. This " +
            "method only returns network summaries that the user is allowed to read. User can only post up to 300 uuids in this function.")
	public List<NetworkSummary> getNetworkSummaries(
			List<String> networkIdStrs)

			throws IllegalArgumentException, NdexException, SQLException, JsonParseException, JsonMappingException, IOException {

		if (networkIdStrs.size() > 300) 
			throw new NdexException ("You can only send up to 300 network ids in this function.");
		
    	logger.info("[start: Getting networkSummary of networks {}]", networkIdStrs);
		
		try (NetworkDAO dao = new NetworkDAO())  {
			UUID userId = getLoggedInUserId();
			return dao.getNetworkSummariesByIdStrList(networkIdStrs, userId);				
		}  finally {
	    	logger.info("[end: Getting networkSummary of networks {}]", networkIdStrs);
		}						
	}
	
	
	@PermitAll
	@POST
	@Path("/{networkid}/aspects")
	@ApiDoc("The getAspectsAsCX method enables an application to obtain aspects of a given network as a CX " +
	        "structure.")
	//TODO: handle cached network from hardDrive.
	public Response getAspectsAsCX(	@PathParam("networkid") final String networkId,
			final List<String> aspectNames)
			throws Exception {

    	logger.info("[start: Getting aspects in network {}]", networkId);

	//	if ( isReadable(networkId) ) {
			Set<String> asp = new HashSet<>(aspectNames.size());
			for ( String s : aspectNames)
				asp.add(s);
			
	/*		try (SingleNetworkDAO dao = new SingleNetworkDAO(networkId) ) {
				Set<String> missingAspects = dao.findMissingAspect(asp);
				if ( !missingAspects.isEmpty()) {
					throw new NdexException ("Aspect(s) " +String.join(",", missingAspects)
					 // missingAspects.toArray(new String[missingAspects.size()]) + 
					+	" not found in network.");
				}
			} */
			
			PipedInputStream in = new PipedInputStream();
			PipedOutputStream out;
			try {
				out = new PipedOutputStream(in);
			} catch (IOException e) {
				throw new NdexException("IOExcetion when creating the piped output stream: "+ e.getMessage());
			}
			
			new CXNetworkAspectsWriterThread(out,networkId, asp).start();
			//setZipFlag();
			logger.info("[end: get aspects from network {}]", networkId);
			return 	Response.ok().type(MediaType.APPLICATION_JSON_TYPE).entity(in).build();
	//	}
	//	else
   //         throw new UnauthorizedOperationException("User doesn't have read access to this network.");

	}  

	@PermitAll
	@GET
	@Path("/{networkid}/metadata")
	@ApiDoc("The getAspectElement method returns elements in the specified aspect up to the given limit.")
	public Response getNetworkCXMetadataCollection(	@PathParam("networkid") final String networkId)
			throws Exception {

    	logger.info("[start: Getting CX metadata from network {}]", networkId);

	//	if ( isReadable(networkId) ) {

		/*	try (CXNetworkExporter dao = new CXNetworkExporter(networkId) ) {
				MetaDataCollection md = 
						dao.getMetaDataCollection();
				logger.info("[end: Return cached network {}]", networkId);
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				JsonWriter wtr = JsonWriter.createInstance(baos,true);
				md.toJson(wtr);
				String s = baos.toString();//"java.nio.charset.StandardCharsets.UTF_8");
				return 	Response.ok().type(MediaType.APPLICATION_JSON_TYPE).entity(s).build();
			} */
			return 	Response.ok().type(MediaType.APPLICATION_JSON_TYPE).entity("").build(); //to be removed when implemented.

	/*	}
		else {
			logger.info("[end: Return CX metadata from network {}]", networkId);
            throw new UnauthorizedOperationException("User doesn't have read access to this network.");
		} */
	}  
	
	
	@PermitAll
	@GET
	@Path("/{networkid}/aspect/{aspectname}/{limit}")
	@ApiDoc("The getAspectElement method returns elements in the specified aspect up to the given limit.")
	//TODO: handle cached network from hardDrive.
	public Response getAspectElements(	@PathParam("networkid") final String networkId,
			@PathParam("aspectname") final String aspectName,
			@PathParam("limit") final int limit)
			throws Exception {

    	logger.info("[start: Getting one aspect in network {}]", networkId);

//		if ( isReadable(networkId) ) {
			
			PipedInputStream in = new PipedInputStream();
			PipedOutputStream out;
			try {
				out = new PipedOutputStream(in);
			} catch (IOException e) {
				throw new NdexException("IOExcetion when creating the piped output stream: "+ e.getMessage());
			}
			
	/*		try (SingleNetworkDAO dao = new SingleNetworkDAO (networkId) ) {
				Set<String> missingAspect = dao.findMissingAspect(new HashSet<String>(Arrays.asList(aspectName)));
				if ( !missingAspect.isEmpty()) {
					out.close();
					throw new NdexException ("Aspect " + aspectName + " not found in network.");
				}
			}; */
			
			new CXAspectElementsWriterThread(out,networkId, aspectName, limit).start();
			logger.info("[end: Return get one aspect in network {}]", networkId);
			return 	Response.ok().type(MediaType.APPLICATION_JSON_TYPE).entity(in).build();
	/*	}
		else
            throw new UnauthorizedOperationException("User doesn't have read access to this network."); */
	}  
	
	
	@PermitAll
	@GET
	@Path("/{networkid}/asCX")
	@ApiDoc("The getCompleteNetwork method enables an application to obtain an entire network as a CX " +
	        "structure. This is performed as a monolithic operation, so care should be taken when requesting " +
	        "very large networks. Applications can use the getNetworkSummary method to check the node " +
	        "and edge counts for a network before attempting to use getCompleteNetwork. As an " +
	        "optimization, networks that are designated read-only (see Make a Network Read-Only below) " +
	        "are cached by NDEx for rapid access. ")
	// new Implmentation to handle cached network 
	//TODO: handle cached network from hardDrive.
	public Response getCompleteNetworkAsCX(	@PathParam("networkid") final String networkId)
			throws IllegalArgumentException, NdexException, SQLException {

    	logger.info("[start: Getting complete network {}]", networkId);

    	
    	try (NetworkDAO dao = new NetworkDAO()) {
    		if ( ! dao.isReadable(UUID.fromString(networkId), getLoggedInUserId()))
                throw new UnauthorizedOperationException("User doesn't have read access to this network.");

    	}
    	
		String cxFilePath = Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/network.cx";

    	try {
			FileInputStream in = new FileInputStream(cxFilePath)  ;
		
		//	setZipFlag();
			logger.info("[end: Return network {}]", networkId);
			return 	Response.ok().type(MediaType.APPLICATION_JSON_TYPE).entity(in).build();
		} catch (IOException e) {
			logger.error("[end: Ndex server can't find file: {}]", e.getMessage());
			throw new NdexException ("Ndex server can't find file: " + e.getMessage());
		}
		
	}  

	@PermitAll
	@GET
	@Path("/{networkid}/sample")
	@ApiDoc("The getSampleNetworkAsCX method enables an application to obtain a sample of the given network as a CX " +
	        "structure. The sample network is a 500 random edge subnetwork of the original network if it was created by the server automatically. "
	        + "User can also upload their own sample network if they "
	        + "")
	public Response getSampleNetworkAsCX(	@PathParam("networkid") final String networkId)
			throws IllegalArgumentException, NdexException, SQLException {

    	logger.info("[start: Getting sample network {}]", networkId);
  	
    	try (NetworkDAO dao = new NetworkDAO()) {
    		if ( ! dao.isReadable(UUID.fromString(networkId), getLoggedInUserId()))
                throw new UnauthorizedOperationException("User doesn't have read access to this network.");

    	}
    	
		String cxFilePath = Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/sample.cx";
		
		try {
			FileInputStream in = new FileInputStream(cxFilePath)  ;
		
			//	setZipFlag();
			logger.info("[end: Return network {}]", networkId);
			return 	Response.ok().type(MediaType.APPLICATION_JSON_TYPE).entity(in).build();
		} catch ( FileNotFoundException e) {
				throw new ObjectNotFoundException("Sample network of " + networkId + " not found");
		}  
		
	}  
	
	
	
	private class CXNetworkWriterThread extends Thread {
		private OutputStream o;
		private String networkId;
		public CXNetworkWriterThread (OutputStream out, String  networkUUIDStr) {
			o = out;
			networkId = networkUUIDStr;
		}
		
		public void run() {
		/*	try (CXNetworkExporter dao = new CXNetworkExporter (networkId) ) {
				    dao.writeNetworkInCX(o, true);
			} catch (IOException e) {
					logger.error("IOException in CXNetworkWriterThread: " + e.getMessage());
					e.printStackTrace();
			} catch (NdexException e1) {
			     logger.error("Ndex error: " + e1.getMessage());
			     e1.printStackTrace();
			} catch (Exception e1) {
				logger.error("Ndex excption: " + e1.getMessage());
				e1.printStackTrace();
			} finally {
				try {
					o.flush();
					o.close();
				} catch (IOException e) {
					e.printStackTrace();
					logger.error("Failed to close outputstream in CXNetworkWriterThread.");
				}
			} */
		}
		
	}
	
	private class CXNetworkQueryWriterThread extends Thread {
		private OutputStream o;
		private String networkId;
		private CXSimplePathQuery parameters;
		
		public CXNetworkQueryWriterThread (OutputStream out, String  networkUUIDStr, CXSimplePathQuery query) {
			o = out;
			networkId = networkUUIDStr;
			this.parameters = query;
		}
		
		public void run() {
	/*		try (CXNetworkExporter dao = new CXNetworkExporter (networkId) ) {
				    dao.exportSubnetworkInCX(o, parameters,true);   
			} catch (IOException e) {
					logger.error("IOException in CXNetworkWriterThread: " + e.getMessage());
					e.printStackTrace();
			} catch (NdexException e1) {
			     logger.error("Ndex error: " + e1.getMessage());
			     e1.printStackTrace();
			} catch (Exception e1) {
				logger.error("Ndex excption: " + e1.getMessage());
				e1.printStackTrace();
			} finally {
				try {
					o.flush();
					o.close();
				} catch (IOException e) {
					e.printStackTrace();
					logger.error("Failed to close outputstream in CXNetworkWriterThread.");
				}
			} */
		}
		
	}


	private class CXAspectElementsWriterThread extends Thread {
		private OutputStream o;
		private String networkId;
		private String aspect;
		private int limit;
		public CXAspectElementsWriterThread (OutputStream out, String networkId, String aspectName, int limit) {
			o = out;
			this.networkId = networkId;
			aspect = aspectName;
			this.limit = limit;
		}
		
		public void run() {
	/*		try (CXNetworkExporter dao = new CXNetworkExporter (networkId)) {
				    dao.writeOneAspectInCX(o, aspect, limit, true); 
			} catch (IOException e) {
					logger.error("IOException in CXAspectElementWriterThread: " + e.getMessage());
					e.printStackTrace();
			} catch (NdexException e1) {
			     logger.error("Ndex error: " + e1.getMessage());
			     e1.printStackTrace();
			} catch (Exception e1) {
				logger.error("Ndex exception: " + e1.getMessage());
				e1.printStackTrace();
			} finally {
				try {
					o.flush();
					o.close();
				} catch (IOException e) {
					logger.error("Failed to close outputstream in CXElementWriterWriterThread");
					e.printStackTrace();
				}
			} */
		}
		
	}

	
	private class CXNetworkAspectsWriterThread extends Thread {
		private OutputStream o;
		private String networkId;
		private Set<String> aspects;
		
		public CXNetworkAspectsWriterThread (OutputStream out, String networkId, Set<String> aspectNames) {
			o = out;
			this.networkId = networkId;
			this.aspects = aspectNames;
		}
		
		public void run() {
	/*		try (CXNetworkExporter dao = new CXNetworkExporter (networkId)) {
				    dao.writeAspectsInCX(o, aspects, true);
			} catch (IOException e) {
					logger.error("IOException in CXNetworkAspectsWriterThread: " + e.getMessage());
					e.printStackTrace();
			} catch (NdexException e1) {
			     logger.error("Ndex error: " + e1.getMessage());
			     e1.printStackTrace();
			} catch (Exception e1) {
				logger.error("Ndex exception: " + e1.getMessage());
				e1.printStackTrace();
			} finally {
				try {
					o.flush();
					o.close();
				} catch (IOException e) {
					e.printStackTrace();
					logger.error("Failed to close outputstream in CXNetworkAspectsWriterThread. " + e.getMessage());
				} 
			} */
		}
		
	}
	private class CXNetworkLoadThread extends Thread {
		private UUID networkId;
		public CXNetworkLoadThread (UUID networkUUID  ) {
			this.networkId = networkUUID;
		}
		
		public void run() {
            
		}
	}

	/*
	
	private ProvenanceEntity getProvenanceEntityFromMultiPart(Map<String, List<InputPart>> uploadForm) throws NdexException, IOException {
		
	       List<InputPart> parts = uploadForm.get("provenance");
	       if (parts == null)
	    	   return null;

		  	StringBuffer sb = new StringBuffer();
		    for (InputPart inputPart : parts) {
		        	   org.jboss.resteasy.plugins.providers.multipart.MultipartInputImpl.PartImpl p =
		        			   (org.jboss.resteasy.plugins.providers.multipart.MultipartInputImpl.PartImpl) inputPart;
		        	   sb.append(p.getBodyAsString());
		    }
		    
		    ObjectMapper mapper = new ObjectMapper();
		    ProvenanceEntity entity = mapper.readValue(sb.toString(), ProvenanceEntity.class);		
	    	
		    if (entity == null || entity.getUri() == null)
	    		   throw new NdexException ("Malformed provenance parameter found in posted form data.");  
		   return entity;
	} */
	

/* Note: Will be implemented in services	
	@PermitAll
	@GET
	@Path("/export/{networkId}/{format}")
	@Produces("application/json")
    @ApiDoc("Retrieve an entire network specified by 'networkId' as a Network object.  (Compare this method to " +
            "getCompleteNetworkAsPropertyGraph).")
	public String exportNetwork(	@PathParam("networkId") final String networkId,
			@PathParam("format") final String format)
			throws  NdexException {

    	logger.info("[start: request to export network {}]", networkId);
		
		if ( isReadable(networkId) ) {
			
			String networkName =null;
			try (NetworkDAO networkdao = new NetworkDAO(NdexDatabase.getInstance().getAConnection())){
				networkName = networkdao.getNetworkSummaryById(networkId).getName();
			}
			
			Task exportNetworkTask = new Task();
			exportNetworkTask.setTaskType(TaskType.EXPORT_NETWORK_TO_FILE);
			exportNetworkTask.setPriority(Priority.LOW);
			exportNetworkTask.setResource(networkId); 
			exportNetworkTask.setStatus(Status.QUEUED);
			exportNetworkTask.setDescription("Export network \""+ networkName + "\" in " + format + " format");
			
			try {
				exportNetworkTask.setFormat(FileFormat.valueOf(format));
			} catch ( Exception e) {
				logger.error("[end: Exception caught:]{}", e);
				throw new NdexException ("Invalid network format for network export.");
			}
			
			try (TaskDAO taskDAO = new TaskDAO(NdexDatabase.getInstance().getAConnection()) ){ 
				exportNetworkTask.setTaskOwnerId(getLoggedInUser().getExternalId());
				UUID taskId = taskDAO.createTask(exportNetworkTask);
				taskDAO.commit();
				logger.info("[start: task created to export network {}]", networkId);
				return taskId.toString();
			}  catch (Exception e) {
				logger.error("[end: Exception caught:]{}", e);
				
				throw new NdexException(e.getMessage());
			} 
		}
		logger.error("[end: User doesn't have read access network {}. Throwing  UnauthorizedOperationException]", networkId);
		throw new UnauthorizedOperationException("User doesn't have read access to this network.");
	}  
	*/
	


	/**************************************************************************
	 * Retrieves array of user membership objects
	 *
	 * @param networkId
	 *            The network ID.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws ObjectNotFoundException
	 *             The group doesn't exist.
	 * @throws NdexException
	 *             Failed to query the database.
	 * @throws SQLException 
	 **************************************************************************/

	@GET
	//@PermitAll
	@Path("/{networkid}/user/{permission}/{start}/{size}")
	@Produces("application/json")
    @ApiDoc("Retrieves a list of Membership objects which specify user permissions for the network specified by " +
            "'networkId'. The value of the 'permission' parameter constrains the type of the returned Membership " +
            "objects and may take the following set of values: READ, WRITE, and ADMIN.  READ, WRITE, and ADMIN are mutually exclusive. Memberships of all types can " +
            "be retrieved by permission = 'ALL'.   The maximum number of Membership objects to retrieve in the query " +
            "is set by 'blockSize' (which may be any number chosen by the user) while  'skipBlocks' specifies the " +
            "number of blocks that have already been read.")
	public List<Membership> getNetworkUserMemberships(@PathParam("networkid") final String networkIdStr,
			@PathParam("permission") final String permissions ,
			@PathParam("start") int skipBlocks,
			@PathParam("size") int blockSize) throws NdexException, SQLException {

		logger.info("[start: Get {} accounts on network {}, skipBlocks {},  blockSize {}]", 
				permissions, networkIdStr, skipBlocks, blockSize);
		
		Permissions permission = null;
		if ( ! permissions.toUpperCase().equals("ALL")) {
			permission = Permissions.valueOf(permissions.toUpperCase());
		} 
		
		UUID networkId = UUID.fromString(networkIdStr);
		
		try (NetworkDAO networkDao = new NetworkDAO()) {
			if ( !networkDao.isAdmin(networkId, getLoggedInUserId())) 
				throw new UnauthorizedOperationException("Authenticated user is not the admin of this network");

			List<Membership> results = networkDao.getNetworkUserMemberships(
					networkId, permission, skipBlocks, blockSize);
			logger.info("[end: Got {} members returned for network {}]", 
					results.size(), networkId);
			return results;
		} 
	}

	@DELETE
	@Path("/{networkid}/member/user/{userid}")
	@Produces("application/json")
    @ApiDoc("Removes any permission for the network specified by 'networkId' for the user specified by 'userUUID': it" +
            " deletes any Membership object that specifies a permission for the user-network combination. This method" +
            " will return an error if the authenticated user making the request does not have sufficient permissions " +
            "to make the deletion or if the network or user is not found. Removal is also denied if it would leave " +
            "the network without any user having ADMIN permissions: NDEx does not permit networks to become 'orphans'" +
            " without any owner.")
	public int deleteNetworkUserMembership(
			@PathParam("networkid") final String networkId,
			@PathParam("userid") final String  userUUID
			)
			throws IllegalArgumentException, NdexException, SolrServerException, IOException, SQLException {
		
		logger.info("[start: Removing any permissions for network {} for user {}]", networkId, userUUID);
		
		try (NetworkDAO networkDao = new NetworkDAO()){
			User user = getLoggedInUser();
			UUID networkUUID = UUID.fromString(networkId);
			
			networkDao.checkPermissionOperationCondition(networkUUID, user.getExternalId());
			
			int count = networkDao.revokeUserPrivilege(UUID.fromString(networkId), UUID.fromString(userUUID));
            networkDao.commit();
    		logger.info("[end: Removed any permissions for network {} for user {}]", networkId, userUUID);
            return count;
		} 
	}
	
	@DELETE
	@Path("/{networkid}/member/group/{groupid}")
	@Produces("application/json")
    @ApiDoc("Removes any permission for the network specified by 'networkId' for the user specified by 'userUUID': it" +
            " deletes any Membership object that specifies a permission for the user-network combination. This method" +
            " will return an error if the authenticated user making the request does not have sufficient permissions " +
            "to make the deletion or if the network or user is not found. Removal is also denied if it would leave " +
            "the network without any user having ADMIN permissions: NDEx does not permit networks to become 'orphans'" +
            " without any owner.")
	public int deleteNetworkGroupMembership(
			@PathParam("networkid") final String networkId,
			@PathParam("groupid") final String  groupUUID
			)
			throws IllegalArgumentException, NdexException, SolrServerException, IOException, SQLException {
		
		logger.info("[start: Removing any permissions for network {} for user {}]", networkId, groupUUID);
		
		try (NetworkDAO networkDao = new NetworkDAO()){
			User user = getLoggedInUser();
			UUID networkUUID = UUID.fromString(networkId);
			networkDao.checkPermissionOperationCondition(networkUUID, user.getExternalId());
			
			int count = networkDao.revokeGroupPrivilege(UUID.fromString(networkId), UUID.fromString(groupUUID));
            networkDao.commit();
    		logger.info("[end: Removed any permissions for network {} for group {}]", networkId, groupUUID);
            return count;
		} 
	}
	
	/*
	 *
	 * Operations on Network permissions
	 * 
	 */

/*	@POST
	@Path("/{networkId}/member")
	@Produces("application/json")
    @ApiDoc("POSTs a Membership object to update the permission of a user specified by userUUID for the network " +
            "specified by networkUUID. The permission is updated to the value specified in the 'permission' field of " +
            "the Membership. This method returns 1 if the update is performed and 0 if the update is redundant, " +
            "where the user already has the specified permission. It also returns an error if the authenticated user " +
            "making the request does not have sufficient permissions or if the network or user is not found. It also " +
            "returns an error if it would leave the network without any user having ADMIN permissions: NDEx does not " +
            "permit networks to become 'orphans' without any owner. Because we only allow user to be the administrator of a network, "
            + "Granting ADMIN permission to another user will move the admin privilege (ownership) from the network's"
            + " previous administrator (owner) to the new user.")
	public int updateNetworkMembership(
			@PathParam("networkId") final String networkIdStr,
			final Membership membership
			)
			throws IllegalArgumentException, NdexException, SolrServerException, IOException, SQLException {

		logger.info("[start: Updating membership for network {}]", networkIdStr);
		
		try (NetworkDAO networkDao = new NetworkDAO()){

			User user = getLoggedInUser();
			UUID networkId = UUID.fromString(networkIdStr);

			if (!networkDao.isAdmin(networkId,user.getExternalId())) {

				logger.error("[end: User {} is not an admin of network {}. Throwing  UnauthorizedOperationException ...]", 
						user.getExternalId().toString(), networkId);
				
				throw new UnauthorizedOperationException("Unable to update network membership: user is not an admin of this network.");
			}

			if ( networkDao.networkIsLocked(networkId)) {
				networkDao.close();
				logger.info("[end: Can't update locked network {}]", networkId);
				throw new NdexException ("Can't modify locked network. The network is currently locked by another updating thread.");
			} 

	        int count = membership.getMemberType().equals("USER")? 
	        		networkDao.grantPrivilegeToUser(networkId, membership.getMemberUUID(), membership.getPermissions(), membership.getMemberAccountName()):
	        			networkDao.grantPrivilegeToGroup(networkId, membership.getMemberUUID(), membership.getPermissions());
			networkDao.commit();
			logger.info("[end: Updated membership for network {}]", networkId);
	        return count;
		} finally {
		}
	} */

	@POST
	@Path("/{networkid}/member/user/{userid}")
	@Produces("application/json")
    @ApiDoc("POSTs a Membership object to update the permission of a user specified by userUUID for the network " +
            "specified by networkUUID. The permission is updated to the value specified in the 'permission' field of " +
            "the Membership. This method returns 1 if the update is performed and 0 if the update is redundant, " +
            "where the user already has the specified permission. It also returns an error if the authenticated user " +
            "making the request does not have sufficient permissions or if the network or user is not found. It also " +
            "returns an error if it would leave the network without any user having ADMIN permissions: NDEx does not " +
            "permit networks to become 'orphans' without any owner. Because we only allow user to be the administrator of a network, "
            + "Granting ADMIN permission to another user will move the admin privilege (ownership) from the network's"
            + " previous administrator (owner) to the new user.")
	public int updateNetworkUserMembership(
			@PathParam("networkid") final String networkIdStr,
			@PathParam("userid") final String userIdStr,
			final Permissions permission
			)
			throws IllegalArgumentException, NdexException, SolrServerException, IOException, SQLException {

		logger.info("[start: Updating membership for network {}]", networkIdStr);
		
		try (NetworkDAO networkDao = new NetworkDAO()){

			User user = getLoggedInUser();
			UUID networkId = UUID.fromString(networkIdStr);
			
			networkDao.checkPermissionOperationCondition(networkId, user.getExternalId());

	        int count = networkDao.grantPrivilegeToUser(networkId, UUID.fromString(userIdStr), permission);
	        			//networkDao.grantPrivilegeToGroup(networkId, membership.getMemberUUID(), membership.getPermissions());
			networkDao.commit();
			logger.info("[end: Updated membership for network {}]", networkId);
	        return count;
		} 
	}	

	
	@POST
	@Path("/{networkid}/member/group/{groupid}")
	@Produces("application/json")
    @ApiDoc("POSTs a Membership object to update the permission of a user specified by userUUID for the network " +
            "specified by networkUUID. The permission is updated to the value specified in the 'permission' field of " +
            "the Membership. This method returns 1 if the update is performed and 0 if the update is redundant, " +
            "where the user already has the specified permission. It also returns an error if the authenticated user " +
            "making the request does not have sufficient permissions or if the network or user is not found. It also " +
            "returns an error if it would leave the network without any user having ADMIN permissions: NDEx does not " +
            "permit networks to become 'orphans' without any owner. Because we only allow user to be the administrator of a network, "
            + "Granting ADMIN permission to another user will move the admin privilege (ownership) from the network's"
            + " previous administrator (owner) to the new user.")
	public int updateNetworkGroupMembership(
			@PathParam("networkid") final String networkIdStr,
			@PathParam("groupid") final String groupIdStr,
			final Permissions permission
			)
			throws IllegalArgumentException, NdexException, SolrServerException, IOException, SQLException {

		logger.info("[start: Updating membership for network {}]", networkIdStr);
		
		try (NetworkDAO networkDao = new NetworkDAO()){

			User user = getLoggedInUser();
			UUID networkId = UUID.fromString(networkIdStr);
			
			networkDao.checkPermissionOperationCondition(networkId,user.getExternalId());
	        int count = networkDao.grantPrivilegeToGroup(networkId, UUID.fromString(groupIdStr), permission);
			networkDao.commit();
			logger.info("[end: Updated membership for network {}]", networkId);
	        return count;
		} 
	}	
	
	
	
	@POST
	@Path("/{networkid}/summary")
	@Produces("application/json")
	@ApiDoc("This method updates the profile information of the network specified by networkId based on a " +
	        "POSTed JSON object specifying the attributes to update. Any profile attributes specified will be " + 
	        "updated but attributes that are not specified will have no effect - omission of an attribute does " +
	        "not mean deletion of that attribute. The network profile attributes that can be updated by this " +
	        "method are: 'name', 'description', 'version'. visibility are no longer updated by this function. It is managed by setNetworkFlag function from 2.0")
	public void updateNetworkProfile(
			@PathParam("networkid") final String networkId,
			final NetworkSummary partialSummary
			)
            throws  NdexException, SQLException, SolrServerException , IOException, IllegalArgumentException 
    {
		logger.info("[start: Updating profile information of network {}]", networkId);
		
		try (NetworkDAO networkDao = new NetworkDAO()){

			User user = getLoggedInUser();
			UUID networkUUID = UUID.fromString(networkId);
	
	  	    if(networkDao.isReadOnly(networkUUID)) {
				logger.info("[end: Can't modify readonly network {}]", networkId);
				throw new NdexException ("Can't update readonly network.");				
			} 
			
			if ( !networkDao.isWriteable(networkUUID, user.getExternalId())) {
				logger.error("[end: No write permissions for user account {} on network {}]", 
						user.getUserName(), networkId);
		        throw new UnauthorizedOperationException("User doesn't have write permissions for this network.");
			} 
			
			Map<String,String> newValues = new HashMap<> ();
	        List<SimplePropertyValuePair> entityProperties = new ArrayList<>();

			if ( partialSummary.getName() != null) {
				newValues.put(NdexClasses.Network_P_name, partialSummary.getName());
			    entityProperties.add( new SimplePropertyValuePair("dc:title", partialSummary.getName()) );
			}
					
			if ( partialSummary.getDescription() != null) {
					newValues.put( NdexClasses.Network_P_desc, partialSummary.getDescription());
		            entityProperties.add( new SimplePropertyValuePair("description", partialSummary.getDescription()) );
			}
				
			if ( partialSummary.getVersion()!=null ) {
					newValues.put( NdexClasses.Network_P_version, partialSummary.getVersion());
		            entityProperties.add( new SimplePropertyValuePair("version", partialSummary.getVersion()) );
			}

			if ( newValues.size() > 0 ) { 
				
				if ( networkDao.networkIsLocked(networkUUID)) {
					logger.info("[end: Can't update locked network {}]", networkId);
					throw new NdexException ("Can't modify locked network.");
				} 
				
				try {
					networkDao.lockNetwork(networkUUID);
				
					networkDao.updateNetworkProfile(networkUUID, newValues);

					//DW: Handle provenance
					//Special Logic. Test whether we should record provenance at all.
					//If the only thing that has changed is the visibility, we should not add a provenance
					//event.
					ProvenanceEntity oldProv = networkDao.getProvenance(networkUUID);
					String oldName = "", oldDescription = "", oldVersion ="";
					if ( oldProv != null ) {
						for( SimplePropertyValuePair oldProperty : oldProv.getProperties() ) {
							if( oldProperty.getName() == null )
								continue;
							if( oldProperty.getName().equals("dc:title") )
								oldName = oldProperty.getValue().trim();
							else if( oldProperty.getName().equals("description") )
								oldDescription = oldProperty.getValue().trim();
							else if( oldProperty.getName().equals("version") )
								oldVersion = oldProperty.getValue().trim();
						}
					}

					//Treat all summary values that are null like ""
					String summaryName = partialSummary.getName() == null ? "" : partialSummary.getName().trim();
					String summaryDescription = partialSummary.getDescription() == null ? "" : partialSummary.getDescription().trim();
					String summaryVersion = partialSummary.getVersion() == null ? "" : partialSummary.getVersion().trim();

					if( !oldName.equals(summaryName) || !oldDescription.equals(summaryDescription) || !oldVersion.equals(summaryVersion) )
					{
						ProvenanceEntity newProv = new ProvenanceEntity();
						if ( oldProv !=null )   //TODO: initialize the URI properly when there is null.
							newProv.setUri(oldProv.getUri());

						newProv.setProperties(entityProperties);

						ProvenanceEvent event = new ProvenanceEvent(NdexProvenanceEventType.UPDATE_NETWORK_PROFILE, partialSummary.getModificationTime());

						List<SimplePropertyValuePair> eventProperties = new ArrayList<>();
						Helper.addUserInfoToProvenanceEventProperties(eventProperties, user);

						if (partialSummary.getName() != null)
							eventProperties.add(new SimplePropertyValuePair("dc:title", partialSummary.getName()));

						if (partialSummary.getDescription() != null)
							eventProperties.add(new SimplePropertyValuePair("description", partialSummary.getDescription()));

						if (partialSummary.getVersion() != null)
							eventProperties.add(new SimplePropertyValuePair("version", partialSummary.getVersion()));

						event.setProperties(eventProperties);
						List<ProvenanceEntity> oldProvList = new ArrayList<>();
						oldProvList.add(oldProv);
						event.setInputs(oldProvList);

						newProv.setCreationEvent(event);
						networkDao.setProvenance(networkUUID, newProv);
					}
				
					NetworkSummary fullSummary = networkDao.getNetworkSummaryById(networkUUID);
					
					//update the networkProperty aspect 
					List<NetworkAttributesElement> attrs = getNetworkAttributeAspectsFromSummary(fullSummary);
					if ( attrs.size() > 0 ) {					
						try (CXAspectWriter writer = new CXAspectWriter(Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/aspects/" 
								+ NetworkAttributesElement.ASPECT_NAME) ) {
							for ( NetworkAttributesElement e : attrs) {
								writer.writeCXElement(e);	
								writer.flush();
							}
						}
					}
					
					//update metadata
					MetaDataCollection metadata = networkDao.getMetaDataCollection(networkUUID);
					MetaDataElement elmt = metadata.getMetaDataElement(NetworkAttributesElement.ASPECT_NAME);
					if ( elmt == null) {
						elmt = new MetaDataElement();
					}
					elmt.setElementCount(Long.valueOf(attrs.size()));
					networkDao.updateMetadataColleciton(networkUUID, metadata);

					//Recreate the CX file 					
					CXNetworkFileGenerator g = new CXNetworkFileGenerator(networkUUID, fullSummary, metadata);
					g.reCreateCXFile();
					
					networkDao.unlockNetwork(networkUUID);
					//	networkDao.commit();
				} catch ( SolrServerException | SQLException | IOException | IllegalArgumentException |NdexException e ) {
					networkDao.rollback();
					try {
						networkDao.unlockNetwork(networkUUID);
					} catch (SQLException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					throw e;
				}
				
			}
		} finally {
			logger.info("[end: Updated profile information of network {}]", networkId);
		}
	}


/* Note: Will be implemented in service
	@PermitAll
	@POST
	@Path("/{networkId}/asCX/query")
	@Produces("application/json")
    @ApiDoc("Retrieves a 'neighborhood' subnetwork of the network specified by ‘networkId’. The query finds " +
            "the subnetwork by a traversal of the network starting with nodes associated with identifiers " +
            "specified in a POSTed JSON query object. " +
            "For more information, please click <a href=\"http://www.ndexbio.org/using-the-ndex-server-api/#queryNetwork\">here</a>.")
	public Response queryNetworkAsCX(
			@PathParam("networkId") final String networkId,
			final CXSimplePathQuery queryParameters
			)

			throws Exception {
		
		logger.info("[start: Retrieving neighborhood subnetwork for network {} with phrase \"{}\"]", 
				networkId, queryParameters.getSearchString());
		
		if ( !isReadable(networkId)) {
			 logger.error("[end: User doesn't have read permission to retrieve neighborhood subnetwork for network {} with phrase\"{}\"]",  
					   networkId, queryParameters.getSearchString());
		       throw new UnauthorizedOperationException("User doesn't have read permissions for this network.");
		}

		Set<String> aspects = queryParameters.getAspects();
		if (aspects == null) {
			aspects = new TreeSet<>();
			queryParameters.setAspects(aspects);
		}
		if ( aspects.size() == 0) {
			aspects.add(NodesElement.ASPECT_NAME);
			aspects.add(EdgesElement.ASPECT_NAME);
		}
		
		try (CXNetworkExporter cxexporter = new CXNetworkExporter(networkId);) {

			PipedInputStream in = new PipedInputStream();
			PipedOutputStream out;
			try {
				out = new PipedOutputStream(in);
			} catch (IOException e) {
				throw new NdexException("IOExcetion when creating the piped output stream: "+ e.getMessage());
			}
			
			new CXNetworkQueryWriterThread(out,networkId,queryParameters).start();
			//setZipFlag();
			logger.info("[end: Return cx network {}]", networkId);
			return 	Response.ok().type(MediaType.APPLICATION_JSON_TYPE).entity(in).build();
		} 
	} 
	
	
	
	@PermitAll
	@POST
	@Path("/{networkId}/asNetwork/prototypeNetworkQuery")
	@Produces("application/json")
    @ApiDoc("This method retrieves a filtered subnetwork of the network specified by ‘networkId’ based on a " +
            "POSTed JSON query object.  The returned subnetwork contains edges which satisfy both the " +
            "edgeFilter and the nodeFilter up to a specified limit. The subnetwork is returned as a Network " +
            "object containing the selected edges plus all other network elements relevant to the edges. " +
            "For more information, please click <a href=\"http://www.ndexbio.org/using-the-ndex-server-api/#queryNetworkByEdgeFilter\">here</a>.")
	public Network queryNetworkByEdgeFilter(
			@PathParam("networkId") final String networkId,
			final EdgeCollectionQuery query
			)

			throws IllegalArgumentException, NdexException {

		logger.info("[start: filter query on network {}]", networkId);
		

		if ( !isReadable(networkId ) ) {
			logger.error("[end: Network {} not readable for this user]", networkId);
			throw new UnauthorizedOperationException("Network " + networkId + " is not readable to this user.");
		}
		
		NetworkFilterQueryExecutor queryExecutor = NetworkFilterQueryExecutorFactory.createODBExecutor(networkId, query);
		
		Network result =  queryExecutor.evaluate();
		logger.info("[end: filter query on network {}]", networkId);
        return result;
	} */
	
	

/*	
	@PermitAll
	@POST
	@Path("/{networkId}/asPropertyGraph/query")
	@Produces("application/json")
    @ApiDoc("Retrieves a 'neighborhood' subnetwork of a network based on identifiers specified in a POSTed " +
            "SimplePathQuery object. The network is specified by networkId. In the first step of the query, " +
            "a set of base terms exactly matching identifiers found in the searchString of the SimplePathQuery is " +
            "selected. In the second step, nodes are selected that reference the base terms identified in the network" +
            ". Finally, a set of edges is selected by traversing outwards from each of these selected nodes, " +
            "up to the limit specified by the 'searchDepth' field of the SimplePathQuery.  The subnetwork is returned" +
            " as a PropertyGraphNetwork object containing the selected PropertyGraphEdge objects along with any other" +
            " network information relevant to the edges.")
	public PropertyGraphNetwork queryNetworkAsPropertyGraph(
			@PathParam("networkId") final String networkId,
			final SimplePathQuery queryParameters
//			@PathParam("skipBlocks") final int skipBlocks, 
//			@PathParam("blockSize") final int blockSize
			)

			throws IllegalArgumentException, NdexException {

		logger.info("[start: Retrieving neighborhood subnetwork for network {} based on SimplePathQuery object]",
				networkId);
		
		try (NetworkDocDAO networkDao = new NetworkDocDAO() ) {

			if ( !isReadable( networkId)) {
				logger.error("[end: network {} is not readble to this user]",
						networkId);
			    throw new UnauthorizedOperationException("User doesn't have read permissions for this network.");
			}
			
			NetworkAOrientDBDAO dao = NetworkAOrientDBDAO.getInstance();

			PropertyGraphNetwork n = dao.queryForSubPropertyGraphNetworkV2(networkId, queryParameters);
			logger.info("[end: Retrieved neighborhood subnetwork for network {} based on SimplePathQuery object]",
						networkId);				
			return n;

		} 

	}
*/



	@POST
	@PermitAll
	@Path("/textsearch/{start}/{size}")
	@Produces("application/json")
	@ApiDoc("This method returns a list of NetworkSummary objects based on a POSTed query JSON object. " +
            "The maximum number of NetworkSummary objects to retrieve in the query is set by the integer " +
            "value 'blockSize' while 'skipBlocks' specifies number of blocks that have already been read. " +
            "For more information, please click <a href=\"http://www.ndexbio.org/using-the-ndex-server-api/#searchNetwork\">here</a>.")
	public NetworkSearchResult searchNetwork_solr(
			final SimpleNetworkQuery query,
			@PathParam("start") final int skipBlocks,
			@PathParam("size") final int blockSize)
			throws IllegalArgumentException, NdexException {
		
		logger.info("[start: Retrieving NetworkSummary objects using query \"{}\"]", 
				query.getSearchString());		
		
    	if(query.getAccountName() != null)
    		query.setAccountName(query.getAccountName().toLowerCase());
        
    	try (NetworkDAO dao = new NetworkDAO()) {

			NetworkSearchResult result = dao.findNetworks(query, skipBlocks, blockSize, this.getLoggedInUser());
			logger.info("[end: Retrieved {} NetworkSummary objects]", result.getNetworks().size());		
			return result;

        } catch (Exception e) {
			logger.error("[end: Retrieving NetworkSummary objects using query \"{}\". Exception caught:]{}", 
					query.getSearchString(), e);	
			e.printStackTrace();
        	throw new NdexException(e.getMessage());
        }
	}

	@POST
	@PermitAll
	@Path("/search/{start}/{size}")
	@Produces("application/json")
	@ApiDoc("This method returns a list of NetworkSummary objects based on a POSTed query JSON object. " +
            "The maximum number of NetworkSummary objects to retrieve in the query is set by the integer " +
            "value 'blockSize' while 'skipBlocks' specifies number of blocks that have already been read. " +
            "For more information, please click <a href=\"http://www.ndexbio.org/using-the-ndex-server-api/#searchNetwork\">here</a>.")
	public Collection<NetworkSummary> searchNetworkV1(
			final SimpleNetworkQuery query,
			@PathParam("start") final int skipBlocks,
			@PathParam("size") final int blockSize)
			throws IllegalArgumentException, NdexException {
		
		return searchNetwork_solr(query, skipBlocks, blockSize).getNetworks();
	}
	
	
    @PUT
    @Path("/asCX/{networkid}")
    @Consumes("multipart/form-data")
    @Produces("application/json")
    @ApiDoc("This method updates an existing network with new content. The method takes a Network CX " +
            "document as the PUT data. The Network's UUID is specified in the URL if this function. " +
            " This method errors if the Network object is not " +
            "provided or if its UUID does not correspond to an existing network on the NDEx Server. It also " +
            "errors if the Network object is larger than a maximum size for network creation set in the NDEx " +
            "server configuration. Network UUID is returned. This function also takes an optional 'provenance' field in the posted form."
            + " See createCXNetwork function for more details of this parameter.")
    public void updateCXNetwork(final @PathParam("networkid") String networkIdStr,
    		MultipartFormDataInput input) throws Exception 
    {
    	
		logger.info("[start: Updating network {} using CX data]", networkIdStr);

        UUID networkId = UUID.fromString(networkIdStr);

        String ownerAccName = null;

        try ( NetworkDAO daoNew = new NetworkDAO() ) {
           User user = getLoggedInUser();
           
         try {
	  	   if( daoNew.isReadOnly(networkId)) {
				logger.info("[end: Can't modify readonly network {}]", networkId);
				throw new NdexException ("Can't update readonly network.");				
			} 
			
			if ( !daoNew.isWriteable(networkId, user.getExternalId())) {
				logger.error("[end: No write permissions for user account {} on network {}]", 
						user.getUserName(), networkId);
		        throw new UnauthorizedOperationException("User doesn't have write permissions for this network.");
			} 
			
			if ( daoNew.networkIsLocked(networkId)) {
				daoNew.close();
				logger.info("[end: Can't update locked network {}]", networkId);
				throw new NdexException ("Can't modify locked network.");
			} 
			
			daoNew.lockNetwork(networkId);
			
			ownerAccName = daoNew.getNetworkOwnerAcc(networkId);
			
	        UUID tmpNetworkId = storeRawNetwork (input);

	        daoNew.clearNetworkSummary(networkId);

			java.nio.file.Path src = Paths.get(Configuration.getInstance().getNdexRoot() + "/data/" + tmpNetworkId);
			java.nio.file.Path tgt = Paths.get(Configuration.getInstance().getNdexRoot() + "/data/" + networkId);
			FileUtils.deleteDirectory(new File(Configuration.getInstance().getNdexRoot() + "/data/" + networkId));
			Files.move(src, tgt, StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);  
			daoNew.commit();
			daoNew.unlockNetwork(networkId);
			
           } catch (SQLException | NdexException | IOException e) {
        	  // e.printStackTrace();
        	   daoNew.rollback();
        	   daoNew.unlockNetwork(networkId);  

        	   throw e;
           } 
			
        }  
    	      
	     NdexServerQueue.INSTANCE.addSystemTask(new CXNetworkLoadingTask(networkId, ownerAccName, true));
	    // return networkIdStr; 
    }

    
    
	@DELETE
	@Path("/{networkid}")
	@Produces("application/json")
    @ApiDoc("Deletes the network specified by networkId. There is no method to undo a deletion, so care " +
	        "should be exercised. A user can only delete networks that they own.")
	public void deleteNetwork(final @PathParam("networkid") String id) throws NdexException, SQLException {

//		logger.info("[start: Deleting network {}]", id);
		    
		try (NetworkDAO networkDao = new NetworkDAO()) {
			UUID networkId = UUID.fromString(id);
			UUID userId = getLoggedInUser().getExternalId();
			if(networkDao.isAdmin(networkId, userId) ) {
				if (!networkDao.isReadOnly(networkId) ) {
					if ( !networkDao.networkIsLocked(networkId)) {
					
						NetworkGlobalIndexManager globalIdx = new NetworkGlobalIndexManager();
						globalIdx.deleteNetwork(id);
						SingleNetworkSolrIdxManager idxManager = new SingleNetworkSolrIdxManager(id);
						idxManager.dropIndex();

						networkDao.deleteNetwork(UUID.fromString(id), getLoggedInUser().getExternalId());
						networkDao.commit();
										
//						logger.info("[end: Deleted network {}]", id);
						return;
					}
					throw new NdexException ("Network is locked by another updating process. Please try again.");
				}
				  throw new NdexException("Can't delete a read-only network.");
			}	
			throw new NdexException("Only network owner can delete a network.");	
		} catch (SolrServerException | IOException e ) {
			throw new NdexException ("Error occurred when deleting network: " + e.getMessage(), e);
		}
			
	}
	

	/**************************************************************************
	 * Saves an uploaded network file. Determines the type of file uploaded,
	 * saves the file, and creates a task.
	 *
	 * @param uploadedNetwork
	 *            The uploaded network file.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws NdexException
	 *             Failed to parse the file, or create the network in the
	 *             database.
	 * @throws IOException 
	 **************************************************************************/
	/*
	 * refactored to support non-transactional database operations
	 */
	@POST
	@Path("/upload")
	@Consumes("multipart/form-data")
	@Produces("application/json")
    @ApiDoc("Upload a network file into the current users NDEx account. This can take some time while background " +
            "processing converts the data from the file into the common NDEx format. This method errors if the " +
            "network is missing or if it has no filename or no file data.")
	public Task uploadNetwork( MultipartFormDataInput input) 
                  //@MultipartForm UploadedFile uploadedNetwork)
			throws IllegalArgumentException, SecurityException, NdexException, IOException {

		logger.info("[start: Uploading network file]");

		Map<String, List<InputPart>> uploadForm = input.getFormDataMap();

		List<InputPart> foo = uploadForm.get("filename");
		
		String fname = "";
		for (InputPart inputPart : foo)
	       {
	               // convert the uploaded file to inputstream and write it to disk
	               fname += inputPart.getBodyAsString();
	      }

		if (fname.length() <1) {
			throw new NdexException ("");
		}
		String ext = FilenameUtils.getExtension(fname).toLowerCase();

		if ( !ext.equals("sif") && !ext.equals("xbel") && !ext.equals("xgmml") && !ext.equals("owl") && !ext.equals("cx")
				&& !ext.equals("xls") && ! ext.equals("xlsx")) {
			logger.error("[end: The uploaded file type is not supported; must be Excel, XGMML, SIF, BioPAX cx, or XBEL.  Throwing  NdexException...]");
			throw new NdexException(
					"The uploaded file type is not supported; must be Excel, XGMML, SIF, BioPAX, cx or XBEL.");
		}
		
		UUID taskId = NdexUUIDFactory.INSTANCE.createNewNDExUUID();

		final File uploadedNetworkPath = new File(Configuration.getInstance().getNdexRoot() +
				"/uploaded-networks");
		if (!uploadedNetworkPath.exists())
			uploadedNetworkPath.mkdir();

		String fileFullPath = uploadedNetworkPath.getAbsolutePath() + "/" + taskId + "." + ext;

	       //Get file data to save
	    List<InputPart> inputParts = uploadForm.get("fileUpload");

		
		final File uploadedNetworkFile = new File(fileFullPath);

		if (!uploadedNetworkFile.exists())
			try {
				uploadedNetworkFile.createNewFile();
			} catch (IOException e1) {
				logger.error("[end: Failed to create file {} on server when uploading {}. Exception caught:]{}",
						fileFullPath, fname, e1);
				throw new NdexException ("Failed to create file " + fileFullPath + " on server when uploading " + 
						fname + ": " + e1.getMessage());				
			}

        byte[] bytes = new byte[2048];
		for (InputPart inputPart : inputParts)
	       {
	               
	               // convert the uploaded file to inputstream and write it to disk
	               InputStream inputStream = inputPart.getBody(InputStream.class, null);
	              
	               OutputStream out = new FileOutputStream(new File(fileFullPath));

	               int read = 0;
	               while ((read = inputStream.read(bytes)) != -1) {
	                  out.write(bytes, 0, read);
	               }
	               inputStream.close();
	               out.flush();
	               out.close();
	 
	      }	

		Task processNetworkTask = new Task();
		processNetworkTask.setExternalId(taskId);
		processNetworkTask.setDescription(fname); //uploadedNetwork.getFilename());
		processNetworkTask.setTaskType(TaskType.PROCESS_UPLOADED_NETWORK);
		processNetworkTask.setPriority(Priority.LOW);
		processNetworkTask.setProgress(0);
		processNetworkTask.setResource(fileFullPath);
		processNetworkTask.setStatus(Status.QUEUED);
		processNetworkTask.setTaskOwnerId(this.getLoggedInUser().getExternalId());

		try (TaskDAO dao = new TaskDAO()){
			dao.createTask(processNetworkTask);
			dao.commit();		
			
		} catch (IllegalArgumentException iae) {
			logger.error("[end: Exception caught:]{}", iae);
			throw iae;
		} catch (Exception e) {
			logger.error("[end: Exception caught:]{}", e);
			throw new NdexException(e.getMessage());
		}

		logger.info("[end: Uploading network file. Task for uploading network is created.]");
		return processNetworkTask;
	}



	@PUT
	@Path("/{networkid}/setFlag/{parameter}={value}")
	@Produces("application/json")
    @ApiDoc("Set the system flag specified by ‘parameter’ to ‘value’ for the network with id ‘networkId’. As of " +
	        "NDEx v1.2, the only supported parameter is readOnly={true|false}. In 2.0, we added visibility={PUBLIC|PRIVATE}")
	public void setNetworkFlag(
			@PathParam("networkid") final String networkIdStr,
			@PathParam("parameter") final String parameter,
			@PathParam("value")     final String value)

			throws IllegalArgumentException, NdexException, SQLException, SolrServerException, IOException {
		
		    logger.info("[start: Setting {}={} for network {}]", parameter, value, networkIdStr);
		    
			try (NetworkDAO networkDao = new NetworkDAO()) {
				UUID networkId = UUID.fromString(networkIdStr);
				UUID userId = getLoggedInUser().getExternalId();
				if(networkDao.isAdmin(networkId, userId) ) {
						if ( !networkDao.networkIsLocked(networkId)) {
								
							  if ( parameter.equals(readOnlyParameter)) {
								  boolean bv = Boolean.parseBoolean(value);
								  try (NetworkDAO daoNew = new NetworkDAO()) {
									  daoNew.setFlag(networkId, "readonly",bv);
									  daoNew.commit();
									  logger.info("[end: Set {}={} for network {}]", parameter, value, networkId);
									  return;
								  }  
							  } else if ( parameter.toLowerCase().equals("visibility")) {
								  networkDao.updateNetworkVisibility(networkId, VisibilityType.valueOf(value));
								  networkDao.commit();		
								  return ;
							  }

						}
						throw new NdexException ("Network is locked by another updating process. Please try again.");
					
				}	
				throw new NdexException("Only network owner can set network permissions.");	
			}
		    
	}

	
	
	@POST
	@Path("/export")
	@Produces("application/json")
    @ApiDoc("Set the system flag specified by ‘parameter’ to ‘value’ for the network with id ‘networkId’. As of " +
	        "NDEx v1.2, the only supported parameter is readOnly={true|false}. In 2.0, we added visibility={PUBLIC|PRIVATE}")
	public Map<UUID,UUID> exportNetworks(NetworkExportRequest exportRequest)

			throws IllegalArgumentException, NdexException, SQLException, SolrServerException, IOException {
		
		    logger.info("exporting networks");
		    if ( !exportRequest.getNetworkFormat().toLowerCase().equals("cx"))
		    	throw new NdexException("Networks can only be exported in cx fromat in this server.");
		    
		    Map<UUID,UUID> result = new TreeMap<>();
			try (NetworkDAO networkDao = new NetworkDAO()) {
				try (TaskDAO taskdao = new TaskDAO()) {

					for ( UUID networkID : exportRequest.getNetworkIds()) {
						Task t = new Task();
						Timestamp currentTime = new Timestamp(Calendar.getInstance().getTimeInMillis());
						t.setCreationTime(currentTime);
						t.setModificationTime(currentTime);
						t.setStartTime(currentTime);
						t.setFinishTime(currentTime);
						t.setDescription("network export");
						t.setTaskType(TaskType.EXPORT_NETWORK_TO_FILE);
						t.setFormat(FileFormat.CX);
						t.setTaskOwnerId(getLoggedInUserId());
						t.setResource(networkID.toString());
						if (! networkDao.isReadable(networkID, getLoggedInUserId())) {
							t.setStatus(Status.COMPLETED_WITH_ERRORS);
							t.setMessage("Network " + networkID + " is not found for user.");
							//throw new NdexException ("Network " + networkID + " is not found.");
						}	else {
							t.setStatus(Status.QUEUED);
							
							NetworkSummary s = networkDao.getNetworkSummaryById(networkID);
							t.setAttribute("downloadFileName", s.getName());
							t.setAttribute("downloadFileExtension", "CX");
						    

						}
						UUID taskId = taskdao.createTask(t);
						taskdao.commit();
						
						result.put(networkID, taskId);
						
						if ( t.getStatus() == Status.QUEUED)
						NdexServerQueue.INSTANCE.addUserTask(new NetworkExportTask(t));

					}
				}
			}
			
			return result;
		    
	}
	
	
	   @POST
	//	@PermitAll

	   @Path("/asCX")
//	   @Produces("application/json")
	   @Produces("text/plain")
	   @Consumes("multipart/form-data")
	   @ApiDoc("Create a network from the uploaded CX stream. The input cx data is expected to be in the CXNetworkStream field of posted multipart/form-data. "
	   		+ "There is an optional 'provenance' field in the form. Users can use this field to pass in a JSON string of ProvenanceEntity object. When a user pass"
	   		+ " in this object, NDEx server will add this object to the provenance history of the CX network. Otherwise NDEx server will create a ProvenanceEntity "
	   		+ "object and add it to the provenance history of the CX network.")
	   public String createCXNetwork( MultipartFormDataInput input) throws Exception
	   {

		   logger.info("[start: Creating a new network based on a POSTed CX stream.]");
	   
		   UUID uuid = storeRawNetwork ( input);
		   String uuidStr = uuid.toString();
		   
		   String urlStr = Configuration.getInstance().getHostURI()  + 
		            Configuration.getInstance().getRestAPIPrefix()+"/network/"+ uuidStr;

		   ProvenanceEntity entity = new ProvenanceEntity();
		   entity.setUri(urlStr + "/summary");
		   // create entry in db. 
	       try (NetworkDAO dao = new NetworkDAO()) {
	    	   NetworkSummary summary = dao.CreateEmptyNetworkEntry(uuid, getLoggedInUser().getExternalId(), getLoggedInUser().getUserName());
	    	   
       
			   ProvenanceEvent event = new ProvenanceEvent(NdexProvenanceEventType.CX_CREATE_NETWORK, summary.getModificationTime());

				List<SimplePropertyValuePair> eventProperties = new ArrayList<>();
				Helper.addUserInfoToProvenanceEventProperties( eventProperties, this.getLoggedInUser());
				event.setProperties(eventProperties);

				entity.setCreationEvent(event);
				dao.setProvenance(summary.getExternalId(), entity);
	    	   dao.commit();
	       } catch (Throwable e) {
	    	   e.printStackTrace();
	    	   throw e;
	       }
	       
	       NdexServerQueue.INSTANCE.addSystemTask(new CXNetworkLoadingTask(uuid, getLoggedInUser().getUserName(), false));
	       
		   logger.info("[end: Created a new network based on a POSTed CX stream.]");
		   //return "\"" + uuidStr + "\"";
		   return  uuidStr ;

	   	}
	   

	   private static UUID storeRawNetwork (MultipartFormDataInput input) throws IOException {
		   Map<String, List<InputPart>> uploadForm = input.getFormDataMap();
	       
			 //      ProvenanceEntity entity = this.getProvenanceEntityFromMultiPart(uploadForm);
			       
			 //Get file data to save
			 List<InputPart> inputParts = uploadForm.get("CXNetworkStream");
					
				   byte[] bytes = new byte[8192];
				   UUID uuid = NdexUUIDFactory.INSTANCE.createNewNDExUUID();
				   String uuidStr = uuid.toString();
				   String pathPrefix = Configuration.getInstance().getNdexRoot() + "/data/" + uuidStr;
				   
				   //Create dir
				   java.nio.file.Path dir = Paths.get(pathPrefix);
				   Files.createDirectory(dir);
				   
				   //write content to file
				   String cxFilePath = pathPrefix + "/network.cx";
				   try (FileOutputStream out = new FileOutputStream (cxFilePath ) ){     
					   for (InputPart inputPart : inputParts) {
				               // convert the uploaded file to inputstream and write it to disk
				        	org.jboss.resteasy.plugins.providers.multipart.MultipartInputImpl.PartImpl p =
				        			   (org.jboss.resteasy.plugins.providers.multipart.MultipartInputImpl.PartImpl) inputPart;
				            try (InputStream inputStream = p.getBody()) {
				              
				            	int read = 0;
				            	while ((read = inputStream.read(bytes)) != -1) {
				                  out.write(bytes, 0, read);
				            	}
				            }
				               
					   }
				   }
				return uuid; 
	   }
	   
	   
	   
	   private static List<NetworkAttributesElement> getNetworkAttributeAspectsFromSummary(NetworkSummary summary) 
			   throws JsonParseException, JsonMappingException, IOException {
			List<NetworkAttributesElement> result = new ArrayList<>();
			if ( summary.getName() !=null) 
				result.add(new NetworkAttributesElement(null, NdexClasses.Network_P_name, summary.getName()));
			if ( summary.getDescription() != null)
				result.add(new NetworkAttributesElement(null, NdexClasses.Network_P_desc, summary.getDescription()));
			if ( summary.getVersion() !=null)
				result.add(new NetworkAttributesElement(null, NdexClasses.Network_P_version, summary.getVersion()));
			
			if ( summary.getProperties() != null) {
				for ( NdexPropertyValuePair p : summary.getProperties()) {
					result.add(NetworkAttributesElement.createInstanceWithJsonValue(p.getSubNetworkId(), p.getPredicateString(),
							p.getValue(), ATTRIBUTE_DATA_TYPE.fromCxLabel(p.getDataType())));
				}
			}
			return result;
		}
	   
	   

	   
}
