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
import org.ndexbio.model.object.Shortcut;
import org.ndexbio.model.object.ShortcutRequest;
import org.ndexbio.model.object.TransferRequest;
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

import org.apache.commons.io.FileUtils;
import org.ndexbio.common.cx.CX2NetworkFileGenerator;
import org.ndexbio.common.cx.CXNetworkFileGenerator;
import org.ndexbio.common.models.dao.FileDAO;
import org.ndexbio.common.models.dao.TrashDAO;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
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
            description = "Returns all folders, networks and shortcuts currently in the authenticated user's trash bin."
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
            description = "Permanently deletes all items in the authenticated user's trash. Only shortcuts for now, rest will be added!"
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

	    if (request == null || request.getFileId() == null || request.getType() == null) {
	        throw new NdexException("Request must include 'from_uuid' and 'type'.");
	    }

	    UUID userId = getLoggedInUserId();
		if (userId == null) {
			if (auth_token != null) {
				userId = getUserIdFromBasicAuthString(auth_token);
			} else if (id_token != null) {
				if (getOAuthAuthenticator() == null)
					throw new UnauthorizedOperationException("Google OAuth is not enabled on this server.");
				userId = getOAuthAuthenticator().getUserUUIDByIdToken(id_token);
			}
		}

	    NdexObjectUpdateStatus status = null;
	    FileType type = request.getType();

	    try {
	        switch (type) {
	            case FOLDER:
	            	throw new NdexException("Coping folder is not supported. Create shortcut instead");

	            case NETWORK:
	                status = copyNetwork(request.getFileId(), userId, request.getTargetId());
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

	private NdexObjectUpdateStatus copyNetwork(UUID srcNetUUID, UUID userId, UUID targetId) throws Exception {
		try (UserDAO dao = new UserDAO()) {
			dao.checkDiskSpace(userId);
		}
		
		try (NetworkDAO dao = new NetworkDAO()) {
			if (!dao.isReadable(srcNetUUID, userId)) {
				throw new UnauthorizedOperationException("User doesn't have read access to this network.");
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

		try (NetworkDAO dao = new NetworkDAO()) {
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
	@Path("/sharing/members")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary     = "Add, updates or deletes permissions to folder or network",
            description = """
                          Grants, updates READ or WRITE permission permission on folders or networks. 
                          Deletes permission if set to null for a user.
                          """
        )
	public Response addMember(SharingMemberRequest request) throws Exception {
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
	                    try (NetworkDAO dao = new NetworkDAO()) {

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
	    description = "Returns a list of users and their permissions for the given folders and networks."
	)
	public Response listMembers(Map<UUID, FileType> files) throws Exception {
	    UUID currentUserId = getLoggedInUserId();
	    if (currentUserId == null) {
	        throw new UnauthorizedOperationException("You must be logged in to view file permissions.");
	    }

	    List<Map<Object, Object>> response = new ArrayList<>();

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
	                try (NetworkDAO networkDAO = new NetworkDAO()) {
                        if (!networkDAO.isAdmin(fileUUID, currentUserId))
                            throw new UnauthorizedOperationException(
                                "You are not an administrator of network " + fileUUID);
                        
	                    userPermissions = networkDAO.getNetworkUserPermissions(fileUUID, null, -1, -1);
	                }
	                break;

	            default:
	                throw new NdexException("Unsupported file type: " + fileType);
	        }

	        Map<Object, Object> fileInfo = new HashMap<>();
	        fileInfo.put(fileUUID, fileType);
	        fileInfo.put("members", userPermissions);

	        response.add(fileInfo);
	    }

	    ObjectMapper om = new ObjectMapper();
	    
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
	    
	    Map<UUID,String> response = new HashMap<>();
	    Map<UUID, FileType> files = request.getFiles();
	    
	    for (Map.Entry<UUID, FileType> file : files.entrySet()) {
		    String accessKey;
		    FileType type = file.getValue();
		    switch (type) {
		    	case NETWORK:
		            try (NetworkDAO dao = new NetworkDAO()) {
	
		                if (!dao.isAdmin(file.getKey(), userId))
		                    throw new UnauthorizedOperationException("You are not an administrator of network " + file.getKey());
	
		                accessKey = dao.enableNetworkAccessKey(file.getKey());   // creates or re‑uses existing key
		                dao.commit();
		            }
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

	    Map<UUID, FileType> files = request.getFiles();
	    
	    for (Map.Entry<UUID, FileType> file : files.entrySet()) {
	    	FileType type = file.getValue();
		    switch (type) {
	    		case NETWORK:
	    	        try (NetworkDAO dao = new NetworkDAO()) {
	
	    	            if (!dao.isAdmin(file.getKey(), userId))
	    	                throw new UnauthorizedOperationException("You are not an administrator of network " + file.getKey());
	
	    	            dao.disableNetworkAccessKey(file.getKey());
	    	            dao.commit();
	    	        }
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
	public Response transferObjects(TransferRequest request) throws Exception {

	    UUID currentUserId = getLoggedInUserId();
	    if (currentUserId == null) {
	        throw new UnauthorizedOperationException("You must be logged in to transfer objects.");
	    }

	    if (request == null) {
	    	throw new NdexException("No request provided!");
	    }
	    
	    Map<UUID, FileType> files = request.getFiles();
	    
	    for (Map.Entry<UUID, FileType> file : files.entrySet()) {

	        FileType type = file.getValue();
	        if (request.getNewOwner() == null) {
	            throw new NdexException("Missing new user uuid in request.");
	        }
	
	        switch (type) {
	        	case NETWORK:
	        		throw new NdexException("Transfer of networks is not implemented yet.");
	            case FOLDER:
	                try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
	                    if (!dao.isFolderOwner(file.getKey(), currentUserId)) {
	                        throw new UnauthorizedOperationException(
	                            "You are not the owner of folder " + file.getKey()
	                        );
	                    }
	                    dao.transferFolder(file.getKey(), request.getNewOwner());
	                    dao.commit();
	                }
	                break;
	
	            case SHORTCUT:
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
                          Returns IDs of folders (and in the future networks) for which the current user has READ or WRITE permission but is not the owner.
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
