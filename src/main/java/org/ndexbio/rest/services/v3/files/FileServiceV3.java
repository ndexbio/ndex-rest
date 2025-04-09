package org.ndexbio.rest.services.v3.files;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

import org.ndexbio.common.util.NdexUUIDFactory;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.CopyRequest;
import org.ndexbio.model.object.FileCount;
import org.ndexbio.model.object.FileItemSummary;
import org.ndexbio.model.object.NdexObjectUpdateStatus;
import org.ndexbio.model.object.SharingMemberRequest;
import org.ndexbio.common.models.dao.ShortcutDAO;
import org.ndexbio.model.object.Shortcut;
import org.ndexbio.model.object.ShortcutRequest;
import org.ndexbio.model.object.TransferRequest;
import org.ndexbio.model.object.TrashRestoreRequest;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.filters.BasicAuthenticationFilter;
import org.ndexbio.rest.services.NdexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.ndexbio.common.models.dao.FileDAO;
import org.ndexbio.common.models.dao.TrashDAO;
import org.ndexbio.common.models.dao.FolderDAO;
import org.ndexbio.model.object.SharingRemoveRequest;
import org.ndexbio.model.object.SharingSimpleRequest;


@Path("/v3/files")
public class FileServiceV3 extends NdexService {

	protected static Logger accLogger = LoggerFactory.getLogger(BasicAuthenticationFilter.accessLoggerName);

	public FileServiceV3(@Context HttpServletRequest httpRequest) {
		super(httpRequest);
	}
	
	@GET
	@Path("/count")
	@Produces("application/json")
    @Operation(
            summary     = "Get my object counts",
            description = "Returns the number of networks, folders and shortcuts owned by the current user."
        )
	public Response getCount() throws Exception {
	    UUID userId = getLoggedInUserId();
	    if (userId == null) {
	        throw new UnauthorizedOperationException("You must be signed in to see your file counts.");
	    }

	    try (FileDAO dao = Configuration.getInstance().getDAOFactory().getFileDAO()) {
	       FileCount counts = dao.getOwnedFileCounts(userId);
	       return Response.ok().type(MediaType.APPLICATION_JSON_TYPE).entity(counts).build();
	    }
	}
	
