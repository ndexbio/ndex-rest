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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.apache.solr.client.solrj.SolrServerException;
import org.ndexbio.common.models.dao.postgresql.GroupDAO;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.models.dao.postgresql.NetworkSetDAO;
import org.ndexbio.common.models.dao.postgresql.RequestDAO;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.common.solr.UserIndexManager;
import org.ndexbio.common.util.Util;
import org.ndexbio.model.exceptions.DuplicateObjectException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.MembershipRequest;
import org.ndexbio.model.object.NetworkSet;
import org.ndexbio.model.object.PermissionRequest;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.Request;
import org.ndexbio.model.object.RequestType;
import org.ndexbio.model.object.ResponseType;
import org.ndexbio.model.object.User;
import org.ndexbio.model.object.network.NetworkIndexLevel;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.annotations.ApiDoc;
import org.ndexbio.rest.filters.BasicAuthenticationFilter;
import org.ndexbio.rest.helpers.AmazonSESMailSender;
import org.ndexbio.rest.helpers.Security;
import org.ndexbio.security.LDAPAuthenticator;
import org.ndexbio.task.NdexServerQueue;
import org.ndexbio.task.SolrIndexScope;
import org.ndexbio.task.SolrTaskRebuildNetworkIdx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

@Path("/v2/user")
public class UserServiceV2 extends NdexService {
	
//	private static final String GOOGLE_OAUTH_FLAG = "USE_GOOGLE_AUTHENTICATION";
//	private static final String GOOGLE_OATH_KEY = "GOOGLE_OATH_KEY";
	
	
	static Logger logger = LoggerFactory.getLogger(UserService.class);
	

