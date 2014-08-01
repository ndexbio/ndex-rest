package org.ndexbio.rest.services;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;

import org.ndexbio.common.access.NdexAOrientDBConnectionPool;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.access.NetworkAOrientDBDAO;
import org.ndexbio.common.exceptions.NdexException;
import org.ndexbio.common.models.dao.orientdb.NetworkDAO;
import org.ndexbio.common.models.dao.orientdb.NetworkSearchDAO;
import org.ndexbio.common.models.object.NetworkQueryParameters;
import org.ndexbio.common.persistence.orientdb.PropertyGraphLoader;
import org.ndexbio.model.object.Permissions;
//import org.ndexbio.model.object.SearchParameters;
import org.ndexbio.model.object.SimpleNetworkQuery;
import org.ndexbio.model.object.SimplePathQuery;
import org.ndexbio.model.object.network.BaseTerm;
import org.ndexbio.model.object.network.Network;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.object.network.PropertyGraphNetwork;
import org.ndexbio.rest.annotations.ApiDoc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.record.impl.ODocument;

@Path("/network")
public class NetworkAService extends NdexService {
	
	
	private NetworkAOrientDBDAO dao = NetworkAOrientDBDAO.getInstance();
	
	public NetworkAService(@Context HttpServletRequest httpRequest) {
		super(httpRequest);
	}
/*	
	@GET
	@Path("/{networkId}/term/{skipBlocks}/{blockSize}")
	@Produces("application/json")
	@ApiDoc("Returns a block of terms from the network specified by networkId as a list. "
			+ "'blockSize' specifies the maximum number of terms to retrieve in the block, "
			+ "'skipBlocks' specifies the number of blocks to skip.")
	public List<BaseTerm> getTerms(
			@PathParam("networkId") final String networkId,
			@PathParam("skipBlocks") final int skipBlocks, 
			@PathParam("blockSize") final int blockSize)
			
			throws IllegalArgumentException, NdexException {
		
		return dao.getTerms(this.getLoggedInUser(), networkId, skipBlocks, blockSize);
		
	}
*/	

		
	@GET
	@Path("/{networkId}")
	@Produces("application/json")
	@ApiDoc("Returns a NetworkSummary network specified by networkUUID. Errors if the network is not found"
			+ " or if the authenticated user does not have read permission for the network.")
	public NetworkSummary getNetworkSummary(
			@PathParam("networkId") final String networkId)
			
			throws IllegalArgumentException, NdexException {
		
		ODatabaseDocumentTx db = NdexAOrientDBConnectionPool.getInstance().acquire();
		NetworkDAO networkDao = new NetworkDAO(db);
		
		boolean hasPrivilege=networkDao.checkPrivilege(getLoggedInUser().getAccountName(), 
				networkId, Permissions.READ);
		if ( hasPrivilege) {
			ODocument doc =  networkDao.getNetworkDocByUUIDString(networkId);
			NetworkSummary summary = NetworkDAO.getNetworkSummary(doc);
			db.close();
			return summary;		
			//getProperytGraphNetworkById(UUID.fromString(networkId),skipBlocks, blockSize);
		}

		db.close();
        throw new WebApplicationException(HttpURLConnection.HTTP_UNAUTHORIZED);
		
	}
	
	
	
	@GET
	@Path("/{networkId}/edge/asNetwork/{skipBlocks}/{blockSize}")
	@Produces("application/json")
	@ApiDoc("Returns a network based on a set of edges selected from the network "
			+ "specified by networkId. The returned network is fully poplulated and "
			+ "'self-sufficient', including all nodes, terms, supports, citations, "
			+ "and namespaces. The query selects a number of edges specified by the "
			+ "'blockSize' parameter, starting at an offset specified by the 'skipBlocks' parameter.")
	public Network getEdges(
			@PathParam("networkId") final String networkId,
			@PathParam("skipBlocks") final int skipBlocks, 
			@PathParam("blockSize") final int blockSize)
	
			throws IllegalArgumentException, NdexException {
		
		ODatabaseDocumentTx db = NdexAOrientDBConnectionPool.getInstance().acquire();
		NetworkDAO dao = new NetworkDAO(db);
 		Network n = dao.getNetwork(UUID.fromString(networkId), skipBlocks, blockSize);
 		//getProperytGraphNetworkById(UUID.fromString(networkId),skipBlocks, blockSize);
		db.close();
        return n;		
	}


	@GET
	@Path("/{networkId}/edge/asPropertyGraph/{skipBlocks}/{blockSize}")
	@Produces("application/json")
	@ApiDoc("Returns a network based on a set of edges selected from the network "
			+ "specified by networkId. The returned network is fully poplulated and "
			+ "'self-sufficient', including all nodes, terms, supports, citations, "
			+ "and namespaces. The query selects a number of edges specified by the "
			+ "'blockSize' parameter, starting at an offset specified by the 'skipBlocks' parameter.")
	public PropertyGraphNetwork getPropertyGraphEdges(
			@PathParam("networkId") final String networkId,
			@PathParam("skipBlocks") final int skipBlocks, 
			@PathParam("blockSize") final int blockSize)
	
			throws IllegalArgumentException, JsonProcessingException {
		
		ODatabaseDocumentTx db = NdexAOrientDBConnectionPool.getInstance().acquire();
		NetworkDAO dao = new NetworkDAO(db);
 		PropertyGraphNetwork n = dao.getProperytGraphNetworkById(UUID.fromString(networkId),skipBlocks, blockSize);
		db.close();
        return n;		
	}
	
	
	
