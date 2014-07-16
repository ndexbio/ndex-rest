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

import org.junit.Assert;
import org.ndexbio.common.exceptions.*;
import org.ndexbio.common.helpers.IdConverter;
import org.ndexbio.common.helpers.Validation;
import org.ndexbio.common.models.data.IGroup;
import org.ndexbio.common.models.data.IGroupMembership;
import org.ndexbio.common.models.data.IUser;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.Group;
import org.ndexbio.model.object.Membership;
import org.ndexbio.model.object.SearchParameters;
import org.ndexbio.rest.CommonValues;
import org.ndexbio.rest.annotations.ApiDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
import com.tinkerpop.blueprints.impls.orient.OrientElement;

@Path("/groups")
public class GroupService extends NdexService {
	private static final Logger _logger = LoggerFactory
			.getLogger(GroupService.class);

	/**************************************************************************
	 * Injects the HTTP request into the base class to be used by
	 * getLoggedInUser().
	 * 
	 * @param httpRequest
	 *            The HTTP request injected by RESTEasy's context.
	 **************************************************************************/
	public GroupService(@Context HttpServletRequest httpRequest) {
		super(httpRequest);
	}

	/**************************************************************************
	 * Creates a group.
	 * 
	 * @param newGroup
	 *            The group to create.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws DuplicateObjectException
	 *             A group with that name already exists.
	 * @throws NdexException
	 *             Failed to create the user in the database.
	 * @return The newly created group.
	 **************************************************************************/
	/*
	 * refactor this method to use non-transactional database interactions
	 * validate input data before creating a database vertex
	 */
	@PUT
	@Produces("application/json")
	@ApiDoc("Create a group owned by the authenticated user based on the supplied group JSON structure. " +
	        "Errors if the group name specified in the JSON is not valid or is already in use. ")
	public Group createGroup(final Group newGroup)
			throws IllegalArgumentException, DuplicateObjectException,
			NdexException {
        return newGroup;		
/*		try {
			Preconditions.checkArgument(null != newGroup, "A group is required");
			Preconditions.checkState(this.isValidGroupName(newGroup), 
					"Group " +newGroup.getName() +" already exists");
		} catch (Exception e1) {
			// for legacy unit tests
			throw new IllegalArgumentException(e1);
		}
			
		try {
			setupDatabase();
			final IUser groupOwner = _orientDbGraph.getVertex(
					IdConverter.toRid(this.getLoggedInUser().getId()),
					IUser.class);
			final IGroup group = _orientDbGraph.addVertex("class:group",
					IGroup.class);
			group.setDescription(newGroup.getDescription());
			group.setName(newGroup.getName());
			group.setOrganizationName(newGroup.getOrganizationName());
			group.setWebsite(newGroup.getWebsite());
			group.setCreatedDate(new Date());
			// register members for new group
			addGroupMembers(newGroup, groupOwner, group);
			newGroup.setId(IdConverter.toJid((ORID) group.asVertex().getId()));
			return newGroup;
		} catch (Exception e) {		
			
			_logger.error(
					"Failed to create group: " + newGroup.getName() + ".", e);		
			throw new NdexException("Failed to create your group.");
		} finally {
			teardownDatabase();
		} */
	}

/*	private void addGroupMembers(final Group newGroup, final IUser groupOwner,
			final IGroup group) {
		if (newGroup.getMembers() == null
				|| newGroup.getMembers().size() == 0) {
			final IGroupMembership membership = _orientDbGraph.addVertex(
					"class:groupMembership", IGroupMembership.class);
			membership.setPermissions(Permissions.ADMIN);
			membership.setMember(groupOwner);
			membership.setGroup(group);

			
		} else {
			for (Membership member : newGroup.getMembers()) {
				final IUser groupMember = _orientDbGraph.getVertex(
						IdConverter.toRid(member.getResourceId()),
						IUser.class);

				final IGroupMembership membership = _orientDbGraph
						.addVertex("class:groupMembership",
								IGroupMembership.class);
				membership.setPermissions(member.getPermissions());
				membership.setMember(groupMember);
				membership.setGroup(group);

				groupMember.addGroup(membership);
				group.addMember(membership);
			}
		}
	}
*/
	/**************************************************************************
	 * Deletes a group.
	 * 
	 * @param groupId
	 *            The ID of the group to delete.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws ObjectNotFoundException
	 *             The group doesn't exist.
	 * @throws SecurityException
	 *             The user doesn't have permissions to delete the group.
	 * @throws NdexException
	 *             Failed to delete the user from the database.
	 **************************************************************************/
	
