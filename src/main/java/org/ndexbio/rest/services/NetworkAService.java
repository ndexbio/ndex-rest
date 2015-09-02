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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
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

import org.apache.commons.io.FilenameUtils;
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.access.NetworkAOrientDBDAO;
import org.ndexbio.common.models.dao.orientdb.Helper;
import org.ndexbio.common.models.dao.orientdb.NetworkDAO;
import org.ndexbio.common.models.dao.orientdb.NetworkDAOTx;
import org.ndexbio.common.models.dao.orientdb.NetworkDocDAO;
import org.ndexbio.common.models.dao.orientdb.NetworkSearchDAO;
import org.ndexbio.common.models.dao.orientdb.TaskDAO;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.network.query.EdgeCollectionQuery;
import org.ndexbio.model.network.query.NetworkPropertyFilter;
import org.ndexbio.model.object.Membership;
import org.ndexbio.model.object.NdexPropertyValuePair;
import org.ndexbio.model.object.NdexProvenanceEventType;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.Priority;
import org.ndexbio.model.object.ProvenanceEntity;
import org.ndexbio.model.object.ProvenanceEvent;
import org.ndexbio.model.object.SimpleNetworkQuery;
import org.ndexbio.model.object.SimplePathQuery;
import org.ndexbio.model.object.SimplePropertyValuePair;
import org.ndexbio.model.object.Status;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.TaskType;
import org.ndexbio.model.object.User;
import org.ndexbio.common.models.object.network.RawNamespace;
import org.ndexbio.common.persistence.orientdb.NdexNetworkCloneService;
import org.ndexbio.common.persistence.orientdb.NdexPersistenceService;
import org.ndexbio.common.persistence.orientdb.PropertyGraphLoader;
import org.ndexbio.common.query.NetworkFilterQueryExecutor;
import org.ndexbio.common.query.NetworkFilterQueryExecutorFactory;
import org.ndexbio.common.query.SearchNetworkByPropertyExecutor;
import org.ndexbio.common.util.MemoryUtilization;
import org.ndexbio.common.util.NdexUUIDFactory;
//import org.ndexbio.model.object.SearchParameters;
import org.ndexbio.model.object.network.BaseTerm;
import org.ndexbio.model.object.network.FileFormat;
import org.ndexbio.model.object.network.Namespace;
import org.ndexbio.model.object.network.Network;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.object.network.PropertyGraphNetwork;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.annotations.ApiDoc;
import org.ndexbio.rest.helpers.UploadedFile;
import org.ndexbio.task.Configuration;
import org.ndexbio.task.NdexServerQueue;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.record.impl.ODocument;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@Path("/network")
public class NetworkAService extends NdexService {
	
	static Logger logger = LoggerFactory.getLogger(NetworkAService.class);
	
	static private final String readOnlyParameter = "readOnly";

	public NetworkAService(@Context HttpServletRequest httpRequest) {
		super(httpRequest);
	}

	/*
	 *
	 * Operations returning or setting Network Elements
	 *
	 */
	@PermitAll
	@GET
	@Path("/{networkId}/baseTerm/{skipBlocks}/{blockSize}")
	@Produces("application/json")
    @ApiDoc("Retrieves a list of BaseTerm objects from the network specified by 'networkId'. The maximum number of " +
            "BaseTerm objects to retrieve in the query is set by 'blockSize'  (which may be any number chosen by the " +
            "user) while  'skipBlocks' specifies the number of blocks that have already been read.")
	public List<BaseTerm> getBaseTerms(
			@PathParam("networkId") final String networkId,
			@PathParam("skipBlocks") final int skipBlocks,
			@PathParam("blockSize") final int blockSize)

			throws IllegalArgumentException, NdexException {

		logger.info("[start: Getting BaseTerm objects from network {}, skipBlocks {}, blockSize {}]",  
				networkId, skipBlocks, blockSize);
		
		ODatabaseDocumentTx db = null;
		try {
			db = NdexDatabase.getInstance().getAConnection();
			NetworkDAO daoNew = new NetworkDAO(db);
			return (List<BaseTerm>) daoNew.getBaseTerms(networkId);
		} finally {
			if ( db != null) db.close();
			logger.info("[end: Got BaseTerm objects from network {}, skipBlocks {}, blockSize {}]",  
					networkId, skipBlocks, blockSize);
		}

	}

	@PermitAll
	@GET
	@Path("/{networkId}/namespace/{skipBlocks}/{blockSize}")
	@Produces("application/json")
    @ApiDoc("Retrieves a list of Namespace objects from the network specified by 'networkId'. The maximum number of " +
            "Namespace objects to retrieve in the query is set by 'blockSize' (which may be any number chosen by the " +
            "user) while  'skipBlocks' specifies the number of blocks that have already been read.")
	public List<Namespace> getNamespaces(
			@PathParam("networkId") final String networkId,
			@PathParam("skipBlocks") final int skipBlocks,
			@PathParam("blockSize") final int blockSize)

			throws IllegalArgumentException, NdexException {

		logger.info("[start: Getting list of namespaces for network UUID='{}', skipBlocks={}, blockSize={}]",  
				networkId, skipBlocks, blockSize);
		
		ODatabaseDocumentTx db = null;
		try {
			db = NdexDatabase.getInstance().getAConnection();
			NetworkDAO daoNew = new NetworkDAO(db);
			return (List<Namespace>) daoNew.getNamespaces(networkId);
		} finally {
			if ( db != null) db.close();
			logger.info("[end: Got list of namespaces for network UUID='{}', skipBlocks={}, blockSize={}]",  
					networkId, skipBlocks, blockSize);
		}

	}

