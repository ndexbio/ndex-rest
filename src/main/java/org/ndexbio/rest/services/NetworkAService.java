package org.ndexbio.rest.services;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;

import org.ndexbio.common.NdexClasses;
import org.ndexbio.common.access.NdexAOrientDBConnectionPool;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.access.NetworkAOrientDBDAO;
import org.ndexbio.common.exceptions.DuplicateObjectException;
import org.ndexbio.common.exceptions.NdexException;
//import org.ndexbio.model.object.SearchParameters;
import org.ndexbio.model.object.SimpleNetworkQuery;
import org.ndexbio.model.object.network.BaseTerm;
import org.ndexbio.model.object.network.Network;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.object.network.PropertyGraphNetwork;
import org.ndexbio.common.models.dao.orientdb.NetworkDAO;
import org.ndexbio.common.models.dao.orientdb.NetworkSearchDAO;
import org.ndexbio.common.models.object.NetworkQueryParameters;
import org.ndexbio.common.persistence.orientdb.PropertyGraphLoader;
import org.ndexbio.rest.annotations.ApiDoc;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.iterator.ORecordIteratorClass;
import com.orientechnologies.orient.core.record.impl.ODocument;

@Path("/network")
public class NetworkAService extends NdexService {
	
	
	private NetworkAOrientDBDAO dao = NetworkAOrientDBDAO.getInstance();
	
	public NetworkAService(@Context HttpServletRequest httpRequest) {
		super(httpRequest);
	}
	
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
	
	@GET
	@Path("/{networkId}/edge/{skipBlocks}/{blockSize}")
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
		
		return null ; //dao.queryForSubnetwork(this.getLoggedInUser(), networkId, queryParameters, skipBlocks, blockSize);
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
	
			throws IllegalArgumentException {
		
		ODatabaseDocumentTx db = NdexAOrientDBConnectionPool.getInstance().acquire();
		NetworkDAO dao = new NetworkDAO(db);
 		PropertyGraphNetwork n = dao.getProperytGraphNetworkById(UUID.fromString(networkId),skipBlocks, blockSize);
		db.close();
        return n;		
	}
	
	
	
	@POST
	@Path("/{networkId}/query/{skipBlocks}/{blockSize}")
	@Produces("application/json")
	@ApiDoc("Returns a network based on a block of edges retrieved by the POSTed queryParameters "
			+ "from the network specified by networkId. The returned network is fully poplulated and "
			+ "'self-sufficient', including all nodes, terms, supports, citations, and namespaces.")
	public Network queryNetwork(
			@PathParam("networkId") final String networkId,
			final NetworkQueryParameters queryParameters,
			@PathParam("skipBlocks") final int skipBlocks, 
			@PathParam("blockSize") final int blockSize)
	
			throws IllegalArgumentException, NdexException {
		
		return dao.queryForSubnetwork(this.getLoggedInUser(), networkId, queryParameters, skipBlocks, blockSize);
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
	
			throws IllegalArgumentException, NdexException {
		
		ODatabaseDocumentTx db = NdexAOrientDBConnectionPool.getInstance().acquire();
		NetworkDAO dao = new NetworkDAO(db);
 		PropertyGraphNetwork n = dao.getProperytGraphNetworkById(UUID.fromString(networkId));
		db.close();
        return n;		
	}
	
	

	@POST
	@Path("/search/{skipBlocks}/{blockSize}")
	@Produces("application/json")
	@ApiDoc("Returns a list of NetworkDescriptors based on POSTed NetworkQuery.  The allowed NetworkQuery subtypes "+
	        "for v1.0 will be NetworkSimpleQuery and NetworkMembershipQuery. 'blockSize' specifies the number of " +
			"NetworkDescriptors to retrieve in each block, 'skipBlocks' specifies the number of blocks to skip.")
	public List<NetworkSummary> searchNetwork(
			final SimpleNetworkQuery query,
			@PathParam("skipBlocks") final int skipBlocks, 
			@PathParam("blockSize") final int blockSize)
			throws IllegalArgumentException, NdexException {
		
        List<NetworkSummary> result = new ArrayList <NetworkSummary> ();
        ODatabaseDocumentTx db = NdexAOrientDBConnectionPool.getInstance().acquire();
        NetworkSearchDAO dao = new NetworkSearchDAO(db);
        ORecordIteratorClass<ODocument> networks; 
        
        try {
        
        	//should move this to DAO
			if (query.getSearchString().equals("*")) {
				
				networks = db.browseClass(NdexClasses.Network);
				
				for (ODocument doc : networks) {
					result.add(NetworkDAO.getNetworkSummary(doc));
				}
				
		        return result;		
			}
			
			result = dao.findNetworks(query, skipBlocks, blockSize);
			
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
