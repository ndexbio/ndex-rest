package org.ndexbio.rest.services;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

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

import org.apache.commons.io.FilenameUtils;
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;
import org.ndexbio.common.access.NdexAOrientDBConnectionPool;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.access.NetworkAOrientDBDAO;
import org.ndexbio.common.exceptions.ObjectNotFoundException;
import org.ndexbio.common.models.dao.orientdb.Helper;
import org.ndexbio.common.models.dao.orientdb.NetworkDAO;
import org.ndexbio.common.models.dao.orientdb.NetworkSearchDAO;
import org.ndexbio.common.models.dao.orientdb.TaskDAO;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.Status;
import org.ndexbio.model.object.TaskType;
import org.ndexbio.common.models.object.network.RawNamespace;
import org.ndexbio.common.persistence.orientdb.NdexNetworkCloneService;
import org.ndexbio.common.persistence.orientdb.NdexPersistenceService;
import org.ndexbio.common.persistence.orientdb.PropertyGraphLoader;
import org.ndexbio.common.util.NdexUUIDFactory;
import org.ndexbio.model.object.Membership;
import org.ndexbio.model.object.NdexPropertyValuePair;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.Priority;
import org.ndexbio.model.object.ProvenanceEntity;
//import org.ndexbio.model.object.SearchParameters;
import org.ndexbio.model.object.SimpleNetworkQuery;
import org.ndexbio.model.object.SimplePathQuery;
import org.ndexbio.model.object.SimplePropertyValuePair;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.User;
import org.ndexbio.model.object.network.BaseTerm;
import org.ndexbio.model.object.network.Namespace;
import org.ndexbio.model.object.network.Network;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.object.network.PropertyGraphNetwork;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.annotations.ApiDoc;
import org.ndexbio.rest.helpers.UploadedFile;
import org.ndexbio.task.Configuration;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.record.impl.ODocument;

@Path("/network")
public class NetworkAService extends NdexService {
	
