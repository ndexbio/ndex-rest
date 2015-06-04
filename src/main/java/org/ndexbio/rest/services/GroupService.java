package org.ndexbio.rest.services;

import java.util.List;

import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
//import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;

import java.util.UUID;

import org.ndexbio.model.exceptions.DuplicateObjectException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.object.SimpleUserQuery;
import org.ndexbio.common.models.dao.orientdb.GroupDAO;
import org.ndexbio.common.models.dao.orientdb.GroupDocDAO;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.model.object.Membership;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.Group;
import org.ndexbio.rest.annotations.ApiDoc;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

@Path("/group")
public class GroupService extends NdexService {

	static Logger logger = LoggerFactory.getLogger(GroupService.class);

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
	@POST
	@Produces("application/json")
	@ApiDoc("Create a group owned by the authenticated user based on the supplied group JSON structure. " +
	        "Errors if the group name specified in the JSON is not valid or is already in use. ")
	public Group createGroup(final Group newGroup)
			throws IllegalArgumentException, DuplicateObjectException,
			NdexException {
		
		//logInfo(logger,"Creating group " + newGroup.getAccountName() + ".");
		logger.info(userNameForLog() + "[start: Creating group " + newGroup.getAccountName() + "]");
		
		try (GroupDAO dao = getGroupDAO()){
			newGroup.setAccountName(newGroup.getAccountName().toLowerCase());
			Group group = dao.createNewGroup(newGroup, this.getLoggedInUser().getExternalId());
			dao.commit();	
			logger.info(userNameForLog() + "[end: Group " + group.getAccountName() + " (" + group.getExternalId() + ") created. ]");
			return group;
		} 
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
	
	
	@DELETE
	@Path("/{groupId}")
	@Produces("application/json")
	@ApiDoc("Delete the group specified by groupId. " +
	        "Errors if the group is not found or if the authenticated user does not have authorization to delete the group.")
	public void deleteGroup(@PathParam("groupId") final String groupId)
			throws ObjectNotFoundException, NdexException {
		
		logger.info(userNameForLog() + "[start: Deleting group " + groupId +  "]");
		
		try (GroupDAO dao = getGroupDAO()){
			dao.deleteGroupById(UUID.fromString(groupId),this.getLoggedInUser().getExternalId());
			dao.commit();
			logger.info(userNameForLog() + "[end: Group " + groupId +  " deleted]");
		} 
	}

	/*
	private void validateGroupDeletionAuthorization(final String groupId,
			final ORID groupRid, final IGroup groupToDelete)
			throws ObjectNotFoundException, NdexException {
		if (groupToDelete == null)
			throw new ObjectNotFoundException("Group", groupId);
		else if (!hasPermission(new Group(groupToDelete), Permissions.ADMIN))
			throw new UnauthorizedOperationException(
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
	@Path("/search/{skipBlocks}/{blockSize}")
	@Produces("application/json")
	@ApiDoc("Returns a list of groups found based on the searchOperator and the POSTed searchParameters.")
	public List<Group> findGroups(SimpleUserQuery simpleQuery,
			@PathParam("skipBlocks") final int skip,
			@PathParam("blockSize") final int top)
			throws IllegalArgumentException, NdexException {
		
		logger.info(userNameForLog() + "[start: Search group \"" + simpleQuery.getSearchString() + "\" ]");
		
		try (GroupDocDAO dao = getGroupDocDAO()) {
			if(simpleQuery.getAccountName() != null)
				simpleQuery.setAccountName(simpleQuery.getAccountName().toLowerCase());
			final List<Group> groups = dao.findGroups(simpleQuery, skip, top);
			logger.info(userNameForLog() + "[end: Search group \"" + simpleQuery.getSearchString() + "\"]");			
			return groups;
		} 
	}

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
	@GET
	@PermitAll
	@Path("/{groupId}")
	@Produces("application/json")
	@ApiDoc("Returns a group JSON structure for the group specified by groupId. Errors if the group is not found. ")
	public Group getGroup(@PathParam("groupId") final String groupId)
			throws IllegalArgumentException,ObjectNotFoundException, NdexException {
		
		logger.info(userNameForLog() + "[start: Getting group " + groupId + "]");
		
		try (GroupDocDAO dao = getGroupDocDAO()) {
			final Group group = dao.getGroupById(UUID.fromString(groupId));
			logger.info(userNameForLog() + "[end: Getting group " + groupId + "]");			
			return group;
		} 
	}

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
				throw new UnauthorizedOperationException("Access denied.");

			final IUser user = _orientDbGraph.getVertex(
					IdConverter.toRid(userId), IUser.class);
			if (user == null)
				throw new ObjectNotFoundException("User", userId);

			for (IGroupMembership groupMember : group.getMembers()) {
				String memberId = IdConverter.toJid((ORID) groupMember
						.getMember().asVertex().getId());
				if (memberId.equals(userId)) {
					if (countAdminMembers(groupRid) < 2)
						throw new UnauthorizedOperationException(
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
	@POST
	@Path("/{groupId}")
	@Produces("application/json")
	@ApiDoc("Updates the group metadata corresponding to the POSTed group JSON structure. " + 
			"Errors if the JSON structure does not specify the group id or if no group is found by that id. ")
	public Group updateGroup(final Group updatedGroup, 
							@PathParam("groupId") final String id)
			throws IllegalArgumentException, ObjectNotFoundException, NdexException {
		
		logger.info(userNameForLog() + "[start: Updating group " + id + "]");		
		
		try (GroupDAO dao = getGroupDAO()){
			Group group = dao.updateGroup(updatedGroup, UUID.fromString(id), this.getLoggedInUser().getExternalId());
			dao.commit();
			logger.info(userNameForLog() + "[end: Updating group " + id + "]");
			return group;
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
	 *             Bad input.
	 * @throws ObjectNotFoundException
	 *             The network or member doesn't exist.
	 * @throws NdexException
	 *             Failed to query the database.
	 **************************************************************************/
	@POST
	@Path("/{groupId}/member")
	@ApiDoc("Updates the membership corresponding to the POSTed GroupMembership JSON structure in the group specified by groupId " +
			"Errors if GroupMembership is or groupId is not provided. " + 
			"Errors if the authenticated user does not have admin permissions for the group. " + 
			"Errors if the change would leave the group without an Admin member.")
	public void updateMember(@PathParam("groupId") final String groupId,
			final Membership groupMember) throws IllegalArgumentException,
			ObjectNotFoundException, NdexException {

		logger.info(userNameForLog() + "[start: Updating members of Group " + groupId + "]");	
		
		try (GroupDAO dao = getGroupDAO()) {
			if(groupMember.getMemberAccountName() != null)
				groupMember.setMemberAccountName(groupMember.getMemberAccountName().toLowerCase());
			//check for resource name? but it can be a network. Not really important, the code uses external id's
			dao.updateMember(groupMember, UUID.fromString(groupId), this.getLoggedInUser().getExternalId());
			dao.commit();
			logger.info(userNameForLog() + "[end: Member " + groupMember.getMemberAccountName()
					+ "(" + groupMember.getMembershipType()+ ") updated for group " + groupId + "]");
		} 
	}

	/**************************************************************************
	 * Remove member from group
	 * 
	 * @param groupId
	 *            The group UUID.
	 * @param memberId
	 *            The member UUID
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws ObjectNotFoundException
	 *             The network or member doesn't exist.
	 * @throws NdexException
	 *             Failed to query the database.
	 **************************************************************************/
	/*
	 * refactored to accommodate non-transactional database interactions
	 */
	@DELETE
	@Path("/{groupId}/member/{memberId}")
	@Produces("application/json")
	@ApiDoc("Removes the member specified by userUUID from the group specified by groupUUID. "
			+ "Errors if the group or the user is not found. "
			+ "Also errors if the authenticated user is not authorized to edit the group "
			+ "or if removing the member would leave the group with no Admin member.")
	public void removeMember(@PathParam("groupId") final String groupId,
			@PathParam("memberId") final String memberId) throws IllegalArgumentException,
			ObjectNotFoundException, NdexException {

		logger.info(userNameForLog() + "[start: Removing member " + memberId + " from group " + groupId + "]");

		try (GroupDAO dao = getGroupDAO()){
			dao.removeMember(UUID.fromString(memberId), UUID.fromString(groupId), this.getLoggedInUser().getExternalId());
			dao.commit();
			logger.info(userNameForLog() + "[end: Member " + memberId + " removed from group " + groupId + "]");
		} 
	}
	
	/**************************************************************************
	 * Retrieves array of network membership objects
	 * 
	 * @param groupId
	 *            The group ID.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws ObjectNotFoundException
	 *             The group doesn't exist.
	 * @throws NdexException
	 *             Failed to query the database.
	 **************************************************************************/
	
	@GET
	@PermitAll
	@Path("/{groupId}/network/{permission}/{skipBlocks}/{blockSize}")
	@Produces("application/json")
	@ApiDoc("")
	public List<Membership> getGroupNetworkMemberships(@PathParam("groupId") final String groupId,
			@PathParam("permission") final String permissions ,
			@PathParam("skipBlocks") int skipBlocks,
			@PathParam("blockSize") int blockSize) throws NdexException {
		
		logger.info(userNameForLog() + "[start: Getting "+ permissions + " networks of group " + groupId + "]");
		
		Permissions permission = Permissions.valueOf(permissions.toUpperCase());
		
		try (GroupDocDAO dao = getGroupDocDAO()){
			List<Membership> l = dao.getGroupNetworkMemberships(UUID.fromString(groupId), permission, skipBlocks, blockSize);
			logger.info(userNameForLog() + "[end: Getting "+ permissions + " networks of group " + groupId + "]");
			return l;
		}
	}
	
	/**************************************************************************
	 * Retrieves array of user membership objects
	 * 
	 * @param groupId
	 *            The group ID.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws ObjectNotFoundException
	 *             The group doesn't exist.
	 * @throws NdexException
	 *             Failed to query the database.
	 **************************************************************************/
	
	@GET
	@PermitAll
	@Path("/{groupId}/user/{permission}/{skipBlocks}/{blockSize}")
	@Produces("application/json")
	@ApiDoc("")
	public List<Membership> getGroupUserMemberships(@PathParam("groupId") final String groupId,
			@PathParam("permission") final String permissions ,
			@PathParam("skipBlocks") int skipBlocks,
			@PathParam("blockSize") int blockSize) throws NdexException {

		logger.info(userNameForLog() + "[start: Getting "+ permissions + " users in group " + groupId + "]");
		
		Permissions permission = Permissions.valueOf(permissions.toUpperCase());
		
		try (GroupDocDAO dao = getGroupDocDAO()){
			List<Membership> l = dao.getGroupUserMemberships(UUID.fromString(groupId), permission, skipBlocks, blockSize);
			logger.info(userNameForLog() + "[end: Getting "+ permissions + " users in group " + groupId + "]");
			return l;
		} 
	}
	
	@GET
	@PermitAll
	@Path("/{groupId}/membership/{networkId}")
	@Produces("application/json")
	@ApiDoc("")
	public Membership getNetworkMembership(@PathParam("groupId") final String groupId,
			@PathParam("networkId") final String networkId) throws NdexException {
		
		logger.info(userNameForLog() + "[start: Getting network membership for groupId"+ groupId + " and networkId " + networkId + "]");
		
		try (GroupDocDAO dao = getGroupDocDAO()) {
			Membership m = dao.getMembershipToNetwork(UUID.fromString(groupId), UUID.fromString(networkId));
			logger.info(userNameForLog() + "[end: Getting network membership for groupId "+ groupId + " and networkId " + networkId + "]");
			return m;
		} 
	}
	
	
	/**************************************************************************
	 * Counter the number of administrative members in the network.
	 **************************************************************************/
	/*private long countAdminMembers(final ORID groupRid) throws NdexException {
		final List<ODocument> adminCount = _ndexDatabase
				.query(new OSQLSynchQuery<Integer>(
						"SELECT COUNT(@RID) FROM GroupMembership WHERE in_groupMembers = "
								+ groupRid + " AND permissions = 'ADMIN'"));
		if (adminCount == null || adminCount.isEmpty())
			throw new NdexException("Unable to count ADMIN members.");

		return (long) adminCount.get(0).field("COUNT");
	}*/

	/**************************************************************************
	 * Determines if the logged in user has sufficient permissions to a group.
	 * 
	 * @param targetGroup
	 *            The group to test for permissions.
	 * @return True if the member has permission, false otherwise.
	 * @throws NdexException 
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
	private static GroupDAO getGroupDAO() throws NdexException {
		return new GroupDAO(NdexDatabase.getInstance().getAConnection());
	}

	private static GroupDocDAO getGroupDocDAO() throws NdexException {
		return new GroupDocDAO(NdexDatabase.getInstance().getAConnection());
	}

}
