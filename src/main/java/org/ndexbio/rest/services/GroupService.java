package org.ndexbio.rest.services;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import org.ndexbio.rest.domain.IGroup;
import org.ndexbio.rest.domain.IGroupMembership;
import org.ndexbio.rest.domain.IUser;
import org.ndexbio.rest.domain.Permissions;
import org.ndexbio.rest.exceptions.NdexException;
import org.ndexbio.rest.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.helpers.RidConverter;
import org.ndexbio.rest.helpers.Validation;
import org.ndexbio.rest.models.Group;
import org.ndexbio.rest.models.Membership;
import org.ndexbio.rest.models.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
import com.tinkerpop.blueprints.impls.orient.OrientElement;

@Path("/groups")
public class GroupService extends NdexService
{
    private static final Logger _logger = LoggerFactory.getLogger(GroupService.class);
    
    
    
    /**************************************************************************
    * Injects the HTTP request into the base class to be used by
    * getLoggedInUser(). 
    * 
    * @param httpRequest
    *            The HTTP request injected by RESTEasy's context.
    **************************************************************************/
    public GroupService(@Context HttpServletRequest httpRequest)
    {
        super(httpRequest);
    }
    
    
    
    /**************************************************************************
    * Creates a group. 
    * 
    * @param newGroup
    *            The group to create.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws NdexException
    *            Failed to create the user in the database.
    * @return The newly created group.
    **************************************************************************/
    @PUT
    @Produces("application/json")
    public Group createGroup(final Group newGroup) throws IllegalArgumentException, NdexException
    {
        if (newGroup == null)
            throw new IllegalArgumentException("The group to create is empty.");
        else if (!Validation.isValid(newGroup.getName(), Validation.REGEX_GROUP_NAME))
            throw new IllegalArgumentException("Invalid group name: " + newGroup.getName() + ".");

        try
        {
            setupDatabase();
            
            final ORID userRid = RidConverter.convertToRid(this.getLoggedInUser().getId());
            final IUser groupOwner = _orientDbGraph.getVertex(userRid, IUser.class);

            final IGroup group = _orientDbGraph.addVertex("class:group", IGroup.class);
            group.setDescription(newGroup.getDescription());
            group.setName(newGroup.getName());
            group.setOrganizationName(newGroup.getOrganizationName());
            group.setWebsite(newGroup.getWebsite());
            group.setCreatedDate(new Date());

            if (newGroup.getMembers() == null || newGroup.getMembers().size() == 0)
            {
                final IGroupMembership membership = _orientDbGraph.addVertex("class:groupMembership", IGroupMembership.class);
                membership.setPermissions(Permissions.ADMIN);
                membership.setMember(groupOwner);
                membership.setGroup(group);
    
                groupOwner.addGroup(membership);
                group.addMember(membership);
            }
            else
            {
                for (Membership member : newGroup.getMembers())
                {
                    final ORID memberRid = RidConverter.convertToRid(member.getResourceId());
                    final IUser groupMember = _orientDbGraph.getVertex(memberRid, IUser.class);
                    
                    final IGroupMembership membership = _orientDbGraph.addVertex("class:groupMembership", IGroupMembership.class);
                    membership.setPermissions(member.getPermissions());
                    membership.setMember(groupMember);
                    membership.setGroup(group);
        
                    groupMember.addGroup(membership);
                    group.addMember(membership);
                }
            }

            _orientDbGraph.getBaseGraph().commit();

            newGroup.setId(RidConverter.convertToJid((ORID) group.asVertex().getId()));
            return newGroup;
        }
        catch (Exception e)
        {
            _logger.error("Failed to create group: " + newGroup.getName() + ".", e);
            _orientDbGraph.getBaseGraph().rollback(); 
            throw new NdexException("Failed to create your group.");
        }
        finally
        {
            teardownDatabase();
        }
    }

