package org.ndexbio.rest.services;

import java.net.URI;
import java.net.URISyntaxException;
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

import org.ndexbio.common.models.dao.DeletedFileIds;
import org.ndexbio.common.models.dao.NetworkDAO;
import org.ndexbio.common.networkset.NetworkSetFolderService;
import org.ndexbio.common.networkset.NetworkSetFolderService.RemovedMembers;
import org.ndexbio.common.networkset.NetworkSetFolderService.UpsertOutcome;
import org.ndexbio.common.networkset.NetworkSetFolderServiceImpl;
import org.ndexbio.common.util.NdexUUIDFactory;
import org.ndexbio.model.exceptions.BadRequestException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.NetworkSet;
import org.ndexbio.model.object.User;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.Configuration;
import org.ndexbio.task.NdexServerQueue;
import org.ndexbio.task.SolrTaskDeleteFiles;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/**
 * Legacy network set API, kept working on top of the v3 folder model.
 *
 * <p>A network set id <em>is</em> a folder id: the v3 migration converted every set into a folder with
 * the same UUID, holding a shortcut per member network. Every endpoint here performs folder and
 * shortcut operations internally and maps the result back onto the legacy {@link NetworkSet}
 * representation, so v2 clients keep working unchanged. The frozen {@code network_set} tables are
 * never read or written.
 *
 * @see NetworkSetFolderService
 */
@Path("/v2/networkset")
@Deprecated
public class NetworkSetServiceV2 extends NdexService {

	/**
	 * Preamble shared by every endpoint. A caller reading the spec has to be able to tell this is a
	 * compatibility shim over folders, so the divergences below read as consequences rather than bugs.
	 */
	private static final String FOLDER_BACKED_DESC =
			"DEPRECATED compatibility endpoint. The NDEx network set feature is retired: a network set id "
			+ "is a **folder** id. This endpoint operates on v3 folders and shortcuts internally and maps "
			+ "the result back onto the legacy NetworkSet representation, so existing v2 clients keep "
			+ "working unchanged. New development should use the v3 folder endpoints (/v3/files/folders, "
			+ "/v3/files/shortcuts, /v3/files/sharing).";

	/** Legacy fields with no folder equivalent, appended where they apply. */
	private static final String NETWORKSET_LIMITS_DESC =
			" Legacy fields without a folder equivalent are not stored or returned: 'showcased' is always "
			+ "false, 'doi' is always absent, and 'properties' is always empty.";

	public NetworkSetServiceV2(@Context HttpServletRequest httpRequest) {
		super(httpRequest);
	}

	private NetworkSetFolderService networkSetService() {
		return new NetworkSetFolderServiceImpl(Configuration.getInstance().getDAOFactory());
	}

	@POST
	@Deprecated
	@Operation(summary = "Create a Network Set (DEPRECATED)",
			description = FOLDER_BACKED_DESC
			+ " Creates a **folder** at the caller's home root (parent = null). The id in the returned "
			+ "Location header is a valid folder id: GET /v3/files/folders/{id} resolves it. The "
			+ "'properties' field of the posted object is ignored — folders have no properties store."
			+ NETWORKSET_LIMITS_DESC,
			deprecated = true)
	@ApiResponse(responseCode = "201", description = "The network set was created; Location holds its URI")
	@ApiResponse(responseCode = "400", description = "A network set name is required")
	@Produces("text/plain")
	public Response createNetworkSet(final NetworkSet newNetworkSet) throws Exception {

		if (newNetworkSet == null || newNetworkSet.getName() == null || newNetworkSet.getName().length() == 0)
			throw new BadRequestException("Network set name is required.");

		UUID setId = NdexUUIDFactory.INSTANCE.createNewNDExUUID();
		networkSetService().createSet(setId, getLoggedInUserId(), newNetworkSet.getName(),
				newNetworkSet.getDescription());
		// createFolder always inserts PRIVATE, so index into the private core.
		createFileIndex(setId, getLoggedInUser(), VisibilityType.PRIVATE, FileType.FOLDER, true);

		try {
			URI l = new URI(Configuration.getInstance().getHostURI()
					+ Configuration.getInstance().getRestAPIPrefix() + "/networkset/" + setId.toString());
			return Response.created(l).entity(l).build();
		} catch (URISyntaxException e) {
			throw new NdexException("Server Error, can't create URL for the new resource: " + e.getMessage(), e);
		}
	}

