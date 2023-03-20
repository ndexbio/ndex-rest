/**
 * Copyright (c) 2013, 2016, The Regents of the University of California, The Cytoscape Consortium
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */
package org.ndexbio.rest.services;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.Encoded;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;

import org.apache.http.client.ClientProtocolException;
import org.ndexbio.common.models.dao.postgresql.RequestDAO;
import org.ndexbio.common.models.dao.postgresql.TaskDAO;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.common.solr.UserIndexManager;
import org.ndexbio.common.util.Util;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.Membership;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.Request;
import org.ndexbio.model.object.SimpleQuery;
import org.ndexbio.model.object.SolrSearchResult;
import org.ndexbio.model.object.Status;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.User;
import org.ndexbio.model.object.UserV1;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.filters.BasicAuthenticationFilter;
import org.ndexbio.rest.helpers.AmazonSESMailSender;
import org.ndexbio.rest.helpers.Security;
import org.ndexbio.security.GoogleOpenIDAuthenticator;
import org.ndexbio.security.LDAPAuthenticator;
import org.ndexbio.security.OAuthAuthenticator;
import org.ndexbio.security.OAuthTokenRenewRequest;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.base.Preconditions;

@Path("/user")
public class UserService extends NdexService {
	
//	private static final String GOOGLE_OAUTH_FLAG = "USE_GOOGLE_AUTHENTICATION";
//	private static final String GOOGLE_OATH_KEY = "GOOGLE_OATH_KEY";
	
	
//	static Logger logger = LoggerFactory.getLogger(UserService.class);

	/**************************************************************************
	 * Injects the HTTP request into the base class to be used by
	 * getLoggedInUser().
	 * 
	 * @param httpRequest
	 *            The HTTP request injected by RESTEasy's context.
	 **************************************************************************/
	public UserService(@Context HttpServletRequest httpRequest) {
		super(httpRequest);
	}
	
	/**************************************************************************
	 * Creates a user. (1.0-snapshot)
	 * 
	 * @param newUser
	 *            The user to create.
	 * @return The new user's profile.
	 * @throws Exception 
	 **************************************************************************/
	/*
	 * refactored to accommodate non-transactional database operations
	 */
	@GET
	@PermitAll
	@Path("/{userid}/verify/{verificationCode}")
	@NdexOpenFunction
//	@Produces("application/json")
	public String verifyUser(@PathParam("userid") String userUUID,
//					@PathParam("accountName") String accountName, 
					@PathParam("verificationCode") String verificationCode
				
			)
			throws Exception {

//		logger.info("[start: verifing User {}]", userUUID);
		
		try (UserDAO userdao = new UserDAO()){
			UUID userId = UUID.fromString(userUUID);
			String accountName = userdao.verifyUser(userId, verificationCode);
			User u = userdao.getUserById(userId, true,false);
			try (UserIndexManager mgr = new UserIndexManager() ) {
			mgr.addUser(userUUID, u.getUserName(), u.getFirstName(), u.getLastName(), u.getDisplayName(), u.getDescription());
			userdao.commit();
			}
		//	logger.info("[end: User {} verified ]", userUUID);
			return // userdao.getUserById(UUID.fromString(userUUID));
					"User account " + accountName + " has been activated."; 
		}
	}

