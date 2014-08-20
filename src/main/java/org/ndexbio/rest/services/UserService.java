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
			localConnection.begin();
			user = dao.createNewUser(newUser);
			localConnection.commit();
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
	 * Adds a network to the user's Work Surface.
	 * 
	 * @param userId
	 *            The user's ID.
	 * @param networkToAdd
	 *            The network to add.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws ObjectNotFoundException
	 *             The network wasn't found.
	 * @throws NdexException
	 *             Failed to add the network in the database.
	 **************************************************************************/
/*	@PUT
	@Path("/work-surface")
	@Produces("application/json")
	@ApiDoc("Add a network to the authenticated user's worksurface, returns the current list of networks on the worksurface. Errors if the network does not exist or is already on the worksurface.")
	public Iterable<Network> addNetworkToWorkSurface(String networkId)
			throws IllegalArgumentException, ObjectNotFoundException,
			NdexException {
		Preconditions.checkArgument(!Strings.isNullOrEmpty(networkId),
				"A network id is required");

		networkId = networkId.replace("\"", "");

		final ORID userRid = IdConverter.toRid(this.getLoggedInUser().getId());
		final ORID networkRid = IdConverter.toRid(networkId);

		try {
			setupDatabase();

			final IUser user = _orientDbGraph.getVertex(userRid, IUser.class);

			final INetwork network = _orientDbGraph.getVertex(networkRid,
					INetwork.class);
			if (network == null)
				throw new ObjectNotFoundException("Network", networkId);

			final Iterable<INetwork> workSurface = user.getWorkSurface();
			if (workSurface != null) {
				for (INetwork checkNetwork : workSurface) {
					if (checkNetwork.asVertex().getId().equals(networkRid))
						throw new DuplicateObjectException("Network with RID: "
								+ networkRid
								+ " is already on the Work Surface.");
				}
			}

			user.addNetworkToWorkSurface(network);			

			final ArrayList<Network> updatedWorkSurface = Lists.newArrayList();
			final Iterable<INetwork> onWorkSurface = user.getWorkSurface();
			if (onWorkSurface != null) {
				for (INetwork workSurfaceNetwork : onWorkSurface)
					updatedWorkSurface.add(new Network(workSurfaceNetwork));
			}

			return updatedWorkSurface;
		} catch (DuplicateObjectException | ObjectNotFoundException onfe) {
			throw onfe;
		} catch (Exception e) {
			_logger.error(
					"Failed to add a network to "
							+ this.getLoggedInUser().getUsername()
							+ "'s Work Surface.", e);
		
			throw new NdexException(
					"Failed to add the network to your Work Surface.");
		} finally {
			teardownDatabase();
		}
	}
*/
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

			localConnection.begin();
			dao.changePassword(password, getLoggedInUser().getExternalId());
			localConnection.commit();

		} finally {
			this.closeDatabase();

		}
	}

	/**************************************************************************
	 * Changes a user's profile/background image.
	 * 
	 * @param imageType
	 *            The image type.
	 * @param uploadedImage
	 *            The uploaded image.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws NdexException
	 *             Failed to save the image.
	 **************************************************************************/
/*	@POST
	@Path("/image/{imageType}")
	@Consumes("multipart/form-data")
	@Produces("application/json")
	@ApiDoc("Uploads and installs a new image file to be used as either the profile or background image (depending on the imageType route parameter) for the authenticated user.")
	public void changeProfileImage(
			@PathParam("imageType") final String imageType,
			@MultipartForm UploadedFile uploadedImage)
			throws IllegalArgumentException, NdexException {
		if (imageType == null || imageType.isEmpty())
			throw new IllegalArgumentException("No image type specified.");
		else if (uploadedImage == null
				|| uploadedImage.getFileData().length < 1)
			throw new IllegalArgumentException("No uploaded image.");

		final ORID userId = IdConverter.toRid(this.getLoggedInUser().getId());

		try {
			setupDatabase();

			final IUser user = _orientDbGraph.getVertex(userId, IUser.class);
			if (user == null)
				throw new ObjectNotFoundException("User", this
						.getLoggedInUser().getId());

			final BufferedImage newImage = ImageIO
					.read(new ByteArrayInputStream(uploadedImage.getFileData()));

			if (imageType.toLowerCase().equals("profile")) {
				final BufferedImage resizedImage = resizeImage(newImage,
						Integer.parseInt(Configuration.getInstance()
								.getProperty("Profile-Image-Width")),
						Integer.parseInt(Configuration.getInstance()
								.getProperty("Profile-Image-Height")));

				ImageIO.write(
						resizedImage,
						"jpg",
						new File(Configuration.getInstance().getProperty(
								"Profile-Image-Path")
								+ user.getUsername() + ".jpg"));
			} else {
				final BufferedImage resizedImage = resizeImage(newImage,
						Integer.parseInt(Configuration.getInstance()
								.getProperty("Profile-Background-Width")),
						Integer.parseInt(Configuration.getInstance()
								.getProperty("Profile-Background-Height")));

				ImageIO.write(resizedImage, "jpg", new File(Configuration
						.getInstance().getProperty("Profile-Background-Path")
						+ user.getUsername() + ".jpg"));
			}
		} catch (ObjectNotFoundException onfe) {
			throw onfe;
		} catch (Exception e) {
			_logger.error("Failed to save a profile image.", e);
			throw new NdexException(e.getMessage());
		} finally {
			teardownDatabase();
		}
	}
*/
	
	/**************************************************************************
	 * Deletes a network from a user's Work Surface.
	 * 
	 * @param networkToDelete
	 *            The network being removed.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws ObjectNotFoundException
	 *             The network doesn't exist.
	 * @throws NdexException
	 *             Failed to remove the network in the database.
	 **************************************************************************/
