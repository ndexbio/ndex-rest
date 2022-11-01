package org.ndexbio.rest.services.v3;

import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.ndexbio.common.models.dao.postgresql.CyWebWorkspaceDAO;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.CyWebWorkspace;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.services.NdexService;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

@Path("/v3/workspaces")
public class CyWebWorkspaceServices extends NdexService {

	public CyWebWorkspaceServices(@Context HttpServletRequest httpRequest) {
		super(httpRequest);
	}

	
	@POST
	@Produces("text/plain")
	@Consumes(MediaType.APPLICATION_JSON)

	public Response createWorkspace(final CyWebWorkspace newWorkspace)
			throws Exception {
		UUID ownerId = getLoggedInUserId();
		
		URI l;
		try (CyWebWorkspaceDAO dao = new CyWebWorkspaceDAO ()){
			CyWebWorkspace ws = dao.createWorkspace(newWorkspace, ownerId);
			dao.commit();
			l = new URI(Configuration.getInstance().getHostURI() 
					+ "/v3/workspaces/" + ws.getWorkspaceId());

		}
		
		
		
		return Response.created(l).entity(l).build();	
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

	public void updateWorkspace(@PathParam("workspaceid") final String workspaceIdStr,
								final CyWebWorkspace newWorkspace)
			throws Exception {
		UUID ownerId = getLoggedInUserId();
		UUID workspaceUUID = UUID.fromString(workspaceIdStr);
		newWorkspace.setWorkspaceId(workspaceUUID);
		
		try (CyWebWorkspaceDAO dao = new CyWebWorkspaceDAO ()){
			dao.updateWorkspace(newWorkspace, ownerId);
			dao.commit();
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
 	
    public void renameWorkspace(@PathParam("workspaceid") final String workspaceIdStr,
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
