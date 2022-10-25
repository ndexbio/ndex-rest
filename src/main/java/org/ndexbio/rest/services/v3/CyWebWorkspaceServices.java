package org.ndexbio.rest.services.v3;

import java.net.URI;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.ndexbio.common.models.dao.postgresql.CyWebWorkspaceDAO;
import org.ndexbio.model.object.CyWebWorkspace;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.services.NdexService;

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
}
