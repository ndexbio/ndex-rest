package org.ndexbio.rest.services;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.security.PermitAll;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;
import org.ndexbio.common.exceptions.*;
import org.ndexbio.common.helpers.Configuration;
import org.ndexbio.common.helpers.IdConverter;
import org.ndexbio.common.models.data.INetwork;
import org.ndexbio.common.models.data.IUser;
import org.ndexbio.common.models.object.Network;
import org.ndexbio.common.models.object.NewUser;
import org.ndexbio.common.models.object.SearchParameters;
import org.ndexbio.common.models.object.UploadedFile;
import org.ndexbio.common.models.object.User;
import org.ndexbio.rest.helpers.Email;
import org.ndexbio.rest.helpers.Security;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.OCommandSQL;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
import com.tinkerpop.blueprints.impls.orient.OrientElement;

@Path("/users")
public class UserService extends NdexService
{
    private static final Logger _logger = LoggerFactory.getLogger(UserService.class);
    
    
    
    /**************************************************************************
    * Injects the HTTP request into the base class to be used by
    * getLoggedInUser(). 
    * 
    * @param httpRequest
    *            The HTTP request injected by RESTEasy's context.
    **************************************************************************/
    public UserService(@Context HttpServletRequest httpRequest)
    {
        super(httpRequest);
    }
    
    
    
    /**************************************************************************
    * Adds a network to the user's Work Surface. 
    * 
    * @param userId
    *            The user's ID.
    * @param networkToAdd
    *            The network to add.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws ObjectNotFoundException
    *            The network wasn't found.
    * @throws NdexException
    *            Failed to add the network in the database.
    **************************************************************************/
    @PUT
    @Path("/work-surface")
    @Produces("application/json")
    public Iterable<Network> addNetworkToWorkSurface(String networkId) throws IllegalArgumentException, ObjectNotFoundException, NdexException
    {
        if (networkId == null || networkId.isEmpty())
            throw new IllegalArgumentException("The network to add is empty.");
        else
            networkId = networkId.replace("\"", "");

        final ORID userRid = IdConverter.toRid(this.getLoggedInUser().getId());
        final ORID networkRid = IdConverter.toRid(networkId);

        try
        {
            setupDatabase();
            
            final IUser user = _orientDbGraph.getVertex(userRid, IUser.class);

            final INetwork network = _orientDbGraph.getVertex(networkRid, INetwork.class);
            if (network == null)
                throw new ObjectNotFoundException("Network", networkId);
            
            final Iterable<INetwork> workSurface = user.getWorkSurface();
            if (workSurface != null)
            {
                for (INetwork checkNetwork : workSurface)
                {
                    if (checkNetwork.asVertex().getId().equals(networkRid))
                        throw new DuplicateObjectException("Network with RID: " + networkRid + " is already on the Work Surface.");
                }
            }

            user.addNetworkToWorkSurface(network);
            _orientDbGraph.getBaseGraph().commit();
            
            final ArrayList<Network> updatedWorkSurface = new ArrayList<Network>();
            final Iterable<INetwork> onWorkSurface = user.getWorkSurface();
            if (onWorkSurface != null)
            {
                for (INetwork workSurfaceNetwork : onWorkSurface)
                    updatedWorkSurface.add(new Network(workSurfaceNetwork));
            }
            
            return updatedWorkSurface;
        }
        catch (DuplicateObjectException | ObjectNotFoundException onfe)
        {
            throw onfe;
        }
        catch (Exception e)
        {
            _logger.error("Failed to add a network to " + this.getLoggedInUser().getUsername() + "'s Work Surface.", e);
            _orientDbGraph.getBaseGraph().rollback(); 
            throw new NdexException("Failed to add the network to your Work Surface.");
        }
        finally
        {
            teardownDatabase();
        }
    }