    /**************************************************************************
    * Deletes a group.
    * 
    * @param groupId
    *            The ID of the group to delete.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws ObjectNotFoundException
    *            The group doesn't exist.
    * @throws SecurityException
    *            The user doesn't have permissions to delete the group.
    * @throws NdexException
    *            Failed to delete the user from the database.
    **************************************************************************/
    @DELETE
    @Path("/{groupId}")
    @Produces("application/json")
    public void deleteGroup(@PathParam("groupId") final String groupId) throws IllegalArgumentException, ObjectNotFoundException, SecurityException, NdexException
    {
        if (groupId == null || groupId.isEmpty())
            throw new IllegalArgumentException("No group ID was specified.");

        final ORID networkRid = RidConverter.convertToRid(groupId);

        try
        {
            setupDatabase();
            
            final IGroup groupToDelete = _orientDbGraph.getVertex(networkRid, IGroup.class);
            if (groupToDelete == null)
                throw new ObjectNotFoundException("Group", groupId);
            else if (!hasPermission(new Group(groupToDelete), Permissions.ADMIN))
                throw new SecurityException("Insufficient privileges to delete the group.");

            final List<ODocument> adminCount = _ndexDatabase.query(new OSQLSynchQuery<Integer>("select count(*) from Membership where in_members = ? and permissions = 'ADMIN'"));
            if (adminCount == null || adminCount.isEmpty())
                throw new NdexException("Unable to count ADMIN members.");
            else if ((long)adminCount.get(0).field("count") > 1)
                throw new NdexException("Cannot delete a group that contains other ADMIN members.");

            final List<ODocument> adminNetworks = _ndexDatabase.query(new OSQLSynchQuery<Integer>("select count(*) from Membership where in_networks = ? and permissions = 'ADMIN'"));
            if (adminCount == null || adminCount.isEmpty())
                throw new NdexException("Unable to query group/network membership.");
            else if ((long)adminNetworks.get(0).field("count") > 1)
                throw new NdexException("Cannot delete a group that is an ADMIN member of any network.");

            for (IGroupMembership groupMembership : groupToDelete.getMembers())
                _orientDbGraph.removeVertex(groupMembership.asVertex());

            final List<ODocument> groupChildren = _ndexDatabase.query(new OSQLSynchQuery<Object>("select @rid from (traverse * from " + networkRid + " while @class <> 'Account')"));
            for (ODocument groupChild : groupChildren)
            {
                final ORID childId = groupChild.field("rid", OType.LINK);

                final OrientElement element = _orientDbGraph.getBaseGraph().getElement(childId);
                if (element != null)
                    element.remove();
            }

            _orientDbGraph.removeVertex(groupToDelete.asVertex());
            _orientDbGraph.getBaseGraph().commit();
        }
        catch (Exception e)
        {
            _logger.error("Failed to delete group: " + groupId + ".", e);
            _orientDbGraph.getBaseGraph().rollback(); 
            throw new NdexException("Failed to delete the group.");
        }
        finally
        {
            teardownDatabase();
        }
    }

    /**************************************************************************
    * Find Groups based on search parameters - string matching for now
    * 
    * @params searchParameters
    *            The search parameters.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws NdexException
    *            Failed to query the database.
    * @return Groups that match the search criteria.
    **************************************************************************/
    @POST
    @PermitAll
    @Path("/search")
    @Produces("application/json")
    public List<Group> findGroups(SearchParameters searchParameters) throws IllegalArgumentException, NdexException
    {
        if (searchParameters.getSearchString() == null || searchParameters.getSearchString().isEmpty())
            throw new IllegalArgumentException("No search string was specified.");
        else
            searchParameters.setSearchString(searchParameters.getSearchString().toUpperCase().trim());
        
        final List<Group> foundGroups = new ArrayList<Group>();
        
        final int startIndex = searchParameters.getSkip() * searchParameters.getTop();

        final String whereClause = " where name.toUpperCase() like '%" + searchParameters.getSearchString()
            + "%' OR description.toUpperCase() like '%" + searchParameters.getSearchString()
            + "%' OR organizationName.toUpperCase() like '%" + searchParameters.getSearchString() + "%'";

        final String query = "select from Group " + whereClause + " order by creation_date desc skip " + startIndex + " limit " + searchParameters.getTop();

        try
        {
            setupDatabase();
            
            final List<ODocument> groupDocumentList = _orientDbGraph
                .getBaseGraph()
                .getRawGraph()
                .query(new OSQLSynchQuery<ODocument>(query));
            
            for (final ODocument document : groupDocumentList)
                foundGroups.add(new Group(_orientDbGraph.getVertex(document, IGroup.class)));
    
            return foundGroups;
        }
        catch (Exception e)
        {
            _logger.error("Failed to search groups: " + searchParameters.getSearchString(), e);
            _orientDbGraph.getBaseGraph().rollback(); 
            throw new NdexException("Failed to search groups.");
        }
        finally
        {
            teardownDatabase();
        }
    }

