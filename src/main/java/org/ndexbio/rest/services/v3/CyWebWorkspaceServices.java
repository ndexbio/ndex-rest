package org.ndexbio.rest.services.v3;

import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.ndexbio.common.models.dao.postgresql.CyWebWorkspaceDAO;
import org.ndexbio.model.exceptions.BadRequestException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.CyWebWorkspace;
import org.ndexbio.model.object.NdexObjectUpdateStatus;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.services.NdexService;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Path("/v3/workspaces")
public class CyWebWorkspaceServices extends NdexService {

	public CyWebWorkspaceServices(@Context HttpServletRequest httpRequest) {
		super(httpRequest);
	}

	
	@POST
	@Produces("application/json")
	@Consumes(MediaType.APPLICATION_JSON)

	public Response createWorkspace(final CyWebWorkspace newWorkspace)
			throws Exception {
		UUID ownerId = getLoggedInUserId();
		
		//URI l;
		NdexObjectUpdateStatus status;
		try (CyWebWorkspaceDAO dao = new CyWebWorkspaceDAO ()){
			status = dao.createWorkspace(newWorkspace, ownerId);
			dao.commit();
			
		}
		
		ObjectMapper om = new ObjectMapper();
		
		return Response.created(new URI(Configuration.getInstance().getHostURI() 
				+ "/v3/workspaces/" + status.getUuid().toString())).
				header("Access-Control-Expose-Headers", "Location").entity(om.writeValueAsString(status)).build();	
	}
	
	
	
	@GET
	@Path("/{workspaceid}")
	@Produces("application/json")
	public CyWebWorkspace getWorkspaceByUUID(@PathParam("workspaceid") final String workspaceIdStr)
			throws IllegalArgumentException, NdexException, JsonParseException, JsonMappingException, SQLException, IOException {
		
		UUID workspaceUUID = UUID.fromString(workspaceIdStr);
		if ( getLoggedInUserId() != null ) {

			try (CyWebWorkspaceDAO dao = new CyWebWorkspaceDAO() ){
				CyWebWorkspace ws = dao.getWorkspace(workspaceUUID, getLoggedInUserId());
				return ws;
			}
		}
		throw new UnauthorizedOperationException("This function is only available to authenticated users.");
	}
	
	
    @PUT
	@Path("/{workspaceid}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces("application/json")
	public NdexObjectUpdateStatus updateWorkspace(@PathParam("workspaceid") final String workspaceIdStr,
								final CyWebWorkspace newWorkspace)
			throws Exception {
		if (newWorkspace.getName() == null)
			throw new BadRequestException("Workspace name is required.");
		
		UUID ownerId = getLoggedInUserId();
		UUID workspaceUUID = UUID.fromString(workspaceIdStr);
		newWorkspace.setWorkspaceId(workspaceUUID);
		
		try (CyWebWorkspaceDAO dao = new CyWebWorkspaceDAO ()){
			NdexObjectUpdateStatus s = dao.updateWorkspace(newWorkspace, ownerId);
			dao.commit();
			return s;
		}
		
	}
    
	@DELETE
	@Path("/{workspaceid}")
	public void deleteWorkspace(@PathParam("workspaceid") final String workspaceIdStr) throws JsonProcessingException, SQLException, NdexException {
		UUID ownerId = getLoggedInUserId();
		UUID workspaceUUID = UUID.fromString(workspaceIdStr);
		try (CyWebWorkspaceDAO dao = new CyWebWorkspaceDAO ()){
			dao.deleteWorkspace(workspaceUUID, ownerId);
			dao.commit();
		}
		
	}
	
	
    @PUT
	@Path("/{workspaceid}/name")
	@Consumes(MediaType.APPLICATION_JSON)
	public void renameWorkspace(@PathParam("workspaceid") final String workspaceIdStr,
			final CyWebWorkspace newWorkspace)
					throws Exception {
		if (newWorkspace.getName() == null)
			throw new BadRequestException("Workspace name is required.");
    	UUID ownerId = getLoggedInUserId();
    	UUID workspaceUUID = UUID.fromString(workspaceIdStr);
    	newWorkspace.setWorkspaceId(workspaceUUID);

    	try (CyWebWorkspaceDAO dao = new CyWebWorkspaceDAO ()){
    		dao.renameWorkspace(workspaceUUID, newWorkspace.getName(), ownerId);
    		dao.commit();
    	}

    }
    
    @PUT
 	@Path("/{workspaceid}/networkids")
 	@Consumes(MediaType.APPLICATION_JSON)
 	
    public void updateWorkspaceNetworks(@PathParam("workspaceid") final String workspaceIdStr,
 			final List<UUID> networkIds)
 					throws Exception {
     	UUID ownerId = getLoggedInUserId();
     	UUID workspaceUUID = UUID.fromString(workspaceIdStr);

     	try (CyWebWorkspaceDAO dao = new CyWebWorkspaceDAO ()){
     		dao.setWorkspaceNetworks(workspaceUUID, networkIds, ownerId);
     		dao.commit();
     	}

     }
     

}
