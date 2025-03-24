package org.ndexbio.rest.services.v3.files;

import java.net.URI;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.ndexbio.common.models.dao.postgresql.PostgresFolderDAO;
import org.ndexbio.common.util.NdexUUIDFactory;
import org.ndexbio.model.exceptions.DuplicateObjectException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.FileCount;
import org.ndexbio.model.object.FileItemSummary;
import org.ndexbio.model.object.Folder;
import org.ndexbio.model.object.FolderRequest;
import org.ndexbio.model.object.NdexObjectUpdateStatus;
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
	public Response createFolder(final FolderRequest request) throws Exception {
		if (request == null) {
			throw new Exception("oo");
		}
		
		UUID folderUUID = NdexUUIDFactory.INSTANCE.createNewNDExUUID();
		
		// create entry in db. 
		NdexObjectUpdateStatus status;
		try (PostgresFolderDAO dao = new PostgresFolderDAO()) {
			status = dao.createFolder(folderUUID, getLoggedInUser().getExternalId(), request.getParent(), request.getName());
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
	@Operation(summary = "Get Folder", description = "XXX")

	public Response getFolder(	@PathParam("folderid") final String folderId,
			@QueryParam("accesskey") String accessKey,
			@QueryParam("id_token") String id_token,
			@QueryParam("auth_token") String auth_token)
			throws Exception {
		
    	Folder folder = null;
    	
    	try (PostgresFolderDAO dao = new PostgresFolderDAO()) {
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
	@Operation(summary = "Delete a Folder", description = "Delete a folder.")
	@Produces("application/json")
	public void deleteFolder(@PathParam("folderid") final String folderIdStr)
			throws NdexException, SQLException {
		
		UUID folderId = UUID.fromString(folderIdStr);
		try (PostgresFolderDAO dao = new PostgresFolderDAO()){
			
			if (!dao.isFolderOwner(folderId, getLoggedInUserId()))
				throw new UnauthorizedOperationException("Signed in user is not the owner of this folder.");
				
			dao.deleteFolder(folderId);
			dao.commit();
		} 
	}
	
	@PUT
	@Path("/{folderid}")
	@Consumes("application/json")
	@Produces("application/json")
	@Operation(summary = "Update a Folder", description = "Updates a folder based on data passed in the body.")
	public void updateFolder(final FolderRequest request,
			@PathParam("folderid") final String folderIdStr)
			throws  DuplicateObjectException,
			NdexException,  SQLException, JsonProcessingException {

		UUID folderId = UUID.fromString(folderIdStr);
		try (PostgresFolderDAO dao = new PostgresFolderDAO()){
			if ( !dao.isFolderOwner(folderId, getLoggedInUserId()))
				throw new UnauthorizedOperationException("Signed in user is not the owner of this folder.");
			
			dao.updateFolder(folderId, request.getName(), request.getParent(), getLoggedInUserId());
			dao.commit();	
			return ;
		} 
	}
	
	@PermitAll
	@GET
	@Path("/{folderid}/count")
	@Produces(MediaType.APPLICATION_JSON)
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

	    try (PostgresFolderDAO dao = new PostgresFolderDAO()) {
	        if (!dao.isReadable(folderUUID, userId) && !dao.accessKeyIsValid(folderUUID, accessKey)) {
	            throw new UnauthorizedOperationException(
	                "User doesn't have read access to this folder."
	            );
	        }
	    }

	    FileCount result;
	    try (PostgresFolderDAO dao = new PostgresFolderDAO()) {
	        result = dao.getFolderChildCounts(folderUUID);
	    }

	    return Response.ok()
	                   .type(MediaType.APPLICATION_JSON_TYPE)
	                   .entity(result)
	                   .build();
	}
	
	@PermitAll
	@GET
	@Path("/{folderid}/list")
	@Produces(MediaType.APPLICATION_JSON)
	public Response listItemsInFolder(
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
	                throw new UnauthorizedOperationException("Google OAuth is not enabled on this server.");
	            }
	            userId = getOAuthAuthenticator().getUserUUIDByIdToken(id_token);
	        }
	    }

	    try (PostgresFolderDAO dao = new PostgresFolderDAO()) {
	        if (!dao.isReadable(folderUUID, userId) && !dao.accessKeyIsValid(folderUUID, accessKey)) {
	            throw new UnauthorizedOperationException("User doesn't have read access to this folder.");
	        }
	    }

	    List<FileItemSummary> items;
	    try (PostgresFolderDAO dao = new PostgresFolderDAO()) {
	        items = dao.listItemsInFolder(folderUUID);
	    }

	    return Response.ok(items).build();
	}
	
	@GET
	@Path("/")
	@Produces(MediaType.APPLICATION_JSON)
	public Response listMyFolders(@QueryParam("limit") @DefaultValue("100") int limit) throws Exception {

	    UUID userId = getLoggedInUserId();
	    if (userId == null) {
	        throw new UnauthorizedOperationException("You must be logged in to list your folders.");
	    }

	    List<Folder> folders;
	    try (PostgresFolderDAO dao = new PostgresFolderDAO()) {
	        folders = dao.listFoldersOfUser(userId, limit);
	    }

	    return Response.ok(folders).build();
	}




}
