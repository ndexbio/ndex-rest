package org.ndexbio.rest.services.v3;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;

import org.ndexbio.common.models.dao.postgresql.CyWebWorkspaceDAO;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.CyWebWorkspace;
import org.ndexbio.rest.services.NdexService;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;


@Path("/v3/users")

public class UserServicesV3 extends NdexService {
	
	public UserServicesV3(@Context HttpServletRequest httpRequest) {
		super(httpRequest);
	}

	
	
  	@GET
	@Path("/{userid}/workspaces")
	@Produces("application/json")
  	
	public List<CyWebWorkspace> getWorkspacesByUserId(
					@PathParam("userid") String userIdStr
		) throws SQLException, JsonParseException, JsonMappingException, IOException, UnauthorizedOperationException, ObjectNotFoundException {

		UUID userId = UUID.fromString(userIdStr);
		if ( !userId.equals(getLoggedInUserId()))
			throw new UnauthorizedOperationException("Userid has to be the same as the autheticated user's");
		
		try (CyWebWorkspaceDAO dao = new CyWebWorkspaceDAO()) {
			return dao.getWorkspaces(userId);
		} 
				
	}      	


}
