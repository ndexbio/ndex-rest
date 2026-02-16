package org.ndexbio.rest.services.v3.files;

import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.ndexbio.common.models.dao.ShortcutDAO;
import org.ndexbio.common.util.NdexUUIDFactory;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.*;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.filters.BasicAuthenticationFilter;
import org.ndexbio.rest.services.NdexService;
import org.ndexbio.rest.services.v3.files.handlers.AbstractFileTypeHandler;
import org.ndexbio.rest.services.v3.files.handlers.FileTypeHandlerFactory;
import org.ndexbio.task.NdexServerQueue;
import org.ndexbio.task.SolrTaskDeleteFile;
import org.ndexbio.task.SolrTaskRebuildFileIdx;
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
	private final FileTypeHandlerFactory fileTypeHandlerFactory = new FileTypeHandlerFactory();
	
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
                          
                          Response:
                          - 201 Created: Shortcut created successfully - Location header contains URL to new shortcut
                          - 500 Internal Server Error: Invalid request parameters or target not accessible
                          - 401 Unauthorized: Not authenticated
                          """
		)
	public Response createShortcut(final ShortcutRequest request) throws Exception {
		if (request == null) {
			throw new NdexException("No shortcut request provided.");
		}
        if (request.getName() == null || request.getName().isEmpty()) {
            throw new NdexException("Shortcut name cannot be empty.");
        }
        if (request.getTarget() == null) {
            throw new NdexException("Shortcut 'target' cannot be null.");
        }
        if (request.getTargetType() == null) {
            throw new NdexException("Shortcut 'target_type' cannot be null.");
        }
        UUID userId = getLoggedInUser().getExternalId();
		
		AbstractFileTypeHandler handler = fileTypeHandlerFactory.getHandler(request.getTargetType());
		handler.validateShortcutTarget(request.getTarget(), userId);
		
		UUID shortcutUUID = NdexUUIDFactory.INSTANCE.createNewNDExUUID();
		
		NdexObjectUpdateStatus status;
		try (ShortcutDAO dao = Configuration.getInstance().getDAOFactory().getShortcutDAO()) {
			status = dao.createShortcut(shortcutUUID, userId, request.getParent(), request.getName(), request.getTarget(), request.getTargetType());
			dao.commit();
			VisibilityType visibilityType = dao.getShortcutVisibility(shortcutUUID);
			indexShortcut(shortcutUUID, getLoggedInUser(), visibilityType, true);
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
	@Produces("application/json")
	@Operation(
			summary = "Get a Shortcut",
			description = """
                          Retrieves the specified shortcut if the current user has read access.
                          
                          Database Tables:
                          - shortcut: Queries shortcut metadata
                          
                          Response:
                          - 200 OK: Shortcut metadata
                          - 401 Unauthorized: Not authenticated, No read access
                          - 404 Not Found: Shortcut doesn't exist or was deleted
                          """
		)
	public NdexShortcut getShortcut(	@PathParam("shortcutid") final String shortcutId)
			throws Exception {
		
    	NdexShortcut shortcut = null;
    	
    	try (ShortcutDAO dao = Configuration.getInstance().getDAOFactory().getShortcutDAO()) {
    		UUID shortcutUUID = UUID.fromString(shortcutId);

     		UUID userId = getLoggedInUserId();
    		if ( ! dao.isReadable(shortcutUUID, userId)) 
                throw new UnauthorizedOperationException("User doesn't have read access to this network.");
    		shortcut = dao.getShortcut(shortcutUUID, userId);
    	}

    	return 	shortcut;
		
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
                          
                          Response:
                          - 204 No Content: Success
                          - 401 Unauthorized: Not authenticated or not owner
                          - 500 Internal Server Error: Invalid UUID
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

			VisibilityType visibilityType = dao.getShortcutVisibility(shortcutId);

			dao.deleteShortcut(shortcutId, permanent);
			dao.commit();
			deleteShortcutIndex(shortcutId, visibilityType);

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
                          
                          Response:
                          - 204 No Content: Success
                          - 401 Unauthorized: Not authenticated or not owner
                          - 500 Internal Server Error: Invalid UUID or parent folder
                          """
		)
	public void updateShortcut(final ShortcutRequest request,
			@PathParam("shortcutid") final String shortcutIdStr)
			throws NdexException,  SQLException, JsonProcessingException, Exception {

		UUID shortcutId = UUID.fromString(shortcutIdStr);
		try (ShortcutDAO dao = Configuration.getInstance().getDAOFactory().getShortcutDAO()){
			if ( !dao.isShortcutOwner(shortcutId, getLoggedInUserId()))
				throw new UnauthorizedOperationException("Signed in user is not the owner of this shortcut.");
			
			dao.updateShortcut(shortcutId, request.getName(), request.getParent());
			dao.commit();
			VisibilityType visibilityType = dao.getShortcutVisibility(shortcutId);
			indexShortcut(shortcutId, getLoggedInUser(), visibilityType, false);

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
                          - No shortcuts: Returns empty array
                          
                          Response:
                          - 200 OK: Array of shortcut metadata
                          - 401 Unauthorized: Not authenticated
                          """
		)
	public List<NdexShortcut> listMyShortcuts(@QueryParam("limit") @DefaultValue("100") int limit) throws Exception {

	    UUID userId = getLoggedInUserId();
	    if (userId == null) {
	        throw new UnauthorizedOperationException("You must be logged in to list your shortcuts.");
	    }

	    List<NdexShortcut> shortcuts;
	    try (ShortcutDAO dao = Configuration.getInstance().getDAOFactory().getShortcutDAO()) {
	        shortcuts = dao.listShortcutsOfUser(userId, limit);
	    }

	    return shortcuts;
	}
	protected void indexShortcut(UUID shortcutId, User user,
							   VisibilityType visibilityType, boolean createOnly) throws SQLException, NdexException, IOException {

		NdexServerQueue.INSTANCE.addSystemTask(new SolrTaskRebuildFileIdx(shortcutId, user.getExternalId(),
				user.getUserName(),visibilityType, FileType.SHORTCUT, createOnly));
	}

	protected void deleteShortcutIndex(UUID shortcutId,
									 VisibilityType visibilityType) throws SQLException, NdexException, IOException {

		NdexServerQueue.INSTANCE.addSystemTask(new SolrTaskDeleteFile(shortcutId, visibilityType));
	}


}