	@POST
	@PermitAll
	@NdexOpenFunction
	@Produces("application/json")
	public User createUser(final User newUser)
			throws Exception {

		logger.info("[start: Creating User {}]", newUser.getUserName());
		
		if ( newUser.getUserName().indexOf(":")>=0) {
			logger.warn("[end: Failed to create user, account name can't contain \":\" in it]");
			throw new NdexException("User account name can't have \":\" in it.");
		}
		
		//verify against AD if AD authentication is defined in the configure file
		if( Configuration.getInstance().getUseADAuthentication()) {
			LDAPAuthenticator authenticator = BasicAuthenticationFilter.getLDAPAuthenticator();
			if (!authenticator.authenticateUser(newUser.getUserName(), newUser.getPassword()) ) {
				logger.error("[end: Unauthorized to create user {}. Throwing UnauthorizedOperationException.]", 
						newUser.getUserName());
				throw new UnauthorizedOperationException("Only valid AD users can have an account in NDEx.");
			}
			newUser.setPassword(Security.generateLongPassword());
			logger.info("[User is a authenticated by AD.]");
		}

		try (UserDAO userdao = new UserDAO()){
			
			newUser.setUserName(newUser.getUserName().toLowerCase());

			String needVerify = Configuration.getInstance().getProperty("VERIFY_NEWUSER_BY_EMAIL");

			String verificationCode =  ( needVerify !=null && needVerify.equalsIgnoreCase("true"))?
					Security.generateVerificationCode() : null;
			
			User user = userdao.createNewUser(newUser, verificationCode);
			
			if ( verificationCode == null) {
				UserIndexManager mgr = new UserIndexManager();
				mgr.addUser(user.getExternalId().toString(), user.getUserName(), user.getFirstName(), user.getLastName(), user.getDisplayName(), user.getDescription());
			}
			
			userdao.commit();
			

			if ( verificationCode != null ) {  // need to email the verification code.			
				
				//Reading in the email template
				String emailTemplate = Util.readFile(Configuration.getInstance().getNdexRoot() + "/conf/Server_notification_email_template.html");
				
				// construct the URL for the verification rest service:
				
				String protocal = this._httpRequest.getHeader("x-forwarded-proto");
				if ( protocal ==null)
					protocal = this._httpRequest.getScheme();
				
				String forwardedHost = this._httpRequest.getHeader("x-forwarded-host");
				if ( forwardedHost == null) {
					forwardedHost = this._httpRequest.getServerName();
					int port = this._httpRequest.getServerPort();
					forwardedHost += port + "/ndexbio-rest/";
				} else 
					forwardedHost += "/rest/";
				
				String restURL  = Configuration.getInstance().getHostURI()  + 
			            Configuration.getInstance().getRestAPIPrefix()+"/user/" + user.getExternalId().toString() 
						+ "/verification?verificationCode=" + verificationCode;
						
			
		        // Now set the actual message
		        String userNameStr = (user.getFirstName()!=null ? user.getFirstName(): "") + " "+ 
		        		  (user.getLastName() !=null ? user.getLastName() : "");
		        String messageBody = "Dear " + userNameStr + ",<br>" + 
		        		  	"Thank you for registering an NDEx account.\n" + 
		        		  	"Please click the link below to confirm your email address and start using NDEx now!<br>" +
		        		  	"You can also copy and paste the link in a new browser window. "+
		        		  	"Please note that you have 24 hours to complete the registration process.<br>" +

							restURL;
							
		        String htmlEmail = emailTemplate.replaceFirst("%%____%%", messageBody) ;
		        
		        AmazonSESMailSender.getInstance().sendEmail(newUser.getEmailAddress(), htmlEmail, "Verify Your New NDEx Account", "html");
				/*Email.sendHTMLEmailUsingLocalhost(Configuration.getInstance().getProperty("Feedback-Email"),
						newUser.getEmailAddress(),
						"Verify Your New NDEx Account",
						htmlEmail); */

				
				user.setExternalId(null);
			}	
			logger.info("[end: User {} created with UUID {}]", 
					newUser.getUserName(), user.getExternalId());
			return user;
		}
	}
	
	
	/**************************************************************************
	 * Gets a user by ID or accountName.(1.0 snapshot) 
	 * 
	 * @param userId
	 *            The ID or accountName of the user.

	 **************************************************************************/
	@GET
	@PermitAll
	@Path("/{userid}")
	@Produces("application/json")
	public User getUserV1(@PathParam("userid") @Encoded final String userId)
			throws IllegalArgumentException, NdexException, JsonParseException, JsonMappingException, SQLException, IOException {

	//	logger.info("[start: Getting user {}]", userId);
		
	/*	try (UserDAO dao = new UserDAO())  {
			try {
				final User user = dao.getUserByAccountName(userId.toLowerCase(),true);
				logger.info("[end: User object returned for user account {}]", userId);
				return user;
			} catch (ObjectNotFoundException e) {
				final User user = dao.getUserById(UUID.fromString(userId),true);
				logger.info("[end: User object returned for user account {}]", userId);
				return user;
			}
		}  */
		
		try (UserDAO dao = new UserDAO()) {
			try {
							
				UUID useruuid = UUID.fromString(userId);
				final User user = dao.getUserById(useruuid,true,false);
				logger.info("[end: User object returned for user account {}]", userId);
				return user;	
			} catch (IllegalArgumentException e) {
				final User user = dao.getUserByAccountName(userId.toLowerCase(),true,false);
				logger.info("[end: User object returned for user account {}]", userId);
				return user;
			}
		}  
	} 
	
