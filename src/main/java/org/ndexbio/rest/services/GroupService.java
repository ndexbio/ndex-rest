/**
 * Copyright (c) 2013, 2016, The Regents of the University of California, The Cytoscape Consortium
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */
package org.ndexbio.rest.services;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;

import java.util.UUID;

import org.ndexbio.model.exceptions.DuplicateObjectException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.object.SimpleUserQuery;
import org.ndexbio.common.models.dao.postgresql.GroupDAO;
import org.ndexbio.model.object.Membership;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.Group;
import org.ndexbio.rest.annotations.ApiDoc;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

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
	 * @throws IOException 
	 * @throws SQLException 
	 * @throws JsonMappingException 
	 * @throws JsonParseException 
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
			NdexException, JsonParseException, JsonMappingException, SQLException, IOException {
	
		logger.info("[start: Creating group {}]", newGroup.getGroupName());

		try (GroupDAO dao = new GroupDAO()){
			newGroup.setGroupName(newGroup.getGroupName().toLowerCase());
			Group group = dao.createNewGroup(newGroup, this.getLoggedInUser().getExternalId());
			dao.commit();	
			logger.info("[end: Group {} ({}) created.]", newGroup.getGroupName(), group.getExternalId());
			return group;
		} 
	}


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
	 * @throws SQLException 
	 **************************************************************************/
	
	
	@DELETE
	@Path("/{groupId}")
	@Produces("application/json")
	@ApiDoc("Delete the group specified by groupId. " +
	        "Errors if the group is not found or if the authenticated user does not have authorization to delete the group.")
	public void deleteGroup(@PathParam("groupId") final String groupId)
			throws ObjectNotFoundException, NdexException, SQLException {
		
		logger.info("[start: Deleting group {}]", groupId);
		
		try (GroupDAO dao = new GroupDAO()){
			dao.deleteGroupById(UUID.fromString(groupId),this.getLoggedInUser().getExternalId());
			dao.commit();
			logger.info("[end: Group {} deleted]", groupId);
		} 
	}


	/**************************************************************************
	 * Find Groups based on search parameters - string matching for now
	 * 
	 * @params searchParameters The search parameters.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws NdexException
	 *             Failed to query the database.
	 * @return Groups that match the search criteria.
	 * @throws SQLException 
	 **************************************************************************/
	@POST
	@PermitAll
	@Path("/search/{skipBlocks}/{blockSize}")
	@Produces("application/json")
	@ApiDoc("Returns a list of groups found based on the searchOperator and the POSTed searchParameters.")
	public List<Group> findGroups(SimpleUserQuery simpleQuery,
			@PathParam("skipBlocks") final int skip,
			@PathParam("blockSize") final int top)
			throws IllegalArgumentException, NdexException, SQLException {

		logger.info("[start: Search group \"{}\"]", simpleQuery.getSearchString());
		
		try (GroupDAO dao = new GroupDAO()) {
			if(simpleQuery.getAccountName() != null)
				simpleQuery.setAccountName(simpleQuery.getAccountName().toLowerCase());
			final List<Group> groups = dao.findGroups(simpleQuery, skip, top);
			logger.info("[end: Search group \"{}\"]", simpleQuery.getSearchString());
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
	 * @throws IOException 
	 * @throws SQLException 
	 * @throws JsonMappingException 
	 * @throws JsonParseException 
	 **************************************************************************/
	@GET
	@PermitAll
	@Path("/{groupId}")
	@Produces("application/json")
	@ApiDoc("Returns a group JSON structure for the group specified by groupId. Errors if the group is not found. ")
	public Group getGroup(@PathParam("groupId") final String groupId)
			throws IllegalArgumentException,ObjectNotFoundException, NdexException, JsonParseException, JsonMappingException, SQLException, IOException {
		
		logger.info("[start: Getting group {}]", groupId);

		try (GroupDAO dao = new GroupDAO()) {
			final Group group = dao.getGroupById(UUID.fromString(groupId));
			logger.info("[end: Getting group {}]", groupId);
			return group;
		} 
	}


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
	 * @throws SQLException 
	 * @throws JsonProcessingException 
	 **************************************************************************/
	@POST
	@Path("/{groupId}")
	@Produces("application/json")
	@ApiDoc("Updates the group metadata corresponding to the POSTed group JSON structure. " + 
			"Errors if the JSON structure does not specify the group id or if no group is found by that id. ")
	public Group updateGroup(final Group updatedGroup, 
							@PathParam("groupId") final String id)
			throws IllegalArgumentException, ObjectNotFoundException, NdexException, SQLException, JsonProcessingException {

		logger.info("[start: Updating group {}]", id);
		
		try (GroupDAO dao = new GroupDAO ()){
			
			UUID groupId = UUID.fromString(id);
			if ( updatedGroup.getExternalId() !=null && !groupId.equals(updatedGroup.getExternalId())) {
				throw new NdexException ( "UUID does't match between URL and uploaded group object.");
			}
			if ( ! dao.isGroupAdmin(groupId, getLoggedInUser().getExternalId()))
				throw new NdexException ("Only group administrators can update a group." );
			
			Group group = dao.updateGroup(updatedGroup, groupId);
			dao.commit();
			logger.info("[end: Updating group {}]", id);		
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
	 * @throws SQLException 
	 **************************************************************************/
	@POST
	@Path("/{groupId}/member/{userId}")
	@ApiDoc("Updates a user's membership corresponding to the POSTed Permission in the group specified by groupId." +
			"Errors if the authenticated user does not have admin permissions for the group. " + 
			"Errors if the change would leave the group without an Admin member.")
	public void updateMember(@PathParam("groupId") final String group_id,
			@PathParam("userId") final String user_id,
			final Permissions permission) throws IllegalArgumentException,
			ObjectNotFoundException, NdexException, SQLException {

		logger.info("[start: Updating members of group {}]", group_id);
		UUID groupId = UUID.fromString(group_id) ;
		UUID userId = UUID.fromString(user_id);
	
		try (GroupDAO dao = new GroupDAO()) {
			
			if ( !dao.isGroupAdmin(groupId, getLoggedInUserId()))
				throw new NdexException("Only group admin can update membership.");
			
			//check for resource name? but it can be a network. Not really important, the code uses external id's
			dao.updateMember(groupId, userId, permission, this.getLoggedInUser().getExternalId());
			dao.commit();
			logger.info("[end: Member updated for group ]");
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
	 * @throws SQLException 
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
	public void removeUserMember(@PathParam("groupId") final String groupIdStr,
			@PathParam("memberId") final String memberId) throws IllegalArgumentException,
			ObjectNotFoundException, NdexException, SQLException {

		logger.info("[start: Removing member {} from group {}]", memberId, groupIdStr);

		UUID groupId = UUID.fromString(groupIdStr);
		try (GroupDAO dao = new GroupDAO()){
			if ( !dao.isGroupAdmin(groupId, getLoggedInUserId()))
				throw new NdexException("Only group admin can update membership.");
			
			dao.removeMember(UUID.fromString(memberId), groupId, this.getLoggedInUser().getExternalId());
			dao.commit();
			logger.info("[start: Member {} removed from group {}]", memberId, groupId);
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
	 * @throws SQLException 
	 * @throws IOException 
	 * @throws JsonMappingException 
	 * @throws JsonParseException 
	 **************************************************************************/
	
	@GET
	@Path("/{groupId}/network/{permission}/{skipBlocks}/{blockSize}")
	@Produces("application/json")
	@ApiDoc("Return a list of network membership objects which the given group have direct permission to.")
	public List<Membership> getGroupNetworkMemberships(@PathParam("groupId") final String groupIdStr,
			@PathParam("permission") final String permissions ,
			@PathParam("skipBlocks") int skipBlocks,
			@PathParam("blockSize") int blockSize) throws NdexException, SQLException, JsonParseException, JsonMappingException, IllegalArgumentException, IOException {

		logger.info("[start: Getting {} networks of group {}]", permissions, groupIdStr);
		
		Permissions permission = Permissions.valueOf(permissions.toUpperCase());
		UUID groupId = UUID.fromString(groupIdStr);
		
		try (GroupDAO dao = new GroupDAO()){
			if ( !dao.isInGroup(groupId, getLoggedInUserId()))
				throw new NdexException("User is not a member of this group.");
			List<Membership> l = dao.getGroupNetworkMemberships(groupId, permission, skipBlocks, blockSize);
			logger.info("[end: Getting {} networks of group {}]", permissions, groupId);
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
	 * @throws SQLException 
	 * @throws IOException 
	 * @throws JsonMappingException 
	 * @throws JsonParseException 
	 **************************************************************************/
	
	@GET
	@Path("/{groupId}/user/{permission}/{skipBlocks}/{blockSize}")
	@Produces("application/json")
	@ApiDoc("")
	public List<Membership> getGroupUserMemberships(@PathParam("groupId") final String groupIdStr,
			@PathParam("permission") final String permissions ,
			@PathParam("skipBlocks") int skipBlocks,
			@PathParam("blockSize") int blockSize) throws NdexException, SQLException, JsonParseException, JsonMappingException, IllegalArgumentException, IOException {

		logger.info("[start: Getting {} users in group {}]", permissions, groupIdStr);

		Permissions permission = Permissions.valueOf(permissions.toUpperCase());
		UUID groupId = UUID.fromString(groupIdStr);
		
		try (GroupDAO dao = new GroupDAO()){
			if ( ! dao.isInGroup(groupId, getLoggedInUserId())) {
				throw new NdexException("User has to be a member of this group.");
			}
			List<Membership> l = dao.getGroupUserMemberships(groupId, permission, skipBlocks, blockSize);
			logger.info("[end:]");
			return l;
		} 
	}
	
	@GET
	@PermitAll
	@Path("/{groupId}/membership/{networkId}")
	@Produces("application/json")
	@ApiDoc("For authenticated users, this function returns all the networks that the given group has direct access to and the authenticated user can see." + 
			"For anonymous users, this function returns all publice networks that the specified group bas direct access to."
			+ "")
	public Permissions getNetworkMembership(@PathParam("groupId") final String groupIdStr,
			@PathParam("networkId") final String networkId) throws NdexException, SQLException {
		
		logger.info("[start: Getting network membership for groupId {} and networkId {}]", 
				groupIdStr, networkId);
		
		UUID groupId = UUID.fromString(groupIdStr);
		try (GroupDAO dao = new GroupDAO()) {
			if ( !dao.isInGroup(groupId,getLoggedInUserId()) )
				throw new NdexException ("Only a group member or admin can check group permission on a network");
			
			Permissions m = dao.getMembershipToNetwork(groupId, UUID.fromString(networkId));
			logger.info("[start: Getting network membership]");
			return m;
		} 
	} 
	
/*	@GET
	@PermitAll
	@Path("/{groupId}/networks")
	@Produces("application/json")
	@ApiDoc("Return a list of networkSummary objects on which the given group has a explicit READ or WRITE permission.")
	public List <NetworkSummary> getNetworkSummaries(@PathParam("groupId") final String groupId) throws NdexException {
		
		logger.info("[start: Getting networks of group {}]", groupId);
				
		try (GroupDocDAO dao = new GroupDocDAO()){
			List<NetworkSummary> l = dao.getGroupNetworks(groupId, getLoggedInUserId() );
					
			logger.info("[end: Getting networks of group {}]", groupId);
			return l;
		}
	}
	
*/
	
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



}
