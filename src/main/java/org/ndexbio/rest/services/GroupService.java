package org.ndexbio.rest.services;
import java.util.Collection;
import java.util.Date;
import java.util.regex.Pattern;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import org.ndexbio.rest.domain.XGroup;
import org.ndexbio.rest.domain.XUser;
import org.ndexbio.rest.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.exceptions.ValidationException;
import org.ndexbio.rest.helpers.RidConverter;
import org.ndexbio.rest.models.Group;
import com.orientechnologies.orient.core.exception.OConcurrentModificationException;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.OCommandSQL;
import com.tinkerpop.blueprints.Vertex;

@Path("/groups")
public class GroupService extends NdexService
{
    private static final Object _commandLock = new Object();


    @DELETE
    @Path("/groups/{groupId}")
    @Produces("application/json")
    public void deleteGroup(@PathParam("groupId")String groupJid)
    {
        final ORID groupId = RidConverter.convertToRid(groupJid);

        final Vertex groupToDelete = _orientDbGraph.getVertex(groupId);
        if (groupToDelete == null)
            throw new ObjectNotFoundException("Group", groupJid);

        int retries = 0;
        while (true)
        {
            try
            {
                synchronized (_commandLock)
                {
                    _orientDbGraph.removeVertex(groupToDelete);
                }
            }
            catch (OConcurrentModificationException e)
            {
                retries++;

                if (retries > 10)
                    throw e;
            }
            catch (Exception e)
            {
                if (_orientDbGraph != null)
                    _orientDbGraph.getBaseGraph().rollback();
            }
        }
    }
    
    @GET
    @Path("/groups/{groupId}")
    @Produces("application/json")
    public Group getGroups(@PathParam("groupId")String groupJid)
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
                return _orientDbGraph.getVertex(matchingGroups.toArray()[0], XGroup.class);
        }
        else
            return group;
    }
    
    @POST
    @Produces("application/json")
    public void updateGroup(Group updatedGroup)
    {
        ORID groupRid = RidConverter.convertToRid(updatedGroup.getId());

        final XGroup groupToUpdate = _orientDbGraph.getVertex(groupRid, XGroup.class);
        if (groupToUpdate == null)
            throw new ObjectNotFoundException("Group", updatedGroup.getId());

        int retries = 0;
        while (true)
        {
            try
            {
                synchronized (_commandLock)
                {
                    groupToUpdate.setOrganizationName(updatedGroup.getProfile().getOrganizationName());
                    groupToUpdate.setWebsite(updatedGroup.getProfile().getWebsite());
                    groupToUpdate.setDescription(updatedGroup.getProfile().getDescription());
                }
            }
            catch (OConcurrentModificationException e)
            {
                retries++;
    
                if (retries > 10)
                    throw e;
            }
            catch (Exception e)
            {
                if (_orientDbGraph != null)
                    _orientDbGraph.getBaseGraph().rollback();
            }
        }
    }
    
    //TODO: Finish this
    //TODO: Create constructors in all models for domain classes
    @PUT
    @Produces("application/json")
    public Group createGroup(Group newGroup)
    {
        ORID userRid = RidConverter.convertToRid(requestContext.userId);
        
        final XUser groupOwner = orientDbGraph.getVertex(userRid, XUser.class);
        if (groupOwner == null)
            throw new ObjectNotFoundException("User", requestContext.userId);

        final Pattern groupNamePattern = Pattern.compile("^[A-Za-z0-9]{6,}$");
        if (!groupNamePattern.matcher(requestContext.groupName).matches())
            throw new ValidationException("Invalid group name: " + requestContext.groupName + ".");

        final XGroup newGroup = orientDbGraph.addVertex("class:xGroup", XGroup.class);
        newGroup.setGroupName(requestContext.groupName);
        newGroup.setCreationDate(new Date());

        groupOwner.addOwnedGroup(newGroup);
    }
}
