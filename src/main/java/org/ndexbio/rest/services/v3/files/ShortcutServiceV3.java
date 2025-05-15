package org.ndexbio.rest.services.v3.files;

import java.net.URI;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.ndexbio.common.models.dao.ShortcutDAO;
import org.ndexbio.common.models.dao.FolderDAO;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
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
	@Operation(
			summary = "Create a Shortcut",
			description = """
                          Creates a new shortcut object in the user's account.
                          
                          Database Tables:
                          - shortcut: Creates new record with name, target, and parent folder
                          
                          Edge Cases:
                          - Not authenticated: Returns 401 Unauthorized
                          - Empty name: Returns 400 Bad Request
                          - Null target: Returns 400 Bad Request
                          - Invalid target type: Returns 400 Bad Request
                          - Target not accessible: Returns 403 Forbidden
                          - Invalid parent folder: Returns 400 Bad Request
                          
                          Response:
                          - 201 Created: Shortcut created successfully
                          - Location header contains URL to new shortcut
                          - 400 Bad Request: Invalid request parameters
                          - 401 Unauthorized: Not authenticated
                          - 403 Forbidden: Target not accessible
                          """
		)
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
        if (request.getTargetType() == null) {
            throw new Exception("Shortcut 'target_type' cannot be null.");
        }
        UUID userId = getLoggedInUser().getExternalId();
		
		switch (request.getTargetType()) {
			case FOLDER:
				try (FolderDAO folderDao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
					if (!folderDao.isReadable(request.getTarget(), userId)) {
						throw new NdexException("Target folder does not exist or is not accessible.");
					}
				}
				break;
			case NETWORK:
				try (NetworkDAO networkDao = new NetworkDAO()) {
					if (!networkDao.isReadable(request.getTarget(), userId)) {
						throw new NdexException("Target network does not exist or is not accessible.");
					}
				}
				break;
			default:
				throw new NdexException("Unsupported target type: " + request.getTargetType());
		}
		
		UUID shortcutUUID = NdexUUIDFactory.INSTANCE.createNewNDExUUID();
		
		NdexObjectUpdateStatus status;
		try (ShortcutDAO dao = Configuration.getInstance().getDAOFactory().getShortcutDAO()) {
			status = dao.createShortcut(shortcutUUID, userId, request.getParent(), request.getName(), request.getTarget(), request.getTargetType());
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
	@Operation(
			summary = "Get a Shortcut",
			description = """
                          Retrieves the specified shortcut if the current user has read access.
                          
                          Database Tables:
                          - shortcut: Queries shortcut metadata
                          
                          Edge Cases:
                          - Not authenticated: Returns 401 Unauthorized
                          - Invalid UUID: Returns 404 Not Found
                          - No read access: Returns 403 Forbidden
                          - Shortcut deleted: Returns 404 Not Found
                          
                          Response:
                          - 200 OK: Shortcut metadata
                          - 401 Unauthorized: Not authenticated
                          - 403 Forbidden: No read access
                          - 404 Not Found: Shortcut doesn't exist
                          """
		)
	public Response getShortcut(	@PathParam("shortcutid") final String shortcutId,
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
    		if ( ! dao.isReadable(shortcutUUID, userId)) 
                throw new UnauthorizedOperationException("User doesn't have read access to this network.");
    		shortcut = dao.getShortcut(shortcutUUID, userId);
    	}

    	return 	Response.ok().type(MediaType.APPLICATION_JSON_TYPE).entity(shortcut).build();
		
	}
	
	@DELETE
	@Path("/{shortcutid}")
	@Operation(
			summary = "Delete a Shortcut",
			description = """
                          Deletes the specified shortcut if the current user is the owner.
                          
                          Database Tables:
                          - shortcut: Updates is_deleted flag or removes record
                          
                          Query Parameters:
                          - permanent: If true, permanently deletes the shortcut from the database. 
                            If false (default), performs a logical delete (sets is_deleted flag).
                          
                          Edge Cases:
                          - Not authenticated: Returns 401 Unauthorized
                          - Invalid UUID: Returns 404 Not Found
                          - Not owner: Returns 403 Forbidden
                          - Already deleted: Returns 404 Not Found
                          
                          Response:
                          - 204 No Content: Success
                          - 401 Unauthorized: Not authenticated
                          - 403 Forbidden: Not owner
                          - 404 Not Found: Shortcut doesn't exist
                          """
		)
	@Produces("application/json")
	public void deleteShortcut(
	        @PathParam("shortcutid") final String shortcutIdStr,
	        @QueryParam("permanent") @DefaultValue("false") boolean permanent
	) throws NdexException, SQLException, Exception {
		
		UUID shortcutId = UUID.fromString(shortcutIdStr);
		try (ShortcutDAO dao = Configuration.getInstance().getDAOFactory().getShortcutDAO()){
			
			if (!dao.isShortcutOwner(shortcutId, getLoggedInUserId()))
				throw new UnauthorizedOperationException("Signed in user is not the owner of this shortcut.");
				
			dao.deleteShortcut(shortcutId, permanent);
			dao.commit();
		}
	}
	
	@PUT
	@Path("/{shortcutid}")
	@Consumes("application/json")
	@Produces("application/json")
	@Operation(
			summary = "Rename or move a Shortcut",
			description = """
                          Updates the shortcut's name or parent folder.
                          
                          Database Tables:
                          - shortcut: Updates name and/or parent folder
                          
                          Edge Cases:
                          - Not authenticated: Returns 401 Unauthorized
                          - Invalid UUID: Returns 404 Not Found
                          - Not owner: Returns 403 Forbidden
                          - Invalid parent folder: Returns 400 Bad Request
                          
                          Response:
                          - 204 No Content: Success
                          - 400 Bad Request: Invalid request
                          - 401 Unauthorized: Not authenticated
                          - 403 Forbidden: Not owner
                          - 404 Not Found: Shortcut doesn't exist
                          """
		)
	public void updateShortcut(final ShortcutRequest request,
			@PathParam("shortcutid") final String shortcutIdStr)
			throws  DuplicateObjectException,
			NdexException,  SQLException, JsonProcessingException, Exception {

		UUID shortcutId = UUID.fromString(shortcutIdStr);
		try (ShortcutDAO dao = Configuration.getInstance().getDAOFactory().getShortcutDAO()){
			if ( !dao.isShortcutOwner(shortcutId, getLoggedInUserId()))
				throw new UnauthorizedOperationException("Signed in user is not the owner of this shortcut.");
			
			dao.updateShortcut(shortcutId, request.getName(), request.getParent());
			dao.commit();	
			return;
		}
	}
	
	@GET
	@Path("/")
	@Produces(MediaType.APPLICATION_JSON)
	@Operation(
			summary = "List my Shortcuts",
			description = """
                          Lists all shortcuts owned by the current user.
                          
                          Database Tables:
                          - shortcut: Queries where owneruuid=userId AND is_deleted=false
                          
                          Query Parameters:
                          - limit: Maximum number of shortcuts to return (default: 100)
                          
                          Edge Cases:
                          - Not authenticated: Returns 401 Unauthorized
                          - No shortcuts: Returns empty array
                          
                          Response:
                          - 200 OK: Array of shortcut metadata
                          - 401 Unauthorized: Not authenticated
                          """
		)
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