	@PUT
	@Path("/{networksetid}")
	@Deprecated
	@Operation(summary = "Update a Network Set (DEPRECATED)",
			description = FOLDER_BACKED_DESC
			+ " Updates the **folder**'s name and description, preserving its current parent. Omitting "
			+ "'description' (or sending null) leaves the existing value unchanged — note the legacy "
			+ "endpoint cleared it in that case; send an empty string to clear it. If no set exists at "
			+ "this id, one is created at it, preserving the legacy upsert behavior."
			+ NETWORKSET_LIMITS_DESC,
			deprecated = true)
	@ApiResponse(responseCode = "204", description = "The network set was updated, or created at this id")
	@ApiResponse(responseCode = "400", description = "A network set name is required")
	@ApiResponse(responseCode = "401", description = "Unauthorized — the network set exists and belongs to another user")
	@ApiResponse(responseCode = "404", description = "The network set is in the trash")
	public void updateNetworkSet(final NetworkSet newNetworkSet, @PathParam("networksetid") final String id)
			throws Exception {

		if (newNetworkSet == null || newNetworkSet.getName() == null || newNetworkSet.getName().length() == 0)
			throw new BadRequestException("Network set name is required.");

		UUID setId = UUID.fromString(id);

		// upsertSet resolves the id itself: it updates when the caller owns a live set, creates when the id
		// is unused, and refuses the two states that already occupy the id — another user's live set (401)
		// and a trashed set (404). Branching on ownership here instead would send both of those into a
		// create and fail on the primary key as a 500.
		UpsertOutcome outcome = networkSetService().upsertSet(setId, getLoggedInUserId(),
				newNetworkSet.getName(), newNetworkSet.getDescription());

		// On an update, createOnly=false drops the stale doc from both cores first, or the old name keeps
		// matching in search. On a create there is nothing to drop.
		createFileIndex(setId, getLoggedInUser(), outcome.visibility(), FileType.FOLDER, outcome.created());
	}

	@DELETE
	@Path("/{networksetid}")
	@Deprecated
	@Operation(summary = "Delete a Network Set (DEPRECATED)",
			description = FOLDER_BACKED_DESC
			+ " Soft-deletes the **folder and everything in it** — subfolders, shortcuts, and any networks "
			+ "parented inside it — into the trash, recoverable with POST /v3/files/trash/restore. This is "
			+ "not merely the removal of a set header.",
			deprecated = true)
	@ApiResponse(responseCode = "204", description = "The network set was deleted")
	@ApiResponse(responseCode = "401", description = "Unauthorized — the caller is not the owner of this network set")
	@Produces("application/json")
	public void deleteNetworkSet(@PathParam("networksetid") final String networkSetIdStr) throws Exception {

		UUID setId = UUID.fromString(networkSetIdStr);
		NetworkSetFolderService service = networkSetService();

		if (!service.isSetOwner(setId, getLoggedInUserId()))
			throw new UnauthorizedOperationException("Signed in user is not the owner of this network set.");

		// The delete cascades over the subtree, so clear the Solr docs of everything it touched, not just
		// the folder itself.
		DeletedFileIds deleted = service.deleteSet(setId);
		if (!deleted.isEmpty()) {
			NdexServerQueue.INSTANCE.addSystemTask(new SolrTaskDeleteFiles(setId, deleted));
		}
	}

	@GET
	@PermitAll
	@Path("/{networksetid}")
	@Deprecated
	@Operation(summary = "Get a Network Set (DEPRECATED)",
			description = FOLDER_BACKED_DESC
			+ " Reads the **folder**. The 'networks' array is the union of the targets of its network "
			+ "shortcuts and any networks parented directly in it. Members are limited to the networks the "
			+ "caller may read; a valid access key instead returns the members that key unlocks. A "
			+ "soft-deleted (trashed) set returns 404 even when a valid access key is supplied. "
			+ "**Behavior change:** because a set is a folder, the folder's visibility governs this read. "
			+ "Sets are PRIVATE by default (including every set the v3 migration converted), so an "
			+ "anonymous or non-permitted caller gets 401 where the legacy endpoint returned the set "
			+ "header to everyone — the old network_set table had no visibility column. Supply an access "
			+ "key, or make the folder PUBLIC via PUT /v3/files/folders/{folderid}, to allow anonymous "
			+ "reads." + NETWORKSET_LIMITS_DESC,
			deprecated = true)
	@ApiResponse(responseCode = "200", description = "The network set")
	@ApiResponse(responseCode = "401", description = "Unauthorized — the caller may not read this network set")
	@ApiResponse(responseCode = "404", description = "No such network set")
	@Produces("application/json")
	public NetworkSet getNetworkSet(@PathParam("networksetid") final String networkSetIdStr,
			@QueryParam("accesskey") String accessKey) throws Exception {
		UUID setId = UUID.fromString(networkSetIdStr);
		return networkSetService().getSet(setId, getLoggedInUserId(), accessKey);
	}