	/**************************************************************************
	 * Gets a user by accountName. 
	 * 
	 * @param accountName
	 *            The accountName of the user.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws NdexException
	 *             Failed to change the password in the database.
	 * @throws SQLException 
	 * @throws IOException 
	 * @throws JsonMappingException 
	 * @throws JsonParseException 
	 **************************************************************************/
	@GET
	@PermitAll
	@Path("/account/{username}")
	@Produces("application/json")
	public User getUserByAccountName(@PathParam("username") @Encoded final String accountName)
			throws IllegalArgumentException, NdexException, SQLException, JsonParseException, JsonMappingException, IOException {

		logger.info("[start: Getting user by account name {}]", accountName);
		try (UserDAO dao = new UserDAO()){
			
			final User user = dao.getUserByAccountName(accountName.toLowerCase(),true,false);
			logger.info("[end: User object returned for user account {}]", accountName);
			return user;
		} 
		
	}
		
	/**************************************************************************
	 * Gets a user by UUID  
	 * 
	 * @param userId
	 *            The UUID of the user.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws NdexException
	 *             Failed to change the password in the database.
	 * @throws IOException 
	 * @throws SQLException 
	 * @throws JsonMappingException 
	 * @throws JsonParseException 
	 **************************************************************************/
	@SuppressWarnings("static-method")
	@GET
	@PermitAll
	@Path("/uuid/{userid}")
	@Produces("application/json")
	public User getUserByUUID(@PathParam("userid") @Encoded final String userId)
			throws IllegalArgumentException, NdexException, JsonParseException, JsonMappingException, SQLException, IOException {

		logger.info("[start: Getting user from UUID {}]", userId);
		
		try (UserDAO dao = new UserDAO() ){
			final User user = dao.getUserById(UUID.fromString(userId),true,false);
			logger.info("[end: User object returned for user uuid {}]", userId);
			return user;	
		} 
		
	}	
/*	
	@SuppressWarnings("static-method")
	@POST
	@PermitAll
	@Path("/users")
	@Produces("application/json")
	@ApiDoc("Return the user corresponding to user's UUID. Error if no such user is found.")
	public List<User> getUsersByUUIDs(
			List<String> userIdStrs)
			throws IllegalArgumentException, NdexException, JsonParseException, JsonMappingException, SQLException, IOException {

		logger.info("[start: Getting users from UUIDs]");
		
		try (UserDAO dao = new UserDAO() ){
			List<User> users = new LinkedList<>();
			for ( String uuidStr : userIdStrs) {
				User user = dao.getUserById(UUID.fromString(uuidStr),true);
				users.add(user);
			}
		    logger.info("[end: User object returned for user uuids]");
			return users;	
		} 
		
	}	*/
	
	/**************************************************************************
	 * Retrieves array of network membership objects
	 * 
	 * @param userId
	 *            The user ID.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws ObjectNotFoundException
	 *             The group doesn't exist.
	 * @throws NdexException
	 *             Failed to query the database.
	 * @throws SQLException 
	 * @throws IOException 
	 * @throws JsonMappingException 
	 * @throws JsonParseException 
	 **************************************************************************/
	
	@GET
	@Path("/network/{permission}/{start}/{size}")
	@Produces("application/json")
	
	//TODO: need to review the spec. Should we remove userID from URL or remove this function and replace with a getNetworksByOwner function?
	
	public List<Membership> getUserNetworkMemberships(
			@PathParam("permission") final String permissions ,
			@PathParam("start") int skipBlocks,
			@PathParam("size") int blockSize,
			@DefaultValue("false") @QueryParam("inclusive") boolean inclusive) throws NdexException, SQLException, JsonParseException, JsonMappingException, IllegalArgumentException, IOException {
		
		logger.info("[start: Getting {} networks ]", permissions);
		
		Permissions permission = Permissions.valueOf(permissions.toUpperCase());
		try (UserDAO dao = new UserDAO ()) {
			
			List<Membership> members= dao.getUserNetworkMemberships(getLoggedInUserId(), permission, skipBlocks, blockSize, inclusive );
			logger.info("[end: Returned {} members ]", members.size());			
			return members;
		} 
	}
	
