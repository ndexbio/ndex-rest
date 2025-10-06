package org.ndexbio.rest.services.v3.files;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashMap;

import org.ndexbio.common.util.NdexUUIDFactory;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.CopyRequest;
import org.ndexbio.model.object.FileCount;
import org.ndexbio.model.object.FileItemSummary;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.NdexObjectUpdateStatus;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.SharingMemberRequest;
import org.ndexbio.common.models.dao.ShortcutDAO;
import org.ndexbio.model.object.NdexShortcut;
import org.ndexbio.model.object.ShortcutRequest;
import org.ndexbio.model.object.TransferOwnershipRequest;
import org.ndexbio.model.object.TrashRestoreRequest;
import org.ndexbio.model.object.network.NetworkIndexLevel;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.filters.BasicAuthenticationFilter;
import org.ndexbio.rest.services.NdexService;
import org.ndexbio.rest.services.NetworkServiceV2;
import org.ndexbio.task.NdexServerQueue;
import org.ndexbio.task.SolrIndexScope;
import org.ndexbio.task.SolrTaskRebuildNetworkIdx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.BadRequestException;
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
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.NotFoundException;
import java.sql.SQLException;

import org.apache.commons.io.FileUtils;
import org.ndexbio.common.cx.CX2NetworkFileGenerator;
import org.ndexbio.common.cx.CXNetworkFileGenerator;
import org.ndexbio.common.models.dao.FileDAO;
import org.ndexbio.common.models.dao.TrashDAO;
import org.ndexbio.common.models.dao.NetworkDAO;
import org.ndexbio.common.models.dao.postgresql.PostgresNetworkDAO;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.common.persistence.CX2NetworkLoader;
import org.ndexbio.common.persistence.CXNetworkLoader;
import org.ndexbio.common.models.dao.FolderDAO;
import org.ndexbio.model.object.SharingSimpleRequest;


@Path("/v3/files")
public class FileServiceV3 extends NdexService {

	protected static Logger accLogger = LoggerFactory.getLogger(BasicAuthenticationFilter.accessLoggerName);

	public FileServiceV3(@Context HttpServletRequest httpRequest) {
		super(httpRequest);
	}
	