	@POST
	@Path("/{networksetid}/members")
	@Deprecated
	@Operation(summary = "Add networks to Network Set (DEPRECATED)",
			description = FOLDER_BACKED_DESC
			+ " Adds each posted network to the **folder** as a **shortcut**; the networks themselves are "
			+ "not moved. Every posted id must be readable by the caller — if any one fails, the whole "
			+ "request is rejected and no shortcut is created. Ids already in the set are ignored rather "
			+ "than duplicated.",
			deprecated = true)
	@ApiResponse(responseCode = "201", description = "The networks were added to the set")
	@ApiResponse(responseCode = "400", description = "A posted network does not exist or is not readable by the caller")
	@ApiResponse(responseCode = "401", description = "Unauthorized — the caller is not the owner of this network set")
	@Produces("text/plain")
	public Response addNetworksToSet(final List<UUID> networkIds,
			@PathParam("networksetid") final String networkSetIdStr) throws Exception {

		UUID setId = UUID.fromString(networkSetIdStr);
		NetworkSetFolderService service = networkSetService();

		if (!service.isSetOwner(setId, getLoggedInUserId()))
			throw new UnauthorizedOperationException("Signed in user is not the owner of this network set.");

		for (UUID shortcutId : service.addMembers(setId, getLoggedInUserId(), networkIds)) {
			// createShortcut always inserts PRIVATE.
			createFileIndex(shortcutId, getLoggedInUser(), VisibilityType.PRIVATE, FileType.SHORTCUT, true);
		}

		try {
			URI l = new URI(Configuration.getInstance().getHostURI()
					+ Configuration.getInstance().getRestAPIPrefix() + "/networkset/" + setId.toString() + "/members");
			return Response.created(l).entity(l).build();
		} catch (URISyntaxException e) {
			throw new NdexException("Server Error, can't create URL for the new resource: " + e.getMessage(), e);
		}
	}

	@DELETE
	@Path("/{networksetid}/members")
	@Deprecated
	@Operation(summary = "Delete networks from Network Set (DEPRECATED)",
			description = FOLDER_BACKED_DESC
			+ " For a network referenced by a **shortcut**, the shortcut is **permanently deleted** (it does "
			+ "not go to the trash) and the network itself is untouched. For a network **parented directly "
			+ "in the folder**, the network is **moved to the caller's home root** rather than deleted; one "
			+ "the caller does not own is left in place.",
			deprecated = true)
	@ApiResponse(responseCode = "204", description = "The networks were removed from the set")
	@ApiResponse(responseCode = "401", description = "Unauthorized — the caller is not the owner of this network set")
	@Produces("application/json")
	public void deleteNetworkSet(final List<UUID> networkIds,
			@PathParam("networksetid") final String networkSetIdStr) throws Exception {

		UUID setId = UUID.fromString(networkSetIdStr);
		NetworkSetFolderService service = networkSetService();

		if (!service.isSetOwner(setId, getLoggedInUserId()))
			throw new UnauthorizedOperationException("Signed in user is not the owner of this network set.");

		RemovedMembers removed = service.removeMembers(setId, getLoggedInUserId(), networkIds);

		// Deleted shortcuts must lose their index docs. One batch task covers them all, and it clears
		// both cores per id, which also spares us looking up a visibility whose row is already gone.
		// Neither list is reported to the client — this endpoint stays a 204.
		if (!removed.deletedShortcutIds().isEmpty()) {
			NdexServerQueue.INSTANCE.addSystemTask(new SolrTaskDeleteFiles(setId,
					new DeletedFileIds(List.of(), List.of(), removed.deletedShortcutIds())));
		}

		// A reparented network needs its own document rebuilt: network docs now carry parentUuid, and
		// search decides visibility from it, so leaving it stale would keep the network findable under
		// the set it was just removed from. The rows are already committed, so the task reads the new
		// parent (null — removal moves the network to home root).
		if (!removed.movedNetworkIds().isEmpty()) {
			User user = getLoggedInUser();
			try (NetworkDAO networkDao = Configuration.getInstance().getDAOFactory().getNetworkDAO()) {
				for (UUID networkId : removed.movedNetworkIds()) {
					createFileIndex(networkId, user, networkDao.getNetworkVisibility(networkId),
							FileType.NETWORK, false);
				}
			}
		}
	}