	@GET
	@Path("/trash")
	@Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary     = "List items in my trash",
            description = "Returns all folders, networks and shortcuts currently in the authenticated user’s trash bin."
        )
	public Response listTrash() throws Exception {
	    UUID userId = getLoggedInUserId();
	    if (userId == null) {
	        throw new UnauthorizedOperationException("You must be logged in to view your trash.");
	    }

	    List<FileItemSummary> trashedItems;
	    try (TrashDAO dao = Configuration.getInstance().getDAOFactory().getTrashDAO()) {
	        trashedItems = dao.listTrashedItemsOfUser(userId);
	    }

	    return Response.ok(trashedItems).build();
	}
	
	@POST
	@Path("/trash/restore")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary     = "Restore objects from trash",
            description = "Restores the supplied folders / networks / shortcuts from the trash bin back to their original locations."
        )
	public void restoreItemsFromTrash(TrashRestoreRequest request) throws Exception {

	    UUID userId = getLoggedInUserId();
	    if (userId == null) {
	        throw new UnauthorizedOperationException("You must be logged in to restore items from trash.");
	    }

	    if ((request.getFolders() == null || request.getFolders().isEmpty()) &&
            (request.getNetworks() == null || request.getNetworks().isEmpty()) &&
            (request.getShortcuts() == null || request.getShortcuts().isEmpty())) {
	        throw new NdexException("No items to restore.");
	    }

	    try (TrashDAO dao = Configuration.getInstance().getDAOFactory().getTrashDAO()) {
	        dao.restoreTrashedItems(userId, request);
	        dao.commit();
	        
	        return ;
	    }
	    
	}
	
	@DELETE
	@Path("/trash")
    @Operation(
            summary     = "Empty my trash bin",
            description = "Permanently deletes all items in the authenticated user’s trash. Only shortcuts for now, rest will be added!"
        )
	public Response clearTrash() throws Exception {
	    UUID userId = getLoggedInUserId();
	    if (userId == null) {
	        throw new UnauthorizedOperationException("You must be logged in to clear your trash.");
	    }

	    try (TrashDAO dao = Configuration.getInstance().getDAOFactory().getTrashDAO()) {
	        dao.permanentlyDeleteAllTrashedItemsOfUser(userId);
	        dao.commit();
	    }

	    return Response.noContent().build();
	}
	
	@POST
	@Path("/copy")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary     = "Copy an object",
            description = """
                          Makes a copy of the supplied object.
                          Coping shortcuts is supported. 
                          Copying folders is not supported – suggest creating a shortcut instead.  
                          Copying networks is under development and will throw an exception.
                          """
        )
	public Response copyFile(final CopyRequest request,
			@QueryParam("accesskey") String accessKey,
			@QueryParam("id_token") String id_token,
			@QueryParam("auth_token") String auth_token) throws Exception {

	    if (request == null || request.getFrom_uuid() == null || request.getType() == null) {
	        throw new NdexException("Request must include 'from_uuid' and 'type'.");
	    }

	    UUID userId = getLoggedInUserId();
	    if (userId == null) {
	        throw new UnauthorizedOperationException("You must be logged in to copy an object.");
	    }

	    NdexObjectUpdateStatus status = null;
	    String type = request.getType().toLowerCase().trim();

	    try {
	        switch (type) {
	            case "folder":
	            	throw new NdexException("Coping folder is not supported. Create shortcut instead");

	            case "network":
	                // status = copyNetwork(request.getFrom_uuid(), userId, request.getTo_path(), accessKey, id_token, auth_token);
	            	throw new NdexException("Coping network is not supported yet. It is in development.");
	                
	            case "shortcut":
					status = copyShortcut(request.getFrom_uuid(), userId, request.getTo_path(), id_token, auth_token);
					break;

	            default:
	                throw new NdexException("Unsupported type: " + type);
	        }
	    } catch (Exception e) {
	        throw e;
	    }
	    
	    if (status == null) {
	        throw new NdexException("Copy operation failed - no status returned");
	    }
		String urlStr = Configuration.getInstance().getHostURI() + "/v3/files/" + type + "s/" + status.getUuid().toString();
		
		URI l = new URI (urlStr);
		ObjectMapper om = new ObjectMapper();
		
		return Response.created(l).header("Access-Control-Expose-Headers", "Location")
				.entity(om.writeValueAsString(status)).build();
	}

	private NdexObjectUpdateStatus copyShortcut(UUID fromUUID, UUID userId, UUID toPath, String id_token, String auth_token) throws Exception {
		try (ShortcutDAO dao = Configuration.getInstance().getDAOFactory().getShortcutDAO()) {
			Shortcut sourceShortcut = dao.getShortcut(fromUUID, userId);
			ShortcutRequest request = new ShortcutRequest();
			request.setName(sourceShortcut.getName());
			request.setTarget(sourceShortcut.getTarget());
			request.setParent(toPath);
			UUID newShortcutUUID = NdexUUIDFactory.INSTANCE.createNewNDExUUID();
			NdexObjectUpdateStatus status = dao.createShortcut(newShortcutUUID, userId, toPath, request.getName(), request.getTarget());
			dao.commit();
			return status;
		}
	}
	
	@POST
	@Path("/sharing/add_member")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary     = "Add permissions to folder or network",
            description = """
                          Grants READ or EDIT permission on a folder.  
                          Network support is not implemented yet and will raise an exception.
                          """
        )
	public Response addMember(List<SharingMemberRequest> requests) throws Exception {
	    UUID currentUserId = getLoggedInUserId();
	    if (currentUserId == null) {
	        throw new UnauthorizedOperationException("You must be logged in to add members.");
	    }

	    NdexObjectUpdateStatus status = null;
	    for (SharingMemberRequest request : requests) {
	        String type = request.getType().toLowerCase();
	        UUID targetId = request.getUuid();
	        
	        for (Map.Entry<UUID, String> entry : request.getMembers().entrySet()) {
	            UUID memberId = entry.getKey();
	            String permission = entry.getValue();

	            switch (type) {
	                case "folder":
	                    try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
	                        status = dao.addFolderPermission(targetId, memberId, permission);
	                        dao.commit();
	                    }
	                    break;
	                case "network":
	                    throw new NdexException("Network sharing is not supported yet");
	                default:
	                    throw new NdexException("Unsupported sharing type: " + type);
	            }
	        }
	    }

	    ObjectMapper om = new ObjectMapper();
	    
	    return Response.ok()
	                   .type(MediaType.APPLICATION_JSON_TYPE)
	                   .entity(om.writeValueAsString(status))
	                   .build();
	}

	@POST
	@Path("/sharing/update_member")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary     = "Update permissions",
            description = "Modifies READ / EDIT permission for existing members on a folder. Network support is not implemented yet."
        )
	public Response updateMember(List<SharingMemberRequest> requests) throws Exception {
	    UUID currentUserId = getLoggedInUserId();
	    if (currentUserId == null) {
	        throw new UnauthorizedOperationException("You must be logged in to update member permissions.");
	    }

	    for (SharingMemberRequest request : requests) {
	        String type = request.getType().toLowerCase();
	        UUID targetId = request.getUuid();
	        
	        for (Map.Entry<UUID, String> entry : request.getMembers().entrySet()) {
	            UUID memberId = entry.getKey();
	            String permission = entry.getValue();

	            switch (type) {
	                case "folder":
	                    try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
	                        dao.updateFolderPermission(targetId, memberId, permission);
	                        dao.commit();
	                    }
	                    break;
	                case "network":
	                    throw new NdexException("Network sharing is not supported yet");
	                default:
	                    throw new NdexException("Unsupported sharing type: " + type);
	            }
	        }
	    }

	    ObjectMapper om = new ObjectMapper();
	    Map<String, Object> response = new HashMap<>();
	    response.put("status", "success");
	    response.put("message", "Successfully updated permissions for " + requests.size() + " sharing requests");
	    
	    return Response.ok()
	                   .type(MediaType.APPLICATION_JSON_TYPE)
	                   .entity(om.writeValueAsString(response))
	                   .build();
	}

	@POST
	@Path("/sharing/remove_member")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary     = "Remove member permissions",
            description = "Revokes access for the supplied users on a folder. Network support is not implemented yet."
        )
	public Response removeMember(List<SharingRemoveRequest> requests) throws Exception {
	    UUID currentUserId = getLoggedInUserId();
	    if (currentUserId == null) {
	        throw new UnauthorizedOperationException("You must be logged in to remove member permissions.");
	    }

	    try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
	        if (requests != null) {
	            for (SharingRemoveRequest req : requests) {
	                if (!dao.isFolderOwner(req.getUuid(), currentUserId)) {
	                    throw new UnauthorizedOperationException(
	                        "You are not the owner of folder " + req.getUuid()
	                    );
	                }

	                // For each user in the 'members' list
	                if (req.getMembers() != null) {
	                    for (UUID userIdToRemove : req.getMembers()) {
	                        dao.removeFolderPermission(req.getUuid(), userIdToRemove);
	                        // If row doesn't exist, does nothing
	                    }
	                }
	            }
	        }

	        dao.commit();
	    }

	    ObjectMapper om = new ObjectMapper();
	    Map<String, Object> response = new HashMap<>();
	    response.put("status", "success");
	    
	    return Response.ok()
	                   .type(MediaType.APPLICATION_JSON_TYPE)
	                   .entity(om.writeValueAsString(response))
	                   .build();
	}
	
	@POST
	@Path("/sharing/share")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary     = "Generate a public access‑key (share)",
            description = """
                          Enables the access‑key on a folder (anonymous READ).  
                          Network sharing is not yet implemented and will throw exceptions.
                          Shortcuts cannot be shared - instead share objects that they point to.
                          """
        )
	public Response shareObject(SharingSimpleRequest request) throws Exception {

	    UUID userId = getLoggedInUserId();
	    if (userId == null) {
	        throw new UnauthorizedOperationException("You must be logged in to share.");
	    }

	    String type = (request.getType() == null) ? "" : request.getType().trim().toLowerCase();

	    String accessKey;
	    switch (type) {
	    	case "network":
	    		throw new NdexException("Sharing networks is not implemented yet.");
	        case "folder":
	            try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
	                if (!dao.isFolderOwner(request.getUuid(), userId)) {
	                    throw new UnauthorizedOperationException("You are not the owner of folder " + request.getUuid());
	                }
	                accessKey = dao.enableFolderAccessKey(request.getUuid());
	                dao.commit();
	            }
	            break;

	        case "shortcut":
	        	throw new NdexException("Sharing shortcut is not supported. Please share the folder or network the shortcut points to instead.");

	        default:
	            throw new NdexException("Unknown type: " + type);
	    }

	    Map<String,String> response = new HashMap<>();
	    response.put("accessKey", accessKey);
	    return Response.ok(response).build();
	}
	
	@POST
	@Path("/sharing/unshare")
	@Consumes(MediaType.APPLICATION_JSON)
    @Operation(
            summary     = "Disable public access‑key (unshare)",
            description = """
                          Disables the access‑key on a folder.  
                          Network un‑share is not implemented yet.
                          Shortcut un-share is not supported.
                          """
        )
	public Response unshareObject(SharingSimpleRequest request) throws Exception {

	    UUID userId = getLoggedInUserId();
	    if (userId == null) {
	        throw new UnauthorizedOperationException("You must be logged in to unshare.");
	    }

	    String type = (request.getType() == null) ? "" : request.getType().trim().toLowerCase();

	    switch (type) {
    		case "network":
    			throw new NdexException("Unshare network is not implemented yet.");
	        case "folder":
	            try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
	                if (!dao.isFolderOwner(request.getUuid(), userId)) {
	                    throw new UnauthorizedOperationException("You are not the owner of folder " + request.getUuid());
	                }
	                dao.disableFolderAccessKey(request.getUuid());
	                dao.commit();
	            }
	            break;

	        case "shortcut":
	        	throw new NdexException("Shortcuts are not sharable. Unshare is not supported.");
	        default:
	            throw new NdexException("Unknown type: " + type);
	    }
	    return Response.noContent().build();
	}
	
	@POST
	@Path("/sharing/transfer")
	@Consumes(MediaType.APPLICATION_JSON)
    @Operation(
            summary     = "Transfer folder ownership",
            description = """
                          Transfers ownership of one or more folders to another user.  
                          Transfer of networks is not yet implemented and will throw exceptions.
                          Transfer of shortcuts is not supported.
                          """
        )
	public Response transferObjects(List<TransferRequest> requests) throws Exception {

	    UUID currentUserId = getLoggedInUserId();
	    if (currentUserId == null) {
	        throw new UnauthorizedOperationException("You must be logged in to transfer objects.");
	    }

	    if (requests == null || requests.isEmpty()) {
	        return Response.noContent().build();
	    }

	    for (TransferRequest item : requests) {
	        String type = (item.getType() == null) ? "" : item.getType().toLowerCase();
	        if (item.getTo_user() == null) {
	            throw new NdexException("Missing 'to_user' in request.");
	        }

	        switch (type) {
	        	case "network":
	        		throw new NdexException("Transfer of networks is not implemented yet.");
	            case "folder":
	                try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
	                    if (!dao.isFolderOwner(item.getUuid(), currentUserId)) {
	                        throw new UnauthorizedOperationException(
	                            "You are not the owner of folder " + item.getUuid()
	                        );
	                    }
	                    dao.transferFolder(item.getUuid(), item.getTo_user());
	                    dao.commit();
	                }
	                break;

	            case "shortcut":
	            	throw new NdexException("Transfer of shortcuts is not supported.");
	            default:
	                throw new NdexException("Unknown type: " + type);
	        }
	    }

	    return Response.noContent().build();
	}

	@POST
	@Path("/sharing/list")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary     = "List objects shared with me",
            description = """
                          Returns IDs of folders (and in the future networks) for which the current user has READ or EDIT permission but is not the owner.
                          Only folder IDs are returned for now; network support is planned.
                          """
        )
	public Response listSharedObjects(
	    @QueryParam("limit") @DefaultValue("100") int limit
	) throws Exception {
	
	    UUID currentUserId = getLoggedInUserId();
	    if (currentUserId == null) {
	        throw new UnauthorizedOperationException("You must be logged in.");
	    }

	    List<UUID> folderIds;
	    try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
	        folderIds = dao.listSharedFolderIds(currentUserId);
	    }
	    return Response.ok().type(MediaType.APPLICATION_JSON_TYPE).entity(folderIds).build();
	}

}
