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

import io.swagger.v3.oas.annotations.Operation;


@Path("/v2/networkset")
public class NetworkSetServiceV2 extends NdexService {

	
	public NetworkSetServiceV2(@Context HttpServletRequest httpRequest) {
		super(httpRequest);
	}

	
	@POST
	@Operation(summary = "Create a Network Set", description = "Create a network set.")
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
	@Operation(summary = "Update a Network Set", description = "Updates a project based on the serialized project object in the PUT data.")
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
	@Operation(summary = "Delete a Network Set", description = "Delete a network set.")
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
	@Operation(summary = "Get a Network Set", description = "Gets a Network Set.")
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
	@Operation(summary = "Add networks to Network Set", description = "Add a list of networks to this set. The posted data is a list of network ids. All the networks should be visible to the owner of network set.")
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
	@Operation(summary = "Delete networks from Network Set", description = "Delete networks from a networks set. Posted data is a list of network ids.")
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
	@Operation(summary = "Get Access key of Network Set", description = "This function returns an access key to the user. This access key will allow any user to have read access to member networks of this network set regardless if that user has READ privilege on that network.")
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
	@Operation(summary = "Disable/Enable Access Key on Network Set", description = "This function turns on/off the access key. It returns the key when it is enabled, and returns http code 204 when it is disabled.")
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
	@Operation(summary = "Update Network Set System Properties", description = "Network Set System properties are the properties that describe the network set’s status in a particular NDEx server.")
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