	/**************************************************************************
	 * Retrieves array of group membership objects
	 * 
	 * @param userId
	 *            The user ID.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws ObjectNotFoundException
	 *             The group doesn't exist.
	 * @throws NdexException
	 *             Failed to query the database.
	 * @throws SQLException 
	 * @throws IOException 
	 * @throws JsonMappingException 
	 * @throws JsonParseException 
	 **************************************************************************/
	
	@GET
	@Path("/{userid}/group/{permission}/{start}/{size}")
	@Produces("application/json")
	public List<Membership> getUserGroupMemberships(
			@PathParam("userid")	final String userIdStr,
			@PathParam("permission") final String permissions ,
			@PathParam("start") int skipBlocks,
			@PathParam("size") int blockSize,
			@DefaultValue("false") @QueryParam("inclusive") boolean inclusive) 
					throws NdexException, SQLException, JsonParseException, JsonMappingException, IllegalArgumentException, IOException {

		logger.info("[start: Getting {} groups for user {}]", permissions, getLoggedInUser().getUserName());

		Permissions permission = Permissions.valueOf(permissions.toUpperCase());
		UUID userId = UUID.fromString(userIdStr);
		try (UserDAO dao = new UserDAO ()) {
			List<Membership> result =
					dao.getUserGroupMemberships(userId, permission, skipBlocks, blockSize, inclusive);
			logger.info("[end: Got {} group membership for user {}]", result.size(), getLoggedInUser().getUserName());
			return result;
		} 
	}



	
	/**************************************************************************
	 * Authenticates a user trying to login.
	 * 
	 * @param username
	 *            The AccountName.
	 * @param password
	 *            The password.
	 * @throws SecurityException
	 *             Invalid accountName or password.
	 * @throws NdexException
	 *             Can't authenticate users against the database.
	 * @return The authenticated user's information.
	 **************************************************************************/
	@GET
	@PermitAll
	@NdexOpenFunction
	@Path("/authenticate")
	@Produces("application/json")
	public UserV1 authenticateUser()
			throws UnauthorizedOperationException {
		
		logger.info("[start: Authenticate user from Auth header]");
       
		User u = this.getLoggedInUser(); 
		if ( u == null ) {
			logger.error("[end: Unauthorized user. Throwing UnauthorizedOperationException...]");
			throw new UnauthorizedOperationException("Unauthorized user.");
		}	
		
		logger.info("[end: user {} autenticated from Auth header]",  u.getUserName());
		return new UserV1(this.getLoggedInUser());
	} 
	


	/**************************************************************************
	 * Authenticates a user from Google OAuth openID Connect
	 * 
	 * @return JWT object to the client
	 * @throws NdexException 
	 * @throws IOException 
	 * @throws ClientProtocolException 
	 * @throws SQLException 
	 * @throws NoSuchAlgorithmException 
	 * @throws IllegalArgumentException 
	 **************************************************************************/
/*	@GET
	@PermitAll
	@NdexOpenFunction
	@Path("/google/authenticate")
	@Produces("application/json")
	@ApiDoc("Callback endpoint for Google OAuth OpenId Connect.")
	public String authenticateFromGoogle()
			throws NdexException, ClientProtocolException, IOException, IllegalArgumentException, NoSuchAlgorithmException, SQLException {
		
		logger.info("[start: Authenticate user using Google oauth endpoint]");

		GoogleOpenIDAuthenticator authenticator = BasicAuthenticationFilter.getGoogleOAuthAuthenticatior();
		if ( authenticator ==null ) {
			logger.error("[end: Unauthorized user from google. Server is not configure to support this.]");
			throw new UnauthorizedOperationException("Server is not configured to Support Google OAuth.");
		}
		
		String qStr = this._httpRequest.getQueryString();
   	    System.out.println(qStr);

 	    String theString =authenticator.getIDTokenFromQueryStr(qStr);
		return theString;
	}*/
	
	
/*	@POST
	@PermitAll
	@NdexOpenFunction
	@Path("/google/authenticate/renew")
	@Produces("application/json")
	public String renewGoogleToken(OAuthTokenRenewRequest renewRequest)
			throws NdexException, ClientProtocolException, IOException {
		
		logger.info("[start: renew Google access token by refresh token]");

		OAuthAuthenticator authenticator = getOAuthAuthenticator();
		if ( authenticator ==null ) {
			logger.error("[end: Unauthorized user from google. Server is not configure to support this.]");
			throw new UnauthorizedOperationException("Server is not configured to Support Google OAuth.");
		}
		
		//String qStr = this._httpRequest.getQueryString();
  
 	    String theString =authenticator.getNewAccessTokenByRefreshToken(
 	    		renewRequest.getAccessToken(), renewRequest.getRefreshToken());
 	    
		return theString;
	}
		

	@GET
	@PermitAll
	@NdexOpenFunction
	@Path("/google/authenticate/revoke/{accessToken}")
	@Produces("application/json")
	public void revokeGoogleAccessToken(@PathParam("accessToken") String accessToken)
			throws NdexException, ClientProtocolException, IOException {
		
		GoogleOpenIDAuthenticator authenticator = getOAuthAuthenticator();
		if ( authenticator ==null ) {
			logger.error("[end: Unauthorized user from google. Server is not configure to support this.]");
			throw new UnauthorizedOperationException("Server is not configured to Support Google OAuth.");
		}

		authenticator.revokeAccessToken(accessToken);
 	    
	}
	*/
	
