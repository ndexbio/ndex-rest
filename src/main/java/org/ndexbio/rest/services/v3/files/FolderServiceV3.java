package org.ndexbio.rest.services.v3.files;

import java.net.URI;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.ndexbio.common.models.dao.FolderDAO;
import org.ndexbio.common.util.NdexUUIDFactory;
import org.ndexbio.model.exceptions.DuplicateObjectException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.FileCount;
import org.ndexbio.model.object.FileItemSummary;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.Folder;
import org.ndexbio.model.object.FolderRequest;
import org.ndexbio.model.object.NdexObjectUpdateStatus;
import org.ndexbio.model.object.Permissions;
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
import jakarta.ws.rs.BadRequestException;
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


@Path("/v3/files/folders")
public class FolderServiceV3 extends NdexService {

	protected static Logger accLogger = LoggerFactory.getLogger(BasicAuthenticationFilter.accessLoggerName);

	public FolderServiceV3(@Context HttpServletRequest httpRequest) {
		super(httpRequest);
	}
	
	@POST
	@Path("/")
	@Consumes("application/json")
	@Produces("application/json")
    @Operation(
            summary = "Create a Folder",
            description = """
                          Creates a new folder object in the user's account.
                          
                          Request Body:
                          - name: Required. The name of the folder.
                          - parent: Optional. UUID of the parent folder. If not provided, folder will be created at root level.
                          
                          Edge Cases:
                          - Duplicate folder name in same parent: Allowed (folders can have same name)
                          
                          Response:
                          - 201 Created: Folder created successfully
                          - Location header contains URL to new folder
                          - Response body contains folder metadata
                          - 401 Unauthorized: Not authenticated, Insufficient permissions, Invalid parent folder
                          - 400 Bad Request: No folder request data was provided, Folder name cannot be empty
                          """
    )
	public Response createFolder(final FolderRequest request) throws Exception {
		if (request == null) {
			throw new BadRequestException("No folder request data was provided!");
		}
		
		if (request.getName() == null || request.getName().trim().isEmpty()) {
			throw new BadRequestException("Folder name cannot be empty.");
		}
		
		UUID parentUUID = request.getParent();
		if (parentUUID != null && !parentUUID.toString().isEmpty()) {
			try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
				if (!dao.isFolderOwner(parentUUID, getLoggedInUser().getExternalId())) {
					// If not owner, check if user has WRITE permission
					Map<String, String> permissions = dao.getFolderPermissions(parentUUID);
					String userPermission = permissions.get(getLoggedInUser().getExternalId().toString());
					if (userPermission == null || !userPermission.equals(Permissions.WRITE.toString())) {
						throw new UnauthorizedOperationException("User doesn't have write access to the parent folder.");
					}
				}
			}
		}
		
		UUID folderUUID = NdexUUIDFactory.INSTANCE.createNewNDExUUID();
		