	@GET
	@Path("/count")
	@Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Get my object counts",
            description = """
                          Returns the number of networks, folders and shortcuts owned by the current user.
                          
                          Database Tables:
                          - folder: Counts non-deleted folders
                          - network: Counts non-deleted networks
                          - shortcut: Counts non-deleted shortcuts
                          
                          Response:
                          - 200 OK: JSON object with counts for each type
                          - 401 Unauthorized: Not authenticated
                          """
    )
	public FileCount getCount() throws Exception {
	    UUID userId = getLoggedInUserId();
	    if (userId == null) {
	        throw new UnauthorizedOperationException("You must be signed in to see your file counts.");
	    }

	    try (FileDAO dao = Configuration.getInstance().getDAOFactory().getFileDAO()) {
	       FileCount counts = dao.getOwnedFileCounts(userId);
	       return counts;
	    }
	}
	
	@GET
	@Path("/trash")
	@Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "List items in my trash",
            description = """
                          Returns all folders, networks and shortcuts currently in the authenticated user's trash bin.
                          
                          Database Tables:
                          - folder: Queries where is_deleted=true AND show_in_trash=true
                          - network: Queries where is_deleted=true AND show_in_trash=true
                          - shortcut: Queries where is_deleted=true AND show_in_trash=true
                          
                          Edge Cases:
                          - Empty trash: Returns empty array
                          
                          Response:
                          - 200 OK: Array of trashed items with metadata
                          - 401 Unauthorized: Not authenticated
                          """
    )
	public List<FileItemSummary> listTrash() throws Exception {
	    UUID userId = getLoggedInUserId();
	    if (userId == null) {
	        throw new UnauthorizedOperationException("You must be logged in to view your trash.");
	    }

	    List<FileItemSummary> trashedItems;
	    try (TrashDAO dao = Configuration.getInstance().getDAOFactory().getTrashDAO()) {
	        trashedItems = dao.listTrashedItemsOfUser(userId);
	    }

	    return trashedItems;
	}
	
	@POST
	@Path("/trash/restore")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Restore objects from trash",
            description = """
                          Restores the supplied folders / networks / shortcuts from the trash bin back to their original locations.
                          
                          When restoring a folder, its children are also restored — but only those with show_in_trash = false. 
                          Any child item that was deleted before the folder (i.e., has show_in_trash = true) will not be restored and needs to be restored separately.
                                                    
                          Database Tables:
                          - folder: Updates is_deleted=false AND show_in_trash=false
                          - network: Updates is_deleted=false AND show_in_trash=false
                          - shortcut: Updates is_deleted=false AND show_in_trash=false
                          
                          Response:
                          - 204 No Content: Success
                          - 401 Unauthorized: Not authenticated
						  - 400 Bad Request: No items to restore
                          """
    )
	public void restoreItemsFromTrash(TrashRestoreRequest request) throws Exception {

	    UUID userId = getLoggedInUserId();
	    if (userId == null) {
	        throw new UnauthorizedOperationException("You must be logged in to restore items from trash.");
	    }

	    if ((request.getFolders() == null || request.getFolders().isEmpty()) &&
            (request.getNetworks() == null || request.getNetworks().isEmpty()) &&
            (request.getShortcuts() == null || request.getShortcuts().isEmpty())) {
	        throw new BadRequestException("No items to restore.");
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
            summary = "Empty my trash bin",
            description = """
                          Permanently deletes all items in the trash of authenticated user. Deleting a folder will delete all its children. This operation cannot be undone. 
                          
                          Database Tables:
                          - folder: Deletes records where is_deleted=true
                          - network: Deletes records where is_deleted=true
                          - shortcut: Deletes records where is_deleted=true
                          
                          Related Tables:
                          - folder_permission: Deletes associated permissions
                          - user_network_membership: Deletes associated network permissions 
                          
                          Response:
                          - 204 No Content: Success
                          - 401 Unauthorized: Not authenticated
                          """
    )
	public void clearTrash() throws Exception {
	    UUID userId = getLoggedInUserId();
	    if (userId == null) {
	        throw new UnauthorizedOperationException("You must be logged in to clear your trash.");
	    }

	    try (TrashDAO dao = Configuration.getInstance().getDAOFactory().getTrashDAO()) {
	        dao.permanentlyDeleteAllTrashedItemsOfUser(userId);
	        dao.commit();
	    }

	    return;
	}
	
	@DELETE
    @Path("/trash/{uuid}")
    @Operation(
        summary = "Permanently delete a specific item from trash",
        description = """
                      Permanently deletes a specific item (folder, network, or shortcut) from the trash.

                      When deleting a folder, all its children are also permanently deleted if they have show_in_trash = false.
                      
                      Database Tables:
                      - folder: Deletes record where UUID=itemId AND is_deleted=true
                      - network: Deletes record where UUID=itemId AND is_deleted=true
                      - shortcut: Deletes record where UUID=itemId AND is_deleted=true
                      
                      Related Tables:
                      - folder_permission: Deletes associated permissions
                      - user_network_membership: Deletes associated network permissions
                      
                      Response:
                      - 204 No Content: Success
                      - 401 Unauthorized: Not authenticated
                      - 404 Not Found: Item doesn't exist
                      """
    )
    public void permanentlyDeleteTrashedItem(@PathParam("uuid") final String itemIdStr) throws Exception, NdexException, SQLException {
        UUID itemId = UUID.fromString(itemIdStr);

	    UUID userId = getLoggedInUserId();
	    if (userId == null) {
	        throw new UnauthorizedOperationException("You must be logged in to restore items from trash.");
	    }
        
        // First get the item type
        FileType type;
        try (TrashDAO dao = Configuration.getInstance().getDAOFactory().getTrashDAO()) {
            type = dao.getTrashedItemType(itemId);
            if (type == null) {
                throw new NotFoundException("Item not found in trash.");
            }
            
            // Permanently delete the item
            dao.permanentlyDeleteTrashedItem(itemId, type);
            dao.commit();
        }
    }
	
	@POST
	@Path("/copy")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Copy an object",
            description = """
                          Makes a copy of the supplied object.

                          Copying folders is not supported – suggest creating a shortcut instead.  

                          
                          Database Tables:
                          - shortcut: Creates new record with copied metadata
                          - network: Creates new record with copied metadata and files
                          
                          Response:
                          - 201 Created: Copy successful - Location header contains URL to new object
                          - 400 Bad Request: Invalid operation
                          - 401 Unauthorized: Not authenticated, Insufficient permissions, Invalid target folder
                          - 500 Internal Server Error: Operation failed, Disk space exceeded, Invalid Network
                          """
    )
	public Response copyFile(final CopyRequest request,
			@QueryParam("accesskey") String accessKey) throws Exception {

	    if (request == null || request.getFileId() == null || request.getType() == null) {
	        throw new BadRequestException("Request must include 'from_uuid' and 'type'.");
	    }

	    UUID userId = getLoggedInUserId();
	    if (userId == null) {
			throw new UnauthorizedOperationException("You must be logged in to copy files.");
	    }

	    NdexObjectUpdateStatus status = null;
	    FileType type = request.getType();

	    try {
	        switch (type) {
	            case FOLDER:
	            	throw new NdexException("Coping folder is not supported. Create shortcut instead");

	            case NETWORK:
	                status = copyNetwork(request.getFileId(), userId, request.getTargetId(), accessKey);
	                break;
	                
	            case SHORTCUT:
					status = copyShortcut(request.getFileId(), userId, request.getTargetId());
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
		
		String urlStr = Configuration.getInstance().getHostURI() + "/v3/files/" + type.toString().toLowerCase() + "s/" + status.getUuid().toString();
		URI l = new URI(urlStr);
		ObjectMapper om = new ObjectMapper();
		
		return Response.created(l)
		               .header("Access-Control-Expose-Headers", "Location")
		               .entity(om.writeValueAsString(status))
		               .build();
	}

	private NdexObjectUpdateStatus copyNetwork(UUID srcNetUUID, UUID userId, UUID targetId, String accessKey) throws Exception {
		try (UserDAO dao = new UserDAO()) {
			dao.checkDiskSpace(userId);
		}
		
		try (PostgresNetworkDAO dao = new PostgresNetworkDAO()) {
			if (!dao.isReadable(srcNetUUID, userId)){
				if(!dao.accessKeyIsValid(srcNetUUID, accessKey)) {
					throw new UnauthorizedOperationException("User doesn't have read access to this network.");
				}
			}
			
			if (!dao.networkIsValid(srcNetUUID)) {
				throw new NdexException("Invalid networks can not be copied.");
			}
		}
		
		if (targetId != null) {
			try (FolderDAO folderDao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
				if (!folderDao.isFolderOwner(targetId, userId)) {
					throw new UnauthorizedOperationException("User doesn't have access to the target folder.");
				}
			}
		}

		UUID uuid = NdexUUIDFactory.INSTANCE.createNewNDExUUID();
		String uuidStr = uuid.toString();
		java.nio.file.Path tgt = Paths.get(Configuration.getInstance().getNdexRoot() + "/data/" + uuidStr);
		
		// Create directory with proper permissions
		Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxrwxr-x");
		FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(perms);
		Files.createDirectory(tgt, attr);
		
		// Copy network files
		copyNetworkFiles(srcNetUUID, uuidStr, attr);
		
		// Create network entry and files
		createNetworkEntryAndFiles(uuid, userId, srcNetUUID, targetId);
		
		// Index the new network
		NdexServerQueue.INSTANCE.addSystemTask(
			new SolrTaskRebuildNetworkIdx(uuid, SolrIndexScope.individual, true, null, NetworkIndexLevel.NONE, false)
		);

		NdexObjectUpdateStatus status = new NdexObjectUpdateStatus();
		status.setUuid(uuid);
		status.setModificationTime(new Timestamp(System.currentTimeMillis()));
		return status;
	}

	private void copyNetworkFiles(UUID srcNetUUID, String tgtUUID, FileAttribute<Set<PosixFilePermission>> attr) throws Exception {
		String srcPathPrefix = Configuration.getInstance().getNdexRoot() + "/data/" + srcNetUUID.toString() + "/";
		String tgtPathPrefix = Configuration.getInstance().getNdexRoot() + "/data/" + tgtUUID + "/";

		// Copy CX1 aspects
		File srcAspectDir = new File(srcPathPrefix + CXNetworkLoader.CX1AspectDir);
		if (srcAspectDir.exists()) {
			File tgtAspectDir = new File(tgtPathPrefix + CXNetworkLoader.CX1AspectDir);
			FileUtils.copyDirectory(srcAspectDir, tgtAspectDir);
		}

		// Copy CX2 aspects
		String tgtCX2AspectPathPrefix = tgtPathPrefix + CX2NetworkLoader.cx2AspectDirName;
		String srcCX2AspectPathPrefix = srcPathPrefix + CX2NetworkLoader.cx2AspectDirName;
		File srcCX2AspectDir = new File(srcCX2AspectPathPrefix);
		Files.createDirectories(Paths.get(tgtCX2AspectPathPrefix), attr);
		
		for (String fname : srcCX2AspectDir.list()) {
			java.nio.file.Path src = Paths.get(srcPathPrefix + CX2NetworkLoader.cx2AspectDirName, fname);
			java.nio.file.Path link = Paths.get(tgtCX2AspectPathPrefix, fname);
			
			if (Files.isSymbolicLink(src)) {
				java.nio.file.Path target = Paths.get(tgtPathPrefix + CXNetworkLoader.CX1AspectDir, fname);
				Files.createSymbolicLink(link, target);
			} else {
				Files.copy(Paths.get(srcCX2AspectPathPrefix, fname), Paths.get(tgtCX2AspectPathPrefix, fname));
			}
		}

		// Copy sample file if it exists
		java.nio.file.Path srcSample = Paths.get(Configuration.getInstance().getNdexRoot() + "/data/" + srcNetUUID.toString() + "/sample.cx");
		if (Files.exists(srcSample, LinkOption.NOFOLLOW_LINKS)) {
			java.nio.file.Path tgtSample = Paths.get(Configuration.getInstance().getNdexRoot() + "/data/" + tgtUUID + "/sample.cx");
			Files.copy(srcSample, tgtSample);
		}
	}

	private void createNetworkEntryAndFiles(UUID uuid, UUID userId, UUID srcNetUUID, UUID targetId) throws Exception {
		String cxFileName = Configuration.getInstance().getNdexRoot() + "/data/" + srcNetUUID.toString() + "/" + NetworkServiceV2.cx1NetworkFileName;
		long fileSize = new File(cxFileName).length();

		try (PostgresNetworkDAO dao = new PostgresNetworkDAO()) {
			dao.CreateCloneNetworkEntry(uuid, getLoggedInUser().getExternalId(), getLoggedInUser().getUserName(), fileSize, srcNetUUID);
			
			// Set parent folder if targetId is provided
			if (targetId != null) {
				dao.setNetworkFolder(uuid, targetId);
			}

			CXNetworkFileGenerator g = new CXNetworkFileGenerator(uuid, dao);
			g.reCreateCXFile();
			
			CX2NetworkFileGenerator g2 = new CX2NetworkFileGenerator(uuid, dao);
			String tmpFilePath = g2.createCX2File();
			Files.move(
				Paths.get(tmpFilePath),
				Paths.get(Configuration.getInstance().getNdexRoot() + "/data/" + uuid.toString() + "/" + CX2NetworkLoader.cx2NetworkFileName),
				StandardCopyOption.ATOMIC_MOVE
			);

			dao.setFlag(uuid, "iscomplete", true);
			dao.commit();
		}
	}

	private NdexObjectUpdateStatus copyShortcut(UUID fromUUID, UUID userId, UUID toPath) throws Exception {
		try (ShortcutDAO dao = Configuration.getInstance().getDAOFactory().getShortcutDAO()) {
			NdexShortcut sourceShortcut = dao.getShortcut(fromUUID, userId);
			ShortcutRequest request = new ShortcutRequest();
			request.setName("Copy of " + sourceShortcut.getName());
			request.setTarget(sourceShortcut.getTarget());
			request.setParent(toPath);
			request.setTargetType(sourceShortcut.getTargetType());
			UUID newShortcutUUID = NdexUUIDFactory.INSTANCE.createNewNDExUUID();
			NdexObjectUpdateStatus status = dao.createShortcut(newShortcutUUID, userId, toPath, request.getName(), request.getTarget(), request.getTargetType());
			dao.commit();
			return status;
		}
	}
	
	@POST
	@Path("/sharing/members")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Add, updates or deletes permissions to folder or network",
            description = """
                          Grants, updates READ or WRITE permission permission on folders or networks. Deletes permission if set to null for a user.

                          If user is granted permission on a folder, they will also be granted permission on all its children. If folder permission is revoked, it will also revoke permission on all its children.
                          
                          Database Tables:
                          - folder_permission: Updates/inserts/deletes permissions for folders
                          - user_network_membership: Updates/inserts/deletes permissions for networks
                          
                          Related Tables:
                          - folder: Verifies folder exists and user has access
                          - network: Verifies network exists and user has access
                          
                          Response:
                          - 200 OK: Permission updated
                          - 401 Unauthorized: Not authenticated, Insufficient permissions
                          - 500 Internal Server Error: Invalid type of file
                          """
    )
	public Response shareMembers(SharingMemberRequest request) throws Exception {
	    UUID currentUserId = getLoggedInUserId();
	    if (currentUserId == null) {
	        throw new UnauthorizedOperationException("You must be logged in to add members.");
	    }
	    
	    Map<String,String> result = new HashMap<>();            // <target‑UUID, status>

    	Map<UUID, FileType> files = request.getFiles();
        
        for (Map.Entry<UUID, Permissions> entry : request.getMembers().entrySet()) {
            UUID memberId = entry.getKey();
            Permissions permission = entry.getValue();
        
            for (Map.Entry<UUID, FileType> file : files.entrySet()) {
            	UUID fileId = file.getKey();
		        FileType type = file.getValue();	
	            switch (type) {
	                case FOLDER:
	                    try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
	            			if (!dao.isFolderOwner(fileId, getLoggedInUserId()))
	            				throw new UnauthorizedOperationException("Signed in user is not the owner of this folder.");
	            			
	                    	if (permission == null) {
	                    		dao.removeFolderPermission(fileId, memberId);
	                    		dao.commit();
	                    		result.put(fileId.toString(), "folder permissions removed");
	                    	}
	                    	else {
		                        dao.setFolderPermission(fileId, memberId, permission);
		                        dao.commit();
		                        result.put(fileId.toString(), "folder permission granted");
	                    	}
	                    }
	                    break;
	                case NETWORK:
	                    try (NetworkDAO dao = Configuration.getInstance().getDAOFactory().getNetworkDAO()) {

	                        if (!dao.isAdmin(fileId, currentUserId))
	                            throw new UnauthorizedOperationException(
	                                "You are not an administrator of network " + fileId);
	                        
	                        if (permission == null) {
        		                dao.revokeUserPrivilege(fileId, memberId);     // commits internally       
        		                result.put(fileId.toString(), "network permissions removed");
	                        }
	                        else {
		                        dao.grantPrivilegeToUser(fileId, memberId, permission);   // commits internally
		                        result.put(fileId.toString(), "network permission granted");		                        	
	                        }
	                    }
	                    break;
                default:
                    throw new NdexException("Unsupported sharing type: " + type);
	           }
            	
           }
        }

	    ObjectMapper om = new ObjectMapper();
	    
	    return Response.ok()
	                   .type(MediaType.APPLICATION_JSON_TYPE)
	                   .entity(om.writeValueAsString(result))
	                   .build();
	}
	
	@POST
	@Path("/sharing/members/list")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Operation(
	    summary = "List users with access to specified files",
	    description = """
	                  Returns a list of users and their permissions for the given folders and networks.
	                  
	                  Database Tables:
	                  - folder_permission: Queries permissions for folders
	                  - user_network_membership: Queries permissions for networks
	                  - ndex_user: Joins to get user information
	                  
	                  Edge Cases:
	                  - No permissions found: Returns empty array
	                  
	                  Response:
	                  - 200 OK: Array of user permissions
	                  - 401 Unauthorized: Not authenticated, Insufficient permissions
	                  - 500 Internal Server Error: Invalid type of file
	                  """
	)
	public List<Map<String, Object>> listMembers(Map<UUID, FileType> files) throws Exception {
	    UUID currentUserId = getLoggedInUserId();
	    if (currentUserId == null) {
	        throw new UnauthorizedOperationException("You must be logged in to view file permissions.");
	    }

	    List<Map<String, Object>> response = new ArrayList<>();

	    for (Map.Entry<UUID, FileType> fileEntry : files.entrySet()) {
	        UUID fileUUID = fileEntry.getKey();
	        FileType fileType = fileEntry.getValue();

	        Map<String, String> userPermissions = new HashMap<>();

	        switch (fileType) {
	            case FOLDER:
	                try (FolderDAO folderDAO = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
            			if (!folderDAO.isFolderOwner(fileUUID, getLoggedInUserId()))
            				throw new UnauthorizedOperationException("Signed in user is not the owner of this folder.");
            			
	                    userPermissions = folderDAO.getFolderPermissions(fileUUID);
	                }
	                break;

	            case NETWORK:
	                try (NetworkDAO networkDAO = Configuration.getInstance().getDAOFactory().getNetworkDAO()) {
                        if (!networkDAO.isAdmin(fileUUID, currentUserId))
                            throw new UnauthorizedOperationException(
                                "You are not an administrator of network " + fileUUID);
                        
	                    userPermissions = networkDAO.getNetworkUserPermissions(fileUUID, null, -1, -1);
	                }
	                break;

	            default:
	                throw new NdexException("Unsupported file type: " + fileType);
	        }

	        Map<String, Object> fileInfo = new HashMap<>();
	        Map<String, Object> details = new HashMap<>();
	        details.put("type", fileType);
	        details.put("members", userPermissions);
	        fileInfo.put(fileUUID.toString(), details);

	        response.add(fileInfo);
	    }
	    
	    return response;
	}
	
	@POST
	@Path("/sharing/share")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Generate a public access‑key (share)",
            description = """
                          Enables the access‑key on a folder (anonymous READ).
                          
                          Database Tables:
                          - folder: Updates access_key and access_key_is_on
                          
                          Edge Cases:
                          - Already shared: Returns existing key
                          
                          Response:
                          - 200 OK: Access key
                          - 401 Unauthorized: Not authenticated, Insufficient permissions
                          - 500 Internal Server Error: Invalid type of file, Shortcut is not supported
                          """
    )
	public Map<UUID,String> shareObject(SharingSimpleRequest request) throws Exception {

	    UUID userId = getLoggedInUserId();
	    if (userId == null) {
	        throw new UnauthorizedOperationException("You must be logged in to share.");
	    }
	    
	    Map<UUID,String> response = new HashMap<>();
	    Map<UUID, FileType> files = request.getFiles();
	    
	    for (Map.Entry<UUID, FileType> file : files.entrySet()) {
		    String accessKey;
		    FileType type = file.getValue();
		    switch (type) {
		    	case NETWORK:
		            try (NetworkDAO dao = Configuration.getInstance().getDAOFactory().getNetworkDAO()) {
	
		                if (!dao.isAdmin(file.getKey(), userId))
		                    throw new UnauthorizedOperationException("You are not an administrator of network " + file.getKey());
	
		                accessKey = dao.enableNetworkAccessKey(file.getKey());   // creates or re‑uses existing key
		                dao.commit();
		            }
		            break;
		        case FOLDER:
		            try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
		                if (!dao.isFolderOwner(file.getKey(), userId)) {
		                    throw new UnauthorizedOperationException("You are not the owner of folder " + file.getKey());
		                }
		                accessKey = dao.enableFolderAccessKey(file.getKey());
		                dao.commit();
		            }
		            break;
	
		        case SHORTCUT:
		        	throw new NdexException("Sharing shortcut is not supported. Please share the folder or network the shortcut points to instead.");
	
		        default:
		            throw new NdexException("Unknown type: " + type);
		    }
		    response.put(file.getKey(), accessKey);
	    }
	    return response;
	}
	
	@POST
	@Path("/sharing/unshare")
	@Consumes(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Disable public access‑key (unshare)",
            description = """
                          Disables the access‑key on a folder or a network.
                          
                          Database Tables:
                          - folder: Updates access_key_is_on=false
                          - network: Updates access_key_is_on=false
                          
                          Response:
                          - 204 No Content: Success
                          - 401 Unauthorized: Not authenticated, Insufficient permissions
                          - 500 Internal Server Error: Invalid type of file, Shortcut is not supported
                          """
    )
	public void unshareObject(SharingSimpleRequest request) throws Exception {

	    UUID userId = getLoggedInUserId();
	    if (userId == null) {
	        throw new UnauthorizedOperationException("You must be logged in to unshare.");
	    }

	    Map<UUID, FileType> files = request.getFiles();
	    
	    for (Map.Entry<UUID, FileType> file : files.entrySet()) {
	    	FileType type = file.getValue();
		    switch (type) {
	    		case NETWORK:
	    	        try (NetworkDAO dao = Configuration.getInstance().getDAOFactory().getNetworkDAO()) {
	
	    	            if (!dao.isAdmin(file.getKey(), userId))
	    	                throw new UnauthorizedOperationException("You are not an administrator of network " + file.getKey());
	
	    	            dao.disableNetworkAccessKey(file.getKey());
	    	            dao.commit();
	    	        }
	    	        break;
		        case FOLDER:
		            try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
		                if (!dao.isFolderOwner(file.getKey(), userId)) {
		                    throw new UnauthorizedOperationException("You are not the owner of folder " + file.getKey());
		                }
		                dao.disableFolderAccessKey(file.getKey());
		                dao.commit();
		            }
		            break;
	
		        case SHORTCUT:
		        	throw new NdexException("Shortcuts are not sharable. Unshare is not supported.");
		        default:
		            throw new NdexException("Unknown type: " + type);
		    }
	    }
	    return ;
	}
	
	@POST
	@Path("/sharing/transfer")
	@Consumes(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Transfer network ownership",
            description = """
                          Transfers ownership of one or more networks to another user.
                          
                          Database Tables:
                          - network: Updates owneruuid and owner
                          - user_network_membership: Updates permissions for old and new owners
                          - shortcut: Creates shortcut for old owner
                          
                          Response:
                          - 204 No Content: Success
                          - 401 Unauthorized: Not authenticated, Insufficient permissions
                          - 500 Internal Server Error: No networks specified for transfer, Missing new owner UUID in request
                          """
    )
	public void transferNetworksOwnership(TransferOwnershipRequest request) throws Exception {
	    UUID currentUserId = getLoggedInUserId();
	    if (currentUserId == null) {
	        throw new UnauthorizedOperationException("You must be logged in to transfer objects.");
	    }

	    if (request == null || request.getNetworks() == null || request.getNetworks().isEmpty()) {
	        throw new NdexException("No networks specified for transfer.");
	    }
	    
	    if (request.getNewOwner() == null) {
	        throw new NdexException("Missing new owner UUID in request.");
	    }

	    try (NetworkDAO networkDao = Configuration.getInstance().getDAOFactory().getNetworkDAO();
	         ShortcutDAO shortcutDao = Configuration.getInstance().getDAOFactory().getShortcutDAO()) {
	        
	        for (UUID networkId : request.getNetworks()) {
	            // Verify current user is the owner
	            if (!networkDao.isAdmin(networkId, currentUserId)) {
	                throw new UnauthorizedOperationException("You are not the owner of network " + networkId);
	            }
	            
	            // Get network info before transfer
	            UUID parentId = networkDao.getNetworkFolder(networkId);
	            String networkName = networkDao.getNetworkName(networkId);
	            
	            // Transfer ownership (this also sets WRITE permission for old owner)
	            networkDao.grantPrivilegeToUser(networkId, request.getNewOwner(), Permissions.ADMIN);
	            
	            // Set network's parent to null
	            networkDao.setNetworkFolder(networkId, null);
	            networkDao.commit();
	            
	            // Create shortcut for old owner
	            UUID shortcutUUID = NdexUUIDFactory.INSTANCE.createNewNDExUUID();
	            shortcutDao.createShortcut(
	                shortcutUUID,
	                currentUserId,
	                parentId,
	                networkName,
	                networkId,
	                FileType.NETWORK
	            );
	            shortcutDao.commit();
	        }
	    }

	    return ;
	}

	@GET
	@Path("/sharing/list")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
    @Operation(
    		summary = "List shared objects",
            description = """
                          Returns a list of folders and networks that have been shared with the authenticated user.
                          
                          Database Tables:
                          - folder_permission: Queries permissions for folders
                          - user_network_membership: Queries permissions for networks
                          - folder: Joins to get folder information
                          - network: Joins to get network information
                          - ndex_user: Joins to get owner information
                          
                          Edge Cases:
                          - No shared items: Returns empty array
                          
                          Response:
                          - 200 OK: Array of shared items with metadata
                          - 401 Unauthorized: Not authenticated
                          """
    )
	public List<FileItemSummary> listSharedObjects(
	    @QueryParam("limit") @DefaultValue("100") int limit
	) throws Exception {
	
	    UUID currentUserId = getLoggedInUserId();
	    if (currentUserId == null) {
	        throw new UnauthorizedOperationException("You must be logged in.");
	    }

	    List<FileItemSummary> fileInfo;
	    try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
	    	fileInfo = dao.listSharedFolders(currentUserId);
	    }
	    
		try (NetworkDAO networkDao = Configuration.getInstance().getDAOFactory().getNetworkDAO()) {
			fileInfo.addAll(networkDao.listSharedNetworks(currentUserId));
		}
		
		
	    return fileInfo;
	}

}
