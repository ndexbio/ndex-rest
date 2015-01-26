package org.ndexbio.rest.services;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

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

import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.Membership;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.Request;
import org.ndexbio.model.object.Status;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.User;
import org.ndexbio.model.object.NewUser;
import org.ndexbio.common.models.dao.orientdb.UserDAO;
import org.ndexbio.rest.helpers.Email;
import org.ndexbio.common.access.NdexAOrientDBConnectionPool;
import org.ndexbio.common.access.NdexDatabase;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;

import org.ndexbio.common.exceptions.*;
import org.ndexbio.model.object.SimpleUserQuery;
import org.ndexbio.rest.annotations.ApiDoc;
import org.ndexbio.task.Configuration;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

@Path("/user")
public class UserService extends NdexService {
	
	static Logger logger = Logger.getLogger(UserService.class.getName());
	
	private UserDAO dao;
	private ODatabaseDocumentTx  localConnection; 

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
			throws IllegalArgumentException, DuplicateObjectException,
			NdexException {
		
		ODatabaseDocumentTx db = null;
		try {
			logInfo(logger, "Creating User "+ newUser.getAccountName());
			newUser.setAccountName(newUser.getAccountName().toLowerCase());

			db = NdexDatabase.getInstance().getAConnection();
			UserDAO userdao = new UserDAO(db);

			User user = userdao.createNewUser(newUser);
			userdao.commit();
			logInfo(logger, "User " + newUser.getAccountName() + " created with UUID " + user.getExternalId());
			return user;
		} finally {
			if ( db != null ) 
				db.close();
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
		
		logInfo( logger, "Getting user " + userId);
		localConnection = NdexDatabase.getInstance().getAConnection();
		dao = new UserDAO(localConnection);
		
		try {
			final User user = dao.getUserByAccountName(userId.toLowerCase());
			logInfo(logger, "User object returned for user account " + userId );
			return user;
		} catch (ObjectNotFoundException e) {
			final User user = dao.getUserById(UUID.fromString(userId));
			logInfo(logger, "User object returned for user id " + userId );
			return user;	
		} finally  {
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
		
		logInfo(logger, "Getting " + permissions + " networks of user " + userId);
		
		Permissions permission = Permissions.valueOf(permissions.toUpperCase());
		
		localConnection = NdexDatabase.getInstance().getAConnection();
		dao = new UserDAO(localConnection);
		
		try {
			List<Membership> members= dao.getUserNetworkMemberships(UUID.fromString(userId), permission, skipBlocks, blockSize);
			logInfo (logger, "Returned " + members.size() + " members for user " + userId);
			return members;
		} finally {
			dao.close();
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
		
		logInfo( logger, "Getting " + permissions + " groups for user " + userId);
		
		Permissions permission = Permissions.valueOf(permissions.toUpperCase());
		
		localConnection = NdexDatabase.getInstance().getAConnection();
		
		try {
			dao = new UserDAO(localConnection);
			return dao.getUserGroupMemberships(UUID.fromString(userId), permission, skipBlocks, blockSize);
		} finally {
			dao.close();
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
			throws SecurityException, NdexException {
		
		logInfo(logger, "Authentiate user " + accountName);
		localConnection = NdexDatabase.getInstance().getAConnection();

		try {
			dao = new UserDAO(localConnection);
			return dao.authenticateUser(accountName.toLowerCase(), password);
		} finally {
			dao.close();
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

		logInfo( logger, "Changing password.");
		Preconditions.checkArgument(!Strings.isNullOrEmpty(password), 
				"A password is required");

		logger.info("Changing password for user " + getLoggedInUser().getAccountName());
		localConnection = NdexDatabase.getInstance().getAConnection();
		
		try {
			dao = new UserDAO(localConnection);
			dao.changePassword(password, getLoggedInUser().getExternalId());
			dao.commit();
			logger.info("Password changed for user " + getLoggedInUser().getAccountName());
		} finally {
			dao.close();
			dao = null;
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

		logInfo(logger, "Deleting user (self).");
		localConnection = NdexDatabase.getInstance().getAConnection();
		dao = new UserDAO(localConnection);
		
		try {
			dao.deleteUserById(getLoggedInUser().getExternalId());
			dao.commit();
			logger.info("User " + getLoggedInUser().getAccountName() + " deleted.");
		} finally {
			dao.close();
			dao = null;
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
		
		logInfo( logger, "Email new password for " + accountName);
		
		Preconditions.checkArgument(!Strings.isNullOrEmpty(accountName), 
				"A accountName is required");
		// TODO: In the future security questions should be implemented - right
		// now anyone can change anyone else's password to a randomly generated
		// password
		
		localConnection = NdexDatabase.getInstance().getAConnection();
		
		BufferedReader fileReader = null;
		try {

			dao = new UserDAO(localConnection);
			User authUser = dao.getUserByAccountName(accountName);
			String newPasswd = dao.setNewPassword(accountName.toLowerCase());

			dao.commit();
			
			final File forgotPasswordFile = new File(Configuration
					.getInstance().getProperty("Forgot-Password-File"));

			if (!forgotPasswordFile.exists()) {
				logger.severe("Could not retrieve forgot password file");
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

			logger.info("Sent password recovery email to user " + accountName);

			return Response.ok().build();
			
		} finally {
			dao.close();
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
		
		logInfo (logger, "Searching user \"" + simpleUserQuery.getSearchString() + "\"");
		localConnection = NdexDatabase.getInstance().getAConnection();
		
		try {

			if(simpleUserQuery.getAccountName() != null)
				simpleUserQuery.setAccountName(simpleUserQuery.getAccountName().toLowerCase());
			
			dao = new UserDAO(localConnection);
			final List<User> users = dao.findUsers(simpleUserQuery, skipBlocks, blockSize);
			logInfo ( logger, "Returning " + users.size() + " users from search.");
			return users;
		} finally {
			dao.close();
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
			throws IllegalArgumentException, ObjectNotFoundException, NdexException {
		Preconditions.checkArgument(null != updatedUser, 
				"Updated user data are required");
		
		// Currently not using path param. We can already retrieve the id from the authentication
		// However, this depends on the authentication method staying consistent?

		logInfo(logger, "Updating user " + updatedUser.getAccountName() );
		localConnection = NdexDatabase.getInstance().getAConnection();

		try {
			dao = new UserDAO(localConnection);
			User user = dao.updateUser(updatedUser, getLoggedInUser().getExternalId());
			dao.commit();
			logger.info("User " + user.getAccountName() + " updated.");
			return user;
		} finally {
			dao.close();
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
		
		logInfo( logger, "Getting membership of account " + accountId );
		localConnection = NdexDatabase.getInstance().getAConnection();
		
		try {
			dao = new UserDAO(localConnection);
			Membership m = dao.getMembership(UUID.fromString(accountId), UUID.fromString(resourceId), depth);
			if ( m==null)
				logInfo(logger, "No membership found for account " + accountId + 
						" on resource " + resourceId);
			else 
				logInfo(logger, "Membership " + m.getPermissions().name() + " found for account " + accountId + 
					" on resource " + resourceId);
			return m;
		} finally {
			dao.close();
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
		
		logInfo (logger, "Getting requests sent by user " + userId);
		
		localConnection = NdexDatabase.getInstance().getAConnection();
		dao = new UserDAO(localConnection);
		
		try {
			List<Request> reqs= dao.getSentRequest(this.getLoggedInUser(),skipBlocks, blockSize);
			logInfo( logger, "Returning " + reqs.size() + " requests sent by user " + userId);
			return reqs;
		} finally {
			dao.close();
		}
	}
	
	@GET
	@Path("/{userId}/request/pending/{skipBlocks}/{blockSize}")
	@Produces("application/json")
	@ApiDoc("")
	public List<Request> getPendingRequests(@PathParam("userId") final String userId,
			@PathParam("skipBlocks") int skipBlocks,
			@PathParam("blockSize") int blockSize) throws NdexException {
		
		logInfo (logger, "Getting pending request for user");
		
		localConnection = NdexDatabase.getInstance().getAConnection();
		dao = new UserDAO(localConnection);
		
		try {
			List<Request> reqs= dao.getPendingRequest(this.getLoggedInUser(),skipBlocks, blockSize);
			logInfo ( logger, "Returning " + reqs.size() + " pending request under user " + userId);
			return reqs;
		} finally {
			dao.close();
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
		
		logInfo( logger, "Getting users tasks.");
		
		localConnection = NdexDatabase.getInstance().getAConnection();
		
		try {
			dao = new UserDAO(localConnection);
			Status taskStatus = Status.valueOf(status);
			List<Task> tasks= dao.getTasks(this.getLoggedInUser(),taskStatus, skipBlocks, blockSize);
			logInfo(logger, "Returned " + tasks.size() + " tasks under user " + userId);
			return tasks;
		} finally {
			dao.close();
		}
	}
	
}