	/**************************************************************************
	 * Injects the HTTP request into the base class to be used by
	 * getLoggedInUser().
	 * 
	 * @param httpRequest
	 *            The HTTP request injected by RESTEasy's context.
	 **************************************************************************/
	public UserServiceV2(@Context HttpServletRequest httpRequest) {
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
	@Path("/{userid}/verification")
	@NdexOpenFunction
	@Produces("text/plain")
	@ApiDoc("Verify the given user with UUID and verificationCode")
	public String verifyUser(@PathParam("userid") String userUUID,
					@QueryParam("verificationCode") String verificationCode		
			)
			throws Exception {

	//	logger.info("[start: verifing User {}]", userUUID);
		if ( verificationCode == null || verificationCode.length()== 0) 
			throw new IllegalArgumentException("Parameter verificationCode can not be empty");
		
		try (UserDAO userdao = new UserDAO()){
			UUID userId = UUID.fromString(userUUID);
			String accountName = userdao.verifyUser(userId, verificationCode);
			User u = userdao.getUserById(userId, true,false);
			try (UserIndexManager mgr = new UserIndexManager()) {
				mgr.addUser(userUUID, u.getUserName(), u.getFirstName(), u.getLastName(), u.getDisplayName(),
						u.getDescription());
				userdao.commit();
				// logger.info("[end: User {} verified ]", userUUID);
				return // userdao.getUserById(UUID.fromString(userUUID));
				"User account " + accountName + " has been activated.";
			}
		}
	}

	@POST
	@PermitAll
	@NdexOpenFunction
	@Produces("text/plain")
	@ApiDoc("Create a new user based on a JSON object specifying username, password, and emailAddress, returns the new user - including its internal id. "
			+ "Username and emailAddress must be unique in the database. If email verification is turned on on the server, the user uuid field will be set to null.")
	public Response createUser(final User newUser)
			throws Exception {

//		logger.info("[start: Creating User {}]", newUser.getUserName());
		
		if ( newUser.getUserName().indexOf(":")>=0) {
//			logger.warn("[end: Failed to create user, account name can't contain \":\" in it]");
			throw new NdexException("User account name can't have \":\" in it.");
		}
		
		//verify against AD if AD authentication is defined in the configure file
		if( Configuration.getInstance().getUseADAuthentication()) {
			LDAPAuthenticator authenticator = BasicAuthenticationFilter.getLDAPAuthenticator();
			if (!authenticator.authenticateUser(newUser.getUserName(), newUser.getPassword()) ) {
//				logger.error("[end: Unauthorized to create user {}. Throwing UnauthorizedOperationException.]", 
//						newUser.getUserName());
				throw new UnauthorizedOperationException("Only valid AD users can have an account in NDEx.");
			}
			newUser.setPassword(Security.generateLongPassword());
//			logger.info("[User is a authenticated by AD.]");
		}

		try (UserDAO userdao = new UserDAO()){
			
			newUser.setUserName(newUser.getUserName().toLowerCase());

			String needVerify = Configuration.getInstance().getProperty("VERIFY_NEWUSER_BY_EMAIL");

			String verificationCode =  ( needVerify !=null && needVerify.equalsIgnoreCase("true"))?
					Security.generateVerificationCode() : null;
			
			User user = userdao.createNewUser(newUser, verificationCode);
			
			if ( verificationCode == null) {
				try (UserIndexManager mgr = new UserIndexManager()) {
					mgr.addUser(user.getExternalId().toString(), user.getUserName(), user.getFirstName(),
							user.getLastName(), user.getDisplayName(), user.getDescription());
				}
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
						
						
					/*	protocal + "://" + forwardedHost+ "user/" + user.getExternalId().toString() 
						+ "/verify/" + verificationCode; */
						
			
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
			/*	Email.sendHTMLEmailUsingLocalhost(Configuration.getInstance().getProperty("Feedback-Email"),
						newUser.getEmailAddress(),
						"Verify Your New NDEx Account",
						htmlEmail); */

				
				user.setExternalId(null);
			}	
	//		logger.info("[end: User {} created with UUID {}]", 
	//				newUser.getUserName(), user.getExternalId());
			
			if ( user.getExternalId() != null) {
			  URI l = new URI (Configuration.getInstance().getHostURI()  + 
			            Configuration.getInstance().getRestAPIPrefix()+"/user/"+ user.getExternalId());

			  return Response.created(l).entity(l).build();
			} 
			
			String url = Configuration.getInstance().getHostURI()  + 
		            Configuration.getInstance().getRestAPIPrefix()+"/user?username=" + URLEncoder.encode(newUser.getUserName().toLowerCase(), "UTF-8");
			return Response.accepted().location(new URI (url)).build();
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
	@Path("")
	@Produces("application/json")
	@ApiDoc("Return the user corresponding to the given user account name. Error if this account is not found.")
	public User getUserByAccountNameOrAuthenticatUser(
			@QueryParam("username") /*@Encoded*/ final String accountName,
			@QueryParam("valid") final String booleanStr
			)
			throws IllegalArgumentException, NdexException, SQLException, JsonParseException, JsonMappingException, IOException {

		if ( booleanStr!=null) {
			if ( !booleanStr.toLowerCase().equals("true"))
				throw new IllegalArgumentException("Paramber valid can only be true.");
			return authenticateUser();
		}
		
	//	logger.info("[start: Getting user by account name {}]", accountName);
		if ( accountName == null || accountName.length() == 0)
			throw new IllegalArgumentException("parameter username is required in the URL.");
		
		try (UserDAO dao = new UserDAO()){
			
			final User user = dao.getUserByAccountName(accountName.toLowerCase(),false,
					getLoggedInUser() !=null && getLoggedInUser().getUserName().equalsIgnoreCase(accountName));
	//		logger.info("[end: User object returned for user account {}]", accountName);
			return user;
		} 
		
	}
		
	/**************************************************************************
	 * Gets a user by UUID  
	 * 
	 * @param userId
	 *            The UUID of the user.

	 **************************************************************************/
	@GET
	@PermitAll
	@Path("/{userid}")
	@Produces("application/json")
	@ApiDoc("Return the user corresponding to user's UUID. Error if no such user is found.")
	public User getUserByUUID(@PathParam("userid") final String userIdStr)
			throws IllegalArgumentException, NdexException, JsonParseException, JsonMappingException, SQLException, IOException {

//		logger.info("[start: Getting user from UUID {}]", userId);
		
		UUID userUUID = UUID.fromString(userIdStr);
		if ( getLoggedInUserId() != null && getLoggedInUserId().equals(userUUID))
			return getLoggedInUser();
		
		try (UserDAO dao = new UserDAO() ){
			final User user = dao.getUserById(userUUID,true,false);
//			logger.info("[end: User object returned for user uuid {}]", userId);
			return user;	
		} 
		
	}	
	

	private User authenticateUser()
			throws UnauthorizedOperationException {
		
	//	logger.info("[start: Authenticate user from Auth header]");
       
		User u = this.getLoggedInUser(); 
		if ( u == null ) {
//			logger.error("[end: Unauthorized user. Throwing UnauthorizedOperationException...]");
			throw new UnauthorizedOperationException("Unauthorized user.");
		}	
		
//		logger.info("[end: user {} autenticated from Auth header]",  u.getUserName());
		return this.getLoggedInUser();
	}
	


	
	/**************************************************************************
	 * Changes a user's password.
	 * 
	 * @param userId
	 *            The user ID.
	 * @param password
	 *            The new password.
	 * @throws Exception 

	 **************************************************************************/
	
	@PUT
	@Path("/{userid}/password")
//	@Consumes(MediaType.APPLICATION_JSON)
	@PermitAll
	@Produces("application/json")
	@ApiDoc("Changes the authenticated user's password to the new password in the POST data.")
	public void changePassword(
				@PathParam("userid") final String userId,
				@QueryParam("forgot") String booleanStr,
				String password)
			throws Exception {
		
		
//		logger.info("[start: Changing password for user {}]", getLoggedInUser().getUserName() );
		
		if( Configuration.getInstance().getUseADAuthentication()) {
			logger.warn("[end: Changing password not allowed for AD authentication method]");
			throw new UnauthorizedOperationException("Emailing new password is not allowed when using AD authentication method");
		}
		
		UUID userUUID = UUID.fromString(userId);

		if ( booleanStr == null) {
			UUID loggedInUserId = getLoggedInUserId() ;
			if (loggedInUserId == null)
				throw new UnauthorizedOperationException("Only authenticated users can their change passwords.");
			if (!userUUID.equals(loggedInUserId))
				throw new UnauthorizedOperationException("Updating other user's password is not allowed.");
			Preconditions.checkArgument(!Strings.isNullOrEmpty(password), 
					"A password is required");
			
			try (UserDAO dao = new UserDAO ()) {
				dao.setNewPassword(loggedInUserId,password);
				dao.commit();
			}
		} else  {
			if ( !booleanStr.toLowerCase().equals("true"))
				throw new IllegalArgumentException("Value of paramter forgot can only be true.");
			emailNewPassword(userUUID);
			return;
		}

		
	}


	/**************************************************************************
	 * Deletes a user.
	 * @throws Exception 
	 **************************************************************************/
	@DELETE
	@Path("/{userid}")
	@Produces("application/json")
	@ApiDoc("Deletes the authenticated user. Errors if the user administrates any group or network. Should remove any other objects depending on the user. "
			+ "If this operation orphans a network or group, an exception will be thrown.")
	public void deleteUser(@PathParam("userid") final String userIdStr)
			throws Exception {

		UUID userId = UUID.fromString(userIdStr);
		if ( !userId.equals(getLoggedInUserId()) )
			throw new NdexException ("An authenticated user can only delete himself.");
		try (UserDAO dao = new UserDAO()) {
			dao.deleteUserById(getLoggedInUser().getExternalId());
			try (UserIndexManager mgr = new UserIndexManager()) {
			mgr.deleteUser(getLoggedInUser().getExternalId().toString());
			dao.commit();
	//		logger.info("[end: User {} deleted]", getLoggedInUser().getUserName());
			}
		} 
	}

	/**************************************************************************
	 * Emails the user a new randomly generated password.
	 * 
	 * @param user
	 *            should be the current authenticated user.
	 * @throws Exception 
	 **************************************************************************/

	private static void emailNewPassword( UUID userId)
			throws Exception {

	//	logger.info("[start: Email new password for {}]", user.getUserName());
		
		if( Configuration.getInstance().getUseADAuthentication()) {
			logger.warn("[end: Emailing new password is not allowed for AD authentication method]");
			throw new UnauthorizedOperationException("Emailing new password is not allowed when using AD authentication method");
		}
	
		try (UserDAO dao = new UserDAO ()){

		//	User authUser = dao.getUserById(userId, true);
			String newPasswd = dao.setNewPassword(userId,null);

			dao.commit();
			
			User u = dao.getUserById(userId, true,false);
			
	        AmazonSESMailSender.getInstance().sendEmail(u.getEmailAddress(),
	        		"Your new password is:" + newPasswd, "Your NDEx Password Has Been Reset", "html");

	        // this is the old method using local host. We are suing AmazonSES now. For enterprise users, we need to find out how to send emails.
			/*
			Email.sendHTMLEmailUsingLocalhost(Configuration.getInstance().getProperty("Forgot-Password-Email"), 
					user.getEmailAddress(), 
					"Your NDEx Password Has Been Reset", 
					"Your new password is:" + newPasswd); */

	//		logger.info("[end: Emailed new password to {}]", user.getUserName());
		}
	}



	/**************************************************************************
	 * Updates a user.
	 * 
	 * @param updatedUser
	 *            The updated user information.
	 * @throws Exception 

	 **************************************************************************/
	@PUT
	@Path("/{userid}")
	@Produces("application/json")

	public void updateUser(@PathParam("userid") final String userId, final User updatedUser)
			throws Exception {
		Preconditions.checkArgument(null != updatedUser, 
				"Updated user data are required");
		Preconditions.checkArgument(UUID.fromString(userId).equals(updatedUser.getExternalId()), 
				"UUID in updated user data doesn't match user ID in the URL.");
		Preconditions.checkArgument(updatedUser.getExternalId().equals(getLoggedInUserId()), 
				"UUID in URL doesn't match the user ID of the signed in user's.");
		Preconditions.checkArgument(null != updatedUser.getEmailAddress() && 
				   updatedUser.getEmailAddress().length()>=6, 
				"A valid email address is required.");
		
		
		if ( Configuration.getInstance().getUseADAuthentication()) {
			if ( !updatedUser.getUserName().equals(getLoggedInUser().getUserName())) {
				throw new UnauthorizedOperationException(
						"Updating accountName is not allowed when NDEx server is running on AD authentication.");
			}
		}
		
		try (UserDAO dao = new UserDAO ()){
			User user = dao.updateUser(updatedUser, getLoggedInUser().getExternalId());
			try (UserIndexManager mgr = new UserIndexManager()) {
			mgr.updateUser(userId, user.getUserName(), user.getFirstName(), user.getLastName(), user.getDisplayName(), user.getDescription());
			dao.commit();
	//		logger.info("[end: User {} updated]", updatedUser.getUserName());
			}
		} 
	}
	
	@GET
	@Path("/{userid}/membership")
	@Produces("application/json")
	@ApiDoc("Returns the group membership information of a user.")
	public Map<String,String> getMembershipInfo(
				@PathParam("userid") final String userIdStr,
			    @QueryParam("groupid") String groupIdStr,
			    @QueryParam("type") String membershipType,
				@DefaultValue("0") @QueryParam("start") int skipBlocks,
				@DefaultValue("100") @QueryParam("size") int blockSize
				) 
			throws IllegalArgumentException, SQLException, NdexException {

		UUID userId = UUID.fromString(userIdStr);
		
		if ( groupIdStr != null) {
			UUID groupId = UUID.fromString(groupIdStr);

			try (UserDAO dao = new UserDAO()) {
				Map<String, String> result = new TreeMap<>();
				Permissions m = dao.getUserMembershipTypeOnGroup(userId, groupId);

				result.put(groupId.toString(), m.toString());
				return result;
			}
		}
		
		Permissions permission = null; // Permissions.MEMBER;
		if ( membershipType != null) {
			permission = Permissions.valueOf(membershipType.toUpperCase());
		}
		
		try (UserDAO dao = new UserDAO ()) {
			Map<String,String> result =
					dao.getUserGroupMembershipMap(userId, permission, skipBlocks, blockSize);
		//	logger.info("[end: Got {} group membership for user {}]", result.size(), getLoggedInUser().getUserName());
			return result;
		} 
	}
	
	/* to be deleted. This is a function specific for web-app. It seems that it is no-longer needed by web-app */
	@Deprecated
	@GET
	@Path("/{userid}/permission")
	@Produces("application/json")
	@ApiDoc("Get the type of permission the logged in user has on the given network. If directOnly is set to true, permissions grant through groups are not included in the result.")
	public Map<String,String> getNetworkPermissionInfo(
			@PathParam("userid") final String userIdStr,
		    @QueryParam("networkid") String networkIdStr,
		    @QueryParam("permission") String permissionType,
			@DefaultValue("0") @QueryParam("start") int skipBlocks,
			@DefaultValue("100") @QueryParam("size") int blockSize,
			@DefaultValue("false") @QueryParam("directOnly") final boolean directOnly) 
			throws IllegalArgumentException, ObjectNotFoundException, NdexException, SQLException {

		logger.info("[start: Getting membership of account {} on {}]", getLoggedInUser().getUserName(), networkIdStr);
		UUID userId = UUID.fromString(userIdStr);
		if ( !userId.equals(getLoggedInUserId()))
			throw new NdexException("Checking other user's network permission is not allowed.");
		
		if ( networkIdStr != null) {
			UUID networkId = UUID.fromString(networkIdStr);
			
			Map<String,String> result = new TreeMap<>();
			try (UserDAO dao = new UserDAO ()){
				Permissions m = dao.getLoggedInUserPermissionOnNetwork(userId, networkId, directOnly);
				if ( m!=null)
					result.put(networkId.toString(), m.name());			
				return result;
			} 
		}
		
		boolean inclusive = true;
		Permissions permission = Permissions.READ;
		if ( permissionType !=null )
			permission = Permissions.valueOf(permissionType);
		
		try (UserDAO dao = new UserDAO ()) {
			Map<String,String> members= dao.getUserNetworkPermissionMap(userId, permission, skipBlocks, blockSize, inclusive, directOnly);
			return members;
		} 	
	}
	
/*
 * requests and responses
 * 
 */
	
	   @POST
	   @Path("/{userid}/membershiprequest")
	   @Produces("text/plain")
	   @ApiDoc("Create a group membership request.")
	    public Response createMembershipRequest(
	    		@PathParam("userid") final String userIdStr,
	    		final MembershipRequest newRequest) 
	    		throws IllegalArgumentException, DuplicateObjectException, NdexException, SQLException, JsonParseException, JsonMappingException, IOException {

			if ( newRequest.getGroupid() == null)
					throw new NdexException("Groupid is required in the Posted object.");
		
	//		logger.info("[start: Creating membership request for {}]", getLoggedInUserId());
			UUID userId = UUID.fromString(userIdStr);
			
			if ( !userId.equals(getLoggedInUserId()))
				throw new NdexException ("Creating request for other users are not allowed.");
			
			try (RequestDAO dao = new RequestDAO ()){	
				
				Request r = new Request(newRequest);
				r.setSourceUUID(userId);
				Request request = dao.createRequest(r, this.getLoggedInUser());
				dao.commit();
				URI l = new URI (Configuration.getInstance().getHostURI()  + 
			            Configuration.getInstance().getRestAPIPrefix()+"/user/"+ userId.toString() + "/membershiprequest/"+
						request.getExternalId());

				return Response.created(l).entity(l).build();
			} catch (URISyntaxException e) {
				throw new NdexException ("Failed to create location URL: " + e.getMessage(), e);
			} 
	    	
	    }
	   
	   
	   

	   @POST
	   @Path("/{userid}/permissionrequest")
	   @Produces("text/plain")
	    public Response createPermissionRequest(
	    		@PathParam("userid") final String userIdStr,
	    		final PermissionRequest newRequest) 
	    		throws IllegalArgumentException, DuplicateObjectException, NdexException, SQLException, JsonParseException, JsonMappingException, IOException {

			if ( newRequest.getNetworkid() == null)
					throw new NdexException("Networkid is required in the Posted object.");
			if ( newRequest.getPermission() == null)
				throw new NdexException("permission is required in the Posted object.");

			
			logger.info("[start: Creating request for {}]", newRequest.getNetworkid());
			UUID userId = UUID.fromString(userIdStr);
			
			if ( !userId.equals(getLoggedInUserId()))
				throw new NdexException ("Creating request for other users are not allowed.");
			
			try (RequestDAO dao = new RequestDAO ()){	
				
				Request r = new Request(RequestType.UserNetworkAccess, newRequest);
				r.setSourceUUID(userId);
				Request request = dao.createRequest(r, this.getLoggedInUser());
				dao.commit();
				URI l = new URI (Configuration.getInstance().getHostURI()  + 
			            Configuration.getInstance().getRestAPIPrefix()+"/user/"+ userId.toString() + "/permissionrequest/"+
						request.getExternalId());

				return Response.created(l).entity(l).build();
			} catch (URISyntaxException e) {
				throw new NdexException ("Failed to create location URL: " + e.getMessage(), e);
			} finally {
				logger.info("[end: Request created]");
			}
	    	
	    }
	   
	   
	   	@GET
		@Path("/{userid}/permissionrequest/{requestid}")
		@Produces("application/json")
		@ApiDoc("")
		public Request getPermissionRequestById(@PathParam("userid") String userIdStr,
				@PathParam("requestid") String requestIdStr) throws NdexException, SQLException, JsonParseException, JsonMappingException, IllegalArgumentException, IOException {

			logger.info("[start: Getting requests sent by user {}]", getLoggedInUser().getUserName());
			
			UUID userId = UUID.fromString(userIdStr);
			UUID requestId = UUID.fromString(requestIdStr);
			
			if ( !userId.equals(getLoggedInUserId()))
				throw new UnauthorizedOperationException("Accessing other user's requests is not allowed.");
			
			
			try (RequestDAO dao = new RequestDAO ()){
				Request reqs= dao.getRequest(requestId, getLoggedInUser());
				logger.info("[end: Returning request]");
				return reqs;
			}
		}
		
	
	   	@GET
		@Path("/{userid}/permissionrequest")
		@Produces("application/json")
		@ApiDoc("")
		public List<Request> getPermissionRequests (
				 @PathParam("userid") String userIdStr,
				  @QueryParam("type") String queryType
				) throws NdexException, SQLException, JsonParseException, JsonMappingException, IOException {

			logger.info("[start: Getting requests sent by user {}]", getLoggedInUser().getUserName());
			String qT = null;
			if ( queryType !=null) {
				qT = queryType.toLowerCase();
				if ( !qT.equals("sent") && !qT.equals("received"))
					throw new NdexException ("Type parameter of this function can only be 'sent' or 'received'.");
			}
			
			UUID userId = UUID.fromString(userIdStr);
			if ( !userId.equals(getLoggedInUserId()))
				throw new UnauthorizedOperationException("Accessing other user's requests is not allowed.");
			
			try (RequestDAO dao = new RequestDAO ()){
				if (qT == null) {
					List<Request> reqs= dao.getSentRequestByUserId(this.getLoggedInUserId(), RequestType.AllNetworkAccess,0, -1);
					List<Request> reqs2= dao.getPendingNetworkAccessRequestByUserId(this.getLoggedInUserId(),0, -1);
					 reqs.addAll(reqs2);
					 return reqs;

				}
				
				List<Request> reqs;
				if ( qT.equals("sent")) {
					 reqs= dao.getSentRequestByUserId(this.getLoggedInUserId(),RequestType.AllNetworkAccess,0, -1);
				} else {
					reqs= dao.getPendingNetworkAccessRequestByUserId(this.getLoggedInUserId(),0, -1);
				}
				
				return reqs;

			}
		}   
	   	

	   	@GET
		@Path("/{userid}/membershiprequest")
		@Produces("application/json")
		@ApiDoc("")
		public List<Request> getMembershipRequests (
				 @PathParam("userid") String userIdStr,
				  @QueryParam("type") String queryType
				) throws NdexException, SQLException, JsonParseException, JsonMappingException, IOException {

			logger.info("[start: Getting requests sent by user {}]", getLoggedInUser().getUserName());
			String qT = null;
			if ( queryType !=null) {
				qT = queryType.toLowerCase();
				if ( !qT.equals("sent") && !qT.equals("received"))
					throw new NdexException ("Type parameter of this function can only be 'sent' or 'received'.");
			}
			
			UUID userId = UUID.fromString(userIdStr);
			if ( !userId.equals(getLoggedInUserId()))
				throw new UnauthorizedOperationException("Accessing other user's requests is not allowed.");
			
			try (RequestDAO dao = new RequestDAO ()){
				if (qT == null) {
					List<Request> reqs= dao.getSentRequestByUserId(this.getLoggedInUserId(), RequestType.JoinGroup,0, -1);
					List<Request> reqs2= dao.getPendingGroupMembershipRequestByUserId(this.getLoggedInUserId(),0, -1);
					 reqs.addAll(reqs2);
					 return reqs;

				}
				
				List<Request> reqs;
				if ( qT.equals("sent")) {
					 reqs= dao.getSentRequestByUserId(this.getLoggedInUserId(),RequestType.JoinGroup,0, -1);
				} else {
					reqs= dao.getPendingGroupMembershipRequestByUserId(this.getLoggedInUserId(),0, -1);
				}
				
				return reqs;

			}
		}   
	   	
	   	
	   	@GET
			@Path("/{userid}/membershiprequest/{requestid}")
			@Produces("application/json")
			@ApiDoc("")
			public Request getMembershipRequestById(
					 @PathParam("userid") String userIdStr,
					 @PathParam("requestid") String requestIdStr
					) throws NdexException, SQLException, JsonParseException, JsonMappingException, IllegalArgumentException, IOException {

				logger.info("[start: Getting requests sent by user {}]", getLoggedInUser().getUserName());
				
				UUID userId = UUID.fromString(userIdStr);
				if ( !userId.equals(getLoggedInUserId()))
					throw new UnauthorizedOperationException("Accessing other user's requests is not allowed.");
				
				UUID requestId = UUID.fromString(requestIdStr);
				
				try (RequestDAO dao = new RequestDAO ()){
					Request reqs= dao.getRequest(requestId, getLoggedInUser());
					logger.info("[end: Returning request]");
					return reqs;
				}
				
			}   
	   	
	   	@PUT
		@Path("/{userid}/membershiprequest/{requestid}")
		@Produces("application/json")
		@ApiDoc("")
		public void respondMembershipRequest(
				 @PathParam("userid") String userIdStr,
				 @PathParam("requestid") String requestIdStr,
				 @QueryParam("action")  String action,
				 @QueryParam("message") String message
				) throws NdexException, SQLException, JsonParseException, JsonMappingException, IllegalArgumentException, IOException {

			logger.info("[start: Getting requests sent by user {}]", getLoggedInUser().getUserName());
			
			UUID userId = UUID.fromString(userIdStr);
			if ( !userId.equals(getLoggedInUserId()))
				throw new UnauthorizedOperationException("Accessing other user's requests is not allowed.");
			
			UUID requestId = UUID.fromString(requestIdStr);
			
			if ( action == null)
				throw new NdexException("Action parameter is required.");
			String act = action.toLowerCase();
			if ( !act.equals("accept") && !act.equals("deny"))
				throw new NdexException("Value of action parameter can only be 'accept' or 'deny'.");
			
			Request reqs;
			try (RequestDAO dao = new RequestDAO ()){
				reqs= dao.getRequest(requestId, getLoggedInUser());
			}
			
			if ( reqs.getRequestType() != RequestType.JoinGroup) {
				throw new NdexException("This request is not a membership request.");
			}
			
			// check if user is the group admin
			try ( GroupDAO gdao = new GroupDAO()) {
				if (!gdao.isGroupAdmin(reqs.getDestinationUUID(), getLoggedInUserId()))
					throw new UnauthorizedOperationException("Authenticated user is not an admin of the group.");			
			}
			
			// act on it
			reqs.setResponseMessage(message);
			if ( act.equals("accept")) {
				reqs.setResponse(ResponseType.ACCEPTED);
				try ( GroupDAO gdao = new GroupDAO()) {
					gdao.updateMember(reqs.getDestinationUUID(), reqs.getSourceUUID(), reqs.getPermission(), getLoggedInUserId());
					gdao.commit();
				}
			} else {
				reqs.setResponse(ResponseType.DECLINED);
			}
				
			try (RequestDAO dao = new RequestDAO ()){
				dao.updateRequest(requestId,reqs, getLoggedInUser());	
				dao.commit();
			}
			
			
		}   
	   	
	   	
	   	@PUT
		@Path("/{userid}/permissionrequest/{requestid}")
		@Produces("application/json")
		@ApiDoc("")
		public void respondPermissionRequest(
				 @PathParam("userid") String userIdStr,
				 @PathParam("requestid") String requestIdStr,
				 @QueryParam("action")  String action,
				 @QueryParam("message") String message
				) throws NdexException, SQLException, IOException {

			logger.info("[start: Getting requests sent by user {}]", getLoggedInUser().getUserName());
			
			UUID userId = UUID.fromString(userIdStr);
			if ( !userId.equals(getLoggedInUserId()))
				throw new UnauthorizedOperationException("Accessing other user's requests is not allowed.");
			
			UUID requestId = UUID.fromString(requestIdStr);
			
			if ( action == null)
				throw new NdexException("Action parameter is required.");
			String act = action.toLowerCase();
			if ( !act.equals("accept") && !act.equals("deny"))
				throw new NdexException("Value of action parameter can only be 'accept' or 'deny'.");
			
			Request reqs;
			try (RequestDAO dao = new RequestDAO ()){
				reqs= dao.getRequest(requestId, getLoggedInUser());
			}
			
			if ( reqs.getRequestType() == RequestType.JoinGroup) {
				throw new NdexException("This request is not a permission request.");
			}
			
			// check if user is the admin of network
			try ( NetworkDAO ndao = new NetworkDAO()) {
				if (!ndao.isAdmin(reqs.getDestinationUUID(), getLoggedInUserId()))
					throw new UnauthorizedOperationException("Authenticated user is not an admin of the network.");			
			}
			
			// act on it
			reqs.setResponseMessage(message);
			if ( act.equals("accept")) {
				reqs.setResponse(ResponseType.ACCEPTED);
				try ( NetworkDAO ndao = new NetworkDAO()) {
					if( reqs.getRequestType() == RequestType.UserNetworkAccess)
						ndao.grantPrivilegeToUser(reqs.getDestinationUUID(), reqs.getSourceUUID(), reqs.getPermission());
					else 
						ndao.grantPrivilegeToGroup(reqs.getDestinationUUID(), reqs.getSourceUUID(), reqs.getPermission());
					ndao.commit();
					
					// update the solr Index
					NetworkIndexLevel idxLvl = ndao.getIndexLevel(reqs.getDestinationUUID());
					if(idxLvl != NetworkIndexLevel.NONE) {
						ndao.setFlag(reqs.getDestinationUUID(), "iscomplete", false);
						ndao.commit();
						NdexServerQueue.INSTANCE.addSystemTask(new SolrTaskRebuildNetworkIdx(reqs.getDestinationUUID(),SolrIndexScope.global,false,null,idxLvl));
					}
				}
			} else {
				reqs.setResponse(ResponseType.DECLINED);
			}
				
			try (RequestDAO dao = new RequestDAO ()){
				dao.updateRequest(requestId,reqs, getLoggedInUser());	
				dao.commit();
			}
			
			
		}   
	   	
	   	
	   	@DELETE
		@Path("/{userid}/membershiprequest/{requestid}")
		@Produces("application/json")
		@ApiDoc("")
		public void deleteMembershipRequestById(
					 @PathParam("userid") String userIdStr,
					 @PathParam("requestid") String requestIdStr
					) throws NdexException, SQLException {

				logger.info("[start: Deleting requests sent by user {}]", getLoggedInUser().getUserName());
				
				UUID userId = UUID.fromString(userIdStr);
				if ( !userId.equals(getLoggedInUserId()))
					throw new UnauthorizedOperationException("Accessing other user's requests is not allowed.");
				
				UUID requestId = UUID.fromString(requestIdStr);
				
				try (RequestDAO dao = new RequestDAO()) {
					
					dao.deleteMembershipRequest(requestId, this.getLoggedInUserId());
					dao.commit();
				} finally {
					logger.info("[end: Request deleted]");
				}
				
			}   

	
	   	@DELETE
		@Path("/{userid}/permissionrequest/{requestid}")
		@Produces("application/json")
		@ApiDoc("")
		public void deletePermissionRequestById(
					 @PathParam("userid") String userIdStr,
					 @PathParam("requestid") String requestIdStr
					) throws NdexException, SQLException {

				UUID userId = UUID.fromString(userIdStr);
				if ( !userId.equals(getLoggedInUserId()))
					throw new UnauthorizedOperationException("Accessing other user's requests is not allowed.");
				
				UUID requestId = UUID.fromString(requestIdStr);
				
				try (RequestDAO dao = new RequestDAO()) {
					
					dao.deletePermissionRequest(requestId, this.getLoggedInUserId());
					dao.commit();
				}
			}   

	   	
	   	@GET
		@Path("/{userid}/showcase")
		@Produces("application/json")
		@PermitAll
		@ApiDoc("")
		public List<NetworkSummary> getUserShowcaseNetworks(
					 @PathParam("userid") String userIdStr
					) throws SQLException, JsonParseException, JsonMappingException, IOException {

				UUID userId = UUID.fromString(userIdStr);
								
				try (NetworkDAO dao = new NetworkDAO()) {
					
					return dao.getUserShowCaseNetworkSummaries(userId, this.getLoggedInUserId());
				} 
				
			}   
	   	
	  	@GET
		@Path("/{userid}/networksummary")
		@Produces("application/json")
		public List<NetworkSummary> getNetworkSummariesForMyAccountPage(
						@PathParam("userid") String userIdStr,
						@DefaultValue("0") @QueryParam("offset") int offset,
						@DefaultValue("0") @QueryParam("limit") int limit
			) throws SQLException, JsonParseException, JsonMappingException, IOException, UnauthorizedOperationException {

			UUID userId = UUID.fromString(userIdStr);
			if ( !userId.equals(getLoggedInUserId()))
				throw new UnauthorizedOperationException("Userid has to be the same as autheticated user's");
			
			try (NetworkDAO dao = new NetworkDAO()) {
				return dao.getNetworkSummariesForMyAccountPage(userId, offset, limit);
			} 
					
		}      	


	  	@GET
		@Path("/{userid}/networkcount")
		@Produces("application/json")
		public Map<String,Integer> getNumNetworksForMyAccountPage(
						 @PathParam("userid") String userIdStr
			) throws SQLException, NdexException {

			UUID userId = UUID.fromString(userIdStr);
			if ( !userId.equals(getLoggedInUserId()))
				throw new UnauthorizedOperationException("Userid has to be the same as autheticated user's");
			
			Map<String, Integer> result = new HashMap<>(2);
			try (NetworkDAO dao = new NetworkDAO()) {
				result.put("networkCount",  dao.getNumNetworksForMyAccountPage(userId));
				try (NetworkSetDAO dao2 = new NetworkSetDAO()) {
					result.put("networkSetCount", dao2.getNetworkSetCountByUserId(userId));
				}
			} 

			return result;
		}      	
	  	
	  	
	   	@GET
		@Path("/{userid}/networksets")
		@Produces("application/json")
		@PermitAll

		public  List<NetworkSet> getNetworksetsByUserId(
					 @PathParam("userid") String userIdStr,
						@DefaultValue("0") @QueryParam("offset") int offset,
						@DefaultValue("0") @QueryParam("limit") int limit
						) throws SQLException, JsonParseException, JsonMappingException, IOException {
				
			UUID userId = UUID.fromString(userIdStr);
					
			try (NetworkSetDAO dao = new NetworkSetDAO ()){
					List<NetworkSet> sets= dao.getNetworkSetsByUserId(userId, getLoggedInUserId(), offset, limit);
					return sets;
				}
				
			}   

	
	// these are just prototypes not in production, 

	/**************************************************************************
	 * Authenticates a user from Google OAuth openID Connect
	 * 
	 * @return JWT object to the client

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
	} */
	
/*	
	@POST
	@PermitAll
	@NdexOpenFunction
	@Path("/google/authenticate/renew")
	@Produces("application/json")
	@ApiDoc("renew the given accessToken on Ndex server.")
	public String renewGoogleToken(OAuthTokenRenewRequest renewRequest)
			throws NdexException, ClientProtocolException, IOException {
		
		logger.info("[start: renew Google access token by refresh token]");

		GoogleOpenIDAuthenticator authenticator = BasicAuthenticationFilter.getGoogleOAuthAuthenticatior();
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
	@ApiDoc("Callback endpoint for Google OAuth OpenId Connect.")
	public void revokeGoogleAccessToken(@PathParam("accessToken") String accessToken)
			throws NdexException, ClientProtocolException, IOException {
		
		GoogleOpenIDAuthenticator authenticator = BasicAuthenticationFilter.getGoogleOAuthAuthenticatior();
		if ( authenticator ==null ) {
			logger.error("[end: Unauthorized user from google. Server is not configure to support this.]");
			throw new UnauthorizedOperationException("Server is not configured to Support Google OAuth.");
		}

		authenticator.revokeAccessToken(accessToken);
 	    
	}
	
*/

}