/*	@DELETE
	@Path("/work-surface/{networkId}")
	@Produces("application/json")
	@ApiDoc("Removes the network with id = networkId from the worksurface of the authenticated user, returning the list of networks on the worksurface. Errors if the network is not found.")
	public Iterable<Network> deleteNetworkFromWorkSurface(
			@PathParam("networkId") final String networkId)
			throws IllegalArgumentException, ObjectNotFoundException,
			NdexException {
		Preconditions.checkArgument(!Strings.isNullOrEmpty(networkId), 
				"A network id is required");
	
		final ORID userRid = IdConverter.toRid(this.getLoggedInUser().getId());
		final ORID networkRid = IdConverter.toRid(networkId);

		try {
			setupDatabase();

			final IUser user = _orientDbGraph.getVertex(userRid, IUser.class);

			final INetwork network = _orientDbGraph.getVertex(networkRid,
					INetwork.class);
			if (network == null)
				throw new ObjectNotFoundException("Network", networkId);

			user.removeNetworkFromWorkSurface(network);
			

			final ArrayList<Network> updatedWorkSurface = Lists.newArrayList();
			final Iterable<INetwork> onWorkSurface = user.getWorkSurface();
			if (onWorkSurface != null) {
				for (INetwork workSurfaceNetwork : onWorkSurface)
					updatedWorkSurface.add(new Network(workSurfaceNetwork));
			}

			return updatedWorkSurface;
		} catch (ObjectNotFoundException onfe) {
			throw onfe;
		} catch (Exception e) {
			_logger.error(
					"Failed to remove a network from "
							+ this.getLoggedInUser().getUsername()
							+ "'s Work Surface.", e);
			
			throw new NdexException(
					"Failed to remove the network from your Work Surface.");
		} finally {
			teardownDatabase();
		}
	}
*/
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

			localConnection.begin();
			dao.deleteUserById(getLoggedInUser().getExternalId());
			localConnection.commit();

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

			localConnection.begin();
			final Response res = dao.emailNewPassword(username);
			localConnection.commit();
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
	 * Gets a user by ID or username.
	 * 
	 * @param userId
	 *            The ID or username of the user.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws NdexException
	 *             Failed to change the password in the database.
	 **************************************************************************/
/*	@GET
	@PermitAll
	@Path("/{userId}")
	@Produces("application/json")
	@ApiDoc("Return the user corresponding to userId, whether userId is actually a database id or a username. Error if neither is found.")
	public User getUser(@PathParam("userId") final String userId)
			throws IllegalArgumentException, NdexException {
		if (userId == null || userId.isEmpty())
			throw new IllegalArgumentException("No user ID was specified.");

		try {
			setupDatabase();

			final ORID userRid = IdConverter.toRid(userId);

			final IUser user = _orientDbGraph.getVertex(userRid, IUser.class);
			if (user != null)
				return new User(user, true);
		} catch (IllegalArgumentException ae) {
			// The user ID is actually a username
			final List<ODocument> matchingUsers = _ndexDatabase
					.query(new OSQLSynchQuery<Object>(
							"SELECT FROM User WHERE username = '" + userId
									+ "'"));
			if (!matchingUsers.isEmpty())
				return new User(_orientDbGraph.getVertex(matchingUsers.get(0),
						IUser.class), true);
		} catch (Exception e) {
			_logger.error("Failed to get user: " + userId + ".", e);
			throw new NdexException("Failed to retrieve that user.");
		} finally {
			teardownDatabase();
		}

		return null;
	}
*/
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
	public List<Request> getPendingRequest(@PathParam("userId") final String userId,
			@PathParam("skipBlocks") int skipBlocks,
			@PathParam("blockSize") int blockSize) throws NdexException {
		
		this.openDatabase();
		
		try {
			
			return dao.getPendingRequest(this.getLoggedInUser(),skipBlocks, blockSize);

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
		//graph.shutdown();
		localConnection.close();
		database.close();
	}
	
	

	/**************************************************************************
	 * Resizes the source image to the specified dimensions.
	 * 
	 * @param sourceImage
	 *            The image to resize.
	 * @param width
	 *            The new image width.
	 * @param height
	 *            The new image height.
	 **************************************************************************/
/*	private BufferedImage resizeImage(final BufferedImage sourceImage,
			final int width, final int height) {
		final Image resizeImage = sourceImage.getScaledInstance(width, height,
				Image.SCALE_SMOOTH);

		final BufferedImage resizedImage = new BufferedImage(width, height,
				Image.SCALE_SMOOTH);
		resizedImage.getGraphics().drawImage(resizeImage, 0, 0, null);

		return resizedImage;
	} */
}
