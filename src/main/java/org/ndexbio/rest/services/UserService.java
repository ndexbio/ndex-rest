package org.ndexbio.rest.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import org.ndexbio.rest.domain.IGroup;
import org.ndexbio.rest.domain.INetwork;
import org.ndexbio.rest.domain.IUser;
import org.ndexbio.rest.exceptions.DuplicateObjectException;
import org.ndexbio.rest.exceptions.NdexException;
import org.ndexbio.rest.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.exceptions.ValidationException;
import org.ndexbio.rest.helpers.RidConverter;
import org.ndexbio.rest.models.Group;
import org.ndexbio.rest.models.Network;
import org.ndexbio.rest.models.User;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.OCommandSQL;

@Path("/users")
public class UserService extends NdexService
{
    @PUT
    @Path("/{userId}/worksurface")
    @Produces("application/json")
    public void addNetworkToWorkSurface(@PathParam("userId")final String userJid, final Network networkToAdd) throws NdexException
    {
        final ORID userRid = RidConverter.convertToRid(userJid);
        final ORID networkRid = RidConverter.convertToRid(networkToAdd.getId());

        IUser user = _orientDbGraph.getVertex(userRid, IUser.class);
        if (user == null)
            throw new ObjectNotFoundException("User", userJid);

        INetwork network = _orientDbGraph.getVertex(networkRid, INetwork.class);
        if (network == null)
            throw new ObjectNotFoundException("Network", networkToAdd.getId());
        
        Iterable<INetwork> workSurface = user.getWorkspace();
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
            user.addWorkspace(network);
            _orientDbGraph.getBaseGraph().commit();
        }
        catch (Exception e)
        {
            if (_orientDbGraph != null)
                _orientDbGraph.getBaseGraph().rollback();
            
            throw e;
        }
        finally
        {
            if (_ndexDatabase != null)
                _ndexDatabase.close();
        }
    }

    @DELETE
    @Path("/{userId}/worksurface")
    @Produces("application/json")
    public void deleteNetworkFromWorkSurface(@PathParam("userId")final String userJid, final Network networkToDelete) throws NdexException
    {
        final ORID userRid = RidConverter.convertToRid(userJid);
        final ORID networkRid = RidConverter.convertToRid(networkToDelete.getId());

        final IUser user = _orientDbGraph.getVertex(userRid, IUser.class);
        if (user == null)
            throw new ObjectNotFoundException("User", userJid);

        Iterable<INetwork> workSurface = user.getWorkspace();
        if (workSurface == null)
            return;

        try
        {
            Iterator<INetwork> networkIterator = workSurface.iterator();
            while (networkIterator.hasNext())
            {
                INetwork network = networkIterator.next();
                if (network.asVertex().getId().equals(networkRid))
                {
                    networkIterator.remove();
                    break;
                }
            }
        }
        catch (Exception e)
        {
            if (_orientDbGraph != null)
                _orientDbGraph.getBaseGraph().rollback();
            
            throw e;
        }
        finally
        {
            if (_ndexDatabase != null)
                _ndexDatabase.close();
        }
    }
    
    @DELETE
    @Path("/{userId}")
    @Produces("application/json")
    public void deleteUser(@PathParam("userId")final String userJid) throws NdexException
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
            if (_orientDbGraph != null)
                _orientDbGraph.getBaseGraph().rollback();
            
            throw e;
        }
        finally
        {
            if (_ndexDatabase != null)
                _ndexDatabase.close();
        }
    }
    
    @GET
    @Path("/{userId}/owned-groups")
    @Produces("application/json")
    public Collection<Group> getGroups(@PathParam("userId")final String userJid) throws NdexException
    {
        final ORID userRid = RidConverter.convertToRid(userJid);
        final IUser user = _orientDbGraph.getVertex(userRid, IUser.class);
        
        if (user == null)
            throw new ObjectNotFoundException("User", userJid);

        ArrayList<Group> ownedGroups = new ArrayList<Group>();
        for (IGroup ownedGroup : user.getOwnedGroups())
            ownedGroups.add(new Group(ownedGroup));
        
        return ownedGroups; 
    }
    
    @GET
    @Path("/{userId}/owned-networks")
    @Produces("application/json")
    public Collection<Network> getNetworks(@PathParam("userId")final String userJid) throws NdexException
    {
        final ORID userRid = RidConverter.convertToRid(userJid);
        final IUser user = _orientDbGraph.getVertex(userRid, IUser.class);
        
        if (user == null)
            throw new ObjectNotFoundException("User", userJid);

        ArrayList<Network> ownedNetworks = new ArrayList<Network>();
        for (INetwork ownedNetwork : user.getOwnedNetworks())
            ownedNetworks.add(new Network(ownedNetwork));
        
        return ownedNetworks; 
    }
    
    @GET
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
            final Iterable<ODocument> matchingUsers = _orientDbGraph
                .getBaseGraph()
                .command(new OCommandSQL("select from xUser where username = ?"))
                .execute(userJid);
            
            Iterator<ODocument> userIterator = matchingUsers.iterator(); 
            if (userIterator.hasNext())
                return new User(_orientDbGraph.getVertex(userIterator.next(), IUser.class), true);
        }
        
        return null;
    }

    @POST
    @Produces("application/json")
    public void updateUser(final User updatedUser) throws NdexException
    {
        final ORID userRid = RidConverter.convertToRid(updatedUser.getId());
        IUser existingUser = _orientDbGraph.getVertex(userRid, IUser.class);
        if (existingUser == null)
            throw new ObjectNotFoundException("User", updatedUser.getId());

        try
        {
            if (updatedUser.getBackgroundImage() != null)
                existingUser.setBackgroundImg(updatedUser.getBackgroundImage());
    
            if (updatedUser.getDescription() != null)
                existingUser.setDescription(updatedUser.getDescription());
    
            if (updatedUser.getFirstName() != null)
                existingUser.setFirstName(updatedUser.getFirstName());
    
            if (updatedUser.getForegroundImage() != null)
                existingUser.setForegroundImg(updatedUser.getForegroundImage());
    
            if (updatedUser.getLastName() != null)
                existingUser.setLastName(updatedUser.getLastName());
    
            if (updatedUser.getWebsite() != null)
                existingUser.setWebsite(updatedUser.getWebsite());

            _orientDbGraph.getBaseGraph().commit();
        }
        catch (Exception e)
        {
            if (_orientDbGraph != null)
                _orientDbGraph.getBaseGraph().rollback();
            
            throw e;
        }
        finally
        {
            if (_ndexDatabase != null)
                _ndexDatabase.close();
        }
    }
    
    @PUT
    @Produces("application/json")
    public User createUser(final String username, final String password) throws NdexException
    {
        try
        {
            final IUser newUser = _orientDbGraph.addVertex("class:xUser", IUser.class);
            newUser.setUsername(username);
            newUser.setPassword(password);
            _orientDbGraph.getBaseGraph().commit();

            return new User(newUser);
        }
        catch (Exception e)
        {
            if (_orientDbGraph != null)
                _orientDbGraph.getBaseGraph().rollback();
            
            throw e;
        }
        finally
        {
            if (_ndexDatabase != null)
                _ndexDatabase.close();
        }
    }
}