	@GET
	@Path("/{networksetid}/accesskey")
	@Deprecated
	@Operation(summary = "Get Access key of Network Set (DEPRECATED)",
			description = FOLDER_BACKED_DESC
			+ " Returns the **folder**'s access key — the same key surfaced by "
			+ "GET /v3/files/folders/{folderid}/accesskey. This is strictly a read: it never creates or "
			+ "enables a key as a side effect — use PUT on this path to enable one. Owner only; a missing "
			+ "set is reported as 404 before ownership is considered.",
			deprecated = true)
	@ApiResponse(responseCode = "200", description = "The access key of the network set")
	@ApiResponse(responseCode = "401", description = "Unauthorized — the caller is not the owner of this network set")
	@ApiResponse(responseCode = "404", description = "No such network set")
	@Produces("application/json")
	public Map<String, String> getNetworkSetAccessKey(@PathParam("networksetid") final String networkSetIdStr)
			throws Exception {
		UUID networkSetId = UUID.fromString(networkSetIdStr);
		NetworkSetFolderService service = networkSetService();

		// Resolve existence first so a missing set surfaces as 404 rather than 401: getFolderAccessKey
		// throws ObjectNotFoundException when there is no live row.
		String key = service.getAccessKey(networkSetId);
		if (!service.isSetOwner(networkSetId, getLoggedInUserId()))
			throw new UnauthorizedOperationException("User is not the owner of this network set.");
		if (key == null || key.isEmpty())
			return null;
		Map<String, String> result = new HashMap<>(1);
		result.put("accessKey", key);
		return result;
	}

	@PUT
	@Path("/{networksetid}/accesskey")
	@Deprecated
	@Operation(summary = "Disable/Enable Access Key on Network Set (DEPRECATED)",
			description = FOLDER_BACKED_DESC
			+ " Enables or disables the **folder**'s access key — the same key managed by "
			+ "POST /v3/files/sharing/share and /unshare. Returns the key when enabling, and HTTP 204 when "
			+ "disabling. Enabling is idempotent, and disabling preserves the key value, so re-enabling "
			+ "returns the same string. Note the key reaches only those member networks owned by the set's "
			+ "owner: a member owned by another user is not unlocked by it.",
			deprecated = true)
	@ApiResponse(responseCode = "200", description = "The access key, when enabling")
	@ApiResponse(responseCode = "204", description = "The access key was disabled")
	@ApiResponse(responseCode = "400", description = "The 'action' parameter must be 'enable' or 'disable'")
	@ApiResponse(responseCode = "401", description = "Unauthorized — the caller is not the owner of this network set")
	@Produces("application/json")
	public Map<String, String> disableNetworkAccessKey(@PathParam("networksetid") final String networkSetIdStr,
			@QueryParam("action") String action) throws Exception {

		UUID networkSetId = UUID.fromString(networkSetIdStr);
		// BadRequestException, not NdexException: a bad parameter is a client error and must map to 400.
		// A plain NdexException goes through NdexExceptionMapper and surfaces as a 500. Also guards the
		// null case, which used to NPE before reaching the check.
		if (action == null || (!action.equalsIgnoreCase("disable") && !action.equalsIgnoreCase("enable")))
			throw new BadRequestException("Value of 'action' parameter can only be 'disable' or 'enable'");

		NetworkSetFolderService service = networkSetService();
		if (!service.isSetOwner(networkSetId, getLoggedInUserId()))
			throw new UnauthorizedOperationException("User is not the owner of this network set.");

		if (action.equalsIgnoreCase("disable")) {
			service.disableAccessKey(networkSetId, getLoggedInUserId());
			return null;
		}

		String key = service.enableAccessKey(networkSetId, getLoggedInUserId());
		if (key == null || key.isEmpty())
			return null;
		Map<String, String> result = new HashMap<>(1);
		result.put("accessKey", key);
		return result;
	}

	@PUT
	@Path("/{networksetid}/systemproperty")
	@Deprecated
	@Operation(summary = "Update Network Set System Properties (DEPRECATED)",
			description = FOLDER_BACKED_DESC
			+ " Accepts 'showcase' and **does nothing with it**: folders have no showcase flag, so there is "
			+ "nowhere to store it. The call succeeds for compatibility and the value is not readable back. "
			+ "The v3 surface for the account page is GET /v3/users/{userid}/home; use visibility to control "
			+ "who may read a folder.",
			deprecated = true)
	@ApiResponse(responseCode = "204", description = "Accepted; the showcase flag is a documented no-op")
	@ApiResponse(responseCode = "401", description = "Unauthorized — the caller is not the owner of this network set")
	@Produces("application/json")
	public void setNetworkFlag(@PathParam("networksetid") final String networkSetIdStr,
			final Map<String, Object> parameters) throws Exception {

		UUID networkSetId = UUID.fromString(networkSetIdStr);
		if (!networkSetService().isSetOwner(networkSetId, getLoggedInUserId()))
			throw new UnauthorizedOperationException("User is not the owner of this network set.");

		// 'showcase' is intentionally ignored — see the endpoint description. Kept as a successful no-op
		// so legacy clients that set it on every save keep working.
	}
}
