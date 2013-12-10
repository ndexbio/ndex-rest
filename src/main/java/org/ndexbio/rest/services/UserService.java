package org.ndexbio.rest.services;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.annotation.security.PermitAll;
import javax.imageio.ImageIO;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import org.jboss.resteasy.client.exception.ResteasyAuthenticationException;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.ndexbio.rest.domain.INetwork;
import org.ndexbio.rest.domain.IUser;
import org.ndexbio.rest.exceptions.DuplicateObjectException;
import org.ndexbio.rest.exceptions.NdexException;
import org.ndexbio.rest.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.exceptions.ValidationException;
import org.ndexbio.rest.helpers.Email;
import org.ndexbio.rest.helpers.RidConverter;
import org.ndexbio.rest.helpers.Security;
import org.ndexbio.rest.models.Network;
import org.ndexbio.rest.models.NewUser;
import org.ndexbio.rest.models.SearchParameters;
import org.ndexbio.rest.models.SearchResult;
import org.ndexbio.rest.models.User;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.OCommandSQL;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
import com.tinkerpop.blueprints.impls.orient.OrientElement;

@Path("/users")
public class UserService extends NdexService
{
    /**************************************************************************
    * Execute parent default constructor to initialize OrientDB.
    **************************************************************************/
    public UserService()
    {
        super();
    }
    
    
    
    /**************************************************************************
    * Adds a network to the user's Work Surface. 
    * 
    * @param userId       The user's ID.
    * @param networkToAdd The network to add.
    **************************************************************************/
    @PUT
    @Path("/{userId}/work-surface")
    @Produces("application/json")
    public void addNetworkToWorkSurface(@PathParam("userId")final String userJid, final Network networkToAdd) throws Exception
    {
        if (userJid == null || userJid.isEmpty())
            throw new ValidationException("The user ID wasn't specified.");
        else if (networkToAdd == null)
            throw new ValidationException("The network to add is empty.");

        final ORID userRid = RidConverter.convertToRid(userJid);
        final ORID networkRid = RidConverter.convertToRid(networkToAdd.getId());

        try
        {
            setupDatabase();
            
            final IUser user = _orientDbGraph.getVertex(userRid, IUser.class);
            if (user == null)
                throw new ObjectNotFoundException("User", userJid);

            final INetwork network = _orientDbGraph.getVertex(networkRid, INetwork.class);
            if (network == null)
                throw new ObjectNotFoundException("Network", networkToAdd.getId());
            
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
        }
        catch (Exception e)
        {
            _orientDbGraph.getBaseGraph().rollback(); 
            throw e;
        }
        finally
        {
            teardownDatabase();
        }
    }

    /**************************************************************************
    * Authenticates a user trying to login. 
    * 
    * @param username The username.
    * @param password The password.
    **************************************************************************/
    @GET
    @PermitAll
    @Path("/authenticate/{username}/{password}")
    @Produces("application/json")
    public User authenticateUser(@PathParam("username")final String username, @PathParam("password")final String password) throws Exception
    {
        if (username == null || username.isEmpty() || password == null || password.isEmpty())
            throw new ResteasyAuthenticationException("Invalid username or password.");
            
        final User authUser = Security.authenticateUser(new String[] { username, password });
        if (authUser == null)
            throw new ResteasyAuthenticationException("Invalid username or password.");
        
        return authUser;
    }
    
