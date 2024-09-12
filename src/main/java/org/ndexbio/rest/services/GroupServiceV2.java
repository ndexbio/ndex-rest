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
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

import org.apache.solr.client.solrj.SolrServerException;
import org.ndexbio.common.models.dao.postgresql.GroupDAO;
import org.ndexbio.common.models.dao.postgresql.RequestDAO;
import org.ndexbio.common.solr.GroupIndexManager;
import org.ndexbio.model.exceptions.DuplicateObjectException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.Group;
import org.ndexbio.model.object.Membership;
import org.ndexbio.model.object.PermissionRequest;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.Request;
import org.ndexbio.model.object.RequestType;
import org.ndexbio.rest.Configuration;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

@Path("/v2/group")
public class GroupServiceV2 extends NdexService {


	/**************************************************************************
	 * Injects the HTTP request into the base class to be used by
	 * getLoggedInUser().
	 * 
	 * @param httpRequest
	 *            The HTTP request injected by RESTEasy's context.
	 **************************************************************************/
	public GroupServiceV2(@Context HttpServletRequest httpRequest) {
		super(httpRequest);
	}

	/**************************************************************************
	 * Creates a group.
	 * 
	 * @param newGroup
	 *            The group to create.

	 * @return The newly created group's URI.
	 * @throws Exception 

	 **************************************************************************/
	/*
	 * refactor this method to use non-transactional database interactions
	 * validate input data before creating a database vertex
	 */
	@POST
	@Produces("text/plain")

	public Response createGroup(final Group newGroup)
			throws  Exception {
	
		try (GroupDAO dao = new GroupDAO()) {
			// newGroup.setGroupName(newGroup.getGroupName().toLowerCase());
			Group group = dao.createNewGroup(newGroup, this.getLoggedInUser().getExternalId());
			try (GroupIndexManager m = new GroupIndexManager()) {
				m.addGroup(group.getExternalId().toString(), group.getGroupName(), group.getDescription());
			}
			dao.commit();

			URI l = new URI(Configuration.getInstance().getHostURI() + Configuration.getInstance().getRestAPIPrefix()
					+ "/group/" + group.getExternalId());

			return Response.created(l).entity(l).build();
			// return group;
		} catch (URISyntaxException e) {
			throw new NdexException("Server Error, can create URL for the new resource: " + e.getMessage(), e);
		} catch (SolrServerException e) {
			throw new NdexException("Failed to create Solr Index for new group: " + e.getMessage(), e);
		} catch (IOException e) {
			throw new NdexException("Failed to create group: " + e.getMessage(), e);

		}
	}


