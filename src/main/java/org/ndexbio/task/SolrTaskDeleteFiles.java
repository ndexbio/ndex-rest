package org.ndexbio.task;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.ndexbio.common.models.dao.DeletedFileIds;
import org.ndexbio.common.solr.FolderIndexManager;
import org.ndexbio.common.solr.SingleNetworkSolrIdxManager;
import org.ndexbio.common.solr.SolrObjectFactory;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.TaskType;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.Configuration;

/**
 * Clears the Solr state of every file a cascading folder delete affected.
 *
 * <p>{@code FolderDAO.deleteFolder(id, force=true, …)} removes or trashes the whole subtree at any
 * depth, but the index docs live outside that transaction. Deleting only the named folder's doc — as
 * {@link SolrTaskDeleteFile} does for a single file — leaves every descendant folder, network and
 * shortcut behind as a permanent phantom hit in {@code POST /v3/search/files}, because the ids are
 * UUIDs and nothing will ever re-index over them.
 *
 * <p>Two properties make this a flat loop over existing primitives rather than a per-type dispatch:
 *
 * <ul>
 * <li>Folder, shortcut <em>and</em> network docs all live in the same two cores — {@code GlobalNetworkIndexManager}
 *     extends {@code NFSIndexManager} just like the folder and shortcut managers — and
 *     {@code delete(uuid, visibility)} is a type-agnostic {@code deleteById}. One manager clears any of them.</li>
 * <li>Each doc's visibility is no longer readable once its row is deleted, so rather than looking it up
 *     the id is deleted from <em>both</em> cores; a {@code deleteById} against the core the doc was not
 *     in is a harmless no-op. This mirrors {@code SolrTaskRebuildFileIdx.rebuildFolderIndex}.</li>
 * </ul>
 *
 * <p>One queue entry covers the whole subtree, since {@code NdexServerQueue.addSystemTask} persists a
 * task row per entry.
 */
public class SolrTaskDeleteFiles extends NdexSystemTask {

	/**
	 * Attribute discriminating a persisted batch delete from a single-network delete. Both use
	 * {@link TaskType#SYS_SOLR_DELETE_NETWORK} — {@code TaskType} lives in {@code ndex-object-model},
	 * so adding a value would force a dependency bump for what is only a bookkeeping label. Rows
	 * written before this task existed lack the attribute and keep taking the old reconstruction path.
	 */
	public static final String folderIdsAttr = "deletedFolderIds";
	public static final String networkIdsAttr = "deletedNetworkIds";
	public static final String shortcutIdsAttr = "deletedShortcutIds";

	private static final TaskType taskType = TaskType.SYS_SOLR_DELETE_NETWORK;

	/** The file the user actually acted on — the single value {@code Task.setResource} can hold. */
	private final UUID rootFileId;
	private final DeletedFileIds ids;

	public SolrTaskDeleteFiles(UUID rootFileId, DeletedFileIds ids) {
		super();
		this.rootFileId = rootFileId;
		this.ids = ids;
	}

	@Override
	public void run() throws Exception {
		if (ids.isEmpty()) {
			return;
		}

		SolrObjectFactory solrObjectFactory = Configuration.getInstance().getSolrObjectFactory();

		try (FolderIndexManager globalIdx = solrObjectFactory.getFolderIndexManager()) {
			for (UUID id : ids.all()) {
				String idStr = id.toString();
				globalIdx.delete(idStr, VisibilityType.PRIVATE);
				globalIdx.delete(idStr, VisibilityType.PUBLIC);
			}
			// Commit both cores explicitly rather than waiting on Solr autoCommit, so a search
			// immediately after the delete does not still see the removed docs.
			globalIdx.commit(VisibilityType.PRIVATE);
			globalIdx.commit(VisibilityType.PUBLIC);
		}

		// A network may additionally own a per-network node query index. That core is keyed by name,
		// not visibility, so it is dropped exactly once. dropCore() already tolerates a core that was
		// never created, which is the common case: only networks above
		// SingleNetworkSolrIdxManager.AUTOCREATE_THRESHHOLD get one.
		for (UUID networkId : ids.networks()) {
			try (SingleNetworkSolrIdxManager idxManager =
					solrObjectFactory.getSingleNetworkSolrIdxManager(networkId.toString())) {
				idxManager.dropIndex();
			}
		}
	}

	@Override
	public Task createTask() {
		Task t = super.createTask();
		t.setResource(rootFileId.toString());
		t.setAttribute(folderIdsAttr, toStrings(ids.folders()));
		t.setAttribute(networkIdsAttr, toStrings(ids.networks()));
		t.setAttribute(shortcutIdsAttr, toStrings(ids.shortcuts()));
		return t;
	}

	@Override
	public TaskType getTaskType() {
		return taskType;
	}

	private static List<String> toStrings(Collection<UUID> ids) {
		List<String> result = new ArrayList<>(ids.size());
		for (UUID id : ids) {
			result.add(id.toString());
		}
		return result;
	}

	/**
	 * Rebuilds the id list from a persisted task attribute. Attributes round-trip through jsonb as a
	 * generic {@code Map<String,Object>}, so a JSON array comes back as a {@code List} of strings.
	 */
	static List<UUID> idsFromAttribute(Object attribute) {
		List<UUID> result = new ArrayList<>();
		if (attribute instanceof Collection<?> values) {
			for (Object value : values) {
				if (value != null) {
					result.add(UUID.fromString(value.toString()));
				}
			}
		}
		return result;
	}

	/** Reconstructs a batch delete from its persisted row, for the startup queue replay. */
	static SolrTaskDeleteFiles fromTask(Task t) {
		DeletedFileIds ids = new DeletedFileIds(
				idsFromAttribute(t.getAttribute(folderIdsAttr)),
				idsFromAttribute(t.getAttribute(networkIdsAttr)),
				idsFromAttribute(t.getAttribute(shortcutIdsAttr)));
		return new SolrTaskDeleteFiles(UUID.fromString(t.getResource()), ids);
	}
}
