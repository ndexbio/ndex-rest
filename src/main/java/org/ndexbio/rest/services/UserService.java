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
import org.ndexbio.rest.domain.IGroup;
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
import org.ndexbio.rest.models.Group;
import org.ndexbio.rest.models.GroupSearchResult;
import org.ndexbio.rest.models.Network;
import org.ndexbio.rest.models.NewUser;
import org.ndexbio.rest.models.SearchParameters;
import org.ndexbio.rest.models.User;
import org.ndexbio.rest.models.UserSearchResult;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.OCommandSQL;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;

@Path("/users")
public class UserService extends NdexService
{
    public UserService()
    {
        super();
    }
    
    
    
    @PUT
    @Path("/{userId}/work-surface")
    @Produces("application/json")
    public void addNetworkToWorkSurface(@PathParam("userId")final String userJid, final Network networkToAdd) throws Exception
    {
        final ORID userRid = RidConverter.convertToRid(userJid);
        final ORID networkRid = RidConverter.convertToRid(networkToAdd.getId());

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

        try
        {
            user.addNetworkToWorkSurface(network);
            _orientDbGraph.getBaseGraph().commit();
        }
        catch (Exception e)
        {
            handleOrientDbException(e);
        }
        finally
        {
            closeOrientDbConnection();
        }
    }

    @GET
    @PermitAll
    @Path("/authenticate/{username}/{password}")
    @Produces("application/json")
    public User authenticateUser(@PathParam("username")final String username, @PathParam("password")final String password) throws Exception
    {
        final User authUser = new BasicAuthenticationFilter().authenticateUser(new String[] { username, password });
        if (authUser == null)
            throw new ResteasyAuthenticationException("Invalid username or password.");
        
        return authUser;
    }
    
    @PUT
    @PermitAll
    @Produces("application/json")
    public User createUser(final NewUser userToCreate) throws Exception
    {
        try
        {
            final IUser newUser = _orientDbGraph.addVertex("class:user", IUser.class);
            newUser.setUsername(userToCreate.getUsername());
            newUser.setPassword(Security.hashText(userToCreate.getPassword()));
            newUser.setEmailAddress(userToCreate.getEmailAddress());
            _orientDbGraph.getBaseGraph().commit();

            return new User(newUser);
        }
        catch (Exception e)
        {
            handleOrientDbException(e);
        }
        finally
        {
            closeOrientDbConnection();
        }
        
        return null;
    }

    @DELETE
    @Path("/{userId}/work-surface")
    @Produces("application/json")
    public void deleteNetworkFromWorkSurface(@PathParam("userId")final String userJid, final Network networkToDelete) throws Exception
    {
        final ORID userRid = RidConverter.convertToRid(userJid);
        final ORID networkRid = RidConverter.convertToRid(networkToDelete.getId());

        final IUser user = _orientDbGraph.getVertex(userRid, IUser.class);
        if (user == null)
            throw new ObjectNotFoundException("User", userJid);

        final INetwork network = _orientDbGraph.getVertex(networkRid, INetwork.class);
        if (network == null)
            throw new ObjectNotFoundException("Network", networkToDelete.getId());
        

//        Iterable<INetwork> workSurface = user.getWorkSurface();
//        if (workSurface == null)
//            return;

        try
        {
            user.removeNetworkFromWorkSurface(network);
            _orientDbGraph.getBaseGraph().commit();

//            Iterator<INetwork> networkIterator = workSurface.iterator();
//            while (networkIterator.hasNext())
//            {
//                INetwork network = networkIterator.next();
//                if (network.asVertex().getId().equals(networkRid))
//                {
//                    networkIterator.remove();
//                    break;
//                }
//            }
        }
        catch (Exception e)
        {
            handleOrientDbException(e);
        }
        finally
        {
            closeOrientDbConnection();
        }
    }
    
    @DELETE
    @Path("/{userId}")
    @Produces("application/json")
    public void deleteUser(@PathParam("userId")final String userJid) throws Exception
    {
        final ORID userRid = RidConverter.convertToRid(userJid);
        final IUser userToDelete = _orientDbGraph.getVertex(userRid, IUser.class);
        
        if (userToDelete == null)
            throw new ObjectNotFoundException("User", userJid);
        
        try
        {
            _orientDbGraph.removeVertex(userToDelete.asVertex());
            _orientDbGraph.getBaseGraph().commit();
        }
        catch (Exception e)
        {
            handleOrientDbException(e);
        }
        finally
        {
            closeOrientDbConnection();
        }
    }

    @GET
    @PermitAll
    @Path("/{username}/forgot-password")
    @Produces("application/json")
    public void emailNewPassword(@PathParam("username")final String username) throws Exception
    {
        Collection<ODocument> usersFound = _ndexDatabase
            .command(new OCommandSQL("select from User where username = ?"))
            .execute(username);
        
        if (usersFound.size() < 1)
            throw new ObjectNotFoundException("User", username);

        final IUser authUser = _orientDbGraph.getVertex(usersFound.toArray()[0], IUser.class);
        
        final String newPassword = Security.generatePassword();
        authUser.setPassword(Security.hashText(newPassword));
        
        //TODO: This should be refactored to use a configuration file and a text file for the email content
        Email.sendEmail("support@ndexbio.org", authUser.getEmailAddress(), "Password Recovery", "Your new password is: " + newPassword);
    }
    
