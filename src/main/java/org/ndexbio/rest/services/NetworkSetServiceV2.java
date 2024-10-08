package org.ndexbio.rest.services;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

import org.ndexbio.common.models.dao.postgresql.NetworkSetDAO;
import org.ndexbio.common.util.NdexUUIDFactory;
import org.ndexbio.model.exceptions.DuplicateObjectException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.NetworkSet;
import org.ndexbio.rest.Configuration;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;


@Path("/v2/networkset")
public class NetworkSetServiceV2 extends NdexService {

	
	public NetworkSetServiceV2(@Context HttpServletRequest httpRequest) {
		super(httpRequest);
	}

	
	@POST
	@Produces("text/plain")
	public Response createNetworkSet(final NetworkSet newNetworkSet)
			throws  DuplicateObjectException,
			NdexException,  SQLException, JsonProcessingException {
	
		if ( newNetworkSet.getName() == null || newNetworkSet.getName().length() == 0) 
			throw new NdexException ("Network set name is required.");
		
		try (NetworkSetDAO dao = new NetworkSetDAO()){
			UUID setId = NdexUUIDFactory.INSTANCE.createNewNDExUUID();
			dao.createNetworkSet(setId, newNetworkSet.getName(), newNetworkSet.getDescription(), getLoggedInUserId(), newNetworkSet.getProperties());
			dao.commit();	
			
			URI l = new URI (Configuration.getInstance().getHostURI()  + 
			            Configuration.getInstance().getRestAPIPrefix()+"/networkset/"+ setId.toString());

			return Response.created(l).entity(l).build();
		} catch (URISyntaxException e) {
			throw new NdexException("Server Error, can't create URL for the new resource: " + e.getMessage(), e);
		} 
	}
	
	
	@PUT
	@Path("/{networksetid}")
	public void updateNetworkSet(final NetworkSet newNetworkSet,
			@PathParam("networksetid") final String id)
			throws  DuplicateObjectException,
			NdexException,  SQLException, JsonProcessingException {
	
		if ( newNetworkSet.getName() == null || newNetworkSet.getName().length() == 0) 
			throw new NdexException ("Network set name is required.");
		
		UUID setId = UUID.fromString(id);
		try (NetworkSetDAO dao = new NetworkSetDAO()){
			if ( !dao.isNetworkSetOwner(setId, getLoggedInUserId()))
				throw new UnauthorizedOperationException("Signed in user is not the owner of this network set.");
			
			dao.updateNetworkSet(setId, newNetworkSet.getName(), newNetworkSet.getDescription(), getLoggedInUserId(),newNetworkSet.getProperties());
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
	public NetworkSet getNetworkSet(@PathParam("networksetid") final String networkSetIdStr,
			@QueryParam("accesskey") String accessKey)
			throws ObjectNotFoundException, NdexException, SQLException, JsonParseException, JsonMappingException, IOException {
		
		UUID setId = UUID.fromString(networkSetIdStr);
		
		try (NetworkSetDAO dao = new NetworkSetDAO()) {
			NetworkSet nset = dao.getNetworkSet(setId, getLoggedInUserId(), accessKey);
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
			throw new NdexException("Server Error, can't create URL for the new resource: " + e.getMessage(), e);
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
	@Produces("application/json")
	public Map<String,String> getNetworkSetAccessKey(@PathParam("networksetid") final String networkSetIdStr)
			throws IllegalArgumentException, NdexException, SQLException {
  	
		UUID networkSetId = UUID.fromString(networkSetIdStr);
    	try (NetworkSetDAO dao = new NetworkSetDAO()) {
    		if ( ! dao.isNetworkSetOwner(networkSetId,  getLoggedInUserId()))
                throw new UnauthorizedOperationException("User is not the owner of this network set.");

    		String key = dao.getNetworkSetAccessKey(networkSetId);
    		if (key == null || key.length()==0)
    			return null;
    		Map<String,String> result = new HashMap<>(1);
    		result.put("accessKey", key);
    		return result;
    	}
	}  
		
	@PUT
	@Path("/{networksetid}/accesskey")
	@Produces("application/json")	
	public  Map<String,String> disableNetworkAccessKey(@PathParam("networksetid") final String networkSetIdStr,
			@QueryParam("action") String action)
			throws IllegalArgumentException, NdexException, SQLException {
  	
		UUID networkSetId = UUID.fromString(networkSetIdStr);
		if ( ! action.equalsIgnoreCase("disable") && ! action.equalsIgnoreCase("enable"))
			throw new NdexException("Value of 'action' paramter can only be 'disable' or 'enable'");
		
    	try (NetworkSetDAO dao = new NetworkSetDAO()) {
    		if ( ! dao.isNetworkSetOwner(networkSetId, getLoggedInUserId()))
                throw new UnauthorizedOperationException("User is not the owner of this network set.");

    		String key = null;
    		if ( action.equalsIgnoreCase("disable"))
    			dao.disableNetworkSetAccessKey(networkSetId);
    		else 
    			key = dao.enableNetworkSetAccessKey(networkSetId);
    		dao.commit();
    		
    		if (key == null || key.length()==0)
    			return null;
    		Map<String,String> result = new HashMap<>(1);
    		result.put("accessKey", key);
    		return result;
    	}
	}  
	
	
	@PUT
	@Path("/{networksetid}/systemproperty")
	@Produces("application/json")
  
	public void setNetworkFlag(
			@PathParam("networksetid") final String networkSetIdStr,
			final Map<String,Object> parameters)

			throws IllegalArgumentException, NdexException, SQLException {
		
			try (NetworkSetDAO dao = new NetworkSetDAO()) {
				UUID networkSetId = UUID.fromString(networkSetIdStr);
				
				if ( ! dao.isNetworkSetOwner(networkSetId, getLoggedInUserId()))
	                throw new UnauthorizedOperationException("User is not the owner of this network set.");
			
				if ( parameters.containsKey("showcase")) {
						boolean bv = ((Boolean)parameters.get("showcase")).booleanValue();
						dao.setShowcaseFlag(networkSetId, bv);
				}
				
				dao.commit();
				return;
			}
		    
	}

	
	
	
}