	/*
	 * refactored to accomodate move to non-transactional database 
	 * operations. 
	 */
/*	@DELETE
	@Path("/{groupId}")
	@Produces("application/json")
	@ApiDoc("Delete the group specified by groupId. " +
	        "Errors if the group is not found or if the authenticated user does not have authorization to delete the group.")
	public void deleteGroup(@PathParam("groupId") final String groupId)
			throws IllegalArgumentException, ObjectNotFoundException,
			SecurityException, NdexException {
		
		Preconditions.checkArgument(!Strings.isNullOrEmpty(groupId), 
				"No group ID was specified.");

		final ORID groupRid = IdConverter.toRid(groupId);

		try {
			setupDatabase();

			final IGroup groupToDelete = _orientDbGraph.getVertex(groupRid,
					IGroup.class);
			// can this group be deleted
			validateGroupDeletionAuthorization(groupId, groupRid, groupToDelete);

			for (IGroupMembership groupMembership : groupToDelete.getMembers()){
				groupMembership.setMember(null);
				groupMembership.setGroup(null);
				_orientDbGraph.removeVertex(groupMembership.asVertex());
				
			}

			final List<ODocument> groupChildren = _ndexDatabase
					.query(new OSQLSynchQuery<Object>(
							"SELECT @RID FROM (TRAVERSE * FROM "
									+ groupRid
									+ " WHILE @Class <> 'user' and @Class <> 'group')"));
			for (ODocument groupChild : groupChildren) {
				final OrientElement element = _orientDbGraph.getBaseGraph()
						.getElement(groupChild.field("rid", OType.LINK));
				if (element != null)
					element.remove();
			}

			_orientDbGraph.removeVertex(groupToDelete.asVertex());
			_orientDbGraph.getBaseGraph().commit();
		} catch (SecurityException | NdexException ne) {
			_logger.error(ne.getMessage());
			throw ne;
		} catch (Exception e) {
			if (e.getMessage().indexOf("cluster: null") > -1){
				_logger.error("Group to be deleted, not found in database.");
				throw new ObjectNotFoundException("Group", groupId);
			}
			
			_logger.error("Failed to delete group: " + groupId + ".", e);
			
			throw new NdexException("Failed to delete the group.");
		} finally {
			teardownDatabase();
		}
	}

	private void validateGroupDeletionAuthorization(final String groupId,
			final ORID groupRid, final IGroup groupToDelete)
			throws ObjectNotFoundException, NdexException {
		if (groupToDelete == null)
			throw new ObjectNotFoundException("Group", groupId);
		else if (!hasPermission(new Group(groupToDelete), Permissions.ADMIN))
			throw new SecurityException(
					"Insufficient privileges to delete the group.");

		final List<ODocument> adminCount = _ndexDatabase
				.query(new OSQLSynchQuery<Integer>(
						"SELECT COUNT(@RID) FROM GroupMembership WHERE in_groupMembers = "
								+ groupRid + " AND permissions = 'ADMIN'"));
		if (adminCount == null || adminCount.isEmpty())
			throw new NdexException("Unable to count ADMIN members.");
		else if ((long) adminCount.get(0).field("COUNT") > 1)
			throw new NdexException(
					"Cannot delete a group that contains other ADMIN members.");

		final List<ODocument> adminNetworks = _ndexDatabase
				.query(new OSQLSynchQuery<Integer>(
						"SELECT COUNT(@RID) FROM Membership WHERE in_userNetworks = "
								+ groupRid + " AND permissions = 'ADMIN'"));
		if (adminCount == null || adminCount.isEmpty())
			throw new NdexException(
					"Unable to query group/network membership.");
		else if ((long) adminNetworks.get(0).field("COUNT") > 1)
			throw new NdexException(
					"Cannot delete a group that is an ADMIN member of any network.");
		_logger.info("OK to delete group id " + groupId);
	}
*/
	/*
	 * private method to determine if a proposed group name is novel
	 * @params groupName - new group name
	 * @returns boolean - true if group name is new
	 *                    false id group name already exists
	 * 
	 */
/*	private boolean isValidGroupName(Group newGroup) throws IllegalArgumentException,
		NdexException{
		try {
			Preconditions.checkNotNull(newGroup.getName(), "The new group requires a name");
			Preconditions.checkState(Validation.isValid(newGroup.getName(),
					Validation.REGEX_GROUP_NAME), "Invalid group name");
		} catch (Exception e) {
			throw new IllegalArgumentException(e);
		}
		
		
		final SearchParameters searchParameters = new SearchParameters();
        searchParameters.setSearchString(newGroup.getName());
        searchParameters.setSkip(0);
        searchParameters.setTop(1);
        
      
           List<Group> groupList = this.findGroups(searchParameters, 
        		   CommonValues.SEARCH_MATCH_EXACT);
           if (groupList.isEmpty()) {
        	   return true;
           }
           return false;
  	
	}
*/
	/**************************************************************************
	 * Find Groups based on search parameters - string matching for now
	 * 
	 * @params searchParameters The search parameters.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws NdexException
	 *             Failed to query the database.
	 * @return Groups that match the search criteria.
	 **************************************************************************/
	@POST
	@PermitAll
	@Path("/search/{searchOperator}")
	@Produces("application/json")
	@ApiDoc("Returns a list of groups found based on the searchOperator and the POSTed searchParameters.")
/*	public List<Group> findGroups(SearchParameters searchParameters,
			@PathParam("searchOperator") final String searchOperator)
			throws IllegalArgumentException, NdexException {
		try {
			Preconditions.checkNotNull(searchParameters,
					"A SearchParameters object is required");
			Preconditions.checkState(!Strings.isNullOrEmpty(searchParameters.getSearchString()),
					"A search string is required");
			Preconditions.checkState(!Strings.isNullOrEmpty(searchOperator),
					"A search operator is required");
		} catch (Exception e1) {
			// TODO Auto-generated catch block
			throw new IllegalArgumentException(e1);
		}
	
		searchParameters.setSearchString(searchParameters.getSearchString()
					.toLowerCase().trim());

		final List<Group> foundGroups = Lists.newArrayList();
		
		String operator = searchOperator.toLowerCase();
		final int startIndex = searchParameters.getSkip()
				* searchParameters.getTop();

		String query = "";
		switch(operator) {
		case CommonValues.SEARCH_MATCH_EXACT:
			query = "SELECT FROM Group\n" + "WHERE name.toLowerCase() LIKE '"
					+ searchParameters.getSearchString() + "'\n"
					+ "  OR description.toLowerCase() LIKE '"
					+ searchParameters.getSearchString() + "'\n"
					+ "  OR organizationName.toLowerCase() LIKE '"
					+ searchParameters.getSearchString() + "'\n"
					+ "ORDER BY creation_date DESC\n" + "SKIP " + startIndex
					+ "\n" + "LIMIT " + searchParameters.getTop();
			break;
		case CommonValues.SEARCH_MATCH_STARTS_WITH:
			query = "SELECT FROM Group\n" + "WHERE name.toLowerCase() LIKE '"
					+ searchParameters.getSearchString() + "%'\n"
					+ "  OR description.toLowerCase() LIKE '"
					+ searchParameters.getSearchString() + "%'\n"
					+ "  OR organizationName.toLowerCase() LIKE '"
					+ searchParameters.getSearchString() + "%'\n"
					+ "ORDER BY creation_date DESC\n" + "SKIP " + startIndex
					+ "\n" + "LIMIT " + searchParameters.getTop();
			break;
		case CommonValues.SEARCH_MATCH_CONTAINS:
			query = "SELECT FROM Group\n" + "WHERE name.toLowerCase() LIKE '%"
					+ searchParameters.getSearchString() + "%'\n"
					+ "  OR description.toLowerCase() LIKE '%"
					+ searchParameters.getSearchString() + "%'\n"
					+ "  OR organizationName.toLowerCase() LIKE '%"
					+ searchParameters.getSearchString() + "%'\n"
					+ "ORDER BY creation_date DESC\n" + "SKIP " + startIndex
					+ "\n" + "LIMIT " + searchParameters.getTop();
			break;
			default:
				throw new IllegalArgumentException(operator +" is not a supported search operator");
		} // end of switch clause

		try {
			setupDatabase();

			final List<ODocument> groups = _ndexDatabase
					.query(new OSQLSynchQuery<ODocument>(query));
			for (final ODocument group : groups)
				foundGroups.add(new Group(_orientDbGraph.getVertex(group,
						IGroup.class)));

			return foundGroups;
		} catch (Exception e) {
			_logger.error(
					"Failed to search groups: "
							+ searchParameters.getSearchString(), e);
			
			throw new NdexException("Failed to search groups.");
		} finally {
			teardownDatabase();
		}
	}
*/
	/**************************************************************************
	 * Gets a group by ID or name.
	 * 
	 * @param groupId
	 *            The ID or name of the group.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws NdexException
	 *             Failed to query the database.
	 * @return The group.
	 **************************************************************************/
/*	@GET
	@PermitAll
	@Path("/{groupId}")
	@Produces("application/json")
	@ApiDoc("Returns a group JSON structure for the group specified by groupId. Errors if the group is not found. ")
	public Group getGroup(@PathParam("groupId") final String groupId)
			throws IllegalArgumentException, NdexException {
		if (groupId == null || groupId.isEmpty())
			throw new IllegalArgumentException("No group ID was specified.");

		try {
			setupDatabase();

			final IGroup group = _orientDbGraph.getVertex(
					IdConverter.toRid(groupId), IGroup.class);
			if (group != null)
				return new Group(group, true);
		} catch (IllegalArgumentException iae) {
			// The group ID is actually a group name
			final List<ODocument> matchingGroups = _ndexDatabase
					.query(new OSQLSynchQuery<Object>(
							"SELECT FROM Group WHERE name = '" + groupId + "'"));
			if (!matchingGroups.isEmpty())
				return new Group(_orientDbGraph.getVertex(
						matchingGroups.get(0), IGroup.class), true);
		} catch (Exception e) {
			_logger.error("Failed to retrieve group: " + groupId + ".", e);
			throw new NdexException("Failed to retrieve the group.");
		} finally {
			teardownDatabase();
		}

		return null;
	}
*/
	/**************************************************************************
	 * Removes a member from a group.
	 * 
	 * @param groupId
	 *            The group ID.
	 * @param userId
	 *            The ID of the member to remove.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws ObjectNotFoundException
	 *             The network or member doesn't exist.
	 * @throws SecurityException
	 *             The user doesn't have access to change members.
	 * @throws NdexException
	 *             Failed to query the database.
	 **************************************************************************/
	/*
	 * refactored to accommodate non-transactional database interactions
	 */
/*	@DELETE
	@Path("/{groupId}/member/{userId}")
	@Produces("application/json")
	@ApiDoc("Removes the member specified by userId from the group specified by groupId. " + 
			"Errors if the group or the user is not found. " +
			"Also errors if the authenticated user is not authorized to edit the group or if removing the member would leave the group with no Admin member.")
	public void removeMember(@PathParam("groupId") final String groupId,
			@PathParam("userId") final String userId)
			throws IllegalArgumentException, ObjectNotFoundException,
			SecurityException, NdexException {
		
		
		Preconditions.checkArgument(!Strings.isNullOrEmpty(groupId), 
				"A group id is required");
		Preconditions.checkArgument(!Strings.isNullOrEmpty(userId),
				"A user id for the member is required");
	

		try {
			setupDatabase();

			final ORID groupRid = IdConverter.toRid(groupId);
			final IGroup group = _orientDbGraph.getVertex(groupRid,
					IGroup.class);
			if (group == null)
				throw new ObjectNotFoundException("Group", groupId);
			else if (!hasPermission(new Group(group), Permissions.ADMIN))
				throw new SecurityException("Access denied.");

			final IUser user = _orientDbGraph.getVertex(
					IdConverter.toRid(userId), IUser.class);
			if (user == null)
				throw new ObjectNotFoundException("User", userId);

			for (IGroupMembership groupMember : group.getMembers()) {
				String memberId = IdConverter.toJid((ORID) groupMember
						.getMember().asVertex().getId());
				if (memberId.equals(userId)) {
					if (countAdminMembers(groupRid) < 2)
						throw new SecurityException(
								"Cannot remove the only ADMIN member.");

					group.removeMember(groupMember);
					user.removeGroup(groupMember);
					_orientDbGraph.getBaseGraph().commit();
					return;
				}
			}
		} catch (ObjectNotFoundException | SecurityException ne) {
			throw ne;
		} catch (Exception e) {
			if (e.getMessage().indexOf("cluster: null") > -1) {
				_logger.error("Group id " +groupId +" not found");
				throw new ObjectNotFoundException("Group", groupId);
			}
			_logger.error("Failed to remove member.", e);
			
			throw new NdexException("Failed to remove member.");
		} finally {
			teardownDatabase();
		}
	}
*/
	/**************************************************************************
	 * Updates a group.
	 * 
	 * @param updatedGroup
	 *            The updated group information.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws ObjectNotFoundException
	 *             The group doesn't exist.
	 * @throws SecurityException
	 *             The user doesn't have access to update the group.
	 * @throws NdexException
	 *             Failed to update the user in the database.
	 **************************************************************************/
/*	@POST
	@Produces("application/json")
	@ApiDoc("Updates the group metadata corresponding to the POSTed group JSON structure. " + 
			"Errors if the JSON structure does not specify the group id or if no group is found by that id. ")
	public void updateGroup(final Group updatedGroup)
			throws IllegalArgumentException, ObjectNotFoundException,
			SecurityException, NdexException {
		Preconditions.checkArgument(null !=updatedGroup, 
				"A Group is required.");
		

		try {
			setupDatabase();

			final IGroup groupToUpdate = _orientDbGraph.getVertex(
					IdConverter.toRid(updatedGroup.getId()), IGroup.class);
			if (groupToUpdate == null)
				throw new ObjectNotFoundException("Group", updatedGroup.getId());
			else if (!hasPermission(updatedGroup, Permissions.WRITE))
				throw new SecurityException("Access denied.");

			if (updatedGroup.getDescription() != null
					&& !updatedGroup.getDescription().equals(
							groupToUpdate.getDescription()))
				groupToUpdate.setDescription(updatedGroup.getDescription());

			if (updatedGroup.getName() != null
					&& !updatedGroup.getName().equals(groupToUpdate.getName()))
				groupToUpdate.setName(updatedGroup.getName());

			if (updatedGroup.getOrganizationName() != null
					&& !updatedGroup.getOrganizationName().equals(
							groupToUpdate.getOrganizationName()))
				groupToUpdate.setOrganizationName(updatedGroup
						.getOrganizationName());

			if (updatedGroup.getWebsite() != null
					&& !updatedGroup.getWebsite().equals(
							groupToUpdate.getWebsite()))
				groupToUpdate.setWebsite(updatedGroup.getWebsite());

			
		} catch (SecurityException | ObjectNotFoundException onfe) {
			throw onfe;
		} catch (Exception e) {
			if (e.getMessage().indexOf("cluster: null") > -1)
				throw new ObjectNotFoundException("Group", updatedGroup.getId());

			_logger.error("Failed to update group: " + updatedGroup.getName()
					+ ".", e);
		
			throw new NdexException("Failed to update the group.");
		} finally {
			teardownDatabase();
		}
	}
*/
	/**************************************************************************
	 * Changes a member's permissions to a group.
	 * 
	 * @param groupId
	 *            The group ID.
	 * @param groupMember
	 *            The member being updated.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws ObjectNotFoundException
	 *             The network or member doesn't exist.
	 * @throws SecurityException
	 *             The user doesn't have access to change members.
	 * @throws NdexException
	 *             Failed to query the database.
	 **************************************************************************/
	/*
	 * refactored to accommodate non-transactional database interactions
	 */
/*	@POST
	@Path("/{groupId}/member")
	@Produces("application/json")
	@ApiDoc("Updates the membership corresponding to the POSTed GroupMembership JSON structure in the group specified by groupId " +
			"Errors if GroupMembership is or groupId is not provided. " + 
			"Errors if the authenticated user does not have admin permissions for the group. " + 
			"Errors if the change would leave the group without an Admin member.")
	public void updateMember(@PathParam("groupId") final String groupId,
			final Membership groupMember) throws IllegalArgumentException,
			ObjectNotFoundException, SecurityException, NdexException {
		try {
			Preconditions.checkArgument(!Strings.isNullOrEmpty(groupId), 
					"A group id is required");
			Preconditions.checkNotNull(groupMember, 
					"A group member is required");
			Preconditions.checkState(!Strings.isNullOrEmpty(groupMember.getResourceId()),
					"A resource id is required for the group member");
		} catch (Exception e1) {
			throw new IllegalArgumentException(e1);
		}
		

		try {
			setupDatabase();

			final ORID groupRid = IdConverter.toRid(groupId);
			final IGroup group = _orientDbGraph.getVertex(groupRid,
					IGroup.class);

			if (group == null)
				throw new ObjectNotFoundException("Group", groupId);
			else if (!hasPermission(new Group(group), Permissions.ADMIN))
				throw new SecurityException("Access denied.");

			final IUser user = _orientDbGraph
					.getVertex(IdConverter.toRid(groupMember.getResourceId()),
							IUser.class);
			if (user == null)
				throw new ObjectNotFoundException("User",
						groupMember.getResourceId());

			for (IGroupMembership groupMembership : group.getMembers()) {
				final String memberId = IdConverter
						.toJid((ORID) groupMembership.getMember().asVertex()
								.getId());
				if (memberId.equals(groupMember.getResourceId())) {
					if (countAdminMembers(groupRid) < 2){
						throw new SecurityException(
								"Cannot change the permissions on the only ADMIN member.");
					}
					groupMembership
							.setPermissions(groupMember.getPermissions());
					
					return;
				}
			}
		} catch (ObjectNotFoundException | SecurityException ne) {
			throw ne;
		} catch (Exception e) {
			if (e.getMessage().indexOf("cluster: null") > -1)
				throw new ObjectNotFoundException("Either the Group " + groupId
						+ " or User " + groupMember.getResourceId()
						+ " was not found.");

			_logger.error(
					"Failed to update member: " + groupMember.getResourceName()
							+ ".", e);
			
			throw new NdexException("Failed to update member: "
					+ groupMember.getResourceName() + ".");
		} finally {
			teardownDatabase();
		}
	}
*/
	/**************************************************************************
	 * Counter the number of administrative members in the network.
	 **************************************************************************/
	private long countAdminMembers(final ORID groupRid) throws NdexException {
		final List<ODocument> adminCount = _ndexDatabase
				.query(new OSQLSynchQuery<Integer>(
						"SELECT COUNT(@RID) FROM GroupMembership WHERE in_groupMembers = "
								+ groupRid + " AND permissions = 'ADMIN'"));
		if (adminCount == null || adminCount.isEmpty())
			throw new NdexException("Unable to count ADMIN members.");

		return (long) adminCount.get(0).field("COUNT");
	}

	/**************************************************************************
	 * Determines if the logged in user has sufficient permissions to a group.
	 * 
	 * @param targetGroup
	 *            The group to test for permissions.
	 * @return True if the member has permission, false otherwise.
	 **************************************************************************/
/*	private boolean hasPermission(Group targetGroup,
			Permissions requiredPermissions) {
		for (Membership groupMembership : this.getLoggedInUser().getGroups()) {
			if (groupMembership.getResourceId().equals(targetGroup.getId())
					&& groupMembership.getPermissions().compareTo(
							requiredPermissions) > -1)
				return true;
		}

		return false;
	}
*/
	
}
