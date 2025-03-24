package org.ndexbio.rest.services.v3.files;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.ndexbio.common.models.dao.postgresql.TrashDAO;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.CopyRequest;
import org.ndexbio.model.object.FileCount;
import org.ndexbio.model.object.FileItemSummary;
import org.ndexbio.model.object.NdexObjectUpdateStatus;
import org.ndexbio.model.object.TrashRestoreRequest;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.filters.BasicAuthenticationFilter;
import org.ndexbio.rest.services.NdexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.ndexbio.common.models.dao.FileDAO;

@Path("/v3/files")
public class FileServiceV3 extends NdexService {

	protected static Logger accLogger = LoggerFactory.getLogger(BasicAuthenticationFilter.accessLoggerName);

	public FileServiceV3(@Context HttpServletRequest httpRequest) {
		super(httpRequest);
	}
	
	@GET
	@Path("/count")
	@Produces("application/json")
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
	public Response listTrash() throws Exception {
	    UUID userId = getLoggedInUserId();
	    if (userId == null) {
	        throw new UnauthorizedOperationException("You must be logged in to view your trash.");
	    }

	    List<FileItemSummary> trashedItems;
	    try (TrashDAO dao = new TrashDAO()) {
	        trashedItems = dao.listTrashedItemsOfUser(userId);
	    }

	    return Response.ok(trashedItems).build();
	}
	
	@POST
	@Path("/trash/restore")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
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

	    try (TrashDAO dao = new TrashDAO()) {
	        dao.restoreTrashedItems(userId, request);
	        dao.commit();
	        
	        return ;
	    }
	    
	}
	
	/*
	@DELETE
	@Path("/trash")
	public Response clearTrash() throws Exception {
	    UUID userId = getLoggedInUserId();
	    if (userId == null) {
	        throw new UnauthorizedOperationException("You must be logged in to clear your trash.");
	    }

	    try (TrashDAO dao = new TrashDAO()) {
	        dao.permanentlyDeleteAllTrashedItemsOfUser(userId);
	        dao.commit();
	    }

	    return Response.noContent().build();
	}
	*/
	
	@POST
	@Path("/copy")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
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
	                break;
	                
	            case "shortcut":
	            	break;

	            default:
	                throw new NdexException("Unsupported type: " + type);
	        }
	    } catch (Exception e) {
	        throw e;
	    }
	    
		String urlStr = Configuration.getInstance().getHostURI() + "/v3/files/" + type + "s/" + status.getUuid().toString();
		
		URI l = new URI (urlStr);
		ObjectMapper om = new ObjectMapper();
		
		return Response.created(l).header("Access-Control-Expose-Headers", "Location")
				.entity(om.writeValueAsString(status)).build();
	}

}
