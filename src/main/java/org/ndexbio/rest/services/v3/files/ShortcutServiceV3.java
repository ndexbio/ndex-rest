package org.ndexbio.rest.services.v3.files;

import java.net.URI;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.ndexbio.common.models.dao.ShortcutDAO;
import org.ndexbio.common.util.NdexUUIDFactory;
import org.ndexbio.model.exceptions.DuplicateObjectException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.NdexObjectUpdateStatus;
import org.ndexbio.model.object.Shortcut;
import org.ndexbio.model.object.ShortcutRequest;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.filters.BasicAuthenticationFilter;
import org.ndexbio.rest.services.NdexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/v3/files/shortcuts")
public class ShortcutServiceV3 extends NdexService {

	protected static Logger accLogger = LoggerFactory.getLogger(BasicAuthenticationFilter.accessLoggerName);
	
	public ShortcutServiceV3(@Context HttpServletRequest httpRequest) {
		super(httpRequest);
	}
	
	
	@POST
	@Path("/")
	@Consumes("application/json")
	@Produces("application/json")
	public Response createShortcut(final ShortcutRequest request) throws Exception {
		if (request == null) {
			throw new Exception("No shortcut request provided.");
		}
        if (request.getName() == null || request.getName().isEmpty()) {
            throw new Exception("Shortcut name cannot be empty.");
        }
        if (request.getTarget() == null) {
            throw new Exception("Shortcut 'target' cannot be null.");
        }
		
		UUID shortcutUUID = NdexUUIDFactory.INSTANCE.createNewNDExUUID();
		
		NdexObjectUpdateStatus status;
		try (ShortcutDAO dao = Configuration.getInstance().getDAOFactory().getShortcutDAO()) {
			status = dao.createShortcut(shortcutUUID, getLoggedInUser().getExternalId(), request.getParent(), request.getName(), request.getTarget());
			dao.commit();
		}
		
		String urlStr = Configuration.getInstance().getHostURI() +"/v3/files/shortcuts/"+ shortcutUUID.toString();
			
		URI l = new URI (urlStr);
		ObjectMapper om = new ObjectMapper();
		
		return Response.created(l).header("Access-Control-Expose-Headers", "Location")
				.entity(om.writeValueAsString(status)).build();

		}
	
	@PermitAll
	@GET
	@Path("/{shortcutid}")
	@Operation(summary = "Get Shortcut", description = "XXX")

	public Response getShortcut(	@PathParam("shortcutid") final String shortcutId,
			@QueryParam("accesskey") String accessKey,
			@QueryParam("id_token") String id_token,
			@QueryParam("auth_token") String auth_token)
			throws Exception {
		
    	Shortcut shortcut = null;
    	
    	try (ShortcutDAO dao = Configuration.getInstance().getDAOFactory().getShortcutDAO()) {
    		UUID shortcutUUID = UUID.fromString(shortcutId);

     		UUID userId = getLoggedInUserId();
    		if ( userId == null ) {
    			if ( auth_token != null) {
    				userId = getUserIdFromBasicAuthString(auth_token);
    			} else if ( id_token !=null) {
    				if ( getOAuthAuthenticator() == null)
    					throw new UnauthorizedOperationException("Google OAuth is not enabled on this server.");
    				userId = getOAuthAuthenticator().getUserUUIDByIdToken(id_token);
    			}
    		}
    		if ( ! dao.isReadable(shortcutUUID, userId) && (!dao.accessKeyIsValid(shortcutUUID, accessKey))) 
                throw new UnauthorizedOperationException("User doesn't have read access to this network.");
    		shortcut = dao.getShortcut(shortcutUUID, userId, accessKey);
    	}

    	return 	Response.ok().type(MediaType.APPLICATION_JSON_TYPE).entity(shortcut).build();
		
	}
	
	@DELETE
	@Path("/{shortcutid}")
	@Operation(summary = "Delete a Shortcut", description = "Delete a shortcut.")
	@Produces("application/json")
	public void deleteShortcut(@PathParam("shortcutid") final String shortcutIdStr)
			throws NdexException, SQLException {
		
		UUID shortcutId = UUID.fromString(shortcutIdStr);
		try (ShortcutDAO dao = Configuration.getInstance().getDAOFactory().getShortcutDAO()){
			
			if (!dao.isShortcutOwner(shortcutId, getLoggedInUserId()))
				throw new UnauthorizedOperationException("Signed in user is not the owner of this shortcut.");
				
			dao.deleteShortcut(shortcutId);
			dao.commit();
		} catch (Exception e) {
			throw new NdexException(e.getMessage());
		} 
	}
	
	@PUT
	@Path("/{shortcutid}")
	@Consumes("application/json")
	@Produces("application/json")
	@Operation(summary = "Rename a Shortcut", description = "Rename a shortcut.")
	public void updateShortcut(@QueryParam("name") String nameStr,
			@PathParam("shortcutid") final String shortcutIdStr)
			throws  DuplicateObjectException,
			NdexException,  SQLException, JsonProcessingException {

		UUID shortcutId = UUID.fromString(shortcutIdStr);
		try (ShortcutDAO dao = Configuration.getInstance().getDAOFactory().getShortcutDAO()){
			if ( !dao.isShortcutOwner(shortcutId, getLoggedInUserId()))
				throw new UnauthorizedOperationException("Signed in user is not the owner of this shortcut.");
			
			dao.updateShortcut(shortcutId, nameStr, getLoggedInUserId());
			dao.commit();	
			return;
		} catch (Exception e) {
			throw new NdexException(e.getMessage());
		} 
	}
	
	@GET
	@Path("/")
	@Produces(MediaType.APPLICATION_JSON)
	public Response listMyShortcuts(@QueryParam("limit") @DefaultValue("100") int limit) throws Exception {

	    UUID userId = getLoggedInUserId();
	    if (userId == null) {
	        throw new UnauthorizedOperationException("You must be logged in to list your shortcuts.");
	    }

	    List<Shortcut> shortcuts;
	    try (ShortcutDAO dao = Configuration.getInstance().getDAOFactory().getShortcutDAO()) {
	        shortcuts = dao.listShortcutsOfUser(userId, limit);
	    }

	    return Response.ok(shortcuts).build();
	}


}