	@POST
	@Path("/{networkId}/namespace")
	@Produces("application/json")
    @ApiDoc("Adds the POSTed Namespace object to the network specified by 'networkId'.")
	public void addNamespace(
			@PathParam("networkId") final String networkId,
			final Namespace namespace
			)
			throws IllegalArgumentException, NdexException, IOException {
		
		logger.info("[start: Adding namespace to the network UUID='{}']", networkId);

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
			logger.info("[end: Added namespace to the network UUID='{}']", networkId);
		}
	}

    /**************************************************************************
    * Returns network provenance.
     * @throws IOException
     * @throws JsonMappingException
     * @throws JsonParseException
     * @throws NdexException
    *
    **************************************************************************/
	@PermitAll
	@GET
	@Path("/{networkId}/provenance")
	@Produces("application/json")
	@ApiDoc("This method retrieves the 'provenance' attribute of the network specified by 'networkId', if it " +
	        "exists. The returned value is a JSON ProvenanceEntity object which in turn contains a " +
	        "tree-structure of ProvenanceEvent and ProvenanceEntity objects that describe the provenance " +
	        "history of the network. See the document NDEx Provenance History for a detailed description of " +
	        "this structure and best practices for its use.")	
	public ProvenanceEntity getProvenance(
			@PathParam("networkId") final String networkId)

			throws IllegalArgumentException, JsonParseException, JsonMappingException, IOException, NdexException {
		

		logger.info("[start: Getting provenance of network {}]", networkId);
		if (  ! isSearchable(networkId) ) {
			logger.error("[end: Network {} not readable for this user]", networkId);
			throw new UnauthorizedOperationException("Network " + networkId + " is not readable to this user.");
		}
		
		try (NetworkDocDAO daoNew = new NetworkDocDAO()) {

			return daoNew.getProvenance(UUID.fromString(networkId));

		} finally {
			logger.info("[end: Got provenance of network {}]", networkId);
		}



	}

    /**************************************************************************
    * Updates network provenance.
     * @throws Exception
    *
    **************************************************************************/
    @PUT
	@Path("/{networkId}/provenance")
	@Produces("application/json")
    @ApiDoc("Updates the 'provenance' field of the network specified by 'networkId' to be the " +
            "ProvenanceEntity object in the PUT data. The ProvenanceEntity object is expected to represent " +
            "the current state of the network and to contain a tree-structure of ProvenanceEvent and " +
            "ProvenanceEntity objects that describe the networks provenance history.")
    public ProvenanceEntity setProvenance(@PathParam("networkId")final String networkId, final ProvenanceEntity provenance)
    		throws Exception {

    	logger.info("[start: Updating provenance of network {}]", networkId);
    	
    	ODatabaseDocumentTx db = null;
    	NetworkDAO daoNew = null;

		try {
			db = NdexDatabase.getInstance().getAConnection();
			
			User user = getLoggedInUser();

			if ( !Helper.checkPermissionOnNetworkByAccountName(db, networkId, user.getAccountName(),
					Permissions.WRITE)) {
				logger.error("[end: No write permissions for user account {} on network {}]", 
						user.getAccountName(), networkId);
		        throw new UnauthorizedOperationException("User doesn't have write permissions for this network.");
			}

			daoNew = new NetworkDAO(db);
			
			if(daoNew.networkIsReadOnly(networkId)) {
				daoNew.close();
				logger.info("[end: Can't modify readonly network {}]", networkId);
				throw new NdexException ("Can't update readonly network.");
			}

			UUID networkUUID = UUID.fromString(networkId);
			daoNew.setProvenance(networkUUID, provenance);
			daoNew.commit();
			return daoNew.getProvenance(networkUUID);
		} catch (Exception e) {
			if (null != daoNew) daoNew.rollback();
			logger.error("[end: Updating provenance of network {}. Exception caught:]{}", networkId, e);	
			throw e;
		} finally {
			if (null != db) db.close();
			logger.info("[end: Updated provenance of network {}]", networkId);
		}
    }



    /**************************************************************************
    * Sets network properties.
     * @throws Exception
    *
    **************************************************************************/
    @PUT
	@Path("/{networkId}/properties")
	@Produces("application/json")
    @ApiDoc("Updates the 'properties' field of the network specified by 'networkId' to be the list of " +
            "NdexPropertyValuePair  objects in the PUT data.")
    public int setNetworkProperties(
    		@PathParam("networkId")final String networkId,
    		final List<NdexPropertyValuePair> properties)
    		throws Exception {

    	logger.info("[start: Updating properties of network {}]", networkId);
    	
    	ODatabaseDocumentTx db = null;
    	NetworkDAO daoNew = null;

		try {
			db = NdexDatabase.getInstance().getAConnection();
			
			User user = getLoggedInUser();

			if ( !Helper.checkPermissionOnNetworkByAccountName(db, networkId, user.getAccountName(),
					Permissions.WRITE)) {
				logger.error("[end: No write permissions for user account {} on network {}]",
						user.getAccountName(), networkId);
		        throw new UnauthorizedOperationException("User doesn't have write permissions for this network.");
			}

			daoNew = new NetworkDAO(db);
			
			if(daoNew.networkIsReadOnly(networkId)) {
				daoNew.close();
				logger.info("[end: Can't update readonly network {}]", networkId);
				throw new NdexException ("Can't update readonly network.");
			}
			
			UUID networkUUID = UUID.fromString(networkId);
			int i = daoNew.setNetworkProperties(networkUUID, properties);

            //DW: Handle provenance



            ProvenanceEntity oldProv = daoNew.getProvenance(networkUUID);
            ProvenanceEntity newProv = new ProvenanceEntity();
            newProv.setUri( oldProv.getUri() );

            Helper.populateProvenanceEntity(newProv, daoNew, networkId);

            NetworkSummary summary = daoNew.getNetworkSummary(daoNew.getRecordByUUIDStr(networkId, null));
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

			daoNew.commit();
			//logInfo(logger, "Finished updating properties of network " + networkId);
			return i;
		} catch (Exception e) {
			//logger.severe("Error occurred when update network properties: " + e.getMessage());
			//e.printStackTrace();
			if (null != daoNew) daoNew.rollback();
			logger.error("[end: Updating properties of network {}. Exception caught:]{}", networkId, e);
			
			throw new NdexException(e.getMessage());
		} finally {
			if (null != db) db.close();
			logger.info("[end: Updated properties of network {}]", networkId);
		}
    }

    @PUT
	@Path("/{networkId}/presentationProperties")
	@Produces("application/json")
    @ApiDoc("Updates the'presentationProperties' field of the network specified by 'networkId' to be the list of " +
            "SimplePropertyValuePair objects in the PUT data.")
    public int setNetworkPresentationProperties(
    		@PathParam("networkId")final String networkId,
    		final List<SimplePropertyValuePair> properties)
    		throws Exception {

    	//logger.info(userNameForLog() + "[start: Updating presentationProperties field of network " + networkId + "]");
    	logger.info("[start: Updating presentationProperties field of network {}]", networkId);
    	
    	ODatabaseDocumentTx db = null;
    	NetworkDAO daoNew = null;

		try {
			db = NdexDatabase.getInstance().getAConnection();
			User user = getLoggedInUser();

			if ( !Helper.checkPermissionOnNetworkByAccountName(db, networkId, user.getAccountName(),
					Permissions.WRITE)) {
				logger.error("[end: No write permissions for user account {} on network {}]",
						user.getAccountName(), networkId);				
		        throw new UnauthorizedOperationException("User doesn't have write permissions for this network.");
			}

			daoNew = new NetworkDAO(db);
			UUID networkUUID = UUID.fromString(networkId);
			
			if(daoNew.networkIsReadOnly(networkId)) {
				daoNew.close();
				logger.info("[end: Can't update readonly network {}]", networkId);
				throw new NdexException ("Can't update readonly network.");
			}

			
			int i = daoNew.setNetworkPresentationProperties(networkUUID, properties);

            //DW: Handle provenance
            ProvenanceEntity oldProv = daoNew.getProvenance(networkUUID);
            ProvenanceEntity newProv = new ProvenanceEntity();
            newProv.setUri( oldProv.getUri() );

            NetworkSummary summary = daoNew.getNetworkSummary(daoNew.getRecordByUUIDStr(networkId, null));
            Helper.populateProvenanceEntity(newProv, summary);
            ProvenanceEvent event = new ProvenanceEvent(NdexProvenanceEventType.SET_PRESENTATION_PROPERTIES, summary.getModificationTime());

            List<SimplePropertyValuePair> eventProperties = new ArrayList<>();
            Helper.addUserInfoToProvenanceEventProperties( eventProperties, user);
            for( SimplePropertyValuePair vp : properties )
            {
                SimplePropertyValuePair svp = new SimplePropertyValuePair(vp.getName(), vp.getValue());
                eventProperties.add(svp);
            }
            event.setProperties(eventProperties);
            List<ProvenanceEntity> oldProvList = new ArrayList<>();
            oldProvList.add(oldProv);
            event.setInputs(oldProvList);

            newProv.setCreationEvent(event);
            daoNew.setProvenance(networkUUID, newProv);


			daoNew.commit();
			return i;
		} catch (Exception e) {
			if (null != daoNew) {
				daoNew.rollback();
				daoNew = null;
			}
			logger.error("[end: Updating presentationProperties field of network {}. Exception caught:]{}", networkId, e);
			throw e;
		} finally {
			if (null != db) db.close();
			logger.error("[end: Updating presentationProperties field of network {}]", networkId);
		}
    }
    
	/*
	 *
	 * Operations returning Networks 
	 * 
	 */

	@PermitAll
	@GET
	@Path("/{networkId}")
	@Produces("application/json")
	@ApiDoc("Retrieves a NetworkSummary object based on the network specified by 'networkId'. This " +
            "method returns an error if the network is not found or if the authenticated user does not have " +
            "READ permission for the network.")
	public NetworkSummary getNetworkSummary(
			@PathParam("networkId") final String networkId)

			throws IllegalArgumentException, NdexException {

    	logger.info("[start: Getting networkSummary of network {}]", networkId);
		
		ODatabaseDocumentTx db = null;
		try {
			db = NdexDatabase.getInstance().getAConnection();

			NetworkDocDAO networkDocDao = new NetworkDocDAO(db);

			VisibilityType vt = Helper.getNetworkVisibility(db, networkId);
			boolean hasPrivilege = (vt == VisibilityType.PUBLIC || vt== VisibilityType.DISCOVERABLE);

			if ( !hasPrivilege && getLoggedInUser() != null) {
				hasPrivilege = networkDocDao.checkPrivilege(getLoggedInUser().getAccountName(),
						networkId, Permissions.READ);
			}
			if ( hasPrivilege) {
				ODocument doc =  networkDocDao.getNetworkDocByUUIDString(networkId);
				if (doc == null)
					throw new ObjectNotFoundException("Network with ID: " + networkId + " doesn't exist.");
				NetworkSummary summary = NetworkDocDAO.getNetworkSummary(doc);
				db.close();
				db = null;
				return summary;

			}
		} finally {
			if (db != null) db.close();
			logger.info("[end: Got networkSummary of network {}]", networkId);
		}
		
		logger.error("[end: Getting networkSummary of network {}. Throwing UnauthorizedOperationException ...]", networkId);	
        throw new UnauthorizedOperationException("User doesn't have read access to this network.");
	}


	@PermitAll
	@GET
	@Path("/{networkId}/edge/asNetwork/{skipBlocks}/{blockSize}")
	@Produces("application/json")
	@ApiDoc("This method retrieves a subnetwork of the network specified by 'networkId' based on a ‘block’ of " +
	        "edges, where a ‘block’ is simply a set that is contiguous in the network as stored in the specific " +
	        "NDEx Server. The maximum number of edges to retrieve in the query is set by 'blockSize' " +
	        "(which may be any number chosen by the user) while 'skipBlocks' specifies the number of " +
	        "blocks of edges in sequence to ignore before selecting the block to return. The subnetwork is " + 
	        "returned as a Network object containing the edges specified by the query plus all of the other " +
 	        "network elements relevant to the edges." +
            "<br /><br />" +
	        "This method is used by the NDEx Web UI to sample a network, enabling the user to view some " +
	        "of the content of a large network without attempting to retrieve and load the full network. It can " + 
	        "also be used to obtain a network in ‘chunks’, but it is anticipated that this use will be superseded "  +
	        "by upcoming API methods that will enable streaming transfers of network content.")
	public Network getEdges(
			@PathParam("networkId") final String networkId,
			@PathParam("skipBlocks") final int skipBlocks,
			@PathParam("blockSize") final int blockSize)

			throws IllegalArgumentException, NdexException {
	
    	logger.info("[start: Getting edges of network {}, skipBlocks {}, blockSize {}]", 
    			networkId, skipBlocks, blockSize);
	    
		ODatabaseDocumentTx db = null;
		try {
			db = NdexDatabase.getInstance().getAConnection();
			NetworkDAO dao = new NetworkDAO(db);
	 		Network n = dao.getNetwork(UUID.fromString(networkId), skipBlocks, blockSize);
	        return n;
		} finally {
			if ( db !=null) db.close();
	    	logger.info("[end: Got edges of network {}, skipBlocks {}, blockSize {}]", 
	    			networkId, skipBlocks, blockSize);
		}
	}

	@PermitAll
	@GET
	@Path("/{networkId}/asNetwork")
