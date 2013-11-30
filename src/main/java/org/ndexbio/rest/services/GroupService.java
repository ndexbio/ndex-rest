package org.ndexbio.rest.services;

import java.util.Collection;
import java.util.Date;
import java.util.regex.Pattern;
import javax.annotation.security.PermitAll;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import org.ndexbio.rest.domain.IGroup;
import org.ndexbio.rest.domain.IGroupMembership;
import org.ndexbio.rest.domain.IUser;
import org.ndexbio.rest.domain.Permissions;
import org.ndexbio.rest.exceptions.NdexException;
import org.ndexbio.rest.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.exceptions.ValidationException;
import org.ndexbio.rest.helpers.RidConverter;
import org.ndexbio.rest.models.Group;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.OCommandSQL;
import com.tinkerpop.blueprints.Vertex;

//TODO: Need to add methods to add/remove members
//TODO: Need to add a method to change a user's permissions
@Path("/groups")
public class GroupService extends NdexService
{
    /**************************************************************************
    * Execute parent default constructor to initialize OrientDB.
    **************************************************************************/
    public GroupService()
    {
        super();
    }
    
    
    
    /**************************************************************************
    * Creates a group. 
    * 
    * @param ownerId  The owner's ID.
    * @param newGroup The group to create.
    **************************************************************************/
    @PUT
    @Produces("application/json")
    public Group createGroup(final String ownerId, final Group newGroup) throws Exception
    {
        if (ownerId == null || ownerId.isEmpty())
            throw new ValidationException("The group owner wasn't specified.");
        else if (newGroup == null)
            throw new ValidationException("The group to create is empty.");

        final Pattern groupNamePattern = Pattern.compile("^[A-Za-z0-9]{6,}$");
        if (!groupNamePattern.matcher(newGroup.getName()).matches())
            throw new ValidationException("Invalid group name: " + newGroup.getName() + ".");
        
        final ORID userRid = RidConverter.convertToRid(ownerId);
        
        final IUser groupOwner = _orientDbGraph.getVertex(userRid, IUser.class);
        if (groupOwner == null)
            throw new ObjectNotFoundException("User", ownerId);

        try
        {
            final IGroup group = _orientDbGraph.addVertex("class:group", IGroup.class);
            group.setDescription(newGroup.getDescription());
            group.setName(newGroup.getName());
            group.setOrganizationName(newGroup.getOrganizationName());
            group.setWebsite(newGroup.getWebsite());
            group.setCreatedDate(new Date());

            final IGroupMembership membership = _orientDbGraph.addVertex("class:groupMembership", IGroupMembership.class);
            membership.setPermissions(Permissions.ADMIN);
            membership.setMember(groupOwner);
            membership.setGroup(group);

            groupOwner.addGroup(membership);
            group.addMember(membership);
            
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

    /**************************************************************************
    * Deletes a group.
    * 
    * @param groupId The ID of the group to delete.
    **************************************************************************/
    @DELETE
    @Path("/{groupId}")
    @Produces("application/json")
    public void deleteGroup(@PathParam("groupId")final String groupJid) throws Exception
    {
        if (groupJid == null || groupJid.isEmpty())
            throw new ValidationException("No group ID was specified.");
        
        final ORID groupId = RidConverter.convertToRid(groupJid);

        final Vertex groupToDelete = _orientDbGraph.getVertex(groupId);
        if (groupToDelete == null)
            throw new ObjectNotFoundException("Group", groupJid);

        try
        {
            //TODO: Need to remove orphaned vertices
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
    
    /**************************************************************************
    * Gets a group by ID or name.
    * 
    * @param groupId The ID or name of the group.
    **************************************************************************/
    @GET
    @PermitAll
    @Path("/{groupId}")
    @Produces("application/json")
    public Group getGroup(@PathParam("groupId")final String groupJid) throws NdexException
    {
        if (groupJid == null || groupJid.isEmpty())
            throw new ValidationException("No group ID was specified.");
        
        try
        {
            final ORID groupId = RidConverter.convertToRid(groupJid);
            final IGroup group = _orientDbGraph.getVertex(groupId, IGroup.class);
            
            if (group != null)
                return new Group(group, true);
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
 
    /**************************************************************************
    * Updates a group.
    * 
    * @param updatedGroup The updated group information.
    **************************************************************************/
    @POST
    @Produces("application/json")
    public void updateGroup(final Group updatedGroup) throws Exception
    {
        if (updatedGroup == null)
            throw new ValidationException("The updated group is empty.");
        
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
