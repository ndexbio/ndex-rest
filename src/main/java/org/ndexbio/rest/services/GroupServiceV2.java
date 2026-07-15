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
import java.util.Map;

import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

import org.ndexbio.model.object.*;

import io.swagger.v3.oas.annotations.Operation;

/**
 * The NDEx group feature has been removed. Every endpoint on this resource returns
 * HTTP 501 (Not Implemented). The resource stays registered so clients receive a 501
 * (rather than a 404) for the retired group API.
 */
@Path("/v2/group")
public class GroupServiceV2 extends NdexService {

	private static final String GROUPS_REMOVED = "The NDEx group feature has been removed.";

	public GroupServiceV2(@Context HttpServletRequest httpRequest) {
		super(httpRequest);
	}

	@POST
	@Deprecated
	@Produces("text/plain")
	@Operation(summary = "Create Group", description = "Removed: the NDEx group feature is no longer supported (HTTP 501).", deprecated = true)
	public Response createGroup(final Group newGroup) {
		throw notImplemented(GROUPS_REMOVED);
	}

	@DELETE
	@Deprecated
	@Path("/{groupid}")
	@Operation(summary = "Delete Group", description = "Removed: the NDEx group feature is no longer supported (HTTP 501).", deprecated = true)
	@Produces("application/json")
	public void deleteGroup(@PathParam("groupid") final String groupId) {
		throw notImplemented(GROUPS_REMOVED);
	}

	@GET
	@Deprecated
	@PermitAll
	@Path("/{groupid}")
	@Operation(summary = "Get a Group", description = "Removed: the NDEx group feature is no longer supported (HTTP 501).", deprecated = true)
	@Produces("application/json")
	public Group getGroup(@PathParam("groupid") final String groupId) {
		throw notImplemented(GROUPS_REMOVED);
	}

	@PUT
	@Deprecated
	@Path("/{groupid}")
	@Operation(summary = "Update Group", description = "Removed: the NDEx group feature is no longer supported (HTTP 501).", deprecated = true)
	@Produces("application/json")
	public void updateGroup(final Group updatedGroup, @PathParam("groupid") final String id) {
		throw notImplemented(GROUPS_REMOVED);
	}

	@PUT
	@Deprecated
	@Path("/{groupid}/membership")
	@Operation(summary = "Add or Update a Group Member", description = "Removed: the NDEx group feature is no longer supported (HTTP 501).", deprecated = true)
	public void updateMember(@PathParam("groupid") final String group_id,
			@QueryParam("userid") final String user_id,
			@QueryParam("type") final Permissions permission) {
		throw notImplemented(GROUPS_REMOVED);
	}

	@DELETE
	@Deprecated
	@Path("/{groupid}/membership")
	@Operation(summary = "Remove a Group Member", description = "Removed: the NDEx group feature is no longer supported (HTTP 501).", deprecated = true)
	@Produces("application/json")
	public void removeGroupMember(@PathParam("groupid") final String groupIdStr,
			@QueryParam("userid") final String memberIdStr) {
		throw notImplemented(GROUPS_REMOVED);
	}

	@GET
	@Deprecated
	@PermitAll
	@Path("/{groupid}/permission")
	@Operation(summary = "Get Network Permissions of a Group", description = "Removed: the NDEx group feature is no longer supported (HTTP 501).", deprecated = true)
	@Produces("application/json")
	public Map<String, String> getGroupNetworkPermissions(@PathParam("groupid") final String groupIdStr,
			@QueryParam("networkid") String networkIdStr,
			@QueryParam("permission") String permissions,
			@DefaultValue("0") @QueryParam("start") int skipBlocks,
			@DefaultValue("100") @QueryParam("size") int blockSize) {
		throw notImplemented(GROUPS_REMOVED);
	}

	@GET
	@Deprecated
	@Path("/{groupid}/membership")
	@Operation(summary = "Get Members of a Group", description = "Removed: the NDEx group feature is no longer supported (HTTP 501).", deprecated = true)
	@Produces("application/json")
	@PermitAll
	public List<Membership> getGroupUserMemberships(@PathParam("groupid") final String groupIdStr,
			@QueryParam("type") String permissions,
			@DefaultValue("0") @QueryParam("start") int skipBlocks,
			@DefaultValue("100") @QueryParam("size") int blockSize) {
		throw notImplemented(GROUPS_REMOVED);
	}

	@POST
	@Path("/{groupid}/permissionrequest")
	@Operation(summary = "Create User Permission Request", description = "Removed: the NDEx group feature is no longer supported (HTTP 501).", deprecated = true)
	@Produces("text/plain")
	@Deprecated
	public Response createRequest(@PathParam("groupid") final String groupIdStr,
			final PermissionRequest newRequest) {
		throw notImplemented(GROUPS_REMOVED);
	}

	@GET
	@Path("/{groupid}/permissionrequest")
	@Operation(summary = "Get a User’s Permission Requests", description = "Removed: the NDEx group feature is no longer supported (HTTP 501).", deprecated = true)
	@Produces("application/json")
	@Deprecated
	public List<Request> getPermissionRequests(@PathParam("groupid") final String groupIdStr,
			@QueryParam("networkid") final String networkIdStr,
			@QueryParam("permission") final String permissionStr) {
		throw notImplemented(GROUPS_REMOVED);
	}

	@GET
	@Path("/{groupid}/permissionrequest/{requestid}")
	@Operation(summary = "Get a User's Permission Requests by id", description = "Removed: the NDEx group feature is no longer supported (HTTP 501).", deprecated = true)
	@Produces("application/json")
	@Deprecated
	public Request getPermissionRequestById(@PathParam("groupid") String groupIdStr,
			@PathParam("requestid") String requestIdStr) {
		throw notImplemented(GROUPS_REMOVED);
	}

}
