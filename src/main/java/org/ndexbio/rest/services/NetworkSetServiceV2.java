package org.ndexbio.rest.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

import org.ndexbio.common.models.dao.postgresql.NetworkSetDAO;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.NetworkSet;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/**
 * The NDEx network set feature has been removed. Every endpoint on this resource now returns
 * HTTP 501 (Not Implemented). The resource stays registered so clients get a 501 rather than a 404.
 * Use folders + shortcuts + folder access keys instead (see the V3 Migration Guide).
 */
@Path("/v2/networkset")
@Deprecated
public class NetworkSetServiceV2 extends NdexService {

	private static final String NETWORKSETS_REMOVED = "The NDEx network set feature has been removed.";
	private static final String REMOVED_DESC =
			"Removed: the NDEx network set feature is no longer supported. This endpoint always returns HTTP 501 Not Implemented.";
	private static final String ARCHIVED_DESC =
			"Read-only access to archived, historical network-set data from the frozen network_set tables. "
			+ "The network set feature is retired: no new network sets can be created and this data is not "
			+ "backed by the v3 folder model. Provided only for backward-compatible reads of legacy network sets.";

	public NetworkSetServiceV2(@Context HttpServletRequest httpRequest) {
		super(httpRequest);
	}

	@POST
	@Deprecated
	@Operation(summary = "Create a Network Set (REMOVED)", description = REMOVED_DESC, deprecated = true)
	@ApiResponse(responseCode = "501", description = "Not Implemented — the network set feature has been removed")
	@Produces("text/plain")
	public Response createNetworkSet(final NetworkSet newNetworkSet) {
		throw notImplemented(NETWORKSETS_REMOVED);
	}

	@PUT
	@Path("/{networksetid}")
	@Deprecated
	@Operation(summary = "Update a Network Set (REMOVED)", description = REMOVED_DESC, deprecated = true)
	@ApiResponse(responseCode = "501", description = "Not Implemented — the network set feature has been removed")
	public void updateNetworkSet(final NetworkSet newNetworkSet, @PathParam("networksetid") final String id) {
		throw notImplemented(NETWORKSETS_REMOVED);
	}

	@DELETE
	@Path("/{networksetid}")
	@Deprecated
	@Operation(summary = "Delete a Network Set (REMOVED)", description = REMOVED_DESC, deprecated = true)
	@ApiResponse(responseCode = "501", description = "Not Implemented — the network set feature has been removed")
	@Produces("application/json")
	public void deleteNetworkSet(@PathParam("networksetid") final String networkSetIdStr) {
		throw notImplemented(NETWORKSETS_REMOVED);
	}

	@GET
	@PermitAll
	@Path("/{networksetid}")
	@Deprecated
	@Operation(summary = "Get a Network Set (ARCHIVED)", description = ARCHIVED_DESC, deprecated = true)
	@Produces("application/json")
	public NetworkSet getNetworkSet(@PathParam("networksetid") final String networkSetIdStr,
			@QueryParam("accesskey") String accessKey) throws Exception {
		UUID setId = UUID.fromString(networkSetIdStr);
		// Serve only the archived network_set / network_set_member data; no v3 folder polyfill.
		try (NetworkSetDAO dao = new NetworkSetDAO()) {
			return dao.getNetworkSet(setId, getLoggedInUserId(), accessKey);
		}
	}

	@POST
	@Path("/{networksetid}/members")
	@Deprecated
	@Operation(summary = "Add networks to Network Set (REMOVED)", description = REMOVED_DESC, deprecated = true)
	@ApiResponse(responseCode = "501", description = "Not Implemented — the network set feature has been removed")
	@Produces("text/plain")
	public Response addNetworksToSet(final List<UUID> networkIds,
			@PathParam("networksetid") final String networkSetIdStr) {
		throw notImplemented(NETWORKSETS_REMOVED);
	}

	@DELETE
	@Path("/{networksetid}/members")
	@Deprecated
	@Operation(summary = "Delete networks from Network Set (REMOVED)", description = REMOVED_DESC, deprecated = true)
	@ApiResponse(responseCode = "501", description = "Not Implemented — the network set feature has been removed")
	@Produces("application/json")
	public void deleteNetworkSet(final List<UUID> networkIds,
			@PathParam("networksetid") final String networkSetIdStr) {
		throw notImplemented(NETWORKSETS_REMOVED);
	}

	@GET
	@Path("/{networksetid}/accesskey")
	@Deprecated
	@Operation(summary = "Get Access key of Network Set (ARCHIVED)", description = ARCHIVED_DESC, deprecated = true)
	@Produces("application/json")
	public Map<String, String> getNetworkSetAccessKey(@PathParam("networksetid") final String networkSetIdStr) throws Exception {
		UUID networkSetId = UUID.fromString(networkSetIdStr);
		// Archived network_set data only: the access key of a legacy network set the caller owns.
		try (NetworkSetDAO dao = new NetworkSetDAO()) {
			if (dao.isNetworkSetOwner(networkSetId, getLoggedInUserId())) {
				String key = dao.getNetworkSetAccessKey(networkSetId);
				if (key == null || key.isEmpty())
					return null;
				Map<String, String> result = new HashMap<>(1);
				result.put("accessKey", key);
				return result;
			}
		}
		throw new UnauthorizedOperationException("User is not the owner of this network set.");
	}

	@PUT
	@Path("/{networksetid}/accesskey")
	@Deprecated
	@Operation(summary = "Disable/Enable Access Key on Network Set (REMOVED)", description = REMOVED_DESC, deprecated = true)
	@ApiResponse(responseCode = "501", description = "Not Implemented — the network set feature has been removed")
	@Produces("application/json")
	public Map<String, String> disableNetworkAccessKey(@PathParam("networksetid") final String networkSetIdStr,
			@QueryParam("action") String action) {
		throw notImplemented(NETWORKSETS_REMOVED);
	}

	@PUT
	@Path("/{networksetid}/systemproperty")
	@Deprecated
	@Operation(summary = "Update Network Set System Properties (REMOVED)", description = REMOVED_DESC, deprecated = true)
	@ApiResponse(responseCode = "501", description = "Not Implemented — the network set feature has been removed")
	@Produces("application/json")
	public void setNetworkFlag(@PathParam("networksetid") final String networkSetIdStr,
			final Map<String, Object> parameters) {
		throw notImplemented(NETWORKSETS_REMOVED);
	}
}
