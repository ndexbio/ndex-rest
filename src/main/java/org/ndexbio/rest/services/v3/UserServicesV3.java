package org.ndexbio.rest.services.v3;

import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;


import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.QueryParam;

import org.ndexbio.common.models.dao.postgresql.CyWebWorkspaceDAO;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.model.exceptions.BadRequestException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.CyWebWorkspace;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.services.NdexOpenFunction;
import org.ndexbio.rest.services.NdexService;
import org.ndexbio.rest.services.UserServiceV2;

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

  	
	@POST
	@NdexOpenFunction
	@PermitAll
	@Path("/signin")
	@Produces("application/json")
  	
	public User singInByIdToken(
			 Map<String,String> payload
		) throws Exception  {

		if (getOAuthAuthenticator() == null) {
			throw new BadRequestException("Open ID authentication is not enabled on this server");
		}
		
		String idToken = payload.get("idToken");
		//throw exception if id token is missing.
		if ( idToken == null) {
			throw new BadRequestException("idToken is missing.");
		}
		//check if we need to create user from OAuth.
        try {
        	User user = getOAuthAuthenticator().getUserByIdToken(idToken);
        	return user;
		} catch (ObjectNotFoundException e) {
			//create user from id token
			User user = UserServiceV2.createUserFromIdToken (new User(), idToken, getOAuthAuthenticator());
			return user;
		} // catch other exceptions from the getUerByIdToken	
		catch (IOException | IllegalArgumentException | UnauthorizedOperationException | BadRequestException
				e) {
			throw new BadRequestException("Sign-in failed: " + e.getMessage());
		}
		        
	}
  	
	
	@GET
	@PermitAll
	@Path("")
	@Produces("application/json")
	public User getUserByAccountName(
			@QueryParam("username") /*@Encoded*/ final String accountName,
			@DefaultValue("false") @QueryParam("fullrecord") final boolean fullRecord,
			@QueryParam("key") final String accessKey
			) throws Exception {
		
		if ( accountName == null || accountName.length() == 0) {
			throw new BadRequestException("username is missing.");
        }
		


		boolean getFullRecord = false;
		if (fullRecord) {
			if (getLoggedInUser() !=null && getLoggedInUser().getUserName().equalsIgnoreCase(accountName))
				getFullRecord = true;
			else {
				if (accessKey == null || accessKey.length() == 0) {
					throw new BadRequestException("key is missing.");
				}
				String pswd = Configuration.getInstance().getSystemUserPassword();
				//getbase64 decoded password from the key
				String pwsd2 =new String(Base64.getDecoder().decode(accessKey));
				if ( !pswd.equals(pwsd2))
                    throw new UnauthorizedOperationException("Access key is not valid.");
				getFullRecord = true;
			}
		}
		
		
		try (UserDAO dao = new UserDAO()){
			
			final User user = dao.getUserByAccountName(accountName.toLowerCase(),true,
					getFullRecord );
			return user;
		} 
	}

}