	/**************************************************************************
	 * Deletes a group.
	 * 
	 * @param groupId
	 *            The ID of the group to delete.
	 * @throws Exception 

	 **************************************************************************/
	
	
	@DELETE
	@Path("/{groupid}")
	@Produces("application/json")
	public void deleteGroup(@PathParam("groupid") final String groupId)
			throws Exception {
				
		try (GroupDAO dao = new GroupDAO()){
			dao.deleteGroupById(UUID.fromString(groupId),this.getLoggedInUser().getExternalId());
			try (GroupIndexManager m = new GroupIndexManager()) {
				m.deleteGroup(groupId);
				dao.commit();
			}
		} catch (SolrServerException | IOException e) {
			throw new NdexException("Failed to delete group: " + e.getMessage(), e);
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
	@Path("/{groupid}")
	@Produces("application/json")
	public Group getGroup(@PathParam("groupid") final String groupId)
			throws ObjectNotFoundException, NdexException, SQLException {
		
		try (GroupDAO dao = new GroupDAO()) {
			final Group group = dao.getGroupById(UUID.fromString(groupId));
			return group;
		} catch (IOException e) {
			throw new NdexException("Failed to get group: " + e.getMessage(), e);
		} 
	}


	/**************************************************************************
	 * Updates a group.
	 * 
	 * @param updatedGroup
	 *            The updated group information.
	 * @throws Exception 

	 **************************************************************************/
	@PUT
	@Path("/{groupid}")
	@Produces("application/json")
	
	public void updateGroup(final Group updatedGroup, 
							@PathParam("groupid") final String id)
			throws Exception {

		
		try (GroupDAO dao = new GroupDAO ()){
			
			UUID groupId = UUID.fromString(id);
			if ( updatedGroup.getExternalId() !=null && !groupId.equals(updatedGroup.getExternalId())) {
				throw new NdexException ( "UUID does't match between URL and uploaded group object.");
			}
			if ( ! dao.isGroupAdmin(groupId, getLoggedInUser().getExternalId()))
				throw new NdexException ("Only group administrators can update a group." );
			
			Group group = dao.updateGroup(updatedGroup, groupId);
			try (GroupIndexManager m = new GroupIndexManager()) {
				m.updateGrp(id, group.getGroupName(), group.getDescription());
				dao.commit();
			}
			return ;
		
		} catch ( SolrServerException | IOException e) {
			throw new NdexException ("Failed to update group: " + e.getMessage(), e);
		} 
		
	}

	/**************************************************************************
	 * Changes a member's permissions to a group.
	 * 
	 * @param groupId
	 *            The group ID.
	 * @param groupMember
	 
	 **************************************************************************/
	@PUT
	@Path("/{groupid}/membership")
	
	public void updateMember(@PathParam("groupid") final String group_id,
			@QueryParam("userid") final String user_id,
			@QueryParam("type")  final Permissions permission
			) throws 
			ObjectNotFoundException, NdexException, SQLException {

		UUID groupId = UUID.fromString(group_id) ;
		UUID userId = UUID.fromString(user_id);
		if ( userId ==null)
			throw new NdexException("userid is required in URL.");
		if ( permission== null)
			throw new NdexException("pamameter 'type' is required in URL.");
		
		try (GroupDAO dao = new GroupDAO()) {
			
			if ( !dao.isGroupAdmin(groupId, getLoggedInUserId()))
				throw new NdexException("Only group admin can update membership.");
			
			//check for resource name? but it can be a network. Not really important, the code uses external id's
			dao.updateMember(groupId, userId, permission, this.getLoggedInUser().getExternalId());
			dao.commit();
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
	@Path("/{groupid}/membership")
	@Produces("application/json")
	
	public void removeGroupMember(@PathParam("groupid") final String groupIdStr,
			@QueryParam("userid") final String memberIdStr) throws IllegalArgumentException,
			ObjectNotFoundException, NdexException, SQLException {


		UUID memberId = UUID.fromString(memberIdStr);
		
		UUID groupId = UUID.fromString(groupIdStr);
		try (GroupDAO dao = new GroupDAO()){
			if ( !dao.isGroupAdmin(groupId, getLoggedInUserId())) {
			    if (  !memberId.equals(getLoggedInUserId())) {
					throw new UnauthorizedOperationException("Unable to delete group membership: user need to be an admin of this group or can only make himself leave this group.");
				}
			}
			dao.removeMember(memberId, groupId, 
					dao.isGroupAdmin(groupId, getLoggedInUserId()) ? this.getLoggedInUser().getExternalId(): null);
			dao.commit();
		} 
	}
	
	/**************************************************************************
	 * Retrieves array of network membership objects
	 * 
	 * @param groupId
	 *            The group ID.

	 **************************************************************************/
	
	@GET
	@PermitAll
	@Path("/{groupid}/permission")
	@Produces("application/json")
	public Map<String,String> getGroupNetworkPermissions(@PathParam("groupid") final String groupIdStr,
		    @QueryParam("networkid") String networkIdStr,
		    @QueryParam("permission") String permissions,
			@DefaultValue("0") @QueryParam("start") int skipBlocks,
			@DefaultValue("100") @QueryParam("size") int blockSize ) 
					throws NdexException, SQLException, IllegalArgumentException {

		
		UUID groupId = UUID.fromString(groupIdStr);
		
		
		if ( networkIdStr != null) {
			Map<String,String> result = new TreeMap<>();
			UUID networkId = UUID.fromString(networkIdStr);
			try (GroupDAO dao = new GroupDAO()) {
				if ( !dao.isInGroup(groupId,getLoggedInUserId()) )
					throw new NdexException ("Only a group member or admin can check group permission on a network");
				
				Permissions m = dao.getMembershipToNetwork(groupId, networkId);
				result.put(networkIdStr, m.toString());
				return result;
			} 
		}	
		
		boolean inclusive = true;
		Permissions permission = Permissions.READ;
		if ( permission !=null) {
			 permission = Permissions.valueOf(permissions.toUpperCase());
		}
		try (GroupDAO dao = new GroupDAO()){
	//		if ( !dao.isInGroup(groupId, getLoggedInUserId()))
	//			throw new NdexException("User is not a member of this group.");
			return dao.getGroupNetworkPermissions(groupId, permission, skipBlocks, blockSize, getLoggedInUserId(), inclusive);
			//logger.info("[end: Getting {} networks of group {}]", permissions, groupId);
			//return l;
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
	@Path("/{groupid}/membership")
	@Produces("application/json")
	@PermitAll
	public List<Membership> getGroupUserMemberships(@PathParam("groupid") final String groupIdStr,
			  @QueryParam("type") String permissions,
				@DefaultValue("0") @QueryParam("start") int skipBlocks,
				@DefaultValue("100") @QueryParam("size") int blockSize
			) throws NdexException, SQLException, JsonParseException, JsonMappingException, IllegalArgumentException, IOException {

//		logger.info("[start: Getting {} users in group {}]", permissions, groupIdStr);

		boolean inclusive = false;
		Permissions permission = null; //Permissions.MEMBER;
		if ( permissions != null) {
			permission = Permissions.valueOf(permissions.toUpperCase());
		}
		UUID groupId = UUID.fromString(groupIdStr);
		
		try (GroupDAO dao = new GroupDAO()){
	/*		if ( ! dao.isInGroup(groupId, getLoggedInUserId())) {
				throw new NdexException("User has to be a member of this group.");
			} */
			List<Membership> l = dao.getGroupUserMemberships(groupId, permission, skipBlocks, blockSize, inclusive);
	//		logger.info("[end:]");
			return l;
		} 
	}
	
	   @POST
	   @Path("/{groupid}/permissionrequest")
	   @Produces("text/plain")
	 
	    public Response createRequest(
	    		@PathParam("groupid") final String groupIdStr,
	    		final PermissionRequest newRequest) 
	    		throws IllegalArgumentException, DuplicateObjectException, NdexException, SQLException, JsonParseException, JsonMappingException, IOException {

			if ( newRequest.getNetworkid() == null)
					throw new NdexException("Networkid is required in the Posted object.");
			if ( newRequest.getPermission() == null)
				throw new NdexException("permission is required in the Posted object.");

			
			UUID groupId = UUID.fromString(groupIdStr);
			
			try (GroupDAO dao = new GroupDAO()) {			
				Group g = dao.getGroupById(groupId);
				if ( !dao.isGroupAdmin(g.getExternalId(), getLoggedInUserId()))
					 throw new NdexException("Only admin of specified group can make a network Access request for a group.");
			}

			try (RequestDAO dao = new RequestDAO ()){	
				
				Request r = new Request(RequestType.GroupNetworkAccess, newRequest);
				r.setSourceUUID(groupId);
				Request request = dao.createRequest(r, this.getLoggedInUser());
				dao.commit();
				
				URI l = new URI (Configuration.getInstance().getHostURI()  + 
			            Configuration.getInstance().getRestAPIPrefix()+"/group/"+ groupId.toString() + "/permissionrequest/"+
						request.getExternalId());

				return Response.created(l).entity(l).build();
			} catch (URISyntaxException e) {
				throw new NdexException ("Failed to create location URL: " + e.getMessage(), e);
			} 
	    	
	    }

	   
	    @GET
		@Path("/{groupid}/permissionrequest")
		@Produces("application/json")
	
		public List<Request> getPermissionRequests(@PathParam("groupid") final String groupIdStr,
				@QueryParam("networkid") final String networkIdStr,
				@QueryParam("permission") final String permissionStr) throws NdexException, SQLException, JsonParseException, JsonMappingException, IOException {
			
			UUID groupId = UUID.fromString(groupIdStr);
			
			try (GroupDAO dao = new GroupDAO()) {
				if ( !dao.isInGroup(groupId,getLoggedInUserId()) )
					throw new NdexException ("Only a group member or admin can check group permission on a network");
			}
			UUID networkId = null;
			if ( networkIdStr !=null)
				networkId = UUID.fromString(networkIdStr);
			
			Permissions permission = null;
			if ( permissionStr !=null)
				permission = Permissions.valueOf(permissionStr);
			
			
			try (RequestDAO dao = new RequestDAO()) {
				
				List<Request> m = dao.getGroupPermissionRequest(groupId, networkId, permission);
				return m;
			} 
		} 
	   
	
	   	@GET
		@Path("/{groupid}/permissionrequest/{requestid}")
		@Produces("application/json")
		public Request getPermissionRequestById(@PathParam("groupid") String groupIdStr,
				@PathParam("requestid") String requestIdStr) throws NdexException, SQLException, JsonParseException, JsonMappingException, IllegalArgumentException, IOException {

		//	logger.info("[start: Getting requests sent by user {}]", getLoggedInUser().getUserName());
			
			UUID groupId = UUID.fromString(groupIdStr);
			UUID requestId = UUID.fromString(requestIdStr);
			
			try ( GroupDAO dao = new GroupDAO()) {
				if (!dao.isInGroup(groupId, getLoggedInUserId()))
					throw new UnauthorizedOperationException("User is not a member of this group.");			
			}
			
			try (RequestDAO dao = new RequestDAO ()){
				Request reqs= dao.getRequest(requestId, getLoggedInUser());
		//		logger.info("[end: Returning request]");
				return reqs;
			}
		}
		
	
	
/*	@GET
	@PermitAll
	@Path("/{groupId}/membership/{networkId}")
	@Produces("application/json")
	@ApiDoc("For authenticated users, this function returns all the networks that the given group has direct access to and the authenticated user can see." + 
			"For anonymous users, this function returns all publice networks that the specified group bas direct access to."
			+ "")
	private Permissions getNetworkMembership(@PathParam("groupId") final String groupIdStr,
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
	}  */
	
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
