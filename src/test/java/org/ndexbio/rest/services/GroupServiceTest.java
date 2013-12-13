package org.ndexbio.rest.services;

import java.util.Date;

import javax.ws.rs.PUT;
import javax.ws.rs.Produces;

import org.ndexbio.rest.domain.IGroup;
import org.ndexbio.rest.domain.IGroupMembership;
import org.ndexbio.rest.domain.IUser;
import org.ndexbio.rest.domain.Permissions;
import org.ndexbio.rest.exceptions.NdexException;
import org.ndexbio.rest.helpers.RidConverter;
import org.ndexbio.rest.helpers.Validation;
import org.ndexbio.rest.models.Group;
import org.ndexbio.rest.models.Membership;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.orientechnologies.orient.core.id.ORID;

public class GroupServiceTest extends NdexServiceTest
{
	
    private static final Logger _logger = LoggerFactory.getLogger(GroupService.class);
    
    
    
    /**************************************************************************
    * Injects the HTTP request into the base class to be used by
    * getLoggedInUser(). 
    * 
    * @param httpRequest
    *            The HTTP request injected by RESTEasy's context.
    **************************************************************************/
    public GroupServiceTest()
    {
        super();
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
            
            Membership newGroupMembership = newGroup.getMembers().iterator().next();
            final ORID userRid = RidConverter.convertToRid(newGroupMembership.getResourceId());
            
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

}
