package org.ndexbio.rest.services;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;


import javax.annotation.security.PermitAll;
import javax.mail.MessagingException;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.apache.commons.lang.RandomStringUtils;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.Membership;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.Request;
import org.ndexbio.model.object.Status;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.User;
import org.ndexbio.model.object.NewUser;
import org.ndexbio.common.models.dao.orientdb.UserDAO;
import org.ndexbio.common.models.dao.orientdb.UserDocDAO;
import org.ndexbio.rest.filters.BasicAuthenticationFilter;
import org.ndexbio.rest.helpers.Email;
import org.ndexbio.common.access.NdexAOrientDBConnectionPool;
import org.ndexbio.common.access.NdexDatabase;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;

import org.ndexbio.common.exceptions.*;
import org.ndexbio.model.object.SimpleUserQuery;
import org.ndexbio.rest.annotations.ApiDoc;
import org.ndexbio.security.LDAPAuthenticator;
import org.ndexbio.task.Configuration;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import org.slf4j.Logger;

@Path("/user")
public class UserService extends NdexService {
	
	static Logger logger = LoggerFactory.getLogger(UserService.class);

//	private UserDAO dao;
//	private ODatabaseDocumentTx  localConnection; 

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
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws DuplicateObjectException
	 *             A user with the same accountName/email address already exists.
	 * @throws NdexException
	 *             Failed to create the user in the database.
	 * @return The new user's profile.
	 **************************************************************************/
	/*
	 * refactored to accommodate non-transactional database operations
	 */
	@POST
	@PermitAll
	@Produces("application/json")
	@ApiDoc("Create a new user based on a JSON object specifying username, password, and emailAddress, returns the new user - including its internal id. Username and emailAddress must be unique in the database.")
	public User createUser(final NewUser newUser)
			throws IllegalArgumentException, DuplicateObjectException,UnauthorizedOperationException,
			NdexException {
		
		logger.info(userNameForLog() + "[start: Creating User "+ newUser.getAccountName() + "]");
		
		//verify against AD if AD authentication is defined in the configure file
		if( Configuration.getInstance().getUseADAuthentication()) {
			LDAPAuthenticator authenticator = BasicAuthenticationFilter.getLDAPAuthenticator();
			if (!authenticator.authenticateUser(newUser.getAccountName(), newUser.getPassword()) ) {
				logger.error(userNameForLog() + "[end: Creating User "+ newUser.getAccountName() + ".  Throwing UnauthorizedOperationException exception]");
				throw new UnauthorizedOperationException("Only valid AD users can have an account in NDEx.");
			}
			newUser.setPassword(RandomStringUtils.random(25));
			logger.info(userNameForLog() + "[User is a authenticated by AD." + "]");
		}

		try (UserDocDAO userdao = new UserDocDAO(NdexDatabase.getInstance().getAConnection())){

			newUser.setAccountName(newUser.getAccountName().toLowerCase());

			User user = userdao.createNewUser(newUser);
			userdao.commit();
			logger.info(userNameForLog() + "[end: User " + newUser.getAccountName() + " created with UUID " + user.getExternalId() + "]");
			return user;
		}
	}
	
	/**************************************************************************
	 * Gets a user by ID or accountName.(1.0 snapshot) 
	 * 
	 * @param userId
	 *            The ID or accountName of the user.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws NdexException
	 *             Failed to change the password in the database.
	 **************************************************************************/
	@GET
	@PermitAll
	@Path("/{userId}")
	@Produces("application/json")
	@ApiDoc("Return the user corresponding to userId, whether userId is actually a database id or a accountName. Error if neither is found.")
	public User getUser(@PathParam("userId") final String userId)
			throws IllegalArgumentException, NdexException {
		
		logger.info(userNameForLog() + "[start: Getting user " + userId + "]");
		UserDocDAO dao = new UserDocDAO(NdexDatabase.getInstance().getAConnection());
		try {
			final User user = dao.getUserByAccountName(userId.toLowerCase());
			logger.info(userNameForLog() + "[end: User object returned for user account " + userId  + "]");
			return user;
		} catch (ObjectNotFoundException e) {
			final User user = dao.getUserById(UUID.fromString(userId));
			logger.info(userNameForLog() + "[end: User object returned for user id " + userId  + "]");
			return user;	
		} finally  {
			if ( dao !=null)
				dao.close();
		}
		
	}
		
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
	 **************************************************************************/
	
