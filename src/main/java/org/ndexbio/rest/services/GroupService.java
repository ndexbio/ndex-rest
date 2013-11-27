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
import org.ndexbio.rest.domain.IGroup;
import org.ndexbio.rest.domain.IUser;
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
    public GroupService()
    {
        super();
    }
    
    
    
    @PUT
    @Produces("application/json")
    public Group createGroup(final String ownerId, final Group newGroup) throws Exception
    {
        final ORID userRid = RidConverter.convertToRid(ownerId);
        
        final IUser groupOwner = _orientDbGraph.getVertex(userRid, IUser.class);
        if (groupOwner == null)
            throw new ObjectNotFoundException("User", ownerId);

        final Pattern groupNamePattern = Pattern.compile("^[A-Za-z0-9]{6,}$");
        if (!groupNamePattern.matcher(newGroup.getName()).matches())
            throw new ValidationException("Invalid group name: " + newGroup.getName() + ".");

        try
        {
            final IGroup group = _orientDbGraph.addVertex("class:group", IGroup.class);
            group.setDescription(newGroup.getDescription());
            group.setName(newGroup.getName());
            group.setOrganizationName(newGroup.getOrganizationName());
            group.setWebsite(newGroup.getWebsite());
            group.setCreatedDate(new Date());
            groupOwner.addOwnedGroup(group);
            _orientDbGraph.getBaseGraph().commit();

            newGroup.setId(RidConverter.convertToJid((ORID)group.asVertex().getId()));
            return newGroup;
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
    @Path("/{groupId}")
    @Produces("application/json")
    public void deleteGroup(@PathParam("groupId")final String groupJid) throws Exception
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
            handleOrientDbException(e);
        }
        finally
        {
            closeOrientDbConnection();
        }
    }
    
    @GET
    @Path("/{groupId}")
    @Produces("application/json")
    public Group getGroup(@PathParam("groupId")final String groupJid) throws NdexException
    {
        try
        {
            final ORID groupId = RidConverter.convertToRid(groupJid);
            final IGroup group = _orientDbGraph.getVertex(groupId, IGroup.class);
            
            if (group != null)
                return new Group(group);
        }
        catch (ValidationException ve)
        {
            //The group ID is actually a group name
            final Collection<ODocument> matchingGroups = _orientDbGraph
                .getBaseGraph()
                .command(new OCommandSQL("select from Group where groupname = ?"))
                .execute(groupJid);

            if (matchingGroups.size() > 0)
                return new Group(_orientDbGraph.getVertex(matchingGroups.toArray()[0], IGroup.class), true);
        }
        
        return null;
    }
 
    @POST
    @Produces("application/json")
    public void updateGroup(final Group updatedGroup) throws Exception
    {
        final ORID groupRid = RidConverter.convertToRid(updatedGroup.getId());

        final IGroup groupToUpdate = _orientDbGraph.getVertex(groupRid, IGroup.class);
        if (groupToUpdate == null)
            throw new ObjectNotFoundException("Group", updatedGroup.getId());

        try
        {
            groupToUpdate.setDescription(updatedGroup.getDescription());
            groupToUpdate.setName(updatedGroup.getName());
            groupToUpdate.setOrganizationName(updatedGroup.getOrganizationName());
            groupToUpdate.setWebsite(updatedGroup.getWebsite());
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
}