//	@Produces("application/json")
	@ApiDoc("The getCompleteNetwork method enables an application to obtain an entire network as a JSON " +
	        "structure. This is performed as a monolithic operation, so care should be taken when requesting " +
	        "very large networks. Applications can use the getNetworkSummary method to check the node " +
	        "and edge counts for a network before attempting to use getCompleteNetwork. As an " +
	        "optimization, networks that are designated read-only (see Make a Network Read-Only below) " +
	        "are cached by NDEx for rapid access. ")
	// new Implmentation to handle cached network 
	public Response getCompleteNetwork(	@PathParam("networkId") final String networkId)
			throws IllegalArgumentException, NdexException {

    	logger.info("[start: Getting complete network UUID='{}']", networkId);
		//logger.info("[memory: {}]", MemoryUtilization.getMemoryUtiliztaion());

		if ( isSearchable(networkId) ) {
			
			ODatabaseDocumentTx db = NdexDatabase.getInstance().getAConnection();
			NetworkDAO daoNew = new NetworkDAO(db);
			
			NetworkSummary sum = daoNew.getNetworkSummaryById(networkId);
			long commitId = sum.getReadOnlyCommitId();
			if ( commitId > 0 && commitId == sum.getReadOnlyCacheId()) {
				daoNew.close();
				try {
					FileInputStream in = new FileInputStream(
							Configuration.getInstance().getNdexNetworkCachePath() + commitId +".gz")  ;
				
					setZipFlag();
					Response response = Response.ok().type(MediaType.APPLICATION_JSON_TYPE).entity(in).build();
					//logger.info("[memory: {}]", MemoryUtilization.getMemoryUtiliztaion());
					logger.info("[end: Return cached network UUID='{}']", networkId);
					return 	response;
				} catch (IOException e) {
					//logger.info("[memory: {}]", MemoryUtilization.getMemoryUtiliztaion());
					logger.error("[end: Ndex server can't find file: {}]", e.getMessage());
					throw new NdexException ("Ndex server can't find file: " + e.getMessage());
				}
			}   	

			Network n = daoNew.getNetworkById(UUID.fromString(networkId));
			daoNew.close();
	    	Response response = Response.ok(n,MediaType.APPLICATION_JSON_TYPE).build();
			//logger.info("[memory: {}]", MemoryUtilization.getMemoryUtiliztaion());
	    	logger.info("[end: Return complete network UUID='{}']", networkId);	    	
			return response;
		}
		else {
			logger.info("[end: User doesn't have read access to network UUID='{}']", networkId);
            throw new UnauthorizedOperationException("User doesn't have read access to this network.");
		}

	}  

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
		
		if ( isSearchable(networkId) ) {
			
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
				UUID taskId = taskDAO.createTask(getLoggedInUser().getAccountName(),exportNetworkTask);
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
	
	
	
	@PermitAll
	@GET
	@Path("/{networkId}/asPropertyGraph")
	@Produces("application/json")
    @ApiDoc("Retrieves an entire network specified by 'networkId' as a PropertyGraphNetwork object. A user may wish " +
            "to retrieve a PropertyGraphNetwork rather than an ordinary Network when the would like to work with the " +
            "network with a table-oriented data structure, for example, an R or Python data frame. (Compare this " +
            "method to getCompleteNetwork).")
	public PropertyGraphNetwork getCompleteNetworkAsPropertyGraph(
			@PathParam("networkId") final String networkId)

			throws IllegalArgumentException, NdexException, JsonProcessingException {
		
    	logger.info("[start: Retrieving an entire network {} as a PropertyGraphNetwork object]", networkId);
		
		if ( isSearchable(networkId) ) {
		
			ODatabaseDocumentTx db =null;
			try {
				db = NdexDatabase.getInstance().getAConnection();
				NetworkDAO daoNew = new NetworkDAO(db);
				PropertyGraphNetwork n = daoNew.getProperytGraphNetworkById(UUID.fromString(networkId));
				return n;
			} finally {
				if (db !=null ) db.close();
		    	logger.info("[end: Retrieved an entire network {} as a PropertyGraphNetwork object]", networkId);
			}
		}
		else {
			logger.error("[end: User doesn't have read access network {}. Throwing  UnauthorizedOperationException]", networkId);
			throw new UnauthorizedOperationException("User doesn't have read access to this network.");
		}
	}

	@PermitAll
	@GET
	@Path("/{networkId}/edge/asPropertyGraph/{skipBlocks}/{blockSize}")
	@Produces("application/json")
    @ApiDoc("Retrieves a subnetwork of a network based on a block (where a block is simply a contiguous set) of edges" +
            ". The network is specified by 'networkId'  and the maximum number of edges to retrieve in the query is " +
            "set by 'blockSize' (which may be any number chosen by the user) while  'skipBlocks' specifies number of " +
            "blocks that have already been read.   The subnetwork is returned as a PropertyGraphNetwork object " +
            "containing the PropertyGraphEdge objects specified by the query along with all of the PropertyGraphNode " +
            "objects and network information relevant to the edges. (Compare this method to getEdges).")
	public PropertyGraphNetwork getPropertyGraphEdges(
			@PathParam("networkId") final String networkId,
			@PathParam("skipBlocks") final int skipBlocks,
			@PathParam("blockSize") final int blockSize)

			throws IllegalArgumentException, NdexException {

    	logger.info("[start: Retrieving a subnetwork of network {}, skipBlocks {}, blockSize {}]", 
    			networkId, skipBlocks, blockSize);
		ODatabaseDocumentTx db = NdexDatabase.getInstance().getAConnection();
		NetworkDAO dao = new NetworkDAO(db);
 		PropertyGraphNetwork n = dao.getProperytGraphNetworkById(UUID.fromString(networkId),skipBlocks, blockSize);
		db.close();
    	logger.info("[start: Retrieved a subnetwork of network {}, skipBlocks {}, blockSize {}]", 
    			networkId, skipBlocks, blockSize);
        return n;
	}

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
	 **************************************************************************/

	@GET
	@PermitAll
	@Path("/{networkId}/user/{permission}/{skipBlocks}/{blockSize}")
	@Produces("application/json")
    @ApiDoc("Retrieves a list of Membership objects which specify user permissions for the network specified by " +
            "'networkId'. The value of the 'permission' parameter constrains the type of the returned Membership " +
            "objects and may take the following set of values: READ, WRITE, and ADMIN.  READ, WRITE, and ADMIN are mutually exclusive. Memberships of all types can " +
            "be retrieved by permission = 'ALL'.   The maximum number of Membership objects to retrieve in the query " +
            "is set by 'blockSize' (which may be any number chosen by the user) while  'skipBlocks' specifies the " +
            "number of blocks that have already been read.")
	public List<Membership> getNetworkUserMemberships(@PathParam("networkId") final String networkId,
			@PathParam("permission") final String permissions ,
			@PathParam("skipBlocks") int skipBlocks,
			@PathParam("blockSize") int blockSize) throws NdexException {

		logger.info("[start: Get {} accounts on network {}, skipBlocks {},  blockSize {}]", 
				permissions, networkId, skipBlocks, blockSize);
		
		Permissions permission = null;
		if ( ! permissions.toUpperCase().equals("ALL")) {
			permission = Permissions.valueOf(permissions.toUpperCase());
		}
		
		ODatabaseDocumentTx db = null;
		try {

			db = NdexDatabase.getInstance().getAConnection();
			NetworkDAO networkDao = new NetworkDAO(db);
            
			List<Membership> results = networkDao.getNetworkUserMemberships(
					UUID.fromString(networkId), permission, skipBlocks, blockSize);
			logger.info("[end: Got {} members returned for network {}]", 
					results.size(), networkId);
			return results;

		} finally {
			if (db != null) db.close();
		}
	}

	@DELETE
	@Path("/{networkId}/member/{userUUID}")
	@Produces("application/json")
    @ApiDoc("Removes any permission for the network specified by 'networkId' for the user specified by 'userUUID': it" +
            " deletes any Membership object that specifies a permission for the user-network combination. This method" +
            " will return an error if the authenticated user making the request does not have sufficient permissions " +
            "to make the deletion or if the network or user is not found. Removal is also denied if it would leave " +
            "the network without any user having ADMIN permissions: NDEx does not permit networks to become 'orphans'" +
            " without any owner.")
	public int deleteNetworkMembership(
			@PathParam("networkId") final String networkId,
			@PathParam("userUUID") final String  userUUID
			)
			throws IllegalArgumentException, NdexException {
		
		logger.info("[start: Removing any permissions for network {} for user {}]", networkId, userUUID);
		
		ODatabaseDocumentTx db = null;
		try {
			db = NdexDatabase.getInstance().getAConnection();
			User user = getLoggedInUser();
			NetworkDAO networkDao = new NetworkDAO(db);

			if (!Helper.isAdminOfNetwork(db, networkId, user.getExternalId().toString())) {
				logger.error("[end: User {} not an admin of network {}. Throwing  UnauthorizedOperationException ...]", 
						userUUID, networkId); 
				throw new UnauthorizedOperationException("Unable to delete network membership: user is not an admin of this network.");
			}

			int count = networkDao.revokePrivilege(networkId, userUUID);
            db.commit();
    		logger.info("[end: Removed any permissions for network {} for user {}]", networkId, userUUID);
            return count;
		} finally {
			if (db != null) db.close();
		}
	}
	
	/*
	 *
	 * Operations on Network permissions
	 * 
	 */

	@POST
	@Path("/{networkId}/member")
	@Produces("application/json")
    @ApiDoc("POSTs a Membership object to update the permission of a user specified by userUUID for the network " +
            "specified by networkUUID. The permission is updated to the value specified in the 'permission' field of " +
            "the Membership. This method returns 1 if the update is performed and 0 if the update is redundant, " +
            "where the user already has the specified permission. It also returns an error if the authenticated user " +
            "making the request does not have sufficient permissions or if the network or user is not found. It also " +
            "returns an error if it would leave the network without any user having ADMIN permissions: NDEx does not " +
            "permit networks to become 'orphans' without any owner.")
	public int updateNetworkMembership(
			@PathParam("networkId") final String networkId,
			final Membership membership
			)
			throws IllegalArgumentException, NdexException {

		logger.info("[start: Updating membership for network {}]", networkId);
		
		ODatabaseDocumentTx db = null;
		try {
			db = NdexDatabase.getInstance().getAConnection();

			User user = getLoggedInUser();
			NetworkDAO networkDao = new NetworkDAO(db);

			if (!Helper.isAdminOfNetwork(db, networkId, user.getExternalId().toString())) {

				logger.error("[end: User {} not an admin of network {}. Throwing  UnauthorizedOperationException ...]", 
						user.getExternalId().toString(), networkId);
				
				throw new UnauthorizedOperationException("Unable to update network membership: user is not an admin of this network.");
			}

	        int count = networkDao.grantPrivilege(networkId, membership.getMemberUUID().toString(), membership.getPermissions());
			db.commit();
			logger.info("[end: Updated membership for network {}]", networkId);
	        return count;
		} finally {
			if (db != null) db.close();
		}
	}

	@POST
	@Path("/{networkId}/summary")
	@Produces("application/json")
	@ApiDoc("This method updates the profile information of the network specified by networkId based on a " +
	        "POSTed JSON object specifying the attributes to update. Any profile attributes specified will be " + 
	        "updated but attributes that are not specified will have no effect - omission of an attribute does " +
	        "not mean deletion of that attribute. The network profile attributes that can be updated by this " +
	        "method are: 'name', 'description', 'version', and 'visibility'.")
	public void updateNetworkProfile(
			@PathParam("networkId") final String networkId,
			final NetworkSummary summary
			)
            throws IllegalArgumentException, NdexException, IOException
    {
		logger.info("[start: Updating profile information of network {}]", networkId);
		
		ODatabaseDocumentTx db = null;
		try {
			db = NdexDatabase.getInstance().getAConnection();

			User user = getLoggedInUser();
			NetworkDAO networkDao = new NetworkDAO(db);
			
			if(networkDao.networkIsReadOnly(networkId)) {
				networkDao.close();
				logger.info("[end: Can't modify readonly network {}]", networkId);
				throw new NdexException ("Can't update readonly network.");				
			}


			if ( !Helper.checkPermissionOnNetworkByAccountName(db, networkId, user.getAccountName(),
					Permissions.WRITE)) {
				logger.error("[end: No write permissions for user account {} on network {}]", 
						user.getAccountName(), networkId);
		        throw new UnauthorizedOperationException("User doesn't have write permissions for this network.");
			}

            UUID networkUUID = UUID.fromString(networkId);
	        networkDao.updateNetworkProfile(networkUUID, summary);

            //DW: Handle provenance
            //Special Logic. Test whether we should record provenance at all.
            //If the only thing that has changed is the visibility, we should not add a provenance
            //event.
            ProvenanceEntity oldProv = networkDao.getProvenance(networkUUID);
            String oldName = "", oldDescription = "", oldVersion ="";
            for( SimplePropertyValuePair oldProperty : oldProv.getProperties() )
            {
                if( oldProperty.getName() == null )
                    continue;
                if( oldProperty.getName().equals("dc:title") )
                    oldName = oldProperty.getValue().trim();
                else if( oldProperty.getName().equals("description") )
                    oldDescription = oldProperty.getValue().trim();
                else if( oldProperty.getName().equals("version") )
                    oldVersion = oldProperty.getValue().trim();
            }

            //Treat all summary values that are null like ""
            String summaryName = summary.getName() == null ? "" : summary.getName().trim();
            String summaryDescription = summary.getDescription() == null ? "" : summary.getDescription().trim();
            String summaryVersion = summary.getVersion() == null ? "" : summary.getVersion().trim();



            if( !oldName.equals(summaryName) || !oldDescription.equals(summaryDescription) || !oldVersion.equals(summaryVersion) )
            {
                ProvenanceEntity newProv = new ProvenanceEntity();
                newProv.setUri(oldProv.getUri());

                Helper.populateProvenanceEntity(newProv, networkDao, networkId);

                ProvenanceEvent event = new ProvenanceEvent(NdexProvenanceEventType.UPDATE_NETWORK_PROFILE, summary.getModificationTime());

                List<SimplePropertyValuePair> eventProperties = new ArrayList<>();
                Helper.addUserInfoToProvenanceEventProperties(eventProperties, user);

                if (summary.getName() != null)
                    eventProperties.add(new SimplePropertyValuePair("dc:title", summary.getName()));

                if (summary.getDescription() != null)
                    eventProperties.add(new SimplePropertyValuePair("description", summary.getDescription()));

                if (summary.getVersion() != null)
                    eventProperties.add(new SimplePropertyValuePair("version", summary.getVersion()));

                event.setProperties(eventProperties);
                List<ProvenanceEntity> oldProvList = new ArrayList<>();
                oldProvList.add(oldProv);
                event.setInputs(oldProvList);

                newProv.setCreationEvent(event);
                networkDao.setProvenance(networkUUID, newProv);
            }
			db.commit();
		} finally {
			if (db != null) db.close();
			logger.info("[end: Updated profile information of network {}]", networkId);
		}
	}



	@PermitAll
	@POST
	@Path("/{networkId}/asNetwork/query")
	@Produces("application/json")
    @ApiDoc("Retrieves a 'neighborhood' subnetwork of the network specified by ‘networkId’. The query finds " +
            "the subnetwork by a traversal of the network starting with nodes associated with identifiers " +
            "specified in a POSTed JSON query object. " +
            "For more information, please click <a href=\"http://www.ndexbio.org/using-the-ndex-server-api/#queryNetwork\">here</a>.")
	public Network queryNetwork(
			@PathParam("networkId") final String networkId,
			final SimplePathQuery queryParameters
//			@PathParam("skipBlocks") final int skipBlocks, 
//			@PathParam("blockSize") final int blockSize
			)

			throws IllegalArgumentException, NdexException {
		
		logger.info("[start: Retrieving neighborhood subnetwork for network UUID='{}' with phrase='{}' depth={}]", 
				networkId, queryParameters.getSearchString(), queryParameters.getSearchDepth());
		//logger.info("[memory: {}]", MemoryUtilization.getMemoryUtiliztaion());
		
		try (ODatabaseDocumentTx db = NdexDatabase.getInstance().getAConnection()) {

		   NetworkDAO networkDao = new NetworkDAO(db);

		   VisibilityType vt = Helper.getNetworkVisibility(db, networkId);
		   boolean hasPrivilege = (vt == VisibilityType.PUBLIC );

		   if ( !hasPrivilege && getLoggedInUser() != null) {
			   hasPrivilege = networkDao.checkPrivilege(
					   (getLoggedInUser() == null ? null : getLoggedInUser().getAccountName()),
					   networkId, Permissions.READ);
		   }

		   if ( hasPrivilege) {
			   NetworkAOrientDBDAO dao = NetworkAOrientDBDAO.getInstance();

			   Network n = dao.queryForSubnetworkV2(networkId, queryParameters);
   			   //logger.info("[memory: {}]", MemoryUtilization.getMemoryUtiliztaion());
			   logger.info("[end: Subnetwork for network UUID='{}' with phrase='{}'; retrieved nodes={} edges={}]", 
                       networkId, queryParameters.getSearchString(), n.getNodeCount(), n.getEdgeCount());	
			   return n;
		   }
		   
           //logger.info("[memory: {}]", MemoryUtilization.getMemoryUtiliztaion());
		   logger.error("[end: User doesn't have read permission to retrieve neighborhood subnetwork for network UUID='{}' with phrase='{}']", 
				   networkId, queryParameters.getSearchString());
	       throw new UnauthorizedOperationException("User doesn't have read permissions for this network.");
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
		

		if ( !isSearchable(networkId ) ) {
			logger.error("[end: Network {} not readable for this user]", networkId);
			throw new UnauthorizedOperationException("Network " + networkId + " is not readable to this user.");
		}
		
		NetworkFilterQueryExecutor queryExecutor = NetworkFilterQueryExecutorFactory.createODBExecutor(networkId, query);
		
		Network result =  queryExecutor.evaluate();
		logger.info("[end: filter query on network {}]", networkId);
        return result;
	}
	
	
	
	
	private boolean isSearchable(String networkId) 
				throws ObjectNotFoundException, NdexException {
		   try ( NetworkDocDAO networkDao = new NetworkDocDAO() ) {

		       VisibilityType vt = Helper.getNetworkVisibility(networkDao.getDBConnection(), networkId);
		       boolean hasPrivilege = (vt == VisibilityType.PUBLIC );

		       if ( !hasPrivilege && getLoggedInUser() != null) {
			     hasPrivilege = networkDao.checkPrivilege(
					   (getLoggedInUser() == null ? null : getLoggedInUser().getAccountName()),
					   networkId, Permissions.READ);
		       }

		       return hasPrivilege;
		   }
	}

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
		
		ODatabaseDocumentTx db = null;

		try {
			db = NdexDatabase.getInstance().getAConnection();

			NetworkDAO networkDao = new NetworkDAO(db);

   		    VisibilityType vt = Helper.getNetworkVisibility(db, networkId);
			boolean hasPrivilege = (vt == VisibilityType.PUBLIC );

			if ( !hasPrivilege && getLoggedInUser() != null) {
				   hasPrivilege = networkDao.checkPrivilege(
						   (getLoggedInUser() == null ? null : getLoggedInUser().getAccountName()),
						   networkId, Permissions.READ);
			}

			db.close();
			db = null;
			
			if ( hasPrivilege) {
				NetworkAOrientDBDAO dao = NetworkAOrientDBDAO.getInstance();

				PropertyGraphNetwork n = dao.queryForSubPropertyGraphNetwork(networkId, queryParameters);
				logger.info("[end: Retrieved neighborhood subnetwork for network {} based on SimplePathQuery object]",
						networkId);				
				return n;

			}
		} finally {
			if ( db !=null) db.close();
		}

		logger.error("[end: Retrieving neighborhood subnetwork for network {} based on SimplePathQuery object. Throwing UnauthorizedOperationException ...]",
				networkId);
	    throw new UnauthorizedOperationException("User doesn't have read permissions for this network.");
	}



/*	@PermitAll
	@POST
	@Path("/{networkId}/asPropertyGraph/query/{skipBlocks}/{blockSize}")
	@Produces("application/json")
    @ApiDoc("Retrieves a 'neighborhood' subnetwork of a network based on identifiers specified in a POSTed " +
            "SimplePathQuery object . The network is specified by networkId and the maximum number of edges to " +
            "retrieve in the query is set by 'blockSize' (which may be any number chosen by the user) while " +
            "'skipBlocks' specifies the number of blocks that have already been read before performing the next read." +
            " In the first step of the query, a set of base terms exactly matching identifiers found in the " +
            "searchString of the SimplePathQuery is selected. In the second step, nodes are selected that reference " +
            "the base terms identified in the network. Finally, a set of edges is selected by traversing outwards " +
            "from each of these selected nodes, up to the limit specified by the 'searchDepth' field of the " +
            "SimplePathQuery.  The subnetwork is returned as a PropertyGraphNetwork object containing the selected " +
            "PropertyGraphEdge objects along with any other network information relevant to the edges.")
	public PropertyGraphNetwork queryNetworkAsPropertyGraph(
			@PathParam("networkId") final String networkId,
			final NetworkQueryParameters queryParameters,
			@PathParam("skipBlocks") final int skipBlocks,
			@PathParam("blockSize") final int blockSize)

			throws IllegalArgumentException, NdexException, JsonProcessingException {

		ODatabaseDocumentTx db = NdexAOrientDBConnectionPool.getInstance().acquire();
		NetworkDAO dao = new NetworkDAO(db);
 		PropertyGraphNetwork n = dao.getProperytGraphNetworkById(UUID.fromString(networkId));
		db.close();
        return n;
	}
*/
	
	/*
	 * 
	 * Network Search
	 * 
	 */

	@POST
	@PermitAll
	@Path("/search/{skipBlocks}/{blockSize}")
	@Produces("application/json")
	@ApiDoc("This method returns a list of NetworkSummary objects based on a POSTed query JSON object. " +
            "The maximum number of NetworkSummary objects to retrieve in the query is set by the integer " +
            "value 'blockSize' while 'skipBlocks' specifies number of blocks that have already been read. " +
            "For more information, please click <a href=\"http://www.ndexbio.org/using-the-ndex-server-api/#searchNetwork\">here</a>.")
	public Collection<NetworkSummary> searchNetwork(
			final SimpleNetworkQuery query,
			@PathParam("skipBlocks") final int skipBlocks,
			@PathParam("blockSize") final int blockSize)
			throws IllegalArgumentException, NdexException {
		
		logger.info("[start: Retrieving NetworkSummary objects using query \"{}\"]", 
				query.getSearchString());		
		
    	if(query.getAccountName() != null)
    		query.setAccountName(query.getAccountName().toLowerCase());
        
    	try (ODatabaseDocumentTx db = NdexDatabase.getInstance().getAConnection()) {
            NetworkSearchDAO dao = new NetworkSearchDAO(db);
            Collection<NetworkSummary> result = new ArrayList <> ();

			result = dao.findNetworks(query, skipBlocks, blockSize, this.getLoggedInUser());
			logger.info("[end: Retrieved {} NetworkSummary objects]", result.size());		
			return result;

        } catch (Exception e) {
			logger.error("[end: Retrieving NetworkSummary objects using query \"{}\". Exception caught:]{}", 
					query.getSearchString(), e);	
        	throw new NdexException(e.getMessage());
        }
	}


	

	@POST
	@PermitAll
	@Path("/searchByProperties")
	@Produces("application/json")
	@ApiDoc("This method returns a list of NetworkSummary objects in no particular order which have " +
            "properties (metadata) that satisfy the constraints specified by a posted JSON query object. " +
            "For more information, please click <a href=\"http://www.ndexbio.org/using-the-ndex-server-api/#searchNetworkByPropertyFilter\">here</a>.")
	public Collection<NetworkSummary> searchNetworkByPropertyFilter(
			final NetworkPropertyFilter query)
			throws IllegalArgumentException, NdexException {

		logger.info("[start: Search network by properties]");

		SearchNetworkByPropertyExecutor queryExecutor = new SearchNetworkByPropertyExecutor(query, this.getLoggedInUser().getAccountName());
		
		Collection<NetworkSummary> result =  queryExecutor.evaluate();
		
		logger.info("[end: returning {} records from property search]", result.size());
		return result;

	}
	
	

	@POST
	@Path("/asPropertyGraph")
	@Produces("application/json")
    @ApiDoc("Creates a new network based on a POSTed NetworkPropertyGraph object. This method errors if the " +
            "NetworkPropertyGraph object is not provided or if it does not specify a name. It also errors if the " +
            "NetworkPropertyGraph object is larger than a maximum size for network creation set in the NDEx server " +
            "configuration. A NetworkSummary object for the new network is returned.")
	public NetworkSummary createNetwork(final PropertyGraphNetwork newNetwork)
			throws 	Exception {
			Preconditions
				.checkArgument(null != newNetwork, "A network is required");
			Preconditions.checkArgument(
				!Strings.isNullOrEmpty(newNetwork.getName()),
				"A network name is required");
			
			logger.info("[start: Creating a new network based on a POSTed NetworkPropertyGraph object]");
			
			NdexDatabase db = NdexDatabase.getInstance();
			PropertyGraphLoader pgl = null;
			pgl = new PropertyGraphLoader(db);
			NetworkSummary ns = pgl.insertNetwork(newNetwork, getLoggedInUser());
			
			logger.info("[end: Created a new network based on a POSTed NetworkPropertyGraph object]");
			
			return ns;

	}

/* comment out this function for now, until we can make this function thread safe.
 *  	
    @PUT
    @Path("/asPropertyGraph")
    @Produces("application/json")
    @ApiDoc("Updates an existing network using a PUT call using a NetworkPropertyGraph object. The NetworkPropertyGraph object must contain a " +
            "UUID (this would normally be the case for a NetworkPropertyGraph object retrieved from NDEx, " +
            "so no additional work is required in the most common case) and the user must have permission to modify " +
            "this network. This method errors if the NetworkPropertyGraph object is not provided or if it does not have a valid " +
            "UUID on the server. It also errors if the NetworkPropertyGraph object is larger than a maximum size for network " +
            "creation set in the NDEx server configuration. A NetworkSummary object is returned.")
    public NetworkSummary updateNetwork(final PropertyGraphNetwork newNetwork)
            throws Exception
    {
        Preconditions
                .checkArgument(null != newNetwork, "A network is required");
        Preconditions.checkArgument(
                !Strings.isNullOrEmpty(newNetwork.getName()),
                "A network name is required");

        NdexDatabase db = new NdexDatabase(Configuration.getInstance().getHostURI());
        PropertyGraphLoader pgl = null;
        ODatabaseDocumentTx conn = null;
        try
        {
            conn = db.getAConnection();
            User user = getLoggedInUser();

            String uuidStr = null;
            
        	for ( NdexPropertyValuePair p : newNetwork.getProperties()) {
				if ( p.getPredicateString().equals ( PropertyGraphNetwork.uuid) ) {
					uuidStr = p.getValue();
					break;
				}
			}
        	
        	if ( uuidStr == null) throw new NdexException ("updateNetwork: UUID not found in PropertyGraph.");
            
            if (!Helper.checkPermissionOnNetworkByAccountName(conn, 
         		   uuidStr, user.getAccountName(),
         		   Permissions.WRITE))
            {
         	   throw new WebApplicationException(HttpURLConnection.HTTP_UNAUTHORIZED);
            }
            pgl = new PropertyGraphLoader(db);
            return pgl.updateNetwork(newNetwork);
        }
        finally
        {

          if (db!=null)
        	  db.close();
          if( conn!=null) conn.close();
        }

    }
*/    
    	

	@POST
	@Path("/asNetwork")
	@Produces("application/json")
	@ApiDoc("This method creates a new network on the NDEx Server based on a POSTed Network object. " +
	        "An error is returned if the Network object is not provided or if the POSTed Network does not " +
	        "specify a name attribute. An error is also returned if the Network object is larger than a " +
	        "maximum size for network creation set in the NDEx server configuration. A NetworkSummary " +
	        "object for the new network is returned so that the caller can obtain the UUID assigned to the " +
	        "network.")
	public NetworkSummary createNetwork(final Network newNetwork)
			throws 	Exception {
			Preconditions
				.checkArgument(null != newNetwork, "A network is required");
			Preconditions.checkArgument(
				!Strings.isNullOrEmpty(newNetwork.getName()),
				"A network name is required");

			logger.info("[start: Creating a new network based on a POSTed Network object]");
			//logger.info("[memory: {}]", MemoryUtilization.getMemoryUtiliztaion());

			NdexDatabase db = NdexDatabase.getInstance();
			NdexNetworkCloneService service = null;
			try {
				newNetwork.setVisibility(VisibilityType.PRIVATE);
				service = new NdexNetworkCloneService(db, newNetwork,
						getLoggedInUser().getAccountName());

                NetworkSummary summary = service.cloneNetwork();
                //DW: Provenance

                ProvenanceEntity entity = new ProvenanceEntity();
                entity.setUri(summary.getURI());
                
                Helper.populateProvenanceEntity(entity, summary);

                ProvenanceEvent event = new ProvenanceEvent(NdexProvenanceEventType.PROGRAM_UPLOAD, summary.getModificationTime());

                List<SimplePropertyValuePair> eventProperties = new ArrayList<>();
                Helper.addUserInfoToProvenanceEventProperties( eventProperties, this.getLoggedInUser());
                event.setProperties(eventProperties);

                entity.setCreationEvent(event);

                service.setNetworkProvenance(entity);
 
				return summary;

			} finally {
				if (service != null)
					service.close();
				//logger.info("[memory: {}]", MemoryUtilization.getMemoryUtiliztaion());
				logger.info("[end: Created a new network based on a POSTed Network object]");
			}
	}

    @PUT
    @Path("/asNetwork")
    @Produces("application/json")
    @ApiDoc("This method updates an existing network with new content. The method takes a Network JSON " +
            "object as the PUT data. The Network object must have its UUID property set in order to identify " +
            "the network on the server to be updated.  This condition would already be satisfied in the case " +
            "of a Network object retrieved from NDEx. This method errors if the Network object is not " +
            "provided or if its UUID does not correspond to an existing network on the NDEx Server. It also " +
            "errors if the Network object is larger than a maximum size for network creation set in the NDEx " +
            "server configuration. A NetworkSummary JSON object corresponding to the updated network is " +
            "returned.")
    public NetworkSummary updateNetwork(final Network newNetwork)
            throws Exception
    {
        Preconditions
                .checkArgument(null != newNetwork, "A network is required");
        Preconditions.checkArgument(
                !Strings.isNullOrEmpty(newNetwork.getName()),
                "A network name is required");
        
		logger.info("[start: Updating network {}]", newNetwork.getExternalId().toString());

        try ( ODatabaseDocumentTx conn = NdexDatabase.getInstance().getAConnection() ) {
           User user = getLoggedInUser();

           if (!Helper.checkPermissionOnNetworkByAccountName(conn, 
        		   newNetwork.getExternalId().toString(), user.getAccountName(),
        		   Permissions.WRITE))
           {
				logger.error("[end: No write permissions for user account {} on network {}]", 
						user.getAccountName(), newNetwork.getExternalId().toString());
		        throw new UnauthorizedOperationException("User doesn't have write permissions for this network.");
           }
           
			NetworkDocDAO daoNew = new NetworkDocDAO(conn);
			
			String networkIDStr = newNetwork.getExternalId().toString();
			
			if(daoNew.networkIsReadOnly(networkIDStr)) {
				daoNew.close();
				logger.info("[end: Can't update readonly network {}]", 
						newNetwork.getExternalId().toString());
				throw new NdexException ("Can't update readonly network.");
			}
			
			if ( daoNew.networkIsLocked(networkIDStr)) {
				daoNew.close();
				logger.info("[end: Can't update locked network {}]", newNetwork.getExternalId().toString());
				throw new NdexException ("Can't modify locked network.");
			} 
			
			daoNew.lockNetwork(networkIDStr);

        }   
           
        try ( NdexNetworkCloneService service = new NdexNetworkCloneService(NdexDatabase.getInstance(), 
        		  newNetwork, getLoggedInUser().getAccountName()) ) {
        	
           logger.info("[end: Updating network {}]", newNetwork.getExternalId().toString());
           return service.updateNetwork();

        }
    }
	
	
	@DELETE
	@Path("/{UUID}")
	@Produces("application/json")
    @ApiDoc("Deletes the network specified by networkId. There is no method to undo a deletion, so care " +
	        "should be exercised. A user can only delete networks that they own.")
	public void deleteNetwork(final @PathParam("UUID") String id) throws NdexException {

		logger.info("[start: Deleting network UUID='{}']", id);
		
		String userAcc = getLoggedInUser().getAccountName();

		ODatabaseDocumentTx db = null;
		try{
			db = NdexDatabase.getInstance().getAConnection();

            if (!Helper.checkPermissionOnNetworkByAccountName(db, id, userAcc, Permissions.ADMIN))
	        {	        
				logger.error("[end: Unable to delete. User name='{}' not an admin of network UUID='{}'. Throwing  UnauthorizedOperationException ...]", 
						userAcc, id); 
				throw new UnauthorizedOperationException("Unable to delete network membership: user is not an admin of this network.");		        
	        }
            
			try (NetworkDAO networkDao = new NetworkDAO(db)) {
				
				if(networkDao.networkIsReadOnly(id)) {
					logger.info("[end: Can't delete readonly network UUID='{}']", id);					
					throw new NdexException ("Can't delete readonly network.");
				}
				  
				//logger.info("Start deleting network " + id);
				networkDao.logicalDeleteNetwork(id);
				networkDao.commit();
				Task task = new Task();
				task.setTaskType(TaskType.SYSTEM_DELETE_NETWORK);
				task.setResource(id);
				task.setAttribute("RequestsUniqueId", MDC.get("RequestsUniqueId"));
				NdexServerQueue.INSTANCE.addSystemTask(task);
			}
			db = null;
			logger.info("[end: Network UUID='{}' deleted]", id);
		} finally {
			if ( db != null) db.close();
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
	public void uploadNetwork(@MultipartForm UploadedFile uploadedNetwork)
			throws IllegalArgumentException, SecurityException, NdexException {

		logger.info("[start: Uploading network file]");
		
		try {
			Preconditions
					.checkNotNull(uploadedNetwork, "A network is required");
			Preconditions.checkState(
					!Strings.isNullOrEmpty(uploadedNetwork.getFilename()),
					"A file name containg the network data is required");
			Preconditions.checkNotNull(uploadedNetwork.getFileData(),
					"Network file data is required");
			Preconditions.checkState(uploadedNetwork.getFileData().length > 0,
					"The file data is empty");
		} catch (Exception e) {
			logger.error("[end: Uploading network file. Exception caught:]{}", e);
			throw new NdexException(e.getMessage());
		}

		String ext = FilenameUtils.getExtension(uploadedNetwork.getFilename()).toLowerCase();

		if ( !ext.equals("sif") && !ext.equals("xbel") && !ext.equals("xgmml") && !ext.equals("owl") 
				&& !ext.equals("xls") && ! ext.equals("xlsx")) {
			logger.error("[end: The uploaded file name={} type is not supported; must be Excel, XGMML, SIF, BioPAX or XBEL.  Throwing  NdexException...]", 
					uploadedNetwork.getFilename());
			throw new NdexException(
					"The uploaded file type is not supported; must be Excel, XGMML, SIF, BioPAX or XBEL.");
		}
		
		UUID taskId = NdexUUIDFactory.INSTANCE.getNDExUUID();

		final File uploadedNetworkPath = new File(Configuration.getInstance().getNdexRoot() +
				"/uploaded-networks");
		if (!uploadedNetworkPath.exists())
			uploadedNetworkPath.mkdir();

		String fileFullPath = uploadedNetworkPath.getAbsolutePath() + "/" + taskId + "." + ext;
		final File uploadedNetworkFile = new File(fileFullPath);

		if (!uploadedNetworkFile.exists())
			try {
				uploadedNetworkFile.createNewFile();
			} catch (IOException e1) {
				logger.error("[end: Failed to create file {} on server when uploading name='{}'. Exception caught:]{}",
						fileFullPath, uploadedNetwork.getFilename(), e1);
				throw new NdexException ("Failed to create file " + fileFullPath + " on server when uploading " + 
						uploadedNetwork.getFilename() + ": " + e1.getMessage());				
			}

		try ( FileOutputStream saveNetworkFile = new FileOutputStream(uploadedNetworkFile)) {
			saveNetworkFile.write(uploadedNetwork.getFileData());
			saveNetworkFile.flush();
		} catch (IOException e1) {
			logger.error("[end: Failed to write content to file {} on server when uploading {}. Exception caught:]{}", 
					fileFullPath, uploadedNetwork.getFilename(),  e1 );
			throw new NdexException ("Failed to write content to file " + fileFullPath + " on server when uploading " + 
					uploadedNetwork.getFilename() + ": " + e1.getMessage());			
		} 

		final String userAccount = this.getLoggedInUser().getAccountName();

		Task processNetworkTask = new Task();
		processNetworkTask.setExternalId(taskId);
		processNetworkTask.setDescription(uploadedNetwork.getFilename());
		processNetworkTask.setTaskType(TaskType.PROCESS_UPLOADED_NETWORK);
		processNetworkTask.setPriority(Priority.LOW);
		processNetworkTask.setProgress(0);
		processNetworkTask.setResource(fileFullPath);
		processNetworkTask.setStatus(Status.QUEUED);
		processNetworkTask.setAttribute("RequestsUniqueId", MDC.get("RequestsUniqueId"));

		try (TaskDAO dao = new TaskDAO(NdexDatabase.getInstance().getAConnection())){
			dao.createTask(userAccount, processNetworkTask);
			dao.commit();		
			
		} catch (IllegalArgumentException iae) {
			logger.error("[end: Exception caught:]{}", iae);
			throw iae;
		} catch (Exception e) {
			logger.error("[end: Exception caught:]{}", e);
			throw new NdexException(e.getMessage());
		}

		logger.info("[end: Uploading network file. Task for uploading network name='{}' is created.]",
				uploadedNetwork.getFilename());
	}



	@GET
	@Path("/{networkId}/setFlag/{parameter}={value}")
	@Produces("application/json")
    @ApiDoc("Set the system flag specified by ‘parameter’ to ‘value’ for the network with id ‘networkId’. As of " +
	        "NDEx v1.2, the only supported parameter is readOnly={true|false}")
	public String setNetworkFlag(
			@PathParam("networkId") final String networkId,
			@PathParam("parameter") final String parameter,
			@PathParam("value")     final String value)

			throws IllegalArgumentException, NdexException {
		
		    logger.info("[start: Setting {}='{}' for network UUID='{}']", parameter, value, networkId);
		    
			try (ODatabaseDocumentTx db = NdexDatabase.getInstance().getAConnection()){
				if (Helper.isAdminOfNetwork(db, networkId, getLoggedInUser().getExternalId().toString())) {
				 
				  if ( parameter.equals(readOnlyParameter)) {
					  boolean bv = Boolean.parseBoolean(value);

					  try (NetworkDAOTx daoNew = new NetworkDAOTx()) {
						  long oldId = daoNew.setReadOnlyFlag(networkId, bv, getLoggedInUser().getAccountName());
						  logger.info("[end: Set {}='{}' for network UUID='{}']", parameter, value, networkId);
						  return Long.toString(oldId);
					  } 
				  }
				}
				logger.error("[end: Unable to set {}='{}' for network UUID='{}'. Only admin can set network flag.]", 
						parameter, value, networkId);
				throw new UnauthorizedOperationException("Only an administrator can set a network flag.");
			}
	}

}