	@POST
	@Path("/{networkId}/asNetwork/query")
	@Produces("application/json")
	@ApiDoc("Returns a network based on a block of edges retrieved by the POSTed queryParameters "
			+ "from the network specified by networkId. The returned network is fully poplulated and "
			+ "'self-sufficient', including all nodes, terms, supports, citations, and namespaces.")
	public Network queryNetwork(
			@PathParam("networkId") final String networkId,
			final SimplePathQuery queryParameters
//			@PathParam("skipBlocks") final int skipBlocks, 
//			@PathParam("blockSize") final int blockSize
			)
	
			throws IllegalArgumentException, NdexException {
		
		ODatabaseDocumentTx db = NdexAOrientDBConnectionPool.getInstance().acquire();
		NetworkDAO networkDao = new NetworkDAO(db);
		
		boolean hasPrivilege=networkDao.checkPrivilege(getLoggedInUser().getAccountName(), 
				networkId, Permissions.READ);
		db.close();
		if ( hasPrivilege) {
			NetworkAOrientDBDAO dao = NetworkAOrientDBDAO.getInstance();
		
			Network n = dao.queryForSubnetwork(networkId, queryParameters);
			return n;		
			//getProperytGraphNetworkById(UUID.fromString(networkId),skipBlocks, blockSize);
		}
        	
        throw new WebApplicationException(HttpURLConnection.HTTP_UNAUTHORIZED);
	}

	@POST
	@Path("/{networkId}/asPropertyGraph/query")
	@Produces("application/json")
	@ApiDoc("Returns a network based on a block of edges retrieved by the POSTed queryParameters "
			+ "from the network specified by networkId. The returned network is fully poplulated and "
			+ "'self-sufficient', including all nodes, terms, supports, citations, and namespaces.")
	public PropertyGraphNetwork queryNetworkAsPropertyGraph(
			@PathParam("networkId") final String networkId,
			final SimplePathQuery queryParameters
//			@PathParam("skipBlocks") final int skipBlocks, 
//			@PathParam("blockSize") final int blockSize
			)
	
			throws IllegalArgumentException, NdexException {
		
		ODatabaseDocumentTx db = NdexAOrientDBConnectionPool.getInstance().acquire();
		NetworkDAO networkDao = new NetworkDAO(db);
		
		boolean hasPrivilege=networkDao.checkPrivilege(getLoggedInUser().getAccountName(), 
				networkId, Permissions.READ);
		db.close();
		if ( hasPrivilege) {
			NetworkAOrientDBDAO dao = NetworkAOrientDBDAO.getInstance();
		
			PropertyGraphNetwork n = dao.queryForSubPropertyGraphNetwork(networkId, queryParameters);
			return n;		
			//getProperytGraphNetworkById(UUID.fromString(networkId),skipBlocks, blockSize);
		}
        	
        throw new WebApplicationException(HttpURLConnection.HTTP_UNAUTHORIZED);
	}
	
	
	
	
	@POST
	@Path("/{networkId}/asPropertyGraph/query/{skipBlocks}/{blockSize}")
	@Produces("application/json")
	@ApiDoc("Returns a network based on a block of edges retrieved by the POSTed queryParameters "
			+ "from the network specified by networkId. The returned network is fully poplulated and "
			+ "'self-sufficient', including all nodes, terms, supports, citations, and namespaces.")
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
	
	

	@POST
	@PermitAll
	@Path("/search/{skipBlocks}/{blockSize}")
	@Produces("application/json")
	@ApiDoc("Returns a list of NetworkSummaries based on POSTed NetworkQuery.  The allowed NetworkQuery subtypes "+
	        "for v1.0 will be NetworkSimpleQuery and NetworkMembershipQuery. 'blockSize' specifies the number of " +
			"NetworkSummaries to retrieve in each block, 'skipBlocks' specifies the number of blocks to skip.")
	public List<NetworkSummary> searchNetwork(
			final SimpleNetworkQuery query,
			@PathParam("skipBlocks") final int skipBlocks, 
			@PathParam("blockSize") final int blockSize)
			throws IllegalArgumentException, NdexException {
		
        List<NetworkSummary> result = new ArrayList <NetworkSummary> ();
        ODatabaseDocumentTx db = NdexAOrientDBConnectionPool.getInstance().acquire();
        NetworkSearchDAO dao = new NetworkSearchDAO(db);
        
        try {
			
			result = dao.findNetworks(query, skipBlocks, blockSize, this.getLoggedInUser());
			
			return result;
		
        } catch (Exception e) {
        	
        	throw new NdexException(e.getMessage());
        	
        } finally {
        	
        	db.close();
        }
		
		//throw new NdexException ("Feature not implemented yet.") ;
	}

	
	
	@POST
	@Path("/asPropertyGraph")
	@Produces("application/json")
	@ApiDoc("Creates a new network based on posted PropertyGraphNetwork object. Errors if the posted network is not provided "
			+ "or if that Network does not specify a name. Errors if the posted network is larger than server-set maximum for"
			+ " network creation (though this is better to check locally in client before request)")
	public NetworkSummary createNetwork(final PropertyGraphNetwork newNetwork)
			throws 	Exception {
			Preconditions
				.checkArgument(null != newNetwork, "A network is required");
			Preconditions.checkArgument(
				!Strings.isNullOrEmpty(newNetwork.getName()),
				"A network name is required");

			NdexDatabase db = new NdexDatabase();
			try {
				PropertyGraphLoader pgl = new PropertyGraphLoader(db);
		
				return pgl.insertNetwork(newNetwork, getLoggedInUser().getAccountName());
			} finally {
				db.close();
			}
		
	}

}