	@GET
	@PermitAll
	@Path("/{userId}/network/{permission}/{skipBlocks}/{blockSize}")
	@Produces("application/json")
	@ApiDoc("")
	public List<Membership> getUserNetworkMemberships(@PathParam("userId") final String userId,
			@PathParam("permission") final String permissions ,
			@PathParam("skipBlocks") int skipBlocks,
			@PathParam("blockSize") int blockSize) throws NdexException {
		
		logger.info(userNameForLog() + "[start: Getting " + permissions + " networks of user " + userId + "]");
		
		Permissions permission = Permissions.valueOf(permissions.toUpperCase());
		
		try (UserDocDAO dao = new UserDocDAO (NdexDatabase.getInstance().getAConnection())) {
			List<Membership> members= dao.getUserNetworkMemberships(UUID.fromString(userId), permission, skipBlocks, blockSize);
			logger.info(userNameForLog() + "[end: Returned " + members.size() + " members for user " + userId + "]");
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
	 **************************************************************************/
	
	@GET
	@PermitAll
	@Path("/{userId}/group/{permission}/{skipBlocks}/{blockSize}")
	@Produces("application/json")
	@ApiDoc("")
	public List<Membership> getUserGroupMemberships(@PathParam("userId") final String userId,
			@PathParam("permission") final String permissions ,
			@PathParam("skipBlocks") int skipBlocks,
			@PathParam("blockSize") int blockSize) throws NdexException {
		
		logger.info(userNameForLog() + "[start: Getting " + permissions + " groups for user " + userId + "]");
		
		Permissions permission = Permissions.valueOf(permissions.toUpperCase());
		
		try (UserDocDAO dao = new UserDocDAO (NdexDatabase.getInstance().getAConnection())) {
			List<Membership> result =
					dao.getUserGroupMemberships(UUID.fromString(userId), permission, skipBlocks, blockSize);
			logger.info(userNameForLog() +  "[end: Got " + result.size() + " grp membership for user " + userId + "]");
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
	@Path("/authenticate/{accountName}/{password}")
	@Produces("application/json")
	@ApiDoc("Authenticates the combination of accountName and password supplied in the route parameters, returns the authenticated user if successful.")
	public User authenticateUser(@PathParam("accountName") final String accountName,
			@PathParam("password") final String password)
			throws SecurityException, UnauthorizedOperationException, NdexException {
		
		logger.info(userNameForLog() + "[start: Authenticate user " + accountName + "]");

		if ( Configuration.getInstance().getUseADAuthentication()) {
			LDAPAuthenticator authenticator = BasicAuthenticationFilter.getLDAPAuthenticator();
			if ( !authenticator.authenticateUser(accountName, password)) {
			    logger.error(userNameForLog() + "[end: Invalid accountName or password in AD.  Throwing SecurityException.]");
				throw new SecurityException("Invalid accountName or password in AD.");
			}
			
			try (UserDocDAO dao = new UserDocDAO (NdexDatabase.getInstance().getAConnection())) {
				logger.info(userNameForLog() + "[end: User " + accountName + " authenticated.]");
				return dao.getUserByAccountName(accountName);
			}	
		}
		
		try (UserDocDAO dao = new UserDocDAO (NdexDatabase.getInstance().getAConnection())) {
			logger.info(userNameForLog() + "[end: User " + accountName + " authenticated.]");		
			return dao.authenticateUser(accountName.toLowerCase(), password);
		}
	}

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
	 **************************************************************************/
	
	@POST
	@Path("/password")
	@Produces("application/json")
	@ApiDoc("Changes the authenticated user's password to the new password in the POST data.")
	public void changePassword(String password)
			throws IllegalArgumentException, NdexException {

		logger.info(userNameForLog() + "[start: Changing password for user " + getLoggedInUser().getAccountName() + "]");
		Preconditions.checkArgument(!Strings.isNullOrEmpty(password), 
				"A password is required");
		
		try (UserDocDAO dao = new UserDocDAO (NdexDatabase.getInstance().getAConnection())) {
			dao.changePassword(password, getLoggedInUser().getExternalId());
			dao.commit();
			logger.info(userNameForLog() + "[end: Password changed for user " + getLoggedInUser().getAccountName() + "]");
		}
	}


	/**************************************************************************
	 * Deletes a user.
	 * 
	 * @throws NdexException
	 *             Failed to delete the user from the database.
	 **************************************************************************/
	@DELETE
	@Produces("application/json")
	@ApiDoc("Deletes the authenticated user. Errors if the user administrates any group or network. Should remove any other objects depending on the user.")
	public void deleteUser() throws NdexException, ObjectNotFoundException {

		logger.info(userNameForLog() + "[start: Deleting user (self)." + "]");
		
		try (UserDAO dao = new UserDAO(NdexDatabase.getInstance().getAConnection())) {
			dao.deleteUserById(getLoggedInUser().getExternalId());
			dao.commit();
			logger.info(userNameForLog() + "[end: User " + getLoggedInUser().getAccountName() + " deleted." + "]");
		} 
	}

	/**************************************************************************
	 * Emails the user a new randomly generated password.
	 * 
	 * @param username
	 *            The username of the user.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws NdexException
	 *             Failed to change the password in the database, or failed to
	 *             send the email.
	 * @throws IOException 
	 * @throws MessagingException 
	 **************************************************************************/
	@GET
	@PermitAll
	@Path("/{accountName}/forgot-password")
	@Produces("application/json")
	@ApiDoc("Causes a new password to be generated for the authenticated user and then emailed to the users emailAddress")
	public Response emailNewPassword(
			@PathParam("accountName") final String accountName)
			throws IllegalArgumentException, NdexException, IOException, MessagingException {
		
		logger.info(userNameForLog() + "[start: Email new password for " + accountName + "]");
		
		Preconditions.checkArgument(!Strings.isNullOrEmpty(accountName), 
				"A accountName is required");
		// TODO: In the future security questions should be implemented - right
		// now anyone can change anyone else's password to a randomly generated
		// password
		
		BufferedReader fileReader = null;
		try (UserDocDAO dao = new UserDocDAO (NdexDatabase.getInstance().getAConnection())){

			User authUser = dao.getUserByAccountName(accountName);
			String newPasswd = dao.setNewPassword(accountName.toLowerCase());

			dao.commit();
			
			final File forgotPasswordFile = new File(Configuration
					.getInstance().getProperty("Forgot-Password-File"));

			if (!forgotPasswordFile.exists()) {
				logger.error(userNameForLog() + "[end: File containing forgot password email content doesn't exist.  Throwing FileNotFoundException.]");
				throw new java.io.FileNotFoundException(
						"File containing forgot password email content doesn't exist.");
			}

			fileReader = Files.newBufferedReader(
					forgotPasswordFile.toPath(), Charset.forName("US-ASCII"));

			final StringBuilder forgotPasswordText = new StringBuilder();

			String lineOfText = null;
			while ((lineOfText = fileReader.readLine()) != null)
				forgotPasswordText.append(lineOfText.replace("{password}",	newPasswd));

			Email.sendEmail(
					Configuration.getInstance().getProperty(
							"Forgot-Password-Email"),
					authUser.getEmailAddress(), "Password Recovery",
					forgotPasswordText.toString());

			logger.info(userNameForLog() + "[end: Sent password recovery email to user " + accountName  + "]");

			return Response.ok().build();
			
		} finally {
			if ( fileReader !=null ) fileReader.close();
		}
	}

	/**************************************************************************
	 * Finds users based on the search parameters.
	 * 
	 * @param searchParameters
	 *            The search parameters.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws NdexException
	 *             Failed to query the database.
	 **************************************************************************/
	@POST
	@PermitAll
	@Path("/search/{skipBlocks}/{blockSize}")
	@Produces("application/json")
	@ApiDoc("Returns a list of users based on the range [skipBlocks, blockSize] and the POST data searchParameters. "
			+ "The searchParameters must contain a 'searchString' parameter. ")
	public List<User> findUsers(SimpleUserQuery simpleUserQuery, @PathParam("skipBlocks") final int skipBlocks, @PathParam("blockSize") final int blockSize)
			throws IllegalArgumentException, NdexException {
		
		logger.info(userNameForLog() + "[start: Searching user \"" + simpleUserQuery.getSearchString() + "\"]");
		
		try (UserDocDAO dao = new UserDocDAO (NdexDatabase.getInstance().getAConnection())){

			if(simpleUserQuery.getAccountName() != null)
				simpleUserQuery.setAccountName(simpleUserQuery.getAccountName().toLowerCase());
			
			final List<User> users = dao.findUsers(simpleUserQuery, skipBlocks, blockSize);
			logger.info(userNameForLog() + "[end: Returning " + users.size() + " users from search.]");
			return users;
		} 
		
	}


	/**************************************************************************
	 * Updates a user.
	 * 
	 * @param updatedUser
	 *            The updated user information.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws ObjectNotFoundException
	 *             Users trying to update someone else.
	 * @throws NdexException
	 *             Failed to update the user in the database.
	 **************************************************************************/
	@POST
	@Path("/{userIdentifier}")
	@Produces("application/json")
	@ApiDoc("Updates the authenticated user based on the serialized user object in the POST data. Errors if the user object references a different user.")
	public User updateUser(@PathParam("userIdentifier") final String userId, final User updatedUser)
			throws IllegalArgumentException, ObjectNotFoundException, UnauthorizedOperationException, NdexException {
		Preconditions.checkArgument(null != updatedUser, 
				"Updated user data are required");
		
		// Currently not using path param. We can already retrieve the id from the authentication
		// However, this depends on the authentication method staying consistent?

		logger.info(userNameForLog() + "[start: Updating user " + updatedUser.getAccountName()  + "]");

		if ( Configuration.getInstance().getUseADAuthentication()) {
			if ( !updatedUser.getAccountName().equals(getLoggedInUser().getAccountName())) {
				logger.error(userNameForLog() + "[end: Updating accountName is not allowed when NDEx server is running on AD authentication.]");
				throw new UnauthorizedOperationException(
						"Updating accountName is not allowed when NDEx server is running on AD authentication.");
			}
		}
		
		try (UserDocDAO dao = new UserDocDAO (NdexDatabase.getInstance().getAConnection())){
			User user = dao.updateUser(updatedUser, getLoggedInUser().getExternalId());
			dao.commit();
			logger.info(userNameForLog() + "[end: User " + user.getAccountName() + " updated.]");
			return user;
		} 
	}
	
	@GET
	@PermitAll
	@Path("/{accountId}/membership/{resourceId}/{depth}")
	@Produces("application/json")
	@ApiDoc("")
	public Membership getMembership(@PathParam("accountId") final String accountId, 
				@PathParam("resourceId") final String resourceId, 
				@PathParam("depth") final int depth) 
			throws IllegalArgumentException, ObjectNotFoundException, NdexException {
		
		logger.info(userNameForLog() + "[start: Getting membership of account " + accountId  + "]");
		
		try (UserDocDAO dao = new UserDocDAO (NdexDatabase.getInstance().getAConnection())){
			Membership m = dao.getMembership(UUID.fromString(accountId), UUID.fromString(resourceId), depth);
			if ( m==null)
				logger.info(userNameForLog() + "[end: No membership found for account " + accountId + 
						" on resource " + resourceId + "]");
			else 
				logger.info(userNameForLog() + "[end: Membership " + m.getPermissions().name() + " found for account " + accountId + 
					" on resource " + resourceId + "]");
			return m;
		} 
	}
	
	
	//TODO both requests methods ignore UUID in url. Should at least verify it is the same as logged in UUID for consistency
	
	@GET
	@Path("/{userId}/request/{skipBlocks}/{blockSize}")
	@Produces("application/json")
	@ApiDoc("")
	public List<Request> getSentRequest(@PathParam("userId") final String userId,
			@PathParam("skipBlocks") int skipBlocks,
			@PathParam("blockSize") int blockSize) throws NdexException {
		
		logger.info(userNameForLog() + "[start: Getting requests sent by user " + userId + "]");
		
		try (UserDocDAO dao = new UserDocDAO (NdexDatabase.getInstance().getAConnection())){
			List<Request> reqs= dao.getSentRequest(this.getLoggedInUser(),skipBlocks, blockSize);
			logger.info(userNameForLog() + "[end: Returning " + reqs.size() + " requests sent by user " + userId  + "]");
			return reqs;
		}
	}
	
	@GET
	@Path("/{userId}/request/pending/{skipBlocks}/{blockSize}")
	@Produces("application/json")
	@ApiDoc("")
	public List<Request> getPendingRequests(@PathParam("userId") final String userId,
			@PathParam("skipBlocks") int skipBlocks,
			@PathParam("blockSize") int blockSize) throws NdexException {
		
		logger.info(userNameForLog() + "[start: Getting pending request for user " + userId + "]");
		
		try (UserDocDAO dao = new UserDocDAO (NdexDatabase.getInstance().getAConnection())){
			List<Request> reqs= dao.getPendingRequest(this.getLoggedInUser(),skipBlocks, blockSize);
			logger.info(userNameForLog() + "[end: Returning " + reqs.size() + " pending request under user " + userId + "]");
			return reqs;
		} 
	}
	
	@GET
	@Path("/{userId}/task/{status}/{skipBlocks}/{blockSize}")
	@Produces("application/json")
	@ApiDoc("Returns an array of Task objects with the specified status")
	public List<Task> getTasks(
			@PathParam("userId") final String userId,
			@PathParam("status") final String status,
			@PathParam("skipBlocks") int skipBlocks,
			@PathParam("blockSize") int blockSize) throws NdexException {
		
		logger.info(userNameForLog() + "[start: Getting tasks for user " + userId + "]");
		
		try (UserDocDAO dao = new UserDocDAO (NdexDatabase.getInstance().getAConnection())){
			Status taskStatus = Status.valueOf(status);
			List<Task> tasks= dao.getTasks(this.getLoggedInUser(),taskStatus, skipBlocks, blockSize);
			logger.info(userNameForLog() + "[end: Returned " + tasks.size() + " tasks under user " + userId  + "]");
			return tasks;
		} 
	}
	
}
