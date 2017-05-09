package org.ndexbio.rest.services;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.models.dao.postgresql.NetworkSetDAO;
import org.ndexbio.common.util.NdexUUIDFactory;
import org.ndexbio.model.exceptions.DuplicateObjectException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.NetworkSet;
import org.ndexbio.rest.Configuration;


@Path("/v2/networkset")
public class NetworkSetServiceV2 extends NdexService {

	
	public NetworkSetServiceV2(@Context HttpServletRequest httpRequest) {
		super(httpRequest);
	}

	
	@POST
	@Produces("text/plain")
	public Response createNetworkSet(final NetworkSet newNetworkSet)
			throws  DuplicateObjectException,
			NdexException,  SQLException {
	
		if ( newNetworkSet.getName() == null || newNetworkSet.getName().length() == 0) 
			throw new NdexException ("Network set name is required.");
		
		try (NetworkSetDAO dao = new NetworkSetDAO()){
			UUID setId = NdexUUIDFactory.INSTANCE.createNewNDExUUID();
			dao.createNetworkSet(setId, newNetworkSet.getName(), newNetworkSet.getDescription(), getLoggedInUserId());
			dao.commit();	
			
			URI l = new URI (Configuration.getInstance().getHostURI()  + 
			            Configuration.getInstance().getRestAPIPrefix()+"/networkset/"+ setId.toString());

			return Response.created(l).entity(l).build();
		} catch (URISyntaxException e) {
			throw new NdexException("Server Error, can create URL for the new resource: " + e.getMessage(), e);
		} 
	}
	
	
	@PUT
	@Path("/{networksetid}")
	public void updateNetworkSet(final NetworkSet newNetworkSet,
			@PathParam("networksetid") final String id)
			throws  DuplicateObjectException,
			NdexException,  SQLException {
	
		if ( newNetworkSet.getName() == null || newNetworkSet.getName().length() == 0) 
			throw new NdexException ("Network set name is required.");
		
		UUID setId = UUID.fromString(id);
		try (NetworkSetDAO dao = new NetworkSetDAO()){
			if ( !dao.isNetworkSetOwner(setId, getLoggedInUserId()))
				throw new UnauthorizedOperationException("Signed in user is not the owner of this network set.");
			
			dao.updateNetworkSet(setId, newNetworkSet.getName(), newNetworkSet.getDescription(), getLoggedInUserId());
			dao.commit();	
			return ;
		} 
	}
	
	
	@DELETE
	@Path("/{networksetid}")
	@Produces("application/json")
	public void deleteNetworkSet(@PathParam("networksetid") final String networkSetIdStr)
			throws NdexException, SQLException {
		
		UUID setId = UUID.fromString(networkSetIdStr);
		try (NetworkSetDAO dao = new NetworkSetDAO()){
			
			if (!dao.isNetworkSetOwner(setId, getLoggedInUserId()))
				throw new UnauthorizedOperationException("Signed in user is not the owner of this network set.");
				
			dao.deleteNetworkSet(setId);
			dao.commit();
		} 
	}
	
	
	@GET
	@PermitAll
	@Path("/{networksetid}")
	@Produces("application/json")
	public NetworkSet getNetworkSet(@PathParam("networksetid") final String networkSetIdStr)
			throws ObjectNotFoundException, NdexException, SQLException {
		
		UUID setId = UUID.fromString(networkSetIdStr);
		
		try (NetworkSetDAO dao = new NetworkSetDAO()) {
			NetworkSet nset = dao.getNetworkSet(setId, getLoggedInUserId());
			return nset;
		} 
	}

	@POST
	@Path("/{networksetid}/members")
	@Produces("text/plain")
	public Response addNetworksToSet(final List<UUID> networkIds,
				@PathParam("networksetid") final String networkSetIdStr )
			throws  DuplicateObjectException,
			NdexException,  SQLException {
	
		UUID setId = UUID.fromString(networkSetIdStr);
		
		try (NetworkSetDAO dao = new NetworkSetDAO()){
			if ( !dao.isNetworkSetOwner(setId, getLoggedInUserId()))
				throw new UnauthorizedOperationException("Signed in user is not the owner of this network set.");
				
			dao.addNetworksToNetworkSet(setId, networkIds);			
			dao.commit();	
			
			URI l = new URI (Configuration.getInstance().getHostURI()  + 
			            Configuration.getInstance().getRestAPIPrefix()+"/networkset/"+ setId.toString()+"/members");

			return Response.created(l).entity(l).build();
		} catch (URISyntaxException e) {
			throw new NdexException("Server Error, can create URL for the new resource: " + e.getMessage(), e);
		} 
	}
	
	
	@DELETE
	@Path("/{networksetid}/members")
	@Produces("application/json")
	public void deleteNetworkSet(final List<UUID> networkIds,
			@PathParam("networksetid") final String networkSetIdStr )
			throws NdexException, SQLException {
		
		UUID setId = UUID.fromString(networkSetIdStr);
		
		try (NetworkSetDAO dao = new NetworkSetDAO()){
			if ( !dao.isNetworkSetOwner(setId, getLoggedInUserId()))
				throw new UnauthorizedOperationException("Signed in user is not the owner of this network set.");
				
			dao.deleteNetworksToNetworkSet(setId, networkIds);
			dao.commit();	
			return;
			
		} 
	}
	
	
	@GET
	@Path("/{networksetid}/accesskey")
	@Produces("text/plain")
	public String getNetworkSetAccessKey(@PathParam("networksetid") final String networkSetIdStr)
			throws IllegalArgumentException, NdexException, SQLException {
  	
		UUID networkSetId = UUID.fromString(networkSetIdStr);
    	try (NetworkSetDAO dao = new NetworkSetDAO()) {
    		if ( ! dao.isNetworkSetOwner(networkSetId,  getLoggedInUserId()))
                throw new UnauthorizedOperationException("User is not the owner of this network set.");

    		return dao.getNetworkSetAccessKey(networkSetId);
    	}
	}  
		
	@PUT
	@Path("/{networksetid}/accesskey")
	
	public void disableNetworkAccessKey(@PathParam("networksetid") final String networkSetIdStr,
			@QueryParam("action") String action)
			throws IllegalArgumentException, NdexException, SQLException {
  	
		UUID networkSetId = UUID.fromString(networkSetIdStr);
		if ( ! action.equalsIgnoreCase("disable"))
			throw new NdexException("Value of 'action' paramter can only be 'disable'");
		
    	try (NetworkSetDAO dao = new NetworkSetDAO()) {
    		if ( ! dao.isNetworkSetOwner(networkSetId, getLoggedInUserId()))
                throw new UnauthorizedOperationException("User is not the owner of this network set.");

    		dao.disableNetworkSetAccessKey(networkSetId);
    		dao.commit();
    	}
	}  
	
}
