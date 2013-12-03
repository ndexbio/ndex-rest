package org.ndexbio.rest.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import javax.annotation.security.PermitAll;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import org.jboss.resteasy.client.exception.ResteasyAuthenticationException;
import org.ndexbio.rest.domain.INetwork;
import org.ndexbio.rest.domain.IUser;
import org.ndexbio.rest.exceptions.DuplicateObjectException;
import org.ndexbio.rest.exceptions.NdexException;
import org.ndexbio.rest.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.exceptions.ValidationException;
import org.ndexbio.rest.filters.BasicAuthenticationFilter;
import org.ndexbio.rest.helpers.Email;
import org.ndexbio.rest.helpers.RidConverter;
import org.ndexbio.rest.helpers.Security;
import org.ndexbio.rest.models.Network;
import org.ndexbio.rest.models.NewUser;
import org.ndexbio.rest.models.SearchParameters;
import org.ndexbio.rest.models.SearchResult;
import org.ndexbio.rest.models.User;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.OCommandSQL;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;

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
            
        final User authUser = new BasicAuthenticationFilter().authenticateUser(new String[] { username, password });
        if (authUser == null)
            throw new ResteasyAuthenticationException("Invalid username or password.");
        
        return authUser;
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
        
        final ORID userRid = RidConverter.convertToRid(userJid);
        
        try
        {
            setupDatabase();
            
            final IUser userToDelete = _orientDbGraph.getVertex(userRid, IUser.class);
            
            if (userToDelete == null)
                throw new ObjectNotFoundException("User", userJid);
            
            //TODO: Need to remove orphaned vertices
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
    public void emailNewPassword(@PathParam("username")final String username) throws Exception
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
            Email.sendEmail("support@ndexbio.org", authUser.getEmailAddress(), "Password Recovery", "Your new password is: " + newPassword);
            
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
    * Finds users based on the search parameters.
    * 
    * @param searchParameters The search parameters.
    **************************************************************************/
    @POST
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
            try
            {
                //The user ID is actually a username
                final Iterable<ODocument> matchingUsers = _orientDbGraph
                    .getBaseGraph()
                    .command(new OCommandSQL("select from User where username = ?"))
                    .execute(userJid);
                
                final Iterator<ODocument> userIterator = matchingUsers.iterator(); 
                if (userIterator.hasNext())
                    return new User(_orientDbGraph.getVertex(userIterator.next(), IUser.class), true);
            }
            catch (Exception e)
            {
                _orientDbGraph.getBaseGraph().rollback(); 
                throw e;
            }
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
}