    /**************************************************************************
    * Changes a user's password.
    * 
    * @param userId   The user ID.
    * @param password The new password.
    **************************************************************************/
    @POST
    @Path("/{userId}/password")
    @Produces("application/json")
    public void changePassword(@PathParam("userId")final String userJid, String password) throws Exception
    {
        if (userJid == null || userJid.isEmpty())
            throw new ValidationException("No user ID was specified.");
        else if (password == null || password.isEmpty())
            throw new ValidationException("No password was specified.");
        else if (!userJid.equals(this.getLoggedInUser().getId()))
            throw new ResteasyAuthenticationException("Access denied.");
        
        final ORID userRid = RidConverter.convertToRid(userJid);
        
        try
        {
            //Remove quotes around the password
            if (password.startsWith("\""))
                password = password.substring(1);
            if (password.endsWith("\""))
                password = password.substring(0, password.length() - 1);
            
            setupDatabase();
            
            final IUser existingUser = _orientDbGraph.getVertex(userRid, IUser.class);
            if (existingUser == null)
                throw new ObjectNotFoundException("User", userJid);

            existingUser.setPassword(Security.hashText(password.trim()));
            
            _orientDbGraph.getBaseGraph().commit();
        }
        catch (Exception e)
        {
            _orientDbGraph.getBaseGraph().rollback(); 
            throw e;
        }
        finally
        {
            teardownDatabase();
        }
    }

    /**************************************************************************
    * Changes a user's profile background image.
    * 
    * @param userId   The user ID.
    * @param password The new password.
    **************************************************************************/
    @POST
    @Path("/{userId}/profile-background")
    @Produces("application/json")
    public void changeProfileBackground(@PathParam("userId")final String userJid, MultipartFormDataInput formData) throws Exception
    {
        final Map<String, List<InputPart>> mappedFormData = formData.getFormDataMap();
        final List<InputPart> uploadedImages = mappedFormData.get("fileNewImage");
        
        if (uploadedImages == null || uploadedImages.isEmpty())
            throw new ValidationException("No uploaded image.");
        else if (!userJid.equals(this.getLoggedInUser().getId()))
            throw new ResteasyAuthenticationException("Access denied.");
        
        final ORID userId = RidConverter.convertToRid(userJid);
        
        try
        {
            setupDatabase();

            final IUser user = _orientDbGraph.getVertex(userId, IUser.class);
            if (user == null)
                throw new ObjectNotFoundException("User", userJid);

            final InputPart newImage = uploadedImages.get(0);
            final InputStream newFile = newImage.getBody(InputStream.class, null);
            final BufferedImage uploadedImage = ImageIO.read(newFile);
            
            //TODO: Refactor this to pull settings from a configuration file
            final BufferedImage resizedImage = resizeImage(uploadedImage, 670, 200);
            
            //TODO: Refactor this to pull settings from a configuration file
            String filePath = new File("").getAbsolutePath(); 
            if (filePath.startsWith("/home"))
                ImageIO.write(resizedImage, "jpg", new File(filePath + "/Projects/NDEx-Site/img/background/" + user.getUsername() + ".jpg"));
            else if (filePath.startsWith("/opt/ndex"))
                ImageIO.write(resizedImage, "jpg", new File("/opt/ndex/accountImg/background/" + user.getUsername() + ".jpg"));

            else
                ImageIO.write(resizedImage, "jpg", new File("/var/node/ndex-site/img/background/" + user.getUsername() + ".jpg"));
        }
        finally
        {
            teardownDatabase();
        }
    }
    