    /**************************************************************************
    * Authenticates a user trying to login. 
    * 
    * @param username
    *            The username.
    * @param password
    *            The password.
    * @throws SecurityException
    *            Invalid username or password.
    * @throws NdexException
    *            Can't authenticate users against the database.
    * @return The authenticated user's information.
    **************************************************************************/
    @GET
    @PermitAll
    @Path("/authenticate/{username}/{password}")
    @Produces("application/json")
    public User authenticateUser(@PathParam("username")final String username, @PathParam("password")final String password) throws SecurityException, NdexException
    {
        if (username == null || username.isEmpty() || password == null || password.isEmpty())
            throw new SecurityException("Invalid username or password.");
            
        try
        {
            final User authUser = Security.authenticateUser(new String[] { username, password });
            if (authUser == null)
                throw new SecurityException("Invalid username or password.");
            
            return authUser;
        }
        catch (SecurityException se)
        {
            throw se;
        }
        catch (Exception e)
        {
            _logger.error("Can't authenticate users.", e);
            throw new NdexException("There's a problem with the authentication server. Please try again later.");
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
    *            Bad input.
    * @throws NdexException
    *            Failed to change the password in the database.
    **************************************************************************/
    @POST
    @Path("/password")
    @Produces("application/json")
    public void changePassword(String password) throws IllegalArgumentException, NdexException
    {
        if (password == null || password.isEmpty())
            throw new IllegalArgumentException("No password was specified.");
        
        final ORID userRid = IdConverter.toRid(this.getLoggedInUser().getId());
        
        try
        {
            //Remove quotes around the password
            if (password.startsWith("\""))
                password = password.substring(1);
            if (password.endsWith("\""))
                password = password.substring(0, password.length() - 1);
            
            setupDatabase();
            
            final IUser existingUser = _orientDbGraph.getVertex(userRid, IUser.class);
            existingUser.setPassword(Security.hashText(password.trim()));
            
            _orientDbGraph.getBaseGraph().commit();
        }
        catch (Exception e)
        {
            _logger.error("Failed to change " + this.getLoggedInUser().getUsername() + "'s password.", e);
            _orientDbGraph.getBaseGraph().rollback(); 
            throw new NdexException("Failed to change your password.");
        }
        finally
        {
            teardownDatabase();
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
    *            Bad input.
    * @throws NdexException
    *            Failed to save the image.
    **************************************************************************/
    @POST
    @Path("/image/{imageType}")
    @Consumes("multipart/form-data")
    @Produces("application/json")
    public void changeProfileImage(@PathParam("imageType")final String imageType, @MultipartForm UploadedFile uploadedImage) throws IllegalArgumentException, NdexException
    {
        if (imageType == null || imageType.isEmpty())
            throw new IllegalArgumentException("No image type specified.");
        else if (uploadedImage == null || uploadedImage.getFileData().length < 1)
            throw new IllegalArgumentException("No uploaded image.");
        
        final ORID userId = IdConverter.toRid(this.getLoggedInUser().getId());
        
        try
        {
            setupDatabase();

            final IUser user = _orientDbGraph.getVertex(userId, IUser.class);
            if (user == null)
                throw new ObjectNotFoundException("User", this.getLoggedInUser().getId());

            final BufferedImage newImage = ImageIO.read(new ByteArrayInputStream(uploadedImage.getFileData()));
            
            if (imageType.toLowerCase().equals("profile"))
            {
                final BufferedImage resizedImage = resizeImage(newImage,
                    Integer.parseInt(Configuration.getInstance().getProperty("Profile-Image-Width")),
                    Integer.parseInt(Configuration.getInstance().getProperty("Profile-Image-Height")));
                
                ImageIO.write(resizedImage, "jpg", new File(Configuration.getInstance().getProperty("Profile-Image-Path") + user.getUsername() + ".jpg"));
            }
            else
            {
                final BufferedImage resizedImage = resizeImage(newImage,
                    Integer.parseInt(Configuration.getInstance().getProperty("Profile-Background-Width")),
                    Integer.parseInt(Configuration.getInstance().getProperty("Profile-Background-Height")));
                
                ImageIO.write(resizedImage, "jpg", new File(Configuration.getInstance().getProperty("Profile-Background-Path") + user.getUsername() + ".jpg"));
            }
        }
        catch (ObjectNotFoundException onfe)
        {
            throw onfe;
        }
        catch (Exception e)
        {
            _logger.error("Failed to save a profile image.", e);
            throw new NdexException(e.getMessage());
        }
        finally
        {
            teardownDatabase();
        }
    }
    
    /**************************************************************************
    * Creates a user. 
    * 
    * @param newUser
    *            The user to create.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws DuplicateObjectException
    *            A user with the same username/email address already exists.
    * @throws NdexException
    *            Failed to create the user in the database.
    * @return The new user's profile.
    **************************************************************************/
    @PUT
    @PermitAll
    @Produces("application/json")
    public User createUser(final NewUser newUser) throws IllegalArgumentException, DuplicateObjectException, NdexException
    {
        if (newUser == null)
            throw new IllegalArgumentException("The new user is empty.");
        else if (newUser.getUsername() == null || newUser.getUsername().isEmpty())
            throw new IllegalArgumentException("Username is empty.");
        else if (newUser.getPassword() == null || newUser.getPassword().isEmpty())
            throw new IllegalArgumentException("Password is empty.");
        else if (newUser.getEmailAddress() == null || newUser.getEmailAddress().isEmpty())
            throw new IllegalArgumentException("Email address is empty.");
        
        try
        {
            setupDatabase();
            
            final IUser user = _orientDbGraph.addVertex("class:user", IUser.class);
            user.setUsername(newUser.getUsername());
            user.setPassword(Security.hashText(newUser.getPassword()));
            user.setEmailAddress(newUser.getEmailAddress());
            
            _orientDbGraph.getBaseGraph().commit();
            return new User(user);
        }
        catch (Exception e)
        {
            if (e.getMessage().indexOf(" duplicated key ") > -1)
                throw new DuplicateObjectException("A user with that name (" + newUser.getUsername() + ") or email address (" + newUser.getEmailAddress() + ") already exists.");
            
            _logger.error("Failed to create a new user: " + newUser.getUsername() + ".", e);
            _orientDbGraph.getBaseGraph().rollback(); 
            throw new NdexException(e.getMessage());
        }
        finally
        {
            teardownDatabase();
        }
    }

    /**************************************************************************
    * Deletes a network from a user's Work Surface.
    * 
    * @param networkToDelete
    *            The network being removed.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws ObjectNotFoundException
    *            The network doesn't exist.
    * @throws NdexException
    *            Failed to remove the network in the database.
    **************************************************************************/
    @DELETE
    @Path("/work-surface/{networkId}")
    @Produces("application/json")
    public Iterable<Network> deleteNetworkFromWorkSurface(@PathParam("networkId")final String networkId) throws IllegalArgumentException, ObjectNotFoundException, NdexException
    {
        if (networkId == null || networkId.isEmpty())
            throw new IllegalArgumentException("The network to delete is empty.");

        final ORID userRid = IdConverter.toRid(this.getLoggedInUser().getId());
        final ORID networkRid = IdConverter.toRid(networkId);

        try
        {
            setupDatabase();
            
            final IUser user = _orientDbGraph.getVertex(userRid, IUser.class);

            final INetwork network = _orientDbGraph.getVertex(networkRid, INetwork.class);
            if (network == null)
                throw new ObjectNotFoundException("Network", networkId);
            
            user.removeNetworkFromWorkSurface(network);
            _orientDbGraph.getBaseGraph().commit();
            
            final ArrayList<Network> updatedWorkSurface = new ArrayList<Network>();
            final Iterable<INetwork> onWorkSurface = user.getWorkSurface();
            if (onWorkSurface != null)
            {
                for (INetwork workSurfaceNetwork : onWorkSurface)
                    updatedWorkSurface.add(new Network(workSurfaceNetwork));
            }
            
            return updatedWorkSurface;
        }
        catch (ObjectNotFoundException onfe)
        {
            throw onfe;
        }
        catch (Exception e)
        {
            _logger.error("Failed to remove a network from " + this.getLoggedInUser().getUsername() + "'s Work Surface.", e);
            _orientDbGraph.getBaseGraph().rollback(); 
            throw new NdexException("Failed to remove the network from your Work Surface.");
        }
        finally
        {
            teardownDatabase();
        }
    }
    
    /**************************************************************************
    * Deletes a user.
    * 
    * @throws NdexException
    *            Failed to delete the user from the database.
    **************************************************************************/
    @DELETE
    @Produces("application/json")
    public void deleteUser() throws NdexException
    {
        final ORID userRid = IdConverter.toRid(this.getLoggedInUser().getId());
        
        try
        {
            setupDatabase();
            
            final IUser userToDelete = _orientDbGraph.getVertex(userRid, IUser.class);
            
            final List<ODocument> adminGroups = _ndexDatabase.query(new OSQLSynchQuery<Integer>("SELECT COUNT(@RID) FROM Membership WHERE in_groups = " + userRid + " AND permissions = 'ADMIN'"));
            if (adminGroups == null || adminGroups.isEmpty())
                throw new NdexException("Unable to query user/group membership.");
            else if ((long)adminGroups.get(0).field("COUNT") > 1)
                throw new NdexException("Cannot delete a user that is an ADMIN member of any group.");

            final List<ODocument> adminNetworks = _ndexDatabase.query(new OSQLSynchQuery<Integer>("SELECT COUNT(@RID) FROM Membership WHERE in_networks = " + userRid + " AND permissions = 'ADMIN'"));
            if (adminNetworks == null || adminNetworks.isEmpty())
                throw new NdexException("Unable to query user/network membership.");
            else if ((long)adminNetworks.get(0).field("COUNT") > 1)
                throw new NdexException("Cannot delete a user that is an ADMIN member of any network.");

            final List<ODocument> userChildren = _ndexDatabase.query(new OSQLSynchQuery<Object>("SELECT @RID FROM (TRAVERSE * FROM " + userRid + " WHILE @class <> 'user')"));
            for (ODocument userChild : userChildren)
            {
                final ORID childId = userChild.field("rid", OType.LINK);

                final OrientElement element = _orientDbGraph.getBaseGraph().getElement(childId);
                if (element != null)
                    element.remove();
            }

            _orientDbGraph.removeVertex(userToDelete.asVertex());
            _orientDbGraph.getBaseGraph().commit();
        }
        catch (NdexException ne)
        {
            throw ne;
        }
        catch (Exception e)
        {
            _logger.error("Failed to delete user: " + this.getLoggedInUser().getUsername() + ".", e);
            _orientDbGraph.getBaseGraph().rollback(); 
            throw new NdexException(e.getMessage());
        }
        finally
        {
            teardownDatabase();
        }
    }

    /**************************************************************************
    * Emails the user a new randomly generated password.
    * 
    * @param username
    *            The username of the user.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws NdexException
    *            Failed to change the password in the database, or failed
    *            to send the email.
    **************************************************************************/
    @GET
    @PermitAll
    @Path("/{username}/forgot-password")
    @Produces("application/json")
    public Response emailNewPassword(@PathParam("username")final String username) throws IllegalArgumentException, NdexException
    {
        //TODO: In the future security questions should be implemented - right
        //now anyone can change anyone else's password to a randomly generated
        //password
        if (username == null || username.isEmpty())
            throw new IllegalArgumentException("No username was specified.");

        try
        {
            setupDatabase();
            
            final Collection<ODocument> usersFound = _ndexDatabase
                .command(new OCommandSQL("select from User where username = ?"))
                .execute(username);
            
            if (usersFound.size() < 1)
                throw new ObjectNotFoundException("User", username);

            final IUser authUser = _orientDbGraph.getVertex(usersFound.toArray()[0], IUser.class);
            
            final String newPassword = Security.generatePassword();
            authUser.setPassword(Security.hashText(newPassword));
            
            final File forgotPasswordFile = new File(Configuration.getInstance().getProperty("Forgot-Password-File"));
            if (!forgotPasswordFile.exists())
                throw new java.io.FileNotFoundException("File containing forgot password email content doesn't exist.");
            
            final BufferedReader fileReader = Files.newBufferedReader(forgotPasswordFile.toPath(), Charset.forName("US-ASCII"));
            final StringBuilder forgotPasswordText = new StringBuilder();

            String lineOfText = null;
            while ((lineOfText = fileReader.readLine()) != null)
                forgotPasswordText.append(lineOfText.replace("{password}", newPassword));
            
            Email.sendEmail(Configuration.getInstance().getProperty("Forgot-Password-Email"),
                authUser.getEmailAddress(),
                "Password Recovery",
                forgotPasswordText.toString());
            
            _orientDbGraph.getBaseGraph().commit();
            return Response.ok().build();
        }
        catch (ObjectNotFoundException onfe)
        {
            throw onfe;
        }
        catch (Exception e)
        {
            _logger.error("Failed to change " + username + "'s password.", e);
            _orientDbGraph.getBaseGraph().rollback(); 
            throw new NdexException("Failed to recover your password.");
        }
        finally
        {
            teardownDatabase();
        }
    }
    
    /**************************************************************************
    * Finds users based on the search parameters.
    * 
    * @param searchParameters
    *            The search parameters.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws NdexException
    *            Failed to query the database.
    **************************************************************************/
    @POST
    @PermitAll
    @Path("/search")
    @Produces("application/json")
    public List<User> findUsers(SearchParameters searchParameters) throws IllegalArgumentException, NdexException
    {
        if (searchParameters == null)
            throw new IllegalArgumentException("Search Parameters are empty.");
        else if (searchParameters.getSearchString() == null || searchParameters.getSearchString().isEmpty())
            throw new IllegalArgumentException("No search string was specified.");
        else
            searchParameters.setSearchString(searchParameters.getSearchString().toLowerCase().trim());
        
        final List<User> foundUsers = new ArrayList<User>();
        
        final int startIndex = searchParameters.getSkip() * searchParameters.getTop();

        final String query = "SELECT FROM User\n"
            + "WHERE username.toLowerCase() LIKE '%" + searchParameters.getSearchString() + "%'\n"
            + "  OR lastName.toLowerCase() LIKE '%" + searchParameters.getSearchString() + "%'\n"
            + "  OR firstName.toLowerCase() LIKE '%" + searchParameters.getSearchString() + "%'\n"            
            + "ORDER BY creation_date DESC\n"
            + "SKIP " + startIndex + "\n"
            + "LIMIT " + searchParameters.getTop();
        
        try
        {
            setupDatabase();
            
            final List<ODocument> users = _ndexDatabase.query(new OSQLSynchQuery<ODocument>(query));
            for (final ODocument user : users)
                foundUsers.add(new User(_orientDbGraph.getVertex(user, IUser.class)));
    
            return foundUsers;
        }
        catch (Exception e)
        {
            _logger.error("Failed to search for users: " + searchParameters.getSearchString() + ".", e);
            _orientDbGraph.getBaseGraph().rollback(); 
            throw new NdexException("Failed to search for users.");
        }
        finally
        {
            teardownDatabase();
        }
    }
    
    /**************************************************************************
    * Gets a user by ID or username.
    * 
    * @param userId
    *            The ID or username of the user.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws NdexException
    *            Failed to change the password in the database.
    **************************************************************************/
    @GET
    @PermitAll
    @Path("/{userId}")
    @Produces("application/json")
    public User getUser(@PathParam("userId")final String userId) throws IllegalArgumentException, NdexException
    {
        if (userId == null || userId.isEmpty())
            throw new IllegalArgumentException("No user ID was specified.");
        
        try
        {
            setupDatabase();
            
            final ORID userRid = IdConverter.toRid(userId);
            
            final IUser user = _orientDbGraph.getVertex(userRid, IUser.class);
            if (user != null)
                return new User(user, true);
        }
        catch (IllegalArgumentException ae)
        {
            //The user ID is actually a username
            final List<ODocument> matchingUsers = _ndexDatabase.query(new OSQLSynchQuery<Object>("SELECT FROM User WHERE username = '" + userId + "'"));
            if (!matchingUsers.isEmpty())
                return new User(_orientDbGraph.getVertex(matchingUsers.get(0), IUser.class), true);
        }
        catch (Exception e)
        {
            _logger.error("Failed to get user: " + userId + ".", e);
            throw new NdexException("Failed to retrieve that user.");
        }
        finally
        {
            teardownDatabase();
        }
        
        return null;
    }

    /**************************************************************************
    * Updates a user.
    * 
    * @param updatedUser
    *            The updated user information.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws SecurityException
    *            Users trying to update someone else.
    * @throws NdexException
    *            Failed to update the user in the database.
    **************************************************************************/
    @POST
    @Produces("application/json")
    public void updateUser(final User updatedUser) throws IllegalArgumentException, SecurityException, NdexException
    {
        if (updatedUser == null)
            throw new IllegalArgumentException("The updated user is empty.");
        else if (!updatedUser.getId().equals(this.getLoggedInUser().getId()))
            throw new SecurityException("You cannot update other users.");
        	
        final ORID userRid = IdConverter.toRid(this.getLoggedInUser().getId());
        
        try
        {
            setupDatabase();
            
            final IUser userToUpdate = _orientDbGraph.getVertex(userRid, IUser.class);

            if (updatedUser.getBackgroundImage() != null && !updatedUser.getBackgroundImage().equals(userToUpdate.getBackgroundImage()))
                userToUpdate.setBackgroundImage(updatedUser.getBackgroundImage());
    
            if (updatedUser.getDescription() != null && !updatedUser.getDescription().equals(userToUpdate.getDescription()))
                userToUpdate.setDescription(updatedUser.getDescription());
            
            if (updatedUser.getEmailAddress() != null && !updatedUser.getEmailAddress().equals(userToUpdate.getEmailAddress()))
                userToUpdate.setEmailAddress(updatedUser.getEmailAddress());
            
            if (updatedUser.getFirstName() != null && !updatedUser.getFirstName().equals(userToUpdate.getFirstName()))
                userToUpdate.setFirstName(updatedUser.getFirstName());
    
            if (updatedUser.getForegroundImage() != null && !updatedUser.getForegroundImage().equals(userToUpdate.getForegroundImage()))
                userToUpdate.setForegroundImage(updatedUser.getForegroundImage());
    
            if (updatedUser.getLastName() != null && !updatedUser.getLastName().equals(userToUpdate.getLastName()))
                userToUpdate.setLastName(updatedUser.getLastName());
    
            if (updatedUser.getWebsite() != null && !updatedUser.getWebsite().equals(userToUpdate.getWebsite()))
                userToUpdate.setWebsite(updatedUser.getWebsite());

            _orientDbGraph.getBaseGraph().commit();
        }
        catch (Exception e)
        {
            if (e.getMessage().indexOf("cluster: null") > -1)
                throw new ObjectNotFoundException("User", updatedUser.getId());
            
            _logger.error("Failed to update user: " + this.getLoggedInUser().getUsername() + ".", e);
            _orientDbGraph.getBaseGraph().rollback(); 
            throw new NdexException("Failed to update your profile.");
        }
        finally
        {
            teardownDatabase();
        }
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
    private BufferedImage resizeImage(final BufferedImage sourceImage, final int width, final int height)
    {
        final Image resizeImage = sourceImage.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        
        final BufferedImage resizedImage = new BufferedImage(width, height, Image.SCALE_SMOOTH);
        resizedImage.getGraphics().drawImage(resizeImage, 0, 0, null);
        
        return resizedImage;
    }
}