		// create entry in db. 
		NdexObjectUpdateStatus status;
		try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
			status = dao.createFolder(folderUUID, getLoggedInUser().getExternalId(), parentUUID, request.getName());
			dao.commit();
		}

		String urlStr = Configuration.getInstance().getHostURI() +"/v3/files/folders/"+ folderUUID.toString();
		
	   URI l = new URI (urlStr);
	   ObjectMapper om = new ObjectMapper();
	
	   return Response.created(l).header("Access-Control-Expose-Headers", "Location")
		   .entity(om.writeValueAsString(status)).build();
	
	}
	
	@PermitAll
	@GET
	@Path("/{folderid}")
    @Operation(
            summary = "Get a Folder",
            description = """
                          Retrieves the specified folder if the current user has read access or if valid access key is provided.
                          
                          Path Parameters:
                          - folderid: UUID of the folder to retrieve
                          
                          Query Parameters:
                          - accesskey: Optional. Access key for anonymous access
                          - id_token: Optional. Google OAuth ID token
                          - auth_token: Optional. Basic auth token
                          
                          Response:
                          - 200 OK: Folder metadata
                          - 404 Not Found: Folder doesn't exist or was deleted
                          - 401 Unauthorized: Not authenticated, Insufficient permissions
                          """
    )
	public Response getFolder(	@PathParam("folderid") final String folderId,
			@QueryParam("accesskey") String accessKey,
			@QueryParam("id_token") String id_token,
			@QueryParam("auth_token") String auth_token)
			throws Exception {
		
    	Folder folder = null;
    	
    	try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
    		UUID folderUUID = UUID.fromString(folderId);

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
    		if ( ! dao.isReadable(folderUUID, userId) && (!dao.accessKeyIsValid(folderUUID, accessKey))) 
                throw new UnauthorizedOperationException("User doesn't have read access to this folder.");
    		folder = dao.getFolder(folderUUID, userId, accessKey);
    	}
    	
    	return 	Response.ok().type(MediaType.APPLICATION_JSON_TYPE).entity(folder).build();
		
	}
	
	@DELETE
	@Path("/{folderid}")
    @Operation(
            summary = "Delete a Folder",
            description = """
                          Deletes the specified folder if the current user is the owner.
                          
                          Path Parameters:
                          - folderid: UUID of the folder to delete
                          
                          Query Parameters:
                          - force: If true, deletes the folder and all its contents. If false (default), only deletes empty folders.
                          - permanent: If true, permanently deletes the folder from the database. If false (default), performs a logical delete (sets is_deleted flag).
                          
                          Edge Cases:
                          - Invalid folder UUID: Returns 404 Not Found
                          - Not folder owner: Returns 403 Forbidden
                          - Non-empty folder without force=true: Returns 400 Bad Request
                          - Already deleted folder: Returns 404 Not Found
                          
                          Deletion Behavior:
                          - Logical delete (permanent=false):
                            * Sets is_deleted=true
                            * Shows in trash
                            * Performs a logical delete on all networks, shortcuts, and folders in the folder tree, but does not show them in the trash
                            * Can be restored
                          - Permanent delete (permanent=true):
                            * Removes from database
                            * Removes all permissions on all networks, shortcuts, and folders in the folder tree
                            * Cannot be restored
                            * Requires force=true if folder not empty
                          
                          Response:
                          - 204 No Content: Success
                          - 401 Unauthorized: Not authenticated or not owner
                          - 404 Not Found: Folder doesn't exist or was deleted
                          """
    )
	@Produces("application/json")
	public void deleteFolder(
	        @PathParam("folderid") final String folderIdStr,
	        @QueryParam("force") @DefaultValue("false") boolean force,
	        @QueryParam("permanent") @DefaultValue("false") boolean permanent
	) throws Exception, NdexException, SQLException {
		
		UUID folderId = UUID.fromString(folderIdStr);
		try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()){
			
			if (!dao.isFolderOwner(folderId, getLoggedInUserId()))
				throw new UnauthorizedOperationException("Signed in user is not the owner of this folder.");
				
			dao.deleteFolder(folderId, force, permanent);
			dao.commit();
		} 
	}
	
	@PUT
	@Path("/{folderid}")
	@Consumes("application/json")
	@Produces("application/json")
    @Operation(
            summary = "Update a Folder",
            description = """
                          Renames or moves a folder based on data passed in the request body. The user must be the folder's owner.
                          
                          Path Parameters:
                          - folderid: UUID of the folder to update
                          
                          Request Body:
                          - name: Optional. New name for the folder
                          - parent: Optional. New parent folder UUID
                          
                          Edge Cases:
                          - Moving to descendant folder: Returns 400 Bad Request (would create cycle)
                          
                          Response:
                          - 204 No Content: Success
                          - 401 Unauthorized: Not authenticated or not owner, not owner of parent folder or insufficient permissions to parent folder
                          - 404 Not Found: Folder doesn't exist or was deleted
                          """
    )
	public void updateFolder(final FolderRequest request,
			@PathParam("folderid") final String folderIdStr)
			throws  DuplicateObjectException,
			NdexException,  SQLException, JsonProcessingException, Exception {

		UUID folderId = UUID.fromString(folderIdStr);
		UUID userId = getLoggedInUserId();
		try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()){
			if ( !dao.isFolderOwner(folderId, userId))
				throw new UnauthorizedOperationException("Signed in user is not the owner of this folder.");

			UUID parentUUID = request.getParent();
			if (parentUUID != null && !parentUUID.toString().isEmpty()) {
				if (!dao.isFolderOwner(parentUUID, userId)) {
					// If not owner, check if user has WRITE permission
					Map<String, String> permissions = dao.getFolderPermissions(parentUUID);
					String userPermission = permissions.get(userId.toString());
					if (userPermission == null || !userPermission.equals(Permissions.WRITE.toString())) {
						throw new UnauthorizedOperationException("User doesn't have write access to the parent folder.");
					}
				}
			}
			
			dao.updateFolder(folderId, request.getName(), parentUUID, userId);
			dao.commit();	
			return ;
		}
	}
	
	@PermitAll
	@GET
	@Path("/{folderid}/count")
	@Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Get Item Counts Within a Folder",
            description = """
                          Returns counts of how many networks, subfolders, and shortcuts exist directly under the specified folder.
                          
                          Path Parameters:
                          - folderid: UUID of the folder to count items in
                          
                          Query Parameters:
                          - accesskey: Optional. Access key for anonymous access
                          - id_token: Optional. Google OAuth ID token
                          - auth_token: Optional. Basic auth token
                          
                          Response:
                          - 200 OK: JSON object with counts
                          - 404 Not Found: Folder doesn't exist or was deleted
                          - 401 Unauthorized: Not authenticated, Insufficient permissions
                          """
    )
	public Response getChildCount(
	        @PathParam("folderid") final String folderIdStr,
	        @QueryParam("accesskey") String accessKey,
	        @QueryParam("id_token") String id_token,
	        @QueryParam("auth_token") String auth_token
	) throws Exception {

	    UUID folderUUID = UUID.fromString(folderIdStr);

	    UUID userId = getLoggedInUserId();
	    if (userId == null) {
	        if (auth_token != null) {
	            userId = getUserIdFromBasicAuthString(auth_token);
	        } else if (id_token != null) {
	            if (getOAuthAuthenticator() == null) {
	                throw new UnauthorizedOperationException(
	                    "Google OAuth is not enabled on this server."
	                );
	            }
	            userId = getOAuthAuthenticator().getUserUUIDByIdToken(id_token);
	        }
	    }

	    try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
	        if (!dao.isReadable(folderUUID, userId) && !dao.accessKeyIsValid(folderUUID, accessKey)) {
	            throw new UnauthorizedOperationException(
	                "User doesn't have read access to this folder."
	            );
	        }
	        FileCount result;
	        result = dao.getFolderChildCounts(folderUUID);
	        
		    return Response.ok()
	                   .type(MediaType.APPLICATION_JSON_TYPE)
	                   .entity(result)
	                   .build();
	    }

	}
	
	@PermitAll
	@GET
	@Path("/{folderid}/list")
	@Produces(MediaType.APPLICATION_JSON)
	@Operation(
	    summary = "List items in a folder",
	    description = """
	                  Lists all items (folders, networks, shortcuts) in the specified folder.
	                  If *folderid* is a UUID, returns the immediate children of that folder  
	                  (folders/networks/shortcuts) provided the caller is an owner or has read access or a valid accesskey.  
	                  If *folderid* is the literal string **"home"**, returns all top level items owned by the signed in user (parent = NULL).
	                  
	                  Path Parameters:
	                  - folderid: UUID of the folder to list items from
	                  
	                  Query Parameters:
	                  - format: Optional. "compact" or "update" (default). Controls level of detail in response.
	                  - type: Optional. Filter by type: "network", "folder", or null for all types.
	                  - accesskey: Optional. Access key for anonymous access
	                  - id_token: Optional. Google OAuth ID token
	                  - auth_token: Optional. Basic auth token
	                  
	                  Response Format:
	                  - Compact: Basic metadata only
	                  - Update: Full metadata including:
	                    * For networks: description, edge count, visibility
	                    * For shortcuts: target type, target status
	                  
	                  Response:
	                  - 200 OK: Array of items
	                  - 404 Not Found: Folder doesn't exist or was deleted
	                  - 401 Unauthorized: User doesn't have read access to this folder
	                  """
	)
	public Response listItemsInFolder(
	        @PathParam("folderid")  final String folderIdStr,
	        @QueryParam("format")   @DefaultValue("update") String format,
	        @QueryParam("type")     String type,
	        @QueryParam("accesskey") String accessKey,
	        @QueryParam("id_token")  String id_token,
	        @QueryParam("auth_token") String auth_token
	) throws Exception {
		
		boolean compact = "compact".equalsIgnoreCase(format);
		FileType fileType = null;
		if (type != null) {
			fileType = FileType.valueOf(type.toUpperCase());
		}
		
	    UUID userId = getLoggedInUserId();
	    if (userId == null) {
	        if (auth_token != null) {
	            userId = getUserIdFromBasicAuthString(auth_token);
	        } else if (id_token != null) {
	            if (getOAuthAuthenticator() == null) {
	                throw new UnauthorizedOperationException("Google OAuth is not enabled on this server.");
	            }
	            userId = getOAuthAuthenticator().getUserUUIDByIdToken(id_token);
	        }
	    }

	    /* ---------------------------------------------------------------- home case */
	    if ("home".equalsIgnoreCase(folderIdStr)) {
	        List<FileItemSummary> items;
	        try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
	            items = dao.listRootItemsOfUser(userId, compact, fileType);
	        }
	        return Response.ok(items).build();
	    }

	    /* ------------------------------------------------------------- normal folder */
	    UUID folderUUID = UUID.fromString(folderIdStr);

	    try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
	        if (!dao.isReadable(folderUUID, userId) && !dao.accessKeyIsValid(folderUUID, accessKey)) {
	            throw new UnauthorizedOperationException("User doesn't have read access to this folder.");
	        }
	        
	        List<FileItemSummary> items;
	        items = dao.listItemsInFolder(folderUUID, compact, fileType);
	        return Response.ok(items).build();
	    }
	}
	
	
	@GET
	@Path("/")
	@Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "List My Folders",
            description = """
                          Lists all folders owned by the current user.
                          
                          Query Parameters:
                          - limit: Optional. Maximum number of folders to return (default: 100)
                          
                          Edge Cases:
                          - No folders: Returns empty array
                          
                          Response:
                          - 200 OK: Array of folders
                          - 401 Unauthorized: Not authenticated
                          """
    )
	public Response listMyFolders(@QueryParam("limit") @DefaultValue("100") int limit) throws Exception {

	    UUID userId = getLoggedInUserId();
	    if (userId == null) {
	        throw new UnauthorizedOperationException("You must be logged in to list your folders.");
	    }

	    List<Folder> folders;
	    try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
	        folders = dao.listFoldersOfUser(userId, limit);
	    }

	    return Response.ok(folders).build();
	}




}
