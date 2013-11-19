package org.ndexbio.rest.services;

import java.util.Collection;
import java.util.Date;
import java.util.regex.Pattern;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import org.ndexbio.rest.domain.XGroup;
import org.ndexbio.rest.domain.XUser;
import org.ndexbio.rest.exceptions.NdexException;
import org.ndexbio.rest.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.exceptions.ValidationException;
import org.ndexbio.rest.helpers.RidConverter;
import org.ndexbio.rest.models.Group;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.OCommandSQL;
import com.tinkerpop.blueprints.Vertex;

@Path("/groups")
public class GroupService extends NdexService
{
    @DELETE
    @Path("/{groupId}")
    @Produces("application/json")
    public void deleteGroup(@PathParam("groupId")String groupJid) throws NdexException
    {
        final ORID groupId = RidConverter.convertToRid(groupJid);

        final Vertex groupToDelete = _orientDbGraph.getVertex(groupId);
        if (groupToDelete == null)
            throw new ObjectNotFoundException("Group", groupJid);

        try
        {
            _orientDbGraph.removeVertex(groupToDelete);
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
    @Path("/{groupId}")
    @Produces("application/json")
    public Group getGroup(@PathParam("groupId")String groupJid) throws NdexException
    {
        final ORID groupId = RidConverter.convertToRid(groupJid);
        final XGroup group = _orientDbGraph.getVertex(groupId, XGroup.class);

        if (group == null)
        {
            final Collection<ODocument> matchingGroups = _orientDbGraph.getBaseGraph()
                .command(new OCommandSQL("select from xGroup where groupname = ?"))
                .execute(groupJid);

            if (matchingGroups.size() < 1)
                return null;
            else
                return new Group(_orientDbGraph.getVertex(matchingGroups.toArray()[0], XGroup.class));
        }
        else
            return new Group(group);
    }
 
    @POST
    @Produces("application/json")
    public void updateGroup(Group updatedGroup) throws NdexException
    {
        ORID groupRid = RidConverter.convertToRid(updatedGroup.getId());

        final XGroup groupToUpdate = _orientDbGraph.getVertex(groupRid, XGroup.class);
        if (groupToUpdate == null)
            throw new ObjectNotFoundException("Group", updatedGroup.getId());

        try
        {
            groupToUpdate.setDescription(updatedGroup.getDescription());
            groupToUpdate.setGroupName(updatedGroup.getName());
            groupToUpdate.setOrganizationName(updatedGroup.getOrganizationName());
            groupToUpdate.setWebsite(updatedGroup.getWebsite());
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
    public Group createGroup(String ownerId, Group newGroup) throws NdexException
    {
        ORID userRid = RidConverter.convertToRid(ownerId);
        
        final XUser groupOwner = _orientDbGraph.getVertex(userRid, XUser.class);
        if (groupOwner == null)
            throw new ObjectNotFoundException("User", ownerId);

        final Pattern groupNamePattern = Pattern.compile("^[A-Za-z0-9]{6,}$");
        if (!groupNamePattern.matcher(newGroup.getName()).matches())
            throw new ValidationException("Invalid group name: " + newGroup.getName() + ".");

        try
        {
            final XGroup group = _orientDbGraph.addVertex("class:xGroup", XGroup.class);
            group.setGroupName(newGroup.getName());
            group.setCreationDate(new Date());
            groupOwner.addOwnedGroup(group);
            _orientDbGraph.getBaseGraph().commit();

            newGroup.setId(RidConverter.convertToJid((ORID)group.asVertex().getId()));
            return newGroup;
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