    /**************************************************************************
    * Changes a user's profile image.
    * 
    * @param userId   The user ID.
    * @param password The new password.
    **************************************************************************/
    @POST
    @Path("/{userId}/profile-image")
    @Produces("application/json")
    public void changeProfileImage(@PathParam("userId")final String userJid, MultipartFormDataInput formData) throws Exception
    {
        final Map<String, List<InputPart>> mappedFormData = formData.getFormDataMap();
        final List<InputPart> uploadedImages = mappedFormData.get("fileNewImage");
        
        if (uploadedImages == null || uploadedImages.isEmpty())
            throw new ValidationException("No uploaded image.");
        else if (!userJid.equals(this.getLoggedInUser().getId()))
            throw new ResteasyAuthenticationException("Access denied.");
        
        final ORID userId = RidConverter.convertToRid(userJid);
        
        try
        {
            setupDatabase();

            final IUser user = _orientDbGraph.getVertex(userId, IUser.class);
            if (user == null)
                throw new ObjectNotFoundException("User", userJid);

            final InputPart newImage = uploadedImages.get(0);
            final InputStream newFile = newImage.getBody(InputStream.class, null);
            final BufferedImage uploadedImage = ImageIO.read(newFile);
            
            //TODO: Refactor this to pull settings from a configuration file
            final BufferedImage resizedImage = resizeImage(uploadedImage, 100, 125);
            
            //TODO: Refactor this to pull settings from a configuration file
            String filePath = new File("").getAbsolutePath(); 
            if (filePath.startsWith("/home"))
                ImageIO.write(resizedImage, "jpg", new File(filePath + "/Projects/NDEx-Site/img/foreground/" + user.getUsername() + ".jpg"));
            else if (filePath.startsWith("/opt/ndex"))
                ImageIO.write(resizedImage, "jpg", new File("/opt/ndex/accountImg/foreground/" + user.getUsername() + ".jpg"));

            else
                ImageIO.write(resizedImage, "jpg", new File("/var/node/ndex-site/img/foreground/" + user.getUsername() + ".jpg"));
        }
        catch (Exception e)
        {
            throw e;
        }
        finally
        {
            teardownDatabase();
        }
    }
    
