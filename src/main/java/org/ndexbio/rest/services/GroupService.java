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
import org.ndexbio.common.exceptions.*;
import org.ndexbio.common.helpers.IdConverter;
import org.ndexbio.common.helpers.Validation;
import org.ndexbio.common.models.data.IGroup;
import org.ndexbio.common.models.data.IGroupMembership;
import org.ndexbio.common.models.data.IUser;
import org.ndexbio.common.models.data.Permissions;
import org.ndexbio.common.models.object.Group;
import org.ndexbio.common.models.object.Membership;
import org.ndexbio.common.models.object.SearchParameters;
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
    * @throws DuplicateObjectException
    *            A group with that name already exists.
    * @throws NdexException
    *            Failed to create the user in the database.
    * @return The newly created group.
    **************************************************************************/
    @PUT
    @Produces("application/json")
    public Group createGroup(final Group newGroup) throws IllegalArgumentException, DuplicateObjectException, NdexException
    {
        if (newGroup == null)
            throw new IllegalArgumentException("The group to create is empty.");
        else if (!Validation.isValid(newGroup.getName(), Validation.REGEX_GROUP_NAME))
            throw new IllegalArgumentException("Invalid group name: " + newGroup.getName() + ".");

        try
        {
            setupDatabase();
            
            final IUser groupOwner = _orientDbGraph.getVertex(IdConverter.toRid(this.getLoggedInUser().getId()), IUser.class);

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
                    final IUser groupMember = _orientDbGraph.getVertex(IdConverter.toRid(member.getResourceId()), IUser.class);
                    
                    final IGroupMembership membership = _orientDbGraph.addVertex("class:groupMembership", IGroupMembership.class);
                    membership.setPermissions(member.getPermissions());
                    membership.setMember(groupMember);
                    membership.setGroup(group);
        
                    groupMember.addGroup(membership);
                    group.addMember(membership);
                }
            }

            _orientDbGraph.getBaseGraph().commit();

            newGroup.setId(IdConverter.toJid((ORID) group.asVertex().getId()));
            return newGroup;
        }
        catch (Exception e)
        {
            if (e.getMessage().indexOf(" duplicated key ") > -1)
                throw new DuplicateObjectException("A group with the name: " + newGroup.getName() + " already exists.");
            
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

        final ORID groupRid = IdConverter.toRid(groupId);
        
        try
        {
            setupDatabase();
            
            final IGroup groupToDelete = _orientDbGraph.getVertex(groupRid, IGroup.class);
            if (groupToDelete == null)
                throw new ObjectNotFoundException("Group", groupId);
            else if (!hasPermission(new Group(groupToDelete), Permissions.ADMIN))
                throw new SecurityException("Insufficient privileges to delete the group.");

            final List<ODocument> adminCount = _ndexDatabase.query(new OSQLSynchQuery<Integer>("select count(@rid) from GroupMembership where in_groupMembers = " + groupRid + " and permissions = 'ADMIN'"));
            if (adminCount == null || adminCount.isEmpty())
                throw new NdexException("Unable to count ADMIN members.");
            else if ((long)adminCount.get(0).field("count") > 1)
                throw new NdexException("Cannot delete a group that contains other ADMIN members.");

            final List<ODocument> adminNetworks = _ndexDatabase.query(new OSQLSynchQuery<Integer>("select count(@rid) from Membership where in_userNetworks = " + groupRid + " and permissions = 'ADMIN'"));
            if (adminCount == null || adminCount.isEmpty())
                throw new NdexException("Unable to query group/network membership.");
            else if ((long)adminNetworks.get(0).field("count") > 1)
                throw new NdexException("Cannot delete a group that is an ADMIN member of any network.");

            for (IGroupMembership groupMembership : groupToDelete.getMembers())
                _orientDbGraph.removeVertex(groupMembership.asVertex());

            final List<ODocument> groupChildren = _ndexDatabase.query(new OSQLSynchQuery<Object>("select @rid from (traverse * from " + groupRid + " while @class <> 'user' and @class <> 'group')"));
            for (ODocument groupChild : groupChildren)
            {
                final OrientElement element = _orientDbGraph.getBaseGraph().getElement(groupChild.field("rid", OType.LINK));
                if (element != null)
                    element.remove();
            }

            _orientDbGraph.removeVertex(groupToDelete.asVertex());
            _orientDbGraph.getBaseGraph().commit();
        }
        catch (SecurityException | NdexException ne)
        {
            throw ne;
        }
        catch (Exception e)
        {
            if (e.getMessage().indexOf("cluster: null") > -1)
                throw new ObjectNotFoundException("Group", groupId);
            
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
        if (searchParameters == null)
            throw new IllegalArgumentException("Search Parameters are empty.");
        else if (searchParameters.getSearchString() == null || searchParameters.getSearchString().isEmpty())
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
    @PermitAll
    @Path("/{groupId}")
    @Produces("application/json")
    public Group getGroup(@PathParam("groupId") final String groupId) throws IllegalArgumentException, NdexException
    {
        if (groupId == null || groupId.isEmpty())
            throw new IllegalArgumentException("No group ID was specified.");

        try
        {
            setupDatabase();
            
            final IGroup group = _orientDbGraph.getVertex(IdConverter.toRid(groupId), IGroup.class);
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
    * Removes a member from a group.
    * 
    * @param groupId
    *            The group ID.
    * @param userId
    *            The ID of the member to remove.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws ObjectNotFoundException
    *            The network or member doesn't exist.
    * @throws SecurityException
    *            The user doesn't have access to change members.
    * @throws NdexException
    *            Failed to query the database.
    **************************************************************************/
    @DELETE
    @Path("/{groupId}/member/{userId}")
    @Produces("application/json")
    public void removeMember(@PathParam("groupId")final String groupId, @PathParam("userId")final String userId) throws IllegalArgumentException, ObjectNotFoundException, SecurityException, NdexException
    {
        if (groupId == null || groupId.isEmpty())
            throw new IllegalArgumentException("No group ID was specified.");
        else if (userId == null || userId.isEmpty())
            throw new IllegalArgumentException("No member was specified.");
        
        try
        {
            setupDatabase();
            
            final ORID groupRid = IdConverter.toRid(groupId);
            final IGroup group = _orientDbGraph.getVertex(groupRid, IGroup.class);
            
            if (group == null)
                throw new ObjectNotFoundException("Group", groupId);
            else if (!hasPermission(new Group(group), Permissions.ADMIN))
                throw new SecurityException("Access denied.");
    
            final IUser user = _orientDbGraph.getVertex(IdConverter.toRid(userId), IUser.class);
            if (user == null)
                throw new ObjectNotFoundException("User", userId);
            
            for (IGroupMembership groupMember : group.getMembers())
            {
                String memberId = IdConverter.toJid((ORID)groupMember.getMember().asVertex().getId());
                if (memberId.equals(userId))
                {
                    if (countAdminMembers(groupRid) < 2)
                        throw new SecurityException("Cannot remove the only ADMIN member.");
                    
                    group.removeMember(groupMember);
                    user.removeGroup(groupMember);
                    _orientDbGraph.getBaseGraph().commit();
                    return;
                }
            }
        }
        catch (ObjectNotFoundException | SecurityException ne)
        {
            throw ne;
        }
        catch (Exception e)
        {
            if (e.getMessage().indexOf("cluster: null") > -1)
                throw new ObjectNotFoundException("Group", groupId);
            
            _logger.error("Failed to remove member.", e);
            _orientDbGraph.getBaseGraph().rollback();
            throw new NdexException("Failed to remove member.");
        }
        finally
        {
            teardownDatabase();
        }
    }

    /**************************************************************************
    * Updates a group.
    * 
    * @param updatedGroup
    *            The updated group information.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws ObjectNotFoundException
    *            The group doesn't exist.
    * @throws SecurityException
    *            The user doesn't have access to update the group.
    * @throws NdexException
    *            Failed to update the user in the database.
    **************************************************************************/
    @POST
    @Produces("application/json")
    public void updateGroup(final Group updatedGroup) throws IllegalArgumentException, ObjectNotFoundException, SecurityException, NdexException
    {
        if (updatedGroup == null)
            throw new IllegalArgumentException("The updated group is empty.");

        try
        {
            setupDatabase();
            
            final IGroup groupToUpdate = _orientDbGraph.getVertex(IdConverter.toRid(updatedGroup.getId()), IGroup.class);
            if (groupToUpdate == null)
                throw new ObjectNotFoundException("Group", updatedGroup.getId());
            else if (!hasPermission(updatedGroup, Permissions.WRITE))
                throw new SecurityException("Access denied.");
            
            if (!updatedGroup.getDescription().equals(groupToUpdate.getDescription()))
                groupToUpdate.setDescription(updatedGroup.getDescription());
            
            if (!updatedGroup.getName().equals(groupToUpdate.getName()))
                groupToUpdate.setName(updatedGroup.getName());
            
            if (!updatedGroup.getOrganizationName().equals(groupToUpdate.getOrganizationName()))
                groupToUpdate.setOrganizationName(updatedGroup.getOrganizationName());
            
            if (!updatedGroup.getWebsite().equals(groupToUpdate.getWebsite()))
                groupToUpdate.setWebsite(updatedGroup.getWebsite());
            
            _orientDbGraph.getBaseGraph().commit();
        }
        catch (SecurityException | ObjectNotFoundException onfe)
        {
            throw onfe;
        }
        catch (Exception e)
        {
            if (e.getMessage().indexOf("cluster: null") > -1)
                throw new ObjectNotFoundException("Group", updatedGroup.getId());
            
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
    * Changes a member's permissions to a group.
    * 
    * @param groupId
    *            The group ID.
    * @param groupMember
    *            The member being updated.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws ObjectNotFoundException
    *            The network or member doesn't exist.
    * @throws SecurityException
    *            The user doesn't have access to change members.
    * @throws NdexException
    *            Failed to query the database.
    **************************************************************************/
    @POST
    @Path("/{groupId}/member")
    @Produces("application/json")
    public void updateMember(@PathParam("groupId")final String groupId, final Membership groupMember) throws IllegalArgumentException, ObjectNotFoundException, SecurityException, NdexException
    {
        if (groupId == null || groupId.isEmpty())
            throw new IllegalArgumentException("No group ID was specified.");
        else if (groupMember == null)
            throw new IllegalArgumentException("The member to update is empty.");
        else if (groupMember.getResourceId() == null || groupMember.getResourceId().isEmpty())
            throw new IllegalArgumentException("No member ID was specified.");
        
        try
        {
            setupDatabase();
            
            final ORID groupRid = IdConverter.toRid(groupId);
            final IGroup group = _orientDbGraph.getVertex(groupRid, IGroup.class);
            
            if (group == null)
                throw new ObjectNotFoundException("Group", groupId);
            else if (!hasPermission(new Group(group), Permissions.ADMIN))
                throw new SecurityException("Access denied.");
    
            final IUser user = _orientDbGraph.getVertex(IdConverter.toRid(groupMember.getResourceId()), IUser.class);
            if (user == null)
                throw new ObjectNotFoundException("User", groupMember.getResourceId());
            
            for (IGroupMembership groupMembership : group.getMembers())
            {
                final String memberId = IdConverter.toJid((ORID)groupMembership.getMember().asVertex().getId());
                if (memberId.equals(groupMember.getResourceId()))
                {
                    if (countAdminMembers(groupRid) < 2)
                        throw new SecurityException("Cannot change the permissions on the only ADMIN member.");
                    
                    groupMembership.setPermissions(groupMember.getPermissions());
                    _orientDbGraph.getBaseGraph().commit();
                    return;
                }
            }
        }
        catch (ObjectNotFoundException | SecurityException ne)
        {
            throw ne;
        }
        catch (Exception e)
        {
            if (e.getMessage().indexOf("cluster: null") > -1)
                throw new ObjectNotFoundException("Group", groupId);
            
            _logger.error("Failed to update member: " + groupMember.getResourceName() + ".", e);
            _orientDbGraph.getBaseGraph().rollback();
            throw new NdexException("Failed to update member: " + groupMember.getResourceName() + ".");
        }
        finally
        {
            teardownDatabase();
        }
    }
    
    

    /**************************************************************************
    * Counter the number of administrative members in the network.
    **************************************************************************/
    private long countAdminMembers(final ORID groupRid) throws NdexException
    {
        final List<ODocument> adminCount = _ndexDatabase.query(new OSQLSynchQuery<Integer>("select count(@rid) from GroupMembership where in_groupMembers = " + groupRid + " and permissions = 'ADMIN'"));
        if (adminCount == null || adminCount.isEmpty())
            throw new NdexException("Unable to count ADMIN members.");
        
        return (long)adminCount.get(0).field("count");
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
            if (groupMembership.getResourceId().equals(targetGroup.getId()) && groupMembership.getPermissions().compareTo(requiredPermissions) > -1)
                return true;
        }
        
        return false;
    }
}