    @GET
    @Path("/{userId}/owned-groups")
    @Produces("application/json")
    public Collection<Group> getOwnedGroups(@PathParam("userId")final String userJid) throws Exception
    {
        final ORID userRid = RidConverter.convertToRid(userJid);
        final IUser user = _orientDbGraph.getVertex(userRid, IUser.class);
        
        if (user == null)
            throw new ObjectNotFoundException("User", userJid);

        final ArrayList<Group> ownedGroups = new ArrayList<Group>();
        for (IGroup ownedGroup : user.getOwnedGroups())
            ownedGroups.add(new Group(ownedGroup));
        
        return ownedGroups; 
    }
    
    @GET
    @Path("/{userId}/owned-networks")
    @Produces("application/json")
    public Collection<Network> getOwnedNetworks(@PathParam("userId")final String userJid) throws Exception
    {
        final ORID userRid = RidConverter.convertToRid(userJid);
        final IUser user = _orientDbGraph.getVertex(userRid, IUser.class);
        
        if (user == null)
            throw new ObjectNotFoundException("User", userJid);

        final ArrayList<Network> ownedNetworks = new ArrayList<Network>();
        for (INetwork ownedNetwork : user.getOwnedNetworks())
            ownedNetworks.add(new Network(ownedNetwork));
        
        return ownedNetworks; 
    }
    
    @GET
    @PermitAll
    @Path("/{userId}")
    @Produces("application/json")
    public User getUser(@PathParam("userId")final String userJid)
    {
        try
        {
            final ORID userId = RidConverter.convertToRid(userJid);
            final IUser user = _orientDbGraph.getVertex(userId, IUser.class);
            if (user != null)
                return new User(user);
        }
        catch (ValidationException ve)
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
        
        return null;
    }

    @POST
    @Produces("application/json")
    public void updateUser(final User updatedUser) throws Exception
    {
        final ORID userRid = RidConverter.convertToRid(updatedUser.getId());
        final IUser existingUser = _orientDbGraph.getVertex(userRid, IUser.class);
        if (existingUser == null)
            throw new ObjectNotFoundException("User", updatedUser.getId());

        try
        {
            if (updatedUser.getBackgroundImage() != null)
                existingUser.setBackgroundImage(updatedUser.getBackgroundImage());
    
            if (updatedUser.getDescription() != null)
                existingUser.setDescription(updatedUser.getDescription());
    
            if (updatedUser.getFirstName() != null)
                existingUser.setFirstName(updatedUser.getFirstName());
    
            if (updatedUser.getForegroundImage() != null)
                existingUser.setForegroundImage(updatedUser.getForegroundImage());
    
            if (updatedUser.getLastName() != null)
                existingUser.setLastName(updatedUser.getLastName());
    
            if (updatedUser.getWebsite() != null)
                existingUser.setWebsite(updatedUser.getWebsite());

            _orientDbGraph.getBaseGraph().commit();
        }
        catch (Exception e)
        {
            handleOrientDbException(e);
        }
        finally
        {
            closeOrientDbConnection();
        }
    }
    
    
 	/*
 	 * Find Users based on search parameters - string matching for now
 	 */
 	@POST
 	@Path("/search")
 	@Produces("application/json")
 	public UserSearchResult findUsers(SearchParameters searchParameters) throws NdexException {
 		Collection<User> foundUsers = Lists.newArrayList();
		UserSearchResult result = new UserSearchResult();
		result.setUsers(foundUsers);
		Integer skip = 0;
		Integer limit = 10;
		if (!Strings.isNullOrEmpty(searchParameters.getSkip())) {
			skip = Ints.tryParse(searchParameters.getSkip());
		}
		if (!Strings.isNullOrEmpty(searchParameters.getLimit())) {
			limit = Ints.tryParse(searchParameters.getLimit());
		}
		result.setPageSize(limit);
		result.setSkip(skip);
		
		if (Strings.isNullOrEmpty(searchParameters.getSearchString())) {
			return result;
		}

		int start = 0;
		if (null != skip && null != limit) {
			start = skip.intValue() * limit.intValue();
		}

 		String searchString = searchParameters.getSearchString().toUpperCase().trim();

 		String where_clause = "";
 		if (searchString.length() > 0)
 			where_clause = " where username.toUpperCase() like '%"
 					+ searchString
 					+ "%' OR lastName.toUpperCase() like '%"
 					+ searchString
 					+ "%' OR firstName.toUpperCase() like '%"
 					+ searchString + "%'";

 		final String query = "select from User " + where_clause
 				+ " order by creation_date desc skip " + start + " limit "
 				+ limit;
 		List<ODocument> userDocumentList = _orientDbGraph.getBaseGraph()
 				.getRawGraph().query(new OSQLSynchQuery<ODocument>(query));
 		for (ODocument document : userDocumentList) {
 			IUser iUser = _orientDbGraph.getVertex(document,
 					IUser.class);
 			foundUsers.add(new User(iUser));
 		}

 		result.setUsers(foundUsers);
 		return result;

 	}
}
