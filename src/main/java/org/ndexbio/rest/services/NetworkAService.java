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
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.io.FilenameUtils;
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.access.NetworkAOrientDBDAO;
import org.ndexbio.common.exceptions.ObjectNotFoundException;
import org.ndexbio.common.models.dao.orientdb.Helper;
import org.ndexbio.common.models.dao.orientdb.NetworkDAO;
import org.ndexbio.common.models.dao.orientdb.NetworkSearchDAO;
import org.ndexbio.common.models.dao.orientdb.TaskDAO;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.Membership;
import org.ndexbio.model.object.NdexPropertyValuePair;
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
import org.ndexbio.common.util.NdexUUIDFactory;
//import org.ndexbio.model.object.SearchParameters;
import org.ndexbio.model.object.network.BaseTerm;
import org.ndexbio.model.object.network.Namespace;
import org.ndexbio.model.object.network.Network;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.object.network.PropertyGraphNetwork;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.annotations.ApiDoc;
import org.ndexbio.rest.helpers.UploadedFile;
import org.ndexbio.task.Configuration;
import org.ndexbio.task.NdexServerQueue;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.record.impl.ODocument;

import org.slf4j.Logger;

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
		
		logger.info(userNameForLog() + "[start: Getting BaseTerm objects from network " + networkId + ", skipBlocks " + skipBlocks + ", blockSize " + blockSize + "]");
		
		ODatabaseDocumentTx db = null;
		try {
			db = NdexDatabase.getInstance().getAConnection();
			NetworkDAO daoNew = new NetworkDAO(db);
			return (List<BaseTerm>) daoNew.getBaseTerms(networkId);
		} finally {
			if ( db != null) db.close();
			logger.info(userNameForLog() + "[end: Got BaseTerm objects from network " + networkId + ", skipBlocks " + skipBlocks + ", blockSize " + blockSize + "]");
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
		
		logger.info(userNameForLog() + "[start: Getting list of namespaces for network " + networkId + ", skipBlocks " + skipBlocks + ", blockSize " + blockSize + "]");
		
		ODatabaseDocumentTx db = null;
		try {
			db = NdexDatabase.getInstance().getAConnection();
			NetworkDAO daoNew = new NetworkDAO(db);
			return (List<Namespace>) daoNew.getNamespaces(networkId);
		} finally {
			if ( db != null) db.close();
			logger.info(userNameForLog() + "[end: Got list of namespaces for network " + networkId + ", skipBlocks " + skipBlocks + ", blockSize " + blockSize + "]");
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
		
		logger.info(userNameForLog() + "[start: Adding namespace to the network " + networkId + "]");

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
            ProvenanceEvent event = new ProvenanceEvent("Add Namespace", now);
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
			logger.info(userNameForLog() + "[end: Added namespace to the network " + networkId + "]");
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
    @ApiDoc("Retrieves the 'provenance' field of the network specified by 'networkId' as a ProvenanceEntity object, " +
            "if it exists.  The ProvenanceEntity object is expected to represent the current state of the network and" +
            " to contain a tree-structure of ProvenanceEvent and ProvenanceEntity objects that describe the networks " +
            "provenance history.")
	public ProvenanceEntity getProvenance(
			@PathParam("networkId") final String networkId)

			throws IllegalArgumentException, JsonParseException, JsonMappingException, IOException, NdexException {
		
		logger.info(userNameForLog() + "[start: Getting provenance of network " + networkId + "]");

		ODatabaseDocumentTx db = null;
		try {

			db = NdexDatabase.getInstance().getAConnection();
			NetworkDAO daoNew = new NetworkDAO(db);
			return daoNew.getProvenance(UUID.fromString(networkId));

		} finally {

			if (null != db) db.close();
			logger.info(userNameForLog() + "[end: Got provenance of network " + networkId + "]");
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
    @ApiDoc("Updates the 'provenance' field of the network specified by 'networkId' to be the ProvenanceEntity object" +
            " in the PUT data.  The ProvenanceEntity object is expected to represent the current state of the network" +
            " and to contain a tree-structure of ProvenanceEvent and ProvenanceEntity objects that describe the " +
            "networks provenance history.")
    public ProvenanceEntity setProvenance(@PathParam("networkId")final String networkId, final ProvenanceEntity provenance)
    		throws Exception {

    	logger.info(userNameForLog() + "[start: Updating provenance of network " + networkId + "]");
    	
    	ODatabaseDocumentTx db = null;
    	NetworkDAO daoNew = null;

		try {
			db = NdexDatabase.getInstance().getAConnection();
			
			User user = getLoggedInUser();

			if ( !Helper.checkPermissionOnNetworkByAccountName(db, networkId, user.getAccountName(),
					Permissions.WRITE)) {
				logger.error(userNameForLog() + "[end: No write permissions for user account " + user.getAccountName() + " on network " +
					networkId + "]");
				throw new WebApplicationException(HttpURLConnection.HTTP_UNAUTHORIZED);
			}

			daoNew = new NetworkDAO(db);
			UUID networkUUID = UUID.fromString(networkId);
			daoNew.setProvenance(networkUUID, provenance);
			daoNew.commit();
			return daoNew.getProvenance(networkUUID);
		} catch (Exception e) {
			if (null != daoNew) daoNew.rollback();
			logger.error(userNameForLog() + "[end: Updating provenance of network " + networkId + ". Exception caught:]",  e);			
			throw e;
		} finally {
			if (null != db) db.close();
			logger.info(userNameForLog() + "[end: Updated provenance of network " + networkId + "]");
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
    	
    	logger.info(userNameForLog() + "[start: Updating properties of network " + networkId + "]");

    	ODatabaseDocumentTx db = null;
    	NetworkDAO daoNew = null;

		try {
			db = NdexDatabase.getInstance().getAConnection();
			
			User user = getLoggedInUser();

			if ( !Helper.checkPermissionOnNetworkByAccountName(db, networkId, user.getAccountName(),
					Permissions.WRITE)) {
				logger.error(userNameForLog() + "[end: No write permissions for user account " + user.getAccountName() + " on network " +
						networkId + "]");				
				throw new WebApplicationException(HttpURLConnection.HTTP_UNAUTHORIZED);
			}

			daoNew = new NetworkDAO(db);
			UUID networkUUID = UUID.fromString(networkId);
			int i = daoNew.setNetworkProperties(networkUUID, properties);

            //DW: Handle provenance



            ProvenanceEntity oldProv = daoNew.getProvenance(networkUUID);
            ProvenanceEntity newProv = new ProvenanceEntity();
            newProv.setUri( oldProv.getUri() );

            Helper.populateProvenanceEntity(newProv, daoNew, networkId);

            NetworkSummary summary = daoNew.getNetworkSummary(daoNew.getRecordByUUIDStr(networkId, null));
            Helper.populateProvenanceEntity(newProv, summary);
            ProvenanceEvent event = new ProvenanceEvent("Set Network Properties", summary.getModificationTime());
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
			logger.error(userNameForLog() + "[end: Updating properties of network " + networkId + ". Exception caught:]", e);	
			throw new NdexException(e.getMessage());
		} finally {
			if (null != db) db.close();
			logger.info(userNameForLog() + "[end: Updated properties of network " + networkId + "]");
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

    	logger.info(userNameForLog() + "[start: Updating presentationProperties field of network " + networkId + "]");
   
    	ODatabaseDocumentTx db = null;
    	NetworkDAO daoNew = null;

		try {
			db = NdexDatabase.getInstance().getAConnection();
			User user = getLoggedInUser();

			if ( !Helper.checkPermissionOnNetworkByAccountName(db, networkId, user.getAccountName(),
					Permissions.WRITE)) {
				logger.error(userNameForLog() + "[end: No write permissions for user account " + user.getAccountName() + " on network " +
						networkId + "]");					
				throw new WebApplicationException(HttpURLConnection.HTTP_UNAUTHORIZED);
			}

			daoNew = new NetworkDAO(db);
			UUID networkUUID = UUID.fromString(networkId);
			int i = daoNew.setNetworkPresentationProperties(networkUUID, properties);

            //DW: Handle provenance
            ProvenanceEntity oldProv = daoNew.getProvenance(networkUUID);
            ProvenanceEntity newProv = new ProvenanceEntity();
            newProv.setUri( oldProv.getUri() );

            NetworkSummary summary = daoNew.getNetworkSummary(daoNew.getRecordByUUIDStr(networkId, null));
            Helper.populateProvenanceEntity(newProv, summary);
            ProvenanceEvent event = new ProvenanceEvent("Set Network Presentation Properties", summary.getModificationTime());

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
			logger.error(userNameForLog() + "[end: Updating presentationProperties field of network " + networkId + ". Exception caught:]", e);	
			throw e;
		} finally {
			if (null != db) db.close();
			logger.info(userNameForLog() + "[end: Updated presentationProperties field of network " + networkId + "]");			
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
    @ApiDoc("Retrieves a NetworkSummary object based on the network specified by 'networkUUID'.  This method returns " +
            "an error if the network is not found or if the authenticated user does not have READ permission for the " +
            "network.")
	public NetworkSummary getNetworkSummary(
			@PathParam("networkId") final String networkId)

			throws IllegalArgumentException, NdexException {

    	logger.info(userNameForLog() + "[start: Getting networkSummary of network " + networkId + "]");

		ODatabaseDocumentTx db = null;
		try {
			db = NdexDatabase.getInstance().getAConnection();

			NetworkDAO networkDao = new NetworkDAO(db);

			VisibilityType vt = Helper.getNetworkVisibility(db, networkId);
			boolean hasPrivilege = (vt == VisibilityType.PUBLIC || vt== VisibilityType.DISCOVERABLE);

			if ( !hasPrivilege && getLoggedInUser() != null) {
				hasPrivilege = networkDao.checkPrivilege(getLoggedInUser().getAccountName(),
						networkId, Permissions.READ);
			}
			if ( hasPrivilege) {
				ODocument doc =  networkDao.getNetworkDocByUUIDString(networkId);
				if (doc == null)
					throw new ObjectNotFoundException("Network", networkId);
				NetworkSummary summary = NetworkDAO.getNetworkSummary(doc);
				db.close();
				db = null;
				//logInfo(logger, "NetworkSummary of " + networkId + " returned.");
				return summary;

			}
		} finally {
			if (db != null) db.close();
			logger.info(userNameForLog() + "[end: Got networkSummary of network " + networkId + "]");
		}
		
		logger.error(userNameForLog() + "[end: Getting networkSummary of network " + networkId + "  Throwing WebApplicationException exception ...]");	
        throw new WebApplicationException(HttpURLConnection.HTTP_UNAUTHORIZED);
	}


	@PermitAll
	@GET
	@Path("/{networkId}/edge/asNetwork/{skipBlocks}/{blockSize}")
	@Produces("application/json")
	@ApiDoc("Retrieves a subnetwork of a network based on a block (where a block is simply a contiguous set) of edges" +
            ". The network is specified by 'networkId'  and the maximum number of edges to retrieve in the query is " +
            "set by 'blockSize' (which may be any number chosen by the user)  while  'skipBlocks' specifies the " +
            "number of blocks that have already been read. The subnetwork is returned as a Network object containing " +
            "the Edge objects specified by the query along with all of the other network elements relevant to the " +
            "edges. (Compare this method to getPropertyGraphEdges).\n")
	public Network getEdges(
			@PathParam("networkId") final String networkId,
			@PathParam("skipBlocks") final int skipBlocks,
			@PathParam("blockSize") final int blockSize)

			throws IllegalArgumentException, NdexException {

    	logger.info(userNameForLog() + "[start: Getting edges of network " + networkId + ", skipBlocks " + skipBlocks + ", blockSize " + blockSize +"]");		
    	
		ODatabaseDocumentTx db = null;
		try {
			db = NdexDatabase.getInstance().getAConnection();
			NetworkDAO dao = new NetworkDAO(db);
	 		Network n = dao.getNetwork(UUID.fromString(networkId), skipBlocks, blockSize);
	        return n;
		} finally {
			if ( db !=null) db.close();
			logger.info(userNameForLog() + "[end: Got edges of network " + networkId + ", skipBlocks " + skipBlocks + ", blockSize " + blockSize +"]");
		}
	}

	@PermitAll
	@GET
	@Path("/{networkId}/asNetwork")
//	@Produces("application/json")
    @ApiDoc("Retrieve an entire network specified by 'networkId' as a Network object.  (Compare this method to " +
            "getCompleteNetworkAsPropertyGraph).")
	// new Implmentation to handle cached network 
	public Response getCompleteNetwork(	@PathParam("networkId") final String networkId)
			throws IllegalArgumentException, NdexException {

		if ( isSearchable(networkId) ) {
			
			ODatabaseDocumentTx db = NdexDatabase.getInstance().getAConnection();
			NetworkDAO daoNew = new NetworkDAO(db);
			
			NetworkSummary sum = daoNew.getNetworkSummaryById(networkId);
/*			if ( sum.getIsReadOnly()) {
				daoNew.close();
				try {
					FileInputStream in = new FileInputStream(
				
						Configuration.getInstance().getNdexRoot() + "/" + NetworkDAO.workspaceDir + "/" 
						+ sum.getOwner() + "/" + sum.getExternalId() + ".json")  ;
				
					setZipFlag();
					logger.info("returning cached network.");
					return 	Response.ok().type(MediaType.APPLICATION_JSON_TYPE).entity(in).build();
				} catch (IOException e) {
					throw new NdexException ("Ndex server can't find file: " + e.getMessage());
				}
			}  */ 	

			Network n = daoNew.getNetworkById(UUID.fromString(networkId));
			daoNew.close();
			logger.info("returning network from query.");
			return Response.ok(n,MediaType.APPLICATION_JSON_TYPE).build();
		}
		else
			throw new WebApplicationException(HttpURLConnection.HTTP_UNAUTHORIZED);

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
		
		logger.info(userNameForLog() + "[start: Retrieving an entire network " + networkId + "as a PropertyGraphNetwork object]");
		
		if ( isSearchable(networkId) ) {
		
			ODatabaseDocumentTx db =null;
			try {
				db = NdexDatabase.getInstance().getAConnection();
				NetworkDAO daoNew = new NetworkDAO(db);
				PropertyGraphNetwork n = daoNew.getProperytGraphNetworkById(UUID.fromString(networkId));
				return n;
			} finally {
				if (db !=null ) db.close();
				logger.info(userNameForLog() + "[end: Retrieved an entire network " + networkId + "as a PropertyGraphNetwork object]");
			}
		}
		else {
			logger.error(userNameForLog() + "[end: Retrieving an entire network " + networkId + "as a PropertyGraphNetwork object. Throwing WebApplicationException exception ...]");
			throw new WebApplicationException(HttpURLConnection.HTTP_UNAUTHORIZED);
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

		logger.info(userNameForLog() + "[start: Retrieving a subnetwork of network " + networkId + " with skipBlocks " + skipBlocks + " and blockSize " + blockSize + "]");
		ODatabaseDocumentTx db = NdexDatabase.getInstance().getAConnection();
		NetworkDAO dao = new NetworkDAO(db);
 		PropertyGraphNetwork n = dao.getProperytGraphNetworkById(UUID.fromString(networkId),skipBlocks, blockSize);
		db.close();
		logger.info(userNameForLog() + "[end: Retrieved a subnetwork of network " + networkId + " with skipBlocks " + skipBlocks + " and blockSize " + blockSize + "]");		
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
            "objects and may take the following set of values: READ, WRITE, and ADMIN. Memberships of all types can " +
            "be retrieved by permission = 'ALL'.   The maximum number of Membership objects to retrieve in the query " +
            "is set by 'blockSize' (which may be any number chosen by the user) while  'skipBlocks' specifies the " +
            "number of blocks that have already been read.")
	public List<Membership> getNetworkUserMemberships(@PathParam("networkId") final String networkId,
			@PathParam("permission") final String permissions ,
			@PathParam("skipBlocks") int skipBlocks,
			@PathParam("blockSize") int blockSize) throws NdexException {

		//logInfo( logger, "Get all " + permissions + " accounts on network " + networkId);
		logger.info(userNameForLog() + "[start: Get all " + permissions + "accounts on network " + networkId + ", skipBlocks " + skipBlocks + " blockSize " + blockSize + "]");
		Permissions permission = Permissions.valueOf(permissions.toUpperCase());

		ODatabaseDocumentTx db = null;
		try {

			db = NdexDatabase.getInstance().getAConnection();
			NetworkDAO networkDao = new NetworkDAO(db);
            
			List<Membership> results = networkDao.getNetworkUserMemberships(
					UUID.fromString(networkId), permission, skipBlocks, blockSize);
			//logInfo(logger, results.size() + " members returned for network " + networkId);
			logger.info(userNameForLog() + "[end: Got " + results.size() + " members returned for network " + networkId + "]");
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
		
		logger.info(userNameForLog() + "[start: Removing any permissions for network " + networkId + " for user " + userUUID + "]");
		
		ODatabaseDocumentTx db = null;
		try {
			db = NdexDatabase.getInstance().getAConnection();
			User user = getLoggedInUser();
			NetworkDAO networkDao = new NetworkDAO(db);

			if (!Helper.isAdminOfNetwork(db, networkId, user.getExternalId().toString())) {
				logger.error(userNameForLog() + "[end: User " + userUUID + " not an admin of network " + networkId + 
						".  Throwing  WebApplicationException exception ...]");				
				throw new WebApplicationException(HttpURLConnection.HTTP_UNAUTHORIZED);
			}

			int count = networkDao.revokePrivilege(networkId, userUUID);
            db.commit();
    		logger.info(userNameForLog() + "[end: Removed any permissions for network " + networkId + " for user " + userUUID + "]");
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
		
		logger.info(userNameForLog() + "[start: Updating membership for network " + networkId + "]");
		
		ODatabaseDocumentTx db = null;
		try {
			db = NdexDatabase.getInstance().getAConnection();

			User user = getLoggedInUser();
			NetworkDAO networkDao = new NetworkDAO(db);

			if (!Helper.isAdminOfNetwork(db, networkId, user.getExternalId().toString())) {
				logger.error(userNameForLog() + "[end: User " + user.getExternalId().toString() + " not an admin of network " + networkId + 
						".  Throwing  WebApplicationException exception ...]");					
				throw new WebApplicationException(HttpURLConnection.HTTP_UNAUTHORIZED);
			}

	        int count = networkDao.grantPrivilege(networkId, membership.getMemberUUID().toString(), membership.getPermissions());
			db.commit();
			logger.info(userNameForLog() + "[end: Updated membership for network " + networkId + "]");
	        return count;
		} finally {
			if (db != null) db.close();
		}
	}

	@POST
	@Path("/{networkId}/summary")
	@Produces("application/json")
    @ApiDoc("POSTs a NetworkSummary object to update the pro information of the network specified by networkUUID." +
            " The NetworkSummary POSTed may be only partially populated. The only fields that will be acted on are: " +
            "'name', 'description','version', and 'visibility' if they are present.")
	public void updateNetworkProfile(
			@PathParam("networkId") final String networkId,
			final NetworkSummary summary
			)
            throws IllegalArgumentException, NdexException, IOException
    {

		logger.info(userNameForLog() + "[start: Updating the pro information for network " + networkId + "]");
		
		ODatabaseDocumentTx db = null;
		try {
			db = NdexDatabase.getInstance().getAConnection();

			User user = getLoggedInUser();
			NetworkDAO networkDao = new NetworkDAO(db);

			if ( !Helper.checkPermissionOnNetworkByAccountName(db, networkId, user.getAccountName(),
					Permissions.WRITE)) {
				logger.error(userNameForLog() + "[end: No write permissions for user account " + user.getAccountName() + " on network " +
						networkId + ".  Throwing  WebApplicationException exception ...]");		
				throw new WebApplicationException(HttpURLConnection.HTTP_UNAUTHORIZED);
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

                ProvenanceEvent event = new ProvenanceEvent("Update Network Profile", summary.getModificationTime());

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
			logger.info(userNameForLog() + "[end: Updated the pro information for network " + networkId + "]");
		}
	}



	@PermitAll
	@POST
	@Path("/{networkId}/asNetwork/query")
	@Produces("application/json")
    @ApiDoc("Retrieves a 'neighborhood' subnetwork of a network based on identifiers specified in a POSTed " +
            "SimplePathQuery object based on a parameter set by the user. The network to be queried is specified by " +
            "networkId. In the first step of the query, a set of base terms exactly matching identifiers found in the" +
            " 'searchString' field of the SimplePathQuery is selected. In the second step, " +
            "nodes are selected that reference the base terms identified in the network.  Finally, " +
            "a set of edges is selected by traversing outwards from each of these selected nodes, " +
            "up to the limit specified by the 'searchDepth' field of the SimplePathQuery.  The subnetwork is returned" +
            " as a Network object containing the selected Edge objects along with any other network elements relevant" +
            " to the edges.")
	public Network queryNetwork(
			@PathParam("networkId") final String networkId,
			final SimplePathQuery queryParameters
//			@PathParam("skipBlocks") final int skipBlocks, 
//			@PathParam("blockSize") final int blockSize
			)

			throws IllegalArgumentException, NdexException {

		//logInfo (logger, "Neighborhood search on " + networkId + " with phrase \"" + queryParameters.getSearchString() + "\"");
		logger.info(userNameForLog() + "[start: Retrieving neighborhood subnetwork for network " + networkId +  
				" with phrase \"" + queryParameters.getSearchString() + "\"]");

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

			   Network n = dao.queryForSubnetwork(networkId, queryParameters);
			   //logInfo(logger, "Subnetwork from query returned." );
			   logger.info(userNameForLog() + "[end: Subnetwork for network " + networkId +  
						" with phrase \"" + queryParameters.getSearchString() + "\" retrieved]");			   
			   return n;
			   //getProperytGraphNetworkById(UUID.fromString(networkId),skipBlocks, blockSize);
		   }

		   logger.error(userNameForLog() + "[end: Retrieving neighborhood subnetwork for network " + networkId +  
					" with phrase \"" + queryParameters.getSearchString() + "\".  Throwing WebApplicationException exception ...]");
		   throw new WebApplicationException(HttpURLConnection.HTTP_UNAUTHORIZED);
		} 
	}
	
	private boolean isSearchable(String networkId) 
				throws ObjectNotFoundException, NdexException {
		   ODatabaseDocumentTx db = NdexDatabase.getInstance().getAConnection();
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
		   return hasPrivilege;
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

		logger.info(userNameForLog() + "[start: Retrieving neighborhood subnetwork for network " + networkId +  
				" based on SimplePathQuery object]");
		
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
				logger.info(userNameForLog() + "[start: Retrieved neighborhood subnetwork for network " + networkId +  
						" based on SimplePathQuery object]");
				return n;

			}
		} finally {
			if ( db !=null) db.close();
		}

		logger.error(userNameForLog() + "[end: Retrieving neighborhood subnetwork for network " + networkId +  
					" based on SimplePathQuery object.  Throwing WebApplicationException exception ...]");		
		throw new WebApplicationException(HttpURLConnection.HTTP_UNAUTHORIZED);
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
    @ApiDoc("Returns a list of NetworkSummary objects based on a POSTed NetworkQuery object. The NetworkQuery may be " +
            "either a NetworkSimpleQuery specifying only a search string or a NetworkMembershipQuery that also " +
            "constrains the search to networks administered by a user or group. The maximum number of NetworkSummary " +
            "objects to retrieve in the query is set by 'blockSize'  (which may be any number chosen by the user)  " +
            "while  'skipBlocks' specifies number of blocks that have already been read.")
	public Collection<NetworkSummary> searchNetwork(
			final SimpleNetworkQuery query,
			@PathParam("skipBlocks") final int skipBlocks,
			@PathParam("blockSize") final int blockSize)
			throws IllegalArgumentException, NdexException {

		//logInfo ( logger, "Search networks: \"" + query.getSearchString() + "\"");
		
		logger.info(userNameForLog() + "[start: Retrieving NetworkSummary objects using query \"" + query.getSearchString() + "\"]");
		
    	if(query.getAccountName() != null)
    		query.setAccountName(query.getAccountName().toLowerCase());
        
    	try (ODatabaseDocumentTx db = NdexDatabase.getInstance().getAConnection()) {
            NetworkSearchDAO dao = new NetworkSearchDAO(db);
            Collection<NetworkSummary> result = new ArrayList <> ();

			result = dao.findNetworks(query, skipBlocks, blockSize, this.getLoggedInUser());
			//logInfo ( logger, result.size() + " networks returned from search.");
			logger.info(userNameForLog() + "[end: Retrieved " + result.size() + " NetworkSummary objects]");
			return result;

        } catch (Exception e) {
        	//e.printStackTrace();
			//logger.error(userNameForLog() + "[end: Updating properties of network " + networkId + "  Exception caught: " + e + "]");
			logger.error(userNameForLog() + "[end: Retrieving NetworkSummary objects using query \"" + 
			    query.getSearchString() + "\". Exception caught:]", e);			
        	throw new NdexException(e.getMessage());
        } 

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
			
			logger.info(userNameForLog() + "[start: Creating a new network based on a POSTed NetworkPropertyGraph object]");
			
			NdexDatabase db = NdexDatabase.getInstance();
			PropertyGraphLoader pgl = null;
			pgl = new PropertyGraphLoader(db);
			NetworkSummary ns = pgl.insertNetwork(newNetwork, getLoggedInUser());
			
			logger.info(userNameForLog() + "[end: Created a new network based on a POSTed NetworkPropertyGraph object]");
			
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
    @ApiDoc("Creates a new network based on a POSTed Network object. This method errors if the Network object is not " +
            "provided or if it does not specify a name. It also errors if the Network object is larger than a maximum" +
            " size for network creation set in the NDEx server configuration. A NetworkSummary object is returned.")
	public NetworkSummary createNetwork(final Network newNetwork)
			throws 	Exception {
			Preconditions
				.checkArgument(null != newNetwork, "A network is required");
			Preconditions.checkArgument(
				!Strings.isNullOrEmpty(newNetwork.getName()),
				"A network name is required");

			logger.info(userNameForLog() + "[start: Creating a new network based on a POSTed Network object]");


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

                ProvenanceEvent event = new ProvenanceEvent("Program Upload", summary.getModificationTime());

                List<SimplePropertyValuePair> eventProperties = new ArrayList<>();
                Helper.addUserInfoToProvenanceEventProperties( eventProperties, this.getLoggedInUser());
                event.setProperties(eventProperties);

                entity.setCreationEvent(event);

                service.setNetworkProvenance(entity);
                
    			logger.info(userNameForLog() + "[end: Created a new network based on a POSTed Network object]");
                
				return summary;

			} finally {
				if ( service !=null)
					service.close();
			}
	}

/*	comment out this function for now, until we can make this function thread safe.
    @PUT
    @Path("/asNetwork")
    @Produces("application/json")
    @ApiDoc("Updates an existing network using a PUT call using a Network object. The Network object must contain a " +
            "UUID (this would normally be the case for a Network object retrieved from NDEx, " +
            "so no additional work is required in the most common case) and the user must have permission to modify " +
            "this network. This method errors if the Network object is not provided or if it does not have a valid " +
            "UUID on the server. It also errors if the Network object is larger than a maximum size for network " +
            "creation set in the NDEx server configuration. A NetworkSummary object is returned.")
    public NetworkSummary updateNetwork(final Network newNetwork)
            throws Exception
    {
        Preconditions
                .checkArgument(null != newNetwork, "A network is required");
        Preconditions.checkArgument(
                !Strings.isNullOrEmpty(newNetwork.getName()),
                "A network name is required");

        NdexDatabase db = null;
        NdexNetworkCloneService service = null;
        ODatabaseDocumentTx conn = null;
        
        try
        {
           db = new NdexDatabase(Configuration.getInstance().getHostURI());
           conn = db.getAConnection();
           
           User user = getLoggedInUser();

           if (!Helper.checkPermissionOnNetworkByAccountName(conn, 
        		   newNetwork.getExternalId().toString(), user.getAccountName(),
        		   Permissions.WRITE))
           {
        	   throw new WebApplicationException(HttpURLConnection.HTTP_UNAUTHORIZED);
           }

           service = new NdexNetworkCloneService(db, newNetwork,
                    getLoggedInUser().getAccountName());

           return service.updateNetwork();

        }
        finally
        {
            if (service != null)
                service.close();
            if (db != null)
                db.close();
            if ( conn!= null) conn.close(); 
        }
    }
*/	
	
	@DELETE
	@Path("/{UUID}")
	@Produces("application/json")
    @ApiDoc("Deletes the network specified by 'UUID'.")
	public void deleteNetwork(final @PathParam("UUID") String id) throws NdexException {

		logger.info(userNameForLog() + "[start: Deleting network " + id + "]");
		
		String userAcc = getLoggedInUser().getAccountName();
		//logInfo(logger, "Deleting network  " + id);
		ODatabaseDocumentTx db = null;
		try{
			db = NdexDatabase.getInstance().getAConnection();

            if (!Helper.checkPermissionOnNetworkByAccountName(db, id, userAcc, Permissions.ADMIN))
	        {
	           throw new WebApplicationException(HttpURLConnection.HTTP_UNAUTHORIZED);
	        }

			try (NetworkDAO networkDao = new NetworkDAO(db)) {
				//logger.info("Start deleting network " + id);
				networkDao.logicalDeleteNetwork(id);
				networkDao.commit();
				Task task = new Task();
				task.setTaskType(TaskType.SYSTEM_DELETE_NETWORK);
				task.setResource(id);
				NdexServerQueue.INSTANCE.addSystemTask(task);
			}
			db = null;
			logger.info(userNameForLog() + "[end: Deleted network " + id + "]");
			//logger.info("Network " + id + " deleted.");
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

		logger.info(userNameForLog() + "[start: Uploading network file]");
		
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
		} catch (Exception e1) {
			logger.error(userNameForLog() + "[end: Uploading network file. Exception caught:]", e1 );
			throw new IllegalArgumentException(e1);
		}

		String ext = FilenameUtils.getExtension(uploadedNetwork.getFilename()).toLowerCase();

		if ( !ext.equals("sif") && !ext.equals("xbel") && !ext.equals("xgmml") && !ext.equals("owl") 
				&& !ext.equals("xls") && ! ext.equals("xlsx")) {
			logger.error(userNameForLog() + "[end: The uploaded file type is not supported; must be Excel, XGMML, SIF, BioPAX or XBEL.  Throwing  IllegalArgumentException...]");
			throw new IllegalArgumentException(
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
				//e1.printStackTrace();
				logger.error(userNameForLog() + "[end: Failed to create file " + fileFullPath + " on server when uploading " + 
						uploadedNetwork.getFilename() + ". Exception caught:]", e1);
				throw new NdexException ("Failed to create file " + fileFullPath + " on server when uploading " + 
						uploadedNetwork.getFilename() + ": " + e1.getMessage());
			}

		try ( FileOutputStream saveNetworkFile = new FileOutputStream(uploadedNetworkFile)) {
			saveNetworkFile.write(uploadedNetwork.getFileData());
			saveNetworkFile.flush();
		} catch (IOException e1) {
			//e1.printStackTrace();
			logger.error(userNameForLog() + "[end: Failed to write content to file " + fileFullPath + " on server when uploading " + 
					uploadedNetwork.getFilename() + ". Exception caught:]", e1 );
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

		try (TaskDAO dao = new TaskDAO(NdexDatabase.getInstance().getAConnection())){
			dao.createTask(userAccount, processNetworkTask);
			dao.commit();
			
		} catch (IllegalArgumentException iae) {
			logger.error(userNameForLog() + "[end: Exception caught:]", iae);
			throw iae;
		} catch (Exception e) {
			//Logger.getLogger(this.getClass().getName()).severe("Failed to process uploaded network: "
			//		+ uploadedNetwork.getFilename() + ". " + e.getMessage());
			logger.error(userNameForLog() + "[end: Exception caught:]",  e);
			throw new NdexException(e.getMessage());
		} 
	}



	@GET
	@Path("/{networkId}/setFlag/{parameter}={value}")
	@Produces("application/json")
    @ApiDoc("Set the certain Ndex system flag onnetwork. Supported parameters are:"+
	        "readOnly")
	public String setNetworkFlag(
			@PathParam("networkId") final String networkId,
			@PathParam("parameter") final String parameter,
			@PathParam("value")     final String value)

			throws IllegalArgumentException, NdexException {
		
		    logger.info(userNameForLog() + "[start: Setting flag " + parameter + " with value " + value + " for network " + networkId + "]");
		
			try (ODatabaseDocumentTx db = NdexDatabase.getInstance().getAConnection()){
				if (Helper.isAdminOfNetwork(db, networkId, getLoggedInUser().getExternalId().toString())) {
				 
				  if ( parameter.equals(readOnlyParameter)) {
					  boolean bv = Boolean.parseBoolean(value);

					  NetworkDAO daoNew = new NetworkDAO(db);
					 // try { 
						  long oldId = daoNew.setReadOnlyFlag(networkId, bv, getLoggedInUser().getAccountName());
						  if( ( (oldId <0 && bv) || oldId >0 && bv == false))
								 daoNew.commit();
						  logger.info(userNameForLog() + "[end: Done setting flag " + parameter + " with value " + value + " for network " + networkId + "]");
						  return Long.toString(oldId);
					 /* } catch (IOException e) {
						  //e.printStackTrace();
						  logger.error(userNameForLog() + "[end: Ndex server internal IOException. Exception caught:]",  e);
						  throw new NdexException ("Ndex server internal IOException: " + e.getMessage());
					  } */
				  }
				}
				throw new UnauthorizedOperationException("Only an administrator can set a network flag.");
			}
	}

}