	static Logger logger = Logger.getLogger(NetworkAService.class.getName());


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
		ODatabaseDocumentTx db = null;
		try {
			db = NdexDatabase.getInstance().getAConnection();
			NetworkDAO daoNew = new NetworkDAO(db);
			return (List<BaseTerm>) daoNew.getBaseTerms(networkId);
		} finally {
			if ( db != null) db.close();
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
		ODatabaseDocumentTx db = null;
		try {
			db = NdexDatabase.getInstance().getAConnection();
			NetworkDAO daoNew = new NetworkDAO(db);
			return (List<Namespace>) daoNew.getNamespaces(networkId);
		} finally {
			if ( db != null) db.close();
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
			throws IllegalArgumentException, NdexException {

		NdexDatabase db = null; 
		NdexPersistenceService networkService = null;
		try {
			db = NdexDatabase.getInstance();
			networkService = new NdexPersistenceService(
					db,
					UUID.fromString(networkId));

			networkService.getNamespace(new RawNamespace(namespace.getPrefix(), namespace.getUri()));

			networkService.commit();
			networkService.close();
		} finally {
			
			if (networkService != null) networkService.close();
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
		ODatabaseDocumentTx db = null;
		try {

			db = NdexDatabase.getInstance().getAConnection();
			NetworkDAO daoNew = new NetworkDAO(db);
			return daoNew.getProvenance(UUID.fromString(networkId));

		} finally {

			if (null != db) db.close();
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

    	ODatabaseDocumentTx db = null;
    	NetworkDAO daoNew = null;

		try {
			db = NdexDatabase.getInstance().getAConnection();
			
			User user = getLoggedInUser();

			if ( !Helper.checkPermissionOnNetworkByAccountName(db, networkId, user.getAccountName(),
					Permissions.WRITE)) {
				throw new WebApplicationException(HttpURLConnection.HTTP_UNAUTHORIZED);
			}

			daoNew = new NetworkDAO(db);
			UUID networkUUID = UUID.fromString(networkId);
			daoNew.setProvenance(networkUUID, provenance);
			daoNew.commit();
			return daoNew.getProvenance(networkUUID);
		} catch (Exception e) {
			if (null != daoNew) daoNew.rollback();
			throw e;
		} finally {
			if (null != db) db.close();
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

		logInfo(logger, "Update properties of network " + networkId);
    	ODatabaseDocumentTx db = null;
    	NetworkDAO daoNew = null;

		try {
			db = NdexDatabase.getInstance().getAConnection();
			
			User user = getLoggedInUser();

			if ( !Helper.checkPermissionOnNetworkByAccountName(db, networkId, user.getAccountName(),
					Permissions.WRITE)) {
				throw new WebApplicationException(HttpURLConnection.HTTP_UNAUTHORIZED);
			}

			daoNew = new NetworkDAO(db);
			UUID networkUUID = UUID.fromString(networkId);
			int i = daoNew.setNetworkProperties(networkUUID, properties);
			daoNew.commit();
			logInfo(logger, "Finished updating properties of network " + networkId);
			return i;
		} catch (Exception e) {
			logger.severe("Error occurred when update network properties: " + e.getMessage());
			e.printStackTrace();
			if (null != daoNew) daoNew.rollback();
			throw new NdexException(e.getMessage());
		} finally {
			if (null != db) db.close();
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

    	ODatabaseDocumentTx db = null;
    	NetworkDAO daoNew = null;

		try {
			db = NdexDatabase.getInstance().getAConnection();
			User user = getLoggedInUser();

			if ( !Helper.checkPermissionOnNetworkByAccountName(db, networkId, user.getAccountName(),
					Permissions.WRITE)) {
				throw new WebApplicationException(HttpURLConnection.HTTP_UNAUTHORIZED);
			}

			daoNew = new NetworkDAO(db);
			UUID networkUUID = UUID.fromString(networkId);
			int i = daoNew.setNetworkPresentationProperties(networkUUID, properties);
			daoNew.commit();
			return i;
		} catch (Exception e) {
			if (null != daoNew) {
				daoNew.rollback();
				daoNew = null;
			}
			
			throw e;
		} finally {
			if (null != db) db.close();
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

		logInfo(logger, "Getting networkSummary of " + networkId);
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
				NetworkSummary summary = NetworkDAO.getNetworkSummary(doc);
				db.close();
				db = null;
				logInfo(logger, "NetworkSummary of " + networkId + " returned.");
				return summary;

			}
		} finally {
			if (db != null) db.close();
		}
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

		ODatabaseDocumentTx db = null;
		try {
			db = NdexDatabase.getInstance().getAConnection();
			NetworkDAO dao = new NetworkDAO(db);
	 		Network n = dao.getNetwork(UUID.fromString(networkId), skipBlocks, blockSize);
	        return n;
		} finally {
			if ( db !=null) db.close();
		}
	}

	@PermitAll
	@GET
	@Path("/{networkId}/asNetwork")
	@Produces("application/json")
    @ApiDoc("Retrieve an entire network specified by 'networkId' as a Network object.  (Compare this method to " +
            "getCompleteNetworkAsPropertyGraph).")
	public Network getCompleteNetwork(
			@PathParam("networkId") final String networkId)

			throws IllegalArgumentException, NdexException {

		if ( isSearchable(networkId) ) {
		
			ODatabaseDocumentTx db = NdexDatabase.getInstance().getAConnection();
			NetworkDAO daoNew = new NetworkDAO(db);

			Network n = daoNew.getNetworkById(UUID.fromString(networkId));
			db.close();
			return n;
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

		if ( isSearchable(networkId) ) {
		
			ODatabaseDocumentTx db =null;
			try {
				db = NdexDatabase.getInstance().getAConnection();
				NetworkDAO daoNew = new NetworkDAO(db);
				PropertyGraphNetwork n = daoNew.getProperytGraphNetworkById(UUID.fromString(networkId));
				return n;
			} finally {
				if (db !=null ) db.close();
			}
		}
		else 
			throw new WebApplicationException(HttpURLConnection.HTTP_UNAUTHORIZED);
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

		ODatabaseDocumentTx db = NdexDatabase.getInstance().getAConnection();
		NetworkDAO dao = new NetworkDAO(db);
 		PropertyGraphNetwork n = dao.getProperytGraphNetworkById(UUID.fromString(networkId),skipBlocks, blockSize);
		db.close();
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

		logInfo( logger, "Get all " + permissions + " accounts on network " + networkId);
		Permissions permission = Permissions.valueOf(permissions.toUpperCase());

		ODatabaseDocumentTx db = null;
		try {

			db = NdexDatabase.getInstance().getAConnection();
			NetworkDAO networkDao = new NetworkDAO(db);
            
			List<Membership> results = networkDao.getNetworkUserMemberships(
					UUID.fromString(networkId), permission, skipBlocks, blockSize);
			logInfo(logger, results.size() + " members returned for network " + networkId);
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
		ODatabaseDocumentTx db = null;
		try {
			db = NdexDatabase.getInstance().getAConnection();
			User user = getLoggedInUser();
			NetworkDAO networkDao = new NetworkDAO(db);

			if (!Helper.isAdminOfNetwork(db, networkId, user.getExternalId().toString())) {
				throw new WebApplicationException(HttpURLConnection.HTTP_UNAUTHORIZED);
			}

			int count = networkDao.revokePrivilege(networkId, userUUID);
            db.commit();
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
		ODatabaseDocumentTx db = null;
		try {
			db = NdexDatabase.getInstance().getAConnection();

			User user = getLoggedInUser();
			NetworkDAO networkDao = new NetworkDAO(db);

			if (!Helper.isAdminOfNetwork(db, networkId, user.getExternalId().toString())) {
				throw new WebApplicationException(HttpURLConnection.HTTP_UNAUTHORIZED);
			}

	        int count = networkDao.grantPrivilege(networkId, membership.getMemberUUID().toString(), membership.getPermissions());
			db.commit();
	        return count;
		} finally {
			if (db != null) db.close();
		}
	}

	@POST
	@Path("/{networkId}/summary")
	@Produces("application/json")
    @ApiDoc("POSTs a NetworkSummary object to update the profile information of the network specified by networkUUID." +
            " The NetworkSummary POSTed may be only partially populated. The only fields that will be acted on are: " +
            "'name', 'description','version', and 'visibility' if they are present.")
	public void updateNetworkProfile(
			@PathParam("networkId") final String networkId,
			final NetworkSummary summary
			)
			throws IllegalArgumentException, NdexException {

		ODatabaseDocumentTx db = null;
		try {
			db = NdexDatabase.getInstance().getAConnection();

			User user = getLoggedInUser();
			NetworkDAO networkDao = new NetworkDAO(db);

			if ( !Helper.checkPermissionOnNetworkByAccountName(db, networkId, user.getAccountName(),
					Permissions.WRITE)) {
				throw new WebApplicationException(HttpURLConnection.HTTP_UNAUTHORIZED);
			}

	        networkDao.updateNetworkProfile(UUID.fromString(networkId), summary);
			db.commit();
		} finally {
			if (db != null) db.close();
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

		logInfo (logger, "Neighborhood search on " + networkId + " with phrase \"" + queryParameters.getSearchString() + "\"");
		
		ODatabaseDocumentTx db = null;

		try {
		   db =	NdexDatabase.getInstance().getAConnection();

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

			   Network n = dao.queryForSubnetwork(networkId, queryParameters);
			   logInfo(logger, "Subnetwork from query returned." );
			   return n;
			   //getProperytGraphNetworkById(UUID.fromString(networkId),skipBlocks, blockSize);
		   }

		   throw new WebApplicationException(HttpURLConnection.HTTP_UNAUTHORIZED);
		} finally {
			if ( db != null) db.close();
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
				return n;

			}
		} finally {
			if ( db !=null) db.close();
		}

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

		logInfo ( logger, "Search networks: \"" + query.getSearchString() + "\"");
    	if(query.getAccountName() != null)
    		query.setAccountName(query.getAccountName().toLowerCase());
        
    	try (ODatabaseDocumentTx db = NdexDatabase.getInstance().getAConnection()) {
            NetworkSearchDAO dao = new NetworkSearchDAO(db);
            Collection<NetworkSummary> result = new ArrayList <> ();

			result = dao.findNetworks(query, skipBlocks, blockSize, this.getLoggedInUser());
			logInfo ( logger, result.size() + " networks returned from search.");
			return result;

        } catch (Exception e) {
        	e.printStackTrace();
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

			NdexDatabase db = NdexDatabase.getInstance();
			PropertyGraphLoader pgl = null;
			try {
				pgl = new PropertyGraphLoader(db);

				return pgl.insertNetwork(newNetwork, getLoggedInUser().getAccountName());
			} finally {

				db.close();
			}

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

			NdexDatabase db = NdexDatabase.getInstance();
			NdexNetworkCloneService service = null;
			try {
				service = new NdexNetworkCloneService(db, newNetwork,
						getLoggedInUser().getAccountName());

				return service.cloneNetwork();

			} finally {
				if ( service !=null)
					service.close();
				if ( db != null)
					db.close();
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

		String userAcc = getLoggedInUser().getAccountName();
		logInfo(logger, "Deleting network  " + id);
		ODatabaseDocumentTx db = null;
		try{
			db = NdexDatabase.getInstance().getAConnection();

            if (!Helper.checkPermissionOnNetworkByAccountName(db, id, userAcc, Permissions.ADMIN))
	        {
	           throw new WebApplicationException(HttpURLConnection.HTTP_UNAUTHORIZED);
	        }

			NetworkDAO networkDao = new NetworkDAO(db);
			logger.info("Start deleting network " + id);
			networkDao.deleteNetwork(id);
			db.commit();
			logger.info("Network " + id + " deleted.");
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
			throw new IllegalArgumentException(e1);
		}

		String ext = FilenameUtils.getExtension(uploadedNetwork.getFilename()).toLowerCase();

		if ( !ext.equals("sif") && !ext.equals("xbel") && !ext.equals("xgmml") 
				&& !ext.equals("xls") && ! ext.equals("xlsx")) {
			throw new IllegalArgumentException(
					"The uploaded file type is not supported; must be Excel, XGMML, SIF, or XBEL.");
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
				e1.printStackTrace();
				throw new NdexException ("Failed to create file " + fileFullPath + " on server when uploading " + 
						uploadedNetwork.getFilename() + ": " + e1.getMessage());
			}

		try ( FileOutputStream saveNetworkFile = new FileOutputStream(uploadedNetworkFile)) {
			saveNetworkFile.write(uploadedNetwork.getFileData());
			saveNetworkFile.flush();
		} catch (IOException e1) {
			e1.printStackTrace();
			throw new NdexException ("Failed to write conent to file " + fileFullPath + " on server when uploading " + 
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
			throw iae;
		} catch (Exception e) {
			Logger.getLogger(this.getClass().getName()).severe("Failed to process uploaded network: "
					+ uploadedNetwork.getFilename() + ". " + e.getMessage());

			throw new NdexException(e.getMessage());
		} 
	}


}