    /**************************************************************************
    * Creates a user. 
    * 
    * @param newUser  The user to create.
    **************************************************************************/
    @PUT
    @PermitAll
    @Produces("application/json")
    public User createUser(final NewUser newUser) throws Exception
    {
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
            _orientDbGraph.getBaseGraph().rollback(); 
            throw e;
        }
        finally
        {
            teardownDatabase();
        }
    }

    /**************************************************************************
    * Deletes a network from a user's Work Surface.
    * 
    * @param userId          The ID of the user.
    * @param networkToDelete The network being removed.
    **************************************************************************/
    @DELETE
    @Path("/{userId}/work-surface")
    @Produces("application/json")
    public void deleteNetworkFromWorkSurface(@PathParam("userId")final String userJid, final Network networkToDelete) throws Exception
    {
        if (userJid == null || userJid.isEmpty())
            throw new ValidationException("No user ID was specified.");
        else if (networkToDelete == null)
            throw new ValidationException("The network to delete is empty.");
        else if (!userJid.equals(this.getLoggedInUser().getId()))
            throw new ResteasyAuthenticationException("Access denied.");

        final ORID userRid = RidConverter.convertToRid(userJid);
        final ORID networkRid = RidConverter.convertToRid(networkToDelete.getId());

        try
        {
            setupDatabase();
            
            final IUser user = _orientDbGraph.getVertex(userRid, IUser.class);
            if (user == null)
                throw new ObjectNotFoundException("User", userJid);

            final INetwork network = _orientDbGraph.getVertex(networkRid, INetwork.class);
            if (network == null)
                throw new ObjectNotFoundException("Network", networkToDelete.getId());
            
            user.removeNetworkFromWorkSurface(network);
            
            _orientDbGraph.getBaseGraph().commit();
        }
        catch (Exception e)
        {
            _orientDbGraph.getBaseGraph().rollback(); 
            throw e;
        }
        finally
        {
            teardownDatabase();
        }
    }
    
    /**************************************************************************
    * Deletes a user.
    * 
    * @param userId The ID of the user to delete.
    **************************************************************************/
    @DELETE
    @Path("/{userId}")
    @Produces("application/json")
    public void deleteUser(@PathParam("userId")final String userJid) throws Exception
    {
        if (userJid == null || userJid.isEmpty())
            throw new ValidationException("No user ID was specified.");
        else if (!userJid.equals(this.getLoggedInUser().getId()))
            throw new ResteasyAuthenticationException("Access denied.");

        final ORID userRid = RidConverter.convertToRid(userJid);
        
        try
        {
            setupDatabase();
            
            final IUser userToDelete = _orientDbGraph.getVertex(userRid, IUser.class);
            
            if (userToDelete == null)
                throw new ObjectNotFoundException("User", userJid);
            
            final List<ODocument> adminGroups = _ndexDatabase.query(new OSQLSynchQuery<Integer>("select count(*) from Membership where in_groups = ? and permissions = 'ADMIN'"));
            if (adminGroups == null || adminGroups.isEmpty())
                throw new Exception("Unable to query user/group membership.");
            else if ((long)adminGroups.get(0).field("count") > 1)
                throw new NdexException("Cannot delete a user that is an ADMIN member of any group.");

            final List<ODocument> adminNetworks = _ndexDatabase.query(new OSQLSynchQuery<Integer>("select count(*) from Membership where in_networks = ? and permissions = 'ADMIN'"));
            if (adminNetworks == null || adminNetworks.isEmpty())
                throw new Exception("Unable to query user/network membership.");
            else if ((long)adminNetworks.get(0).field("count") > 1)
                throw new NdexException("Cannot delete a user that is an ADMIN member of any network.");

            final List<ODocument> userChildren = _ndexDatabase.query(new OSQLSynchQuery<Object>("select @rid from (traverse * from " + userRid + ")"));
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
        catch (Exception e)
        {
            _orientDbGraph.getBaseGraph().rollback(); 
            throw e;
        }
        finally
        {
            teardownDatabase();
        }
    }

    /**************************************************************************
    * Emails the user a new randomly generated password..
    * 
    * @param username The username of the user.
    **************************************************************************/
    @GET
    @PermitAll
    @Path("/{username}/forgot-password")
    @Produces("application/json")
    public Response emailNewPassword(@PathParam("username")final String username) throws Exception
    {
        if (username == null || username.isEmpty())
            throw new ValidationException("No username was specified.");

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
            
            //TODO: This should be refactored to use a configuration file and a text file for the email content
            Email.sendEmail("support@ndexbio.org", authUser.getEmailAddress(), "Password Recovery", "Your new password is:\t" + newPassword);
            
            _orientDbGraph.getBaseGraph().commit();
            return Response.ok().build();
        }
        catch (Exception e)
        {
            _orientDbGraph.getBaseGraph().rollback(); 
            throw e;
        }
        finally
        {
            teardownDatabase();
        }
    }
    
    /**************************************************************************
    * Finds users based on the search parameters.
    * 
    * @param searchParameters The search parameters.
    **************************************************************************/
    @POST
    @PermitAll
    @Path("/search")
    @Produces("application/json")
    public SearchResult<User> findUsers(SearchParameters searchParameters) throws NdexException
    {
        if (searchParameters.getSearchString() == null || searchParameters.getSearchString().isEmpty())
            throw new ValidationException("No search string was specified.");
        else
            searchParameters.setSearchString(searchParameters.getSearchString().toUpperCase().trim());
        
        final List<User> foundUsers = new ArrayList<User>();
        final SearchResult<User> result = new SearchResult<User>();
        result.setResults(foundUsers);
        
        //TODO: Remove these, they're unnecessary
        result.setPageSize(searchParameters.getTop());
        result.setSkip(searchParameters.getSkip());
        
        final int startIndex = searchParameters.getSkip() * searchParameters.getTop();

        String whereClause = " where username.toUpperCase() like '%" + searchParameters.getSearchString()
                    + "%' OR lastName.toUpperCase() like '%" + searchParameters.getSearchString()
                    + "%' OR firstName.toUpperCase() like '%" + searchParameters.getSearchString() + "%'";

        final String query = "select from User " + whereClause
                + " order by creation_date desc skip " + startIndex + " limit " + searchParameters.getTop();
        
        try
        {
            setupDatabase();
            
            List<ODocument> userDocumentList = _orientDbGraph
                .getBaseGraph()
                .getRawGraph()
                .query(new OSQLSynchQuery<ODocument>(query));
            
            for (final ODocument document : userDocumentList)
                foundUsers.add(new User(_orientDbGraph.getVertex(document, IUser.class)));
    
            result.setResults(foundUsers);
            
            return result;
        }
        catch (Exception e)
        {
            _orientDbGraph.getBaseGraph().rollback(); 
            throw e;
        }
        finally
        {
            teardownDatabase();
        }
    }
    
    /**************************************************************************
    * Gets a user by ID or username.
    * 
    * @param userId The ID or username of the user.
    **************************************************************************/
    @GET
    @PermitAll
    @Path("/{userId}")
    @Produces("application/json")
    public User getUser(@PathParam("userId")final String userJid) throws Exception
    {
        if (userJid == null || userJid.isEmpty())
            throw new ValidationException("No user ID was specified.");
        
        try
        {
            setupDatabase();
            
            final ORID userId = RidConverter.convertToRid(userJid);
            
            final IUser user = _orientDbGraph.getVertex(userId, IUser.class);
            if (user != null)
                return new User(user, true);
        }
        catch (ValidationException ve)
        {
            //The user ID is actually a username
            final List<ODocument> matchingUsers = _ndexDatabase.query(new OSQLSynchQuery<Object>("select from User where username = '" + userJid + "'"));
            if (!matchingUsers.isEmpty())
                return new User(_orientDbGraph.getVertex(matchingUsers.get(0), IUser.class), true);
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
    * @param updatedUser The updated user information.
    **************************************************************************/
    @POST
    @Produces("application/json")
    public void updateUser(final User updatedUser) throws Exception
    {
        if (updatedUser == null)
            throw new ValidationException("The updated user is empty.");
        else 
        	if (null != this.servletRequest) // don't check if we are running locally.
        		if (!updatedUser.getId().equals(this.getLoggedInUser().getId()))
        				throw new ResteasyAuthenticationException("Access denied.");
        	
        final ORID userRid = RidConverter.convertToRid(updatedUser.getId());
        
        try
        {
            setupDatabase();
            
            final IUser existingUser = _orientDbGraph.getVertex(userRid, IUser.class);
            if (existingUser == null)
                throw new ObjectNotFoundException("User", updatedUser.getId());

            if (updatedUser.getBackgroundImage() != null && !updatedUser.getBackgroundImage().isEmpty())
                existingUser.setBackgroundImage(updatedUser.getBackgroundImage());
    
            if (updatedUser.getDescription() != null && !updatedUser.getDescription().isEmpty())
                existingUser.setDescription(updatedUser.getDescription());
            
            if (updatedUser.getEmailAddress() != null && !updatedUser.getEmailAddress().isEmpty())
                existingUser.setEmailAddress(updatedUser.getEmailAddress());
            
            if (updatedUser.getFirstName() != null && !updatedUser.getFirstName().isEmpty())
                existingUser.setFirstName(updatedUser.getFirstName());
    
            if (updatedUser.getForegroundImage() != null && !updatedUser.getForegroundImage().isEmpty())
                existingUser.setForegroundImage(updatedUser.getForegroundImage());
    
            if (updatedUser.getLastName() != null && !updatedUser.getLastName().isEmpty())
                existingUser.setLastName(updatedUser.getLastName());
    
            if (updatedUser.getWebsite() != null && !updatedUser.getWebsite().isEmpty())
                existingUser.setWebsite(updatedUser.getWebsite());

            _orientDbGraph.getBaseGraph().commit();
        }
        catch (Exception e)
        {
            _orientDbGraph.getBaseGraph().rollback(); 
            throw e;
        }
        finally
        {
            teardownDatabase();
        }
    }



    
    /**************************************************************************
    * Resizes the source image to the specified dimensions.
    * 
    * @param sourceImage The image to resize.
    * @param width       The new image width.
    * @param height      The new image height.
    **************************************************************************/
    private BufferedImage resizeImage(final BufferedImage sourceImage, final int width, final int height)
    {
        final Image resizeImage = sourceImage.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        
        final BufferedImage resizedImage = new BufferedImage(width, height, Image.SCALE_SMOOTH);
        resizedImage.getGraphics().drawImage(resizeImage, 0, 0, null);
        
        return resizedImage;
    }
}
