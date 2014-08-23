package org.ndexbio.rest.services;

import java.util.List;
import java.util.UUID;

import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.ndexbio.model.object.Membership;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.Request;
import org.ndexbio.model.object.Status;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.User;
import org.ndexbio.model.object.NewUser;
import org.ndexbio.common.models.dao.orientdb.UserDAO;
import org.ndexbio.common.access.NdexDatabase;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.tinkerpop.blueprints.impls.orient.OrientGraph;

import org.ndexbio.common.exceptions.*;
import org.ndexbio.model.object.SimpleUserQuery;
import org.ndexbio.rest.annotations.ApiDoc;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

@Path("/user")
public class UserService extends NdexService {
	
	private static UserDAO dao;
	private static NdexDatabase database;
	private static ODatabaseDocumentTx  localConnection;  //all DML will be in this connection, in one transaction.
	private static OrientGraph graph;

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
		
		final User user;
		
		this.openDatabase();
		
		try {
			//localConnection.begin();
			user = dao.createNewUser(newUser);
			graph.commit();//localConnection.commit();
		} finally {
			this.closeDatabase();

		}
		
		return user;
        
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
		
		this.openDatabase();
		
		try {

			final User user = dao.getUserByAccountName(userId);
			return user;

		} catch (ObjectNotFoundException e) {
			
			final User user = dao.getUserById(UUID.fromString(userId));
			return user;
				
		} finally  {
			this.closeDatabase();

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
		
		Permissions permission = Permissions.valueOf(permissions.toUpperCase());
		
		this.openDatabase();
		
		try {
			
			return dao.getUserNetworkMemberships(UUID.fromString(userId), permission, skipBlocks, blockSize);
	
		} finally {
			this.closeDatabase();
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
	public List<Membership> getUserGroupMemberships(@PathParam("userId") final String groupId,
			@PathParam("permission") final String permissions ,
			@PathParam("skipBlocks") int skipBlocks,
			@PathParam("blockSize") int blockSize) throws NdexException {
		
		Permissions permission = Permissions.valueOf(permissions.toUpperCase());
		
		this.openDatabase();
		
		try {
			
			return dao.getUserGroupMemberships(UUID.fromString(groupId), permission, skipBlocks, blockSize);

		} finally {
			this.closeDatabase();
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
		
		this.openDatabase();

		try {
			
			return dao.authenticateUser(accountName, password);

		} finally {
			this.closeDatabase();
			
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
		Preconditions.checkArgument(!Strings.isNullOrEmpty(password), 
				"A password is required");

		this.openDatabase();
		
		try {

			//localConnection.begin();
			dao.changePassword(password, getLoggedInUser().getExternalId());
			graph.commit();//localConnection.commit();

		} finally {
			this.closeDatabase();

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

		this.openDatabase();
		
		try {

			//localConnection.begin();
			dao.deleteUserById(getLoggedInUser().getExternalId());
			graph.commit();//localConnection.commit();

		} finally {
			this.closeDatabase();

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
	 **************************************************************************/
	@GET
	@PermitAll
	@Path("/{username}/forgot-password")
	@Produces("application/json")
	@ApiDoc("Causes a new password to be generated for the authenticated user and then emailed to the users emailAddress")
	public Response emailNewPassword(
			@PathParam("username") final String username)
			throws IllegalArgumentException, NdexException {
		
		Preconditions.checkArgument(!Strings.isNullOrEmpty(username), 
				"A username is required");
		// TODO: In the future security questions should be implemented - right
		// now anyone can change anyone else's password to a randomly generated
		// password
		
		this.openDatabase();
		
		try {

			//localConnection.begin();
			final Response res = dao.emailNewPassword(username);
			graph.commit();//localConnection.commit();
			return res;

		} finally {
			this.closeDatabase();

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
		
		this.openDatabase();
		
		try {

			final List<User> users = dao.findUsers(simpleUserQuery, skipBlocks, blockSize);
			return users;

		} finally {
			this.closeDatabase();

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
		
		this.openDatabase();

		try {

			//localConnection.begin();
			final User user = dao.updateUser(updatedUser, getLoggedInUser().getExternalId());
			graph.commit();
			
			return user;

		} finally {
			this.closeDatabase();
		}
	}
	
	@GET
	@PermitAll
	@Path("/{accountId}/membership/{resourceId}")
	@Produces("application/json")
	@ApiDoc("")
	public Membership getMembership(@PathParam("accountId") final String accountId, @PathParam("resourceId") final String resourceId) 
			throws IllegalArgumentException, ObjectNotFoundException, NdexException {
		
		this.openDatabase();
		
		try {
			
			return dao.getMembership(UUID.fromString(accountId), UUID.fromString(resourceId));

		} finally {
			this.closeDatabase();
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
		
		this.openDatabase();
		
		try {
			
			return dao.getSentRequest(this.getLoggedInUser(),skipBlocks, blockSize);

		} finally {
			this.closeDatabase();
		}
	}
	
	@GET
	@Path("/{userId}/request/pending/{skipBlocks}/{blockSize}")
	@Produces("application/json")
	@ApiDoc("")
	public List<Request> getPendingRequests(@PathParam("userId") final String userId,
			@PathParam("skipBlocks") int skipBlocks,
			@PathParam("blockSize") int blockSize) throws NdexException {
		
		this.openDatabase();
		
		try {
			
			return dao.getPendingRequest(this.getLoggedInUser(),skipBlocks, blockSize);

		} finally {
			this.closeDatabase();
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
		
		this.openDatabase();
		
		try {
			Status taskStatus = Status.valueOf(status);
			return dao.getTasks(this.getLoggedInUser(),taskStatus, skipBlocks, blockSize);

		} finally {
			this.closeDatabase();
		}
	}
	

	private void openDatabase() throws NdexException {
		database = new NdexDatabase();
		localConnection = database.getAConnection();
		graph = new OrientGraph(localConnection);
		dao = new UserDAO(localConnection, graph);
	}
	private void closeDatabase() {
		graph.shutdown();
		//localConnection.close();
		database.close();
	}
	
	

}
