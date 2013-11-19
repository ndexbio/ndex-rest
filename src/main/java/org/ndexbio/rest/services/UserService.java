package org.ndexbio.rest.services;

import java.util.Collection;
import java.util.Iterator;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import org.ndexbio.rest.domain.XNetwork;
import org.ndexbio.rest.domain.XUser;
import org.ndexbio.rest.exceptions.DuplicateObjectException;
import org.ndexbio.rest.exceptions.NdexException;
import org.ndexbio.rest.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.helpers.RidConverter;
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
    public void addNetworkToWorkSurface(@PathParam("userId")String userJid, Network networkToAdd) throws NdexException
    {
        final ORID userRid = RidConverter.convertToRid(userJid);
        final ORID networkRid = RidConverter.convertToRid(networkToAdd.getId());

        XUser user = _orientDbGraph.getVertex(userRid, XUser.class);
        if (user == null)
            throw new ObjectNotFoundException("User", userJid);

        XNetwork network = _orientDbGraph.getVertex(networkRid, XNetwork.class);
        if (network == null)
            throw new ObjectNotFoundException("Network", networkToAdd.getId());
        
        Iterable<XNetwork> workSurface = user.getWorkspace();
        if (workSurface != null)
        {
            for (XNetwork checkNetwork : workSurface)
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
            _orientDbGraph.shutdown();
        }
    }

    @DELETE
    @Path("/{userId}/worksurface")
    @Produces("application/json")
    public void deleteNetworkFromWorkSurface(@PathParam("userId")String userJid, Network networkToDelete) throws NdexException
    {
        final ORID userRid = RidConverter.convertToRid(userJid);
        final ORID networkRid = RidConverter.convertToRid(networkToDelete.getId());

        final XUser user = _orientDbGraph.getVertex(userRid, XUser.class);
        if (user == null)
            throw new ObjectNotFoundException("User", userJid);

        Iterable<XNetwork> workSurface = user.getWorkspace();
        if (workSurface == null)
            return;

        try
        {
            Iterator<XNetwork> networkIterator = workSurface.iterator();
            while (networkIterator.hasNext())
            {
                XNetwork network = networkIterator.next();
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
            _orientDbGraph.shutdown();
        }
    }
    
    @DELETE
    @Path("/{userId}")
    @Produces("application/json")
    public void deleteUser(@PathParam("userId")String userJid) throws NdexException
    {
        final ORID userRid = RidConverter.convertToRid(userJid);
        final XUser userToDelete = _orientDbGraph.getVertex(userRid, XUser.class);
        
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
            _orientDbGraph.shutdown();
        }
    }
    
    @GET
    @Path("/{userId}")
    @Produces("application/json")
    public User getUser(@PathParam("userId")String userJid) throws NdexException
    {
        final ORID userId = RidConverter.convertToRid(userJid);
        final XUser user = _orientDbGraph.getVertex(userId, XUser.class);

        if (user == null)
        {
            final Collection<ODocument> matchingUsers = _orientDbGraph.getBaseGraph().command(new OCommandSQL("select from xUser where username equals " + userJid + " limit 10")).execute();

            if (matchingUsers.size() > 0)
                return new User(_orientDbGraph.getVertex(matchingUsers.toArray()[0], XUser.class));
        }
        else
            return new User(user);
        
        return null;
    }

    @POST
    @Produces("application/json")
    public void updateUser(User updatedUser) throws NdexException
    {
        final ORID userRid = RidConverter.convertToRid(updatedUser.getId());
        XUser existingUser = _orientDbGraph.getVertex(userRid, XUser.class);
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
            _orientDbGraph.shutdown();
        }
    }
    
    @PUT
    @Produces("application/json")
    public User createUser(String username, String password) throws NdexException
    {
        try
        {
            final XUser newUser = _orientDbGraph.addVertex("class:xUser", XUser.class);
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
            _orientDbGraph.shutdown();
        }
    }
}
