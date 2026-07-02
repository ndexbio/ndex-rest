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

import java.util.List;

import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;

import org.ndexbio.model.object.Group;
import org.ndexbio.model.object.Membership;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.SimpleQuery;
import org.ndexbio.model.object.SolrSearchResult;

import io.swagger.v3.oas.annotations.Operation;

/**
 * The NDEx group feature has been removed. Every endpoint on this (v1) resource returns
 * HTTP 501 (Not Implemented). The resource stays registered so clients receive a 501
 * (rather than a 404) for the retired group API.
 */
@Path("/group")
public class GroupService extends NdexService {

	private static final String GROUPS_REMOVED = "The NDEx group feature has been removed.";

	public GroupService(@Context HttpServletRequest httpRequest) {
		super(httpRequest);
	}

	private static final String GROUPS_REMOVED_DESC = "Removed: the NDEx group feature is no longer supported (HTTP 501).";

	@POST
	@Deprecated
	@Produces("application/json")
	@Operation(summary = "Create Group", description = GROUPS_REMOVED_DESC, deprecated = true)
	public Group createGroup(final Group newGroup) {
		throw notImplemented(GROUPS_REMOVED);
	}

	@DELETE
	@Deprecated
	@Path("/{groupId}")
	@Produces("application/json")
	@Operation(summary = "Delete Group", description = GROUPS_REMOVED_DESC, deprecated = true)
	public void deleteGroup(@PathParam("groupId") final String groupId) {
		throw notImplemented(GROUPS_REMOVED);
	}

	@POST
	@Deprecated
	@PermitAll
	@Path("/search/{start}/{size}")
	@Produces("application/json")
	@Operation(summary = "Search Groups", description = GROUPS_REMOVED_DESC, deprecated = true)
	public SolrSearchResult<Group> findGroups(SimpleQuery simpleQuery,
			@PathParam("start") final int skip,
			@PathParam("size") final int top) {
		throw notImplemented(GROUPS_REMOVED);
	}

	@GET
	@Deprecated
	@PermitAll
	@Path("/{groupid}")
	@Produces("application/json")
	@Operation(summary = "Get a Group", description = GROUPS_REMOVED_DESC, deprecated = true)
	public Group getGroup(@PathParam("groupid") final String groupId) {
		throw notImplemented(GROUPS_REMOVED);
	}

	@POST
	@Deprecated
	@PermitAll
	@Path("/groups")
	@Produces("application/json")
	@Operation(summary = "Get Groups by UUIDs", description = GROUPS_REMOVED_DESC, deprecated = true)
	public List<Group> getGroupsByUUIDs(List<String> groupIdStrs) {
		throw notImplemented(GROUPS_REMOVED);
	}

	@POST
	@Deprecated
	@Path("/{groupid}")
	@Produces("application/json")
	@Operation(summary = "Update Group", description = GROUPS_REMOVED_DESC, deprecated = true)
	public Group updateGroup(final Group updatedGroup, @PathParam("groupid") final String id) {
		throw notImplemented(GROUPS_REMOVED);
	}

	@POST
	@Deprecated
	@Path("/{groupid}/member/{userid}")
	@Operation(summary = "Add or Update a Group Member", description = GROUPS_REMOVED_DESC, deprecated = true)
	public void updateMember(@PathParam("groupid") final String group_id,
			@PathParam("userid") final String user_id,
			final Permissions permission) {
		throw notImplemented(GROUPS_REMOVED);
	}

	@DELETE
	@Deprecated
	@Path("/{groupid}/member/{memberid}")
	@Produces("application/json")
	@Operation(summary = "Remove a Group Member", description = GROUPS_REMOVED_DESC, deprecated = true)
	public void removeUserMember(@PathParam("groupid") final String groupIdStr,
			@PathParam("memberid") final String memberId) {
		throw notImplemented(GROUPS_REMOVED);
	}

	@GET
	@Deprecated
	@PermitAll
	@Path("/{groupid}/network/{permission}/{start}/{size}")
	@Produces("application/json")
	@Operation(summary = "Get Network Memberships of a Group", description = GROUPS_REMOVED_DESC, deprecated = true)
	public List<Membership> getGroupNetworkMemberships(@PathParam("groupid") final String groupIdStr,
			@PathParam("permission") final String permissions,
			@PathParam("start") int skipBlocks,
			@PathParam("size") int blockSize,
			@DefaultValue("false") @QueryParam("inclusive") boolean inclusive) {
		throw notImplemented(GROUPS_REMOVED);
	}

	@GET
	@Deprecated
	@Path("/{groupId}/user/{permission}/{skipBlocks}/{blockSize}")
	@Produces("application/json")
	@Operation(summary = "Get Members of a Group", description = GROUPS_REMOVED_DESC, deprecated = true)
	public List<Membership> getGroupUserMemberships(@PathParam("groupId") final String groupIdStr,
			@PathParam("permission") final String permissions,
			@PathParam("skipBlocks") int skipBlocks,
			@PathParam("blockSize") int blockSize,
			@DefaultValue("false") @QueryParam("inclusive") boolean inclusive) {
		throw notImplemented(GROUPS_REMOVED);
	}

	@GET
	@Deprecated
	@PermitAll
	@Path("/{groupId}/membership/{networkId}")
	@Produces("application/json")
	@Operation(summary = "Get a Group's Permission on a Network", description = GROUPS_REMOVED_DESC, deprecated = true)
	public Permissions getNetworkMembership(@PathParam("groupId") final String groupIdStr,
			@PathParam("networkId") final String networkId) {
		throw notImplemented(GROUPS_REMOVED);
	}

}
