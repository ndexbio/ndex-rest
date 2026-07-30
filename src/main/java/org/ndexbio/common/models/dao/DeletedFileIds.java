package org.ndexbio.common.models.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The ids a folder deletion actually affected, grouped by file type.
 *
 * <p>Returned by {@link FolderDAO#deleteFolder(UUID, boolean, boolean)} so callers can clear the Solr
 * state of <em>every</em> item the delete touched, not just the folder the user named. A
 * {@code force=true} delete cascades over the whole subtree at any depth, but the index docs live
 * outside the transaction and have to be removed explicitly.
 *
 * <p>The ids come back from the same transaction that did the deleting, so they cannot drift from
 * what was actually affected — which is why this is a return value rather than a separate
 * "enumerate the subtree" query run beforehand.
 *
 * <p>Purely internal: never serialized to a client.
 */
public record DeletedFileIds(List<UUID> folders, List<UUID> networks, List<UUID> shortcuts) {

	/** Copies defensively so a caller cannot mutate what the DAO reported. Null lists are a bug: fail fast. */
	public DeletedFileIds {
		folders = List.copyOf(folders);
		networks = List.copyOf(networks);
		shortcuts = List.copyOf(shortcuts);
	}

	public static DeletedFileIds empty() {
		return new DeletedFileIds(List.of(), List.of(), List.of());
	}

	/** Every affected id across all three types, for callers that treat them uniformly. */
	public List<UUID> all() {
		List<UUID> result = new ArrayList<>(folders.size() + networks.size() + shortcuts.size());
		result.addAll(folders);
		result.addAll(networks);
		result.addAll(shortcuts);
		return Collections.unmodifiableList(result);
	}

	public int size() {
		return folders.size() + networks.size() + shortcuts.size();
	}

	public boolean isEmpty() {
		return size() == 0;
	}
}