	/**************************************************************************
	 * Changes a user's password.
	 * 
	 * @param userId
	 *            The user ID.
	 * @param password
	 *            The new password.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws NdexException
	 *             Failed to change the password in the database.
	 * @throws SQLException 
	 * @throws NoSuchAlgorithmException 
	 **************************************************************************/
	
/*	@POST
	@Path("/password")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces("application/json")
	@ApiDoc("Changes the authenticated user's password to the new password in the POST data.")
	public void changePassword(String password)
			throws IllegalArgumentException, NdexException, SQLException, NoSuchAlgorithmException {
		
		logger.info("[start: Changing password for user {}]", getLoggedInUser().getUserName() );
		
		if( Configuration.getInstance().getUseADAuthentication()) {
			logger.warn("[end: Changing password not allowed for AD authentication method]");
			throw new UnauthorizedOperationException("Emailing new password is not allowed when using AD authentication method");
		}
		
		Preconditions.checkArgument(!Strings.isNullOrEmpty(password), 
				"A password is required");
		
		try (UserDAO dao = new UserDAO ()) {
			dao.setNewPassword(getLoggedInUser().getUserName(),password);
			dao.commit();
			logger.info("[end: Password changed for user {}]", getLoggedInUser().getUserName());
		}
	} */


	/**************************************************************************
	 * Deletes a user.
	 * @throws Exception 
	 **************************************************************************/
	@DELETE
	@Produces("application/json")

	public void deleteUser() throws Exception {

	//	logger.info("[start: Deleting user (self).]");
		
		try (UserDAO dao = new UserDAO()) {
			dao.deleteUserById(getLoggedInUser().getExternalId());
			try (UserIndexManager mgr = new UserIndexManager()) {
				mgr.deleteUser(getLoggedInUser().getExternalId().toString());
				dao.commit();
			}
//			logger.info("[end: User {} deleted]", getLoggedInUser().getUserName());
		} 
	}


	/**************************************************************************
	 * Finds users based on the search parameters.
	 * 
	 * @param searchParameters
	 *            The search parameters.
	 * @throws Exception 
	 **************************************************************************/
	@POST
	@PermitAll
	@Path("/search/{start}/{size}")
	@Produces("application/json")
	
	public SolrSearchResult<User> findUsers(SimpleQuery simpleUserQuery, 
			@PathParam("start") final int skipBlocks, 
			@PathParam("size") final int blockSize)
			throws Exception {

		logger.info("[start: Searching user \"{}\"]", simpleUserQuery.getSearchString());
		
		try (UserDAO dao = new UserDAO ()){

			final SolrSearchResult<User> users = dao.findUsers(simpleUserQuery, skipBlocks, blockSize);
			
			logger.info("[end: Returning {} users from search]", users.getNumFound());			
			return users;
		} 
		
	}


