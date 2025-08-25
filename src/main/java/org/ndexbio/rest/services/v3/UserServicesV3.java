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
import jakarta.ws.rs.core.Response;
import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.QueryParam;

import org.ndexbio.common.models.dao.FolderDAO;
import org.ndexbio.common.models.dao.NetworkDAO;
import org.ndexbio.common.models.dao.postgresql.CyWebWorkspaceDAO;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.model.exceptions.BadRequestException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.CyWebWorkspace;
import org.ndexbio.model.object.FileItemSummary;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.SharedFile;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.services.NdexOpenFunction;
import org.ndexbio.rest.services.NdexService;
import org.ndexbio.rest.services.UserServiceV2;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import io.swagger.v3.oas.annotations.Operation;


@Path("/v3/users")

public class UserServicesV3 extends NdexService {
	
	public UserServicesV3(@Context HttpServletRequest httpRequest) {
		super(httpRequest);
	}

	
	
  	@GET
	@Path("/{userid}/workspaces")
	@Operation(summary = "Get User's Workspaces", description = "Returns a list of CyWebWorkspace objects owned by the specified user. The authenticated user must be the same as the userid parameter.")
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
	@Operation(summary = "Sign In with ID Token", description = "Authenticate a user using an OpenID Connect ID token. If the user doesn't exist, a new account will be created automatically.")
	@Produces("application/json")
  	
	public User signInByIdToken(
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
			//if ( !user.getIsVerified())
			//		throw UnauthorizedOperationException.createUnVerifiedAccountError(user.getUserName(), user.getEmailAddress());
        	return user;
		} catch (ObjectNotFoundException e) {
			//create user from id token
			User user = UserServiceV2.createUserFromIdToken (new User(), idToken, getOAuthAuthenticator());
			return user;
		} // catch other exceptions from the getUerByIdToken	
		catch (IOException | IllegalArgumentException | BadRequestException e) {
			throw new BadRequestException("Sign-in failed: " + e.getMessage());
		}
		        
	}
  	
	
	@GET
	@PermitAll
	@Path("")
	@Operation(summary = "Get User By Account Name", description = "Return the user corresponding to the provided user name. Use fullrecord=true and provide an access key to get complete user information.")
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
	
	
	@GET
	@Path("/{userid}/home")
	@Operation(summary = "Get User's Home Content", description = "Returns the content of a user's home folder including networks, folders, and shortcuts. Shows different content based on authentication status and relationship to the user.")
	@Produces("application/json")
	@PermitAll
	public Response getUserHomeContent(
			@PathParam("userid")  final String userIdStr, 
			@QueryParam("format") @DefaultValue("update") String format)
	        throws Exception {

		boolean compact = "compact".equalsIgnoreCase(format);
	    UUID userId = UUID.fromString(userIdStr);
	    UUID requesterId = getLoggedInUserId(); // null if not signed in

	    boolean isSelf = requesterId != null && requesterId.equals(userId);

	    List<FileItemSummary> items;
        if (isSelf) {
	        try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
	            items = dao.listRootItemsOfUser(userId, compact, null);
	        }
	        return Response.ok(items).build();
        } else if (requesterId != null) {
            // Shared-with-me content in user's home folder
            try (NetworkDAO networkDAO = Configuration.getInstance().getDAOFactory().getNetworkDAO()) {
                items = networkDAO.listNetworksSharedBySpecificUser(requesterId, userId, compact);
            }
            try (FolderDAO folderDAO = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
            	items.addAll(folderDAO.listFoldersSharedBySpecificUser(requesterId, userId, compact));
            	items.addAll(folderDAO.listPublicRootItemsOfUser(userId, compact, FileType.SHORTCUT));
            }

            return Response.ok().entity(items).build();
        } else {
            // Anonymous - public content only
            try (FolderDAO folderDAO = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
                items = folderDAO.listPublicRootItemsOfUser(userId, compact);
            }
            return Response.ok(items).build();
        }
	}


}