    /**************************************************************************
    * Gets a group by ID or name.
    * 
    * @param groupId
    *            The ID or name of the group.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws NdexException
    *            Failed to query the database.
    * @return The group.
    **************************************************************************/
    @GET
    @Path("/{groupId}")
    @Produces("application/json")
    public Group getGroup(@PathParam("groupId") final String groupId) throws IllegalArgumentException, NdexException
    {
        if (groupId == null || groupId.isEmpty())
            throw new IllegalArgumentException("No group ID was specified.");

        try
        {
            setupDatabase();
            
            final ORID groupRid = RidConverter.convertToRid(groupId);
            final IGroup group = _orientDbGraph.getVertex(groupRid, IGroup.class);

            if (group != null)
                return new Group(group, true);
        }
        catch (IllegalArgumentException iae)
        {
            //The group ID is actually a group name
            final List<ODocument> matchingGroups = _ndexDatabase.query(new OSQLSynchQuery<Object>("select from Group where name = '" + groupId + "'"));
            if (!matchingGroups.isEmpty())
                return new Group(_orientDbGraph.getVertex(matchingGroups.get(0), IGroup.class), true);
        }
        catch (Exception e)
        {
            _logger.error("Failed to retrieve group: " + groupId + ".", e);
            throw new NdexException("Failed to retrieve the group.");
        }
        finally
        {
            teardownDatabase();
        }
        
        return null;
    }

    /**************************************************************************
    * Updates a group.
    * 
    * @param updatedGroup
    *            The updated group information.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws SecurityException
    *            The user doesn't have access to update the group.
    * @throws NdexException
    *            Failed to update the user in the database.
    **************************************************************************/
    @POST
    @Produces("application/json")
    public void updateGroup(final Group updatedGroup) throws IllegalArgumentException, SecurityException, NdexException
    {
        if (updatedGroup == null)
            throw new IllegalArgumentException("The updated group is empty.");

        final ORID groupRid = RidConverter.convertToRid(updatedGroup.getId());

        try
        {
            setupDatabase();
            
            final IGroup groupToUpdate = _orientDbGraph.getVertex(groupRid, IGroup.class);
            if (groupToUpdate == null)
                throw new ObjectNotFoundException("Group", updatedGroup.getId());
            else if (!hasPermission(updatedGroup, Permissions.WRITE))
                throw new SecurityException("Access denied.");
            
            //TODO: Don't allow the only ADMIN member to change their own permissions
            if (updatedGroup.getDescription() != null && !updatedGroup.getDescription().isEmpty())
                groupToUpdate.setDescription(updatedGroup.getDescription());
            
            if (updatedGroup.getName() != null && !updatedGroup.getName().isEmpty())
                groupToUpdate.setName(updatedGroup.getName());
            
            if (updatedGroup.getOrganizationName() != null && !updatedGroup.getOrganizationName().isEmpty())
                groupToUpdate.setOrganizationName(updatedGroup.getOrganizationName());
            
            if (updatedGroup.getWebsite() != null && !updatedGroup.getWebsite().isEmpty())
                groupToUpdate.setWebsite(updatedGroup.getWebsite());
            
            _orientDbGraph.getBaseGraph().commit();
        }
        catch (Exception e)
        {
            _logger.error("Failed to update group: " + updatedGroup.getName() + ".", e);
            _orientDbGraph.getBaseGraph().rollback(); 
            throw new NdexException("Failed to update the group.");
        }
        finally
        {
            teardownDatabase();
        }
    }
    
    
    
    /**************************************************************************
    * Determines if the logged in user has sufficient permissions to a group. 
    * 
    * @param targetGroup
    *            The group to test for permissions.
    * @return True if the member has permission, false otherwise.
    **************************************************************************/
    private boolean hasPermission(Group targetGroup, Permissions requiredPermissions)
    {
        for (Membership groupMembership : this.getLoggedInUser().getGroups())
        {
            if (groupMembership.getResourceId() == targetGroup.getId() && groupMembership.getPermissions().compareTo(requiredPermissions) > -1)
                return true;
        }
        
        return false;
    }
}