	/**************************************************************************
	 * Updates a user.
	 * 
	 * @param updatedUser
	 *            The updated user information.
	 * @throws Exception 
	 **************************************************************************/
	@POST
	@Path("/{userid}")
	@Produces("application/json")
	public User updateUser(@PathParam("userid") final String userId, final User updatedUser)
			throws Exception {
		Preconditions.checkArgument(null != updatedUser, 
				"Updated user data are required");
		Preconditions.checkArgument(UUID.fromString(userId).equals(updatedUser.getExternalId()), 
				"UUID in updated user data doesn't match user ID in the URL.");
		Preconditions.checkArgument(updatedUser.getExternalId().equals(getLoggedInUser().getExternalId()), 
				"UUID in URL doesn't match the user ID of the signed in user's.");
		
		// Currently not using path param. We can already retrieve the id from the authentication
		// However, this depends on the authentication method staying consistent?

	//	logger.info("[start: Updating user {}]", updatedUser.getUserName());
		
		if ( Configuration.getInstance().getUseADAuthentication()) {
			if ( !updatedUser.getUserName().equals(getLoggedInUser().getUserName())) {
		//		logger.error("[end: Updating accountName is not allowed when NDEx server is running on AD authentication.]");
				throw new UnauthorizedOperationException(
						"Updating accountName is not allowed when NDEx server is running on AD authentication.");
			}
		}
		
		try (UserDAO dao = new UserDAO ()){
			User user = dao.updateUser(updatedUser, getLoggedInUser().getExternalId());
			try (UserIndexManager mgr = new UserIndexManager()) {
				mgr.updateUser(userId, user.getUserName(), user.getFirstName(), user.getLastName(),
						user.getDisplayName(), user.getDescription());
				dao.commit();
			}
	//		logger.info("[end: User {} updated]", updatedUser.getUserName());
			return user;
		} 
	}
	
	@GET
	@Path("/membership/group/{groupid}")
	@Produces("application/json")
	public Permissions getGroupMembership(
				@PathParam("groupid") final String groupId) 
			throws IllegalArgumentException, SQLException {

		logger.info("[start: Getting membership of account {}]", groupId);
		
		try (UserDAO dao = new UserDAO ()){
			
			Permissions m = dao.getUserMembershipTypeOnGroup(getLoggedInUserId(), UUID.fromString(groupId));
			
			if ( m==null)
			   logger.info("[end: No membership found.]" );			
			else 
			   logger.info("[end: Membership {} found.]", m.toString());
			return m;
		} 
	}
	
	@GET
	@Path("/membership/network/{networkid}/{directonly}")
	@Produces("application/json")
	public Permissions getNetworkMembership(@PathParam("networkid") final String networkId, 
				@PathParam("directonly") final boolean directOnly) 
			throws IllegalArgumentException, ObjectNotFoundException, NdexException, SQLException {

		
		try (UserDAO dao = new UserDAO ()){
			Permissions m = dao.getLoggedInUserPermissionOnNetwork(getLoggedInUserId(), UUID.fromString(networkId), directOnly);
		
			return m;
		} 
	}
	
	
	@GET
	@Path("/request/{start}/{size}")
	@Produces("application/json")
	public List<Request> getSentRequest(@PathParam("start") int skipBlocks,
			@PathParam("size") int blockSize) throws SQLException, JsonParseException, JsonMappingException, IOException {
		
		try (RequestDAO dao = new RequestDAO ()){
			List<Request> reqs= dao.getSentRequestByUserId(this.getLoggedInUserId(),null,skipBlocks, blockSize);
			return reqs;
		}
	}
	
	@GET
	@Path("/request/pending/{start}/{size}")
	@Produces("application/json")
	public List<Request> getPendingRequests(
			@PathParam("start") int skipBlocks,
			@PathParam("size") int blockSize) throws SQLException, JsonParseException, JsonMappingException, IOException {
		
		try (RequestDAO dao = new RequestDAO ()){
			List<Request> reqs= dao.getPendingRequestByUserId(this.getLoggedInUserId(),skipBlocks, blockSize);
			return reqs;
		} 
	}
	

	@GET
	@Path("/task/{status}/{start}/{size}")
	@Produces("application/json")
	public List<Task> getTasks(

			@PathParam("status") final String status,
			@PathParam("start") int skipBlocks,
			@PathParam("size") int blockSize) throws SQLException, JsonParseException, JsonMappingException, IOException {

		
		try (TaskDAO dao = new TaskDAO ()){
			Status taskStatus = Status.valueOf(status);
			List<Task> tasks= dao.getTasksByUserId(this.getLoggedInUser().getExternalId(),taskStatus, skipBlocks, blockSize);
			return tasks;
		} 
	}

}
