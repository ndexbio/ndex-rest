package org.ndexbio.rest.services;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;

import org.ndexbio.common.access.NetworkAOrientDBDAO;
import org.ndexbio.common.exceptions.NdexException;
import org.ndexbio.model.object.network.BaseTerm;
import org.ndexbio.model.object.network.Network;
import org.ndexbio.common.models.object.NetworkQueryParameters;
import org.ndexbio.rest.annotations.ApiDoc;

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
		
		return dao.getEdges(this.getLoggedInUser(), networkId, skipBlocks, blockSize);
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

}
