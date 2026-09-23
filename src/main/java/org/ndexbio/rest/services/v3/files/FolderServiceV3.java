package org.ndexbio.rest.services.v3.files;

import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import org.ndexbio.common.models.dao.DeletedFileIds;
import org.ndexbio.common.models.dao.FolderDAO;
import org.ndexbio.common.util.NdexUUIDFactory;
import org.ndexbio.model.exceptions.DuplicateObjectException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.*;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.filters.BasicAuthenticationFilter;
import org.ndexbio.rest.services.NdexService;
import org.ndexbio.task.NdexServerQueue;
import org.ndexbio.task.SolrTaskDeleteFile;
import org.ndexbio.task.SolrTaskDeleteFiles;
import org.ndexbio.task.SolrTaskRebuildFileIdx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.ndexbio.rest.services.v3.PagingParameters;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
// NDEx's own BadRequestException, not the JAX-RS one: DefaultExceptionMapper is registered as
// ExceptionMapper<Throwable>, so it intercepts a JAX-RS WebApplicationException and reports it as
// a 500 "Uncaught exception" instead of the 400 the caller should see.
import org.ndexbio.model.exceptions.BadRequestException;
import jakarta.ws.rs.BeanParam;
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
                          """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Folder created",
                    content = @Content(schema = @Schema(implementation = NdexObjectUpdateStatus.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Server error")
    })
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
		VisibilityType visibility = request.getVisibility() != null ? request.getVisibility() : VisibilityType.PRIVATE;

		// create entry in db.
		NdexObjectUpdateStatus status;
		try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
			status = dao.createFolder(folderUUID, getLoggedInUser().getExternalId(), parentUUID, request.getName(), request.getDescription());
			if (visibility != VisibilityType.PRIVATE) {
				dao.setFolderVisibility(folderUUID, visibility);
			}
			dao.commit();
			createFileIndex(folderUUID, getLoggedInUser(), visibility, FileType.FOLDER, true);
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
	@Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Get a Folder",
            description = """
                          Retrieves the specified folder if the current user has read access or if valid access key is provided.
                          
                          Note: This endpoint does NOT support the "home" folder. The folderid must be a valid UUID.
                          
                          Path Parameters:
                          - folderid: UUID of the folder to retrieve
                          
                          Query Parameters:
                          - accesskey: Optional. Access key for anonymous access
                          - id_token: Optional. Google OAuth ID token
                          - auth_token: Optional. Basic auth token
                          """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Folder retrieved",
                    content = @Content(schema = @Schema(implementation = NdexFolder.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Folder not found")
    })
	public NdexFolder getFolder(	@PathParam("folderid") final String folderId,
			@QueryParam("accesskey") String accessKey,
			@QueryParam("id_token") String id_token,
			@QueryParam("auth_token") String auth_token)
			throws Exception {
		
    	NdexFolder folder = null;
    	
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
    	
		return folder;
		
	}

	@GET
	@Path("/{folderid}/accesskey")
	@Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Get Access Key of Folder",
            description = "Returns the access key for a folder when access key sharing is enabled. Only the folder owner may call this endpoint. Note: This endpoint does NOT support the \"home\" folder. The folderid must be a valid UUID."
    )
	public Map<String, String> getFolderAccessKey(
	        @PathParam("folderid") final String folderIdStr
	) throws Exception {

	    UUID userId = getLoggedInUserId();
	    if (userId == null) {
	        throw new UnauthorizedOperationException("You must be logged in to retrieve a folder access key.");
	    }

	    UUID folderId = UUID.fromString(folderIdStr);
	    try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
	        if (!dao.isFolderOwner(folderId, userId)) {
	            throw new UnauthorizedOperationException("Signed in user is not the owner of this folder.");
	        }

	        String key = dao.getFolderAccessKey(folderId);
	        if (key == null || key.isEmpty()) {
	            return null;
	        }

	        Map<String, String> result = new HashMap<>(1);
	        result.put("accessKey", key);
	        return result;
	    }
	}
	
	@DELETE
	@Path("/{folderid}")
    @Operation(
            summary = "Delete a Folder",
            description = """
                          Deletes the specified folder if the current user is the owner.
                          
                          Note: This endpoint does NOT support the "home" folder. The folderid must be a valid UUID.
                          
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
                          """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Folder deleted"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Folder not found")
    })
	@Produces(MediaType.APPLICATION_JSON)
	public void deleteFolder(
	        @PathParam("folderid") final String folderIdStr,
	        @QueryParam("force") @DefaultValue("false") boolean force,
	        @QueryParam("permanent") @DefaultValue("false") boolean permanent
	) throws Exception, NdexException, SQLException {
		
		UUID folderId = UUID.fromString(folderIdStr);
		try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()){
			
			if (!dao.isFolderOwner(folderId, getLoggedInUserId()))
				throw new UnauthorizedOperationException("Signed in user is not the owner of this folder.");

			// A force delete cascades over the whole subtree, so clear the Solr state of everything it
			// touched, not just this folder. The returned ids come from the same transaction that did
			// the deleting. No pre-delete getFolderVisibility() is needed: the batch task deletes each
			// id from both cores, which is also the only way to reach a row whose visibility is no
			// longer readable once it is deleted.
			DeletedFileIds deleted = dao.deleteFolder(folderId, force, permanent);
			dao.commit();
			if (!deleted.isEmpty()) {
				NdexServerQueue.INSTANCE.addSystemTask(new SolrTaskDeleteFiles(folderId, deleted));
			}

		}
	}
	
	@PUT
	@Path("/{folderid}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Update a Folder",
            description = """
                          Renames or moves a folder based on data passed in the request body. The user must be the folder's owner.
                          
                          Note: This endpoint does NOT support the "home" folder. The folderid must be a valid UUID.
                          
                          Path Parameters:
                          - folderid: UUID of the folder to update
                          
                          Request Body:
                          - name: Optional. New name for the folder
                          - parent: Optional. New parent folder UUID
                          
                          Edge Cases:
                          - Moving to descendant folder: Returns 400 Bad Request (would create cycle)
                          """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Folder updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Folder not found")
    })
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
				// Check if new parent is a descendant of the current folder
				if (dao.isDescendantOf(folderId, parentUUID)) {
					throw new NdexException("Cannot move folder to its descendant (would create cycle).");
				}
				
				if (!dao.isFolderOwner(parentUUID, userId)) {
					// If not owner, check if user has WRITE permission
					Map<String, String> permissions = dao.getFolderPermissions(parentUUID);
					String userPermission = permissions.get(userId.toString());
					if (userPermission == null || !userPermission.equals(Permissions.WRITE.toString())) {
						throw new UnauthorizedOperationException("User doesn't have write access to the parent folder.");
					}
				}
			}
			
			dao.updateFolder(folderId, request.getName(), parentUUID, userId, request.getDescription());
			if (request.getVisibility() != null) {
				dao.setFolderVisibility(folderId, request.getVisibility());
			}
			dao.commit();
			VisibilityType visibilityType = dao.getFolderVisibility(folderId);
			createFileIndex(folderId, getLoggedInUser(), visibilityType,  FileType.FOLDER, false);
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
                          Returns counts of the networks, subfolders, and shortcuts directly under the specified folder that the caller is allowed to see (matches the /list result for the same caller). When a valid folder access key is provided on the request, the counts cover the key-accessible children — folders, networks, and same-owner NETWORK shortcuts whose target networks the key now unlocks.
                          
                          If *folderid* is the literal string **"home"**, returns counts of all top-level items owned by the signed-in user (parent = NULL). The caller must be authenticated.
                          
                          Path Parameters:
                          - folderid: UUID of the folder to count items in, or the literal string "home" for the caller's home directory
                          
                          Query Parameters:
                          - accesskey: Optional. Access key for anonymous access
                          """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Counts retrieved",
                    content = @Content(schema = @Schema(implementation = FileCount.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Folder not found")
    })
	public FileCount getChildCount(
	        @PathParam("folderid") final String folderIdStr,
	        @QueryParam("accesskey") String accessKey
	) throws Exception {

	    UUID userId = getLoggedInUserId();

	    /* ---------------------------------------------------------------- home case */
	    if ("home".equalsIgnoreCase(folderIdStr)) {
	        if (userId == null) {
	            throw new UnauthorizedOperationException("You must be logged in to count your home folder.");
	        }
	        try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
	            return dao.getRootChildCountsOfUser(userId);
	        }
	    }

	    /* ------------------------------------------------------------- normal folder */
	    UUID folderUUID = UUID.fromString(folderIdStr);

	    try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
	        // If the request provides a valid access key, return the key-accessible child counts —
	        // folders, networks, and same-owner NETWORK shortcuts whose targets the key unlocks. If no
	        // valid key is present, the caller must be able to read the folder and gets the counts of
	        // the children they may see (matches /list).
	        if (dao.accessKeyIsValid(folderUUID, accessKey)) {
	            return dao.getFolderChildCountsKeyFiltered(folderUUID);
	        }
	        if (!dao.isReadable(folderUUID, userId)) {
	            throw new UnauthorizedOperationException(
	                "User doesn't have read access to this folder."
	            );
	        }
	        return dao.getReadableFolderChildCounts(folderUUID, userId);
	    }

	}
	
	@PermitAll
	@GET
	@Path("/{folderid}/list")
	@Produces(MediaType.APPLICATION_JSON)
	@Operation(
	    summary = "List items in a folder",
	    description = """
					Lists items (folders, networks, shortcuts) in the specified folder.
					If *folderid* is a UUID, returns the immediate children of that folder  
					that the caller is allowed to read (PUBLIC/UNLISTED, plus items they own or are shared on); when a valid folder access key is provided on the request, the response contains the key-accessible children — folders, networks, and same-owner NETWORK shortcuts whose target networks the key now unlocks.
					If *folderid* is the literal string **"home"**, returns all top level items owned by the signed in user (parent = NULL).

					Path Parameters:
					- folderid: UUID of the folder to list items from

					Query Parameters:
					- format: Optional. "compact" or "update" (default). Controls level of detail in response.
					- type: Optional. "folder", "network" or "shortcut". Omit it to get everything.
					A shortcut is a pointer at a folder or a network, so it counts as a way of seeing
					whatever it points at:
					* "folder"   returns folders, plus shortcuts pointing at folders
					* "network"  returns networks, plus shortcuts pointing at networks
					* "shortcut" returns shortcuts only, whatever they point at
					- accesskey: Optional. Access key for anonymous access
					- start: Optional. Zero-based index of the first item to return (default: 0). A negative
					value is rejected with 400. A non-numeric value is a parameter-conversion failure and
					surfaces as 404, not 400.
					- size: Optional. Maximum number of items to return. **This endpoint defaults to
					returning every item**, so an unparameterised call behaves exactly as it always has.
					Pass -1 (or any non-positive value) to request all items explicitly.

					Pagination:
					- Items are ordered by last modification time descending, nulls last, tie-broken by UUID.
					The order is stable across pages, so concatenating consecutive pages reproduces one
					unbounded listing.
					- A *start* beyond the last item returns 200 with an empty array, not an error. The same
					applies to a page whose items were all deleted concurrently.
					- The response carries no total. Use GET /v3/files/folders/{folderid}/count. Note the two
					are not directly comparable under a type filter, since /list?type=network also returns
					network-targeted shortcuts while FileCount.network does not count them.

					Response Format: despite the names, **compact is the fuller view** and update is
					the leaner one. Every item carries uuid, type, name, modificationTime, updatedBy,
					owner and isShared in both views; the fields below are added only by compact.
					- compact adds:
					* For folders: description, visibility, and creationTime (inside attributes)
					* For networks: description, edgecount, visibility
					* For shortcuts: target type, target status, target visibility, target edge count if target is a network
					- update (the default) omits all of the above.
					Null fields are omitted from the response entirely rather than sent as null.
					"""
	)
	@ApiResponses(value = {
	        @ApiResponse(responseCode = "200", description = "Items listed. The array is empty when the "
	                + "folder has no visible children, or when 'start' is past the last item.",
	                content = @Content(array = @ArraySchema(schema = @Schema(implementation = FileItemSummary.class)))),
	        @ApiResponse(responseCode = "400", description = "Invalid pagination parameter (start < 0)"),
	        @ApiResponse(responseCode = "401", description = "Unauthorized"),
	        @ApiResponse(responseCode = "404", description = "Folder not found")
	})
	public List<FileItemSummary> listItemsInFolder(
	        @PathParam("folderid")  final String folderIdStr,
	        @QueryParam("format")   @DefaultValue("update") String format,
	        @QueryParam("type")     String type,
	        @QueryParam("accesskey") String accessKey,
	        @BeanParam PagingParameters paging
	) throws Exception {

		paging.validate();
		int start = paging.getStart();
		// Unbounded unless the caller asks for a page: every existing client calls this endpoint with no
		// pagination parameters, and PagingParameters' own 100 default would silently truncate them.
		int size = paging.getSize(PagingParameters.UNBOUNDED_SIZE);

		boolean compact = "compact".equalsIgnoreCase(format);
		FileType fileType = null;
		if (type != null) {
			fileType = FileType.valueOf(type.toUpperCase());
		}
		
	    UUID userId = getLoggedInUserId();

	    /* ---------------------------------------------------------------- home case */
	    if ("home".equalsIgnoreCase(folderIdStr)) {
			if (userId == null) {
				throw new UnauthorizedOperationException("You must be logged in to list your home folder.");
			}
	        List<FileItemSummary> items;
	        try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
	            items = dao.listRootItemsOfUser(userId, compact, fileType, start, size);
	        }
	        return items;
	    }

	    /* ------------------------------------------------------------- normal folder */
	    UUID folderUUID = UUID.fromString(folderIdStr);

	    try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
	        // If the request provides a valid access key, return the key-accessible children —
	        // folders, networks, and same-owner NETWORK shortcuts whose targets the key unlocks. If no
	        // valid key is present, the caller must be able to read the folder and gets only the
	        // children they may see.
	        if (dao.accessKeyIsValid(folderUUID, accessKey)) {
	            return dao.listItemsInFolderKeyFiltered(folderUUID, compact, fileType, start, size);
	        }
	        if (!dao.isReadable(folderUUID, userId)) {
	            throw new UnauthorizedOperationException("User doesn't have read access to this folder.");
	        }
	        return dao.listReadableItemsInFolder(folderUUID, compact, fileType, userId, start, size);
	    }
	}
	
	

}
