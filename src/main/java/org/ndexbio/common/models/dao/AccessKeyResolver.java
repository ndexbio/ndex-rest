package org.ndexbio.common.models.dao;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves whether an anonymous access key grants READ access to a network or folder, following the
 * v3 folder hierarchy.
 *
 * <p>An access key is a binary READ grant that propagates <em>downward</em> through the folder tree,
 * exactly like folder permissions (see the "Access key propagation through folders" section of
 * {@code docs/specifications/FOLDER_SHORTCUT_SPECIFICATION.md}). A key is valid for a subject when it
 * matches the enabled {@code access_key} of the subject itself (network's own key) or of <em>any</em>
 * ancestor folder (full-chain accrual / OR semantics — no "nearest parent" override).</p>
 *
 * <p>For networks the chain is additionally seeded by <em>shortcuts</em>: a key is valid for a network
 * when a same-owner (shortcut owner = network owner) live {@code NETWORK} shortcut pointing at it lives
 * in a folder whose ancestry carries the matching enabled key. This restores access keys stranded by
 * the v3 networkset migration, which turned keyed networksets into keyed folders full of shortcuts
 * (issue #133; the previously-deferred #137 edge case). The same-owner guard mirrors
 * {@code DbMigrationTool}'s cross-owner skip and prevents a keyed folder from granting anonymous read
 * to a network its owner does not own.</p>
 *
 * <p>This is the single source of truth for access-key validation, shared by
 * {@code PostgresNetworkDAO} and {@code PostgresFolderDAO}.</p>
 */
public interface AccessKeyResolver {

	/**
	 * @return true if {@code accessKey} matches an enabled access key on {@code folderId} or any of its
	 *         ancestor folders. Returns false for a null/empty key.
	 */
	boolean isFolderKeyValid(UUID folderId, String accessKey) throws SQLException;

	/**
	 * @return true if {@code accessKey} matches the network's own enabled access key, an enabled access
	 *         key on any ancestor folder of the network (walking {@code network.parent} to the root), or
	 *         an enabled key on the ancestry of a folder that holds a same-owner {@code NETWORK} shortcut
	 *         pointing at the network. Returns false for a null/empty key.
	 */
	boolean isNetworkKeyValid(UUID networkId, String accessKey) throws SQLException;

	/**
	 * Batch form of {@link #isNetworkKeyValid}: given a candidate set of network ids, return the subset
	 * for which {@code accessKey} is valid (own key, ancestor-folder chain, or a same-owner {@code NETWORK}
	 * shortcut's folder chain). Evaluated in a single query. Returns an empty set for a null/empty key or
	 * empty input.
	 */
	Set<UUID> filterNetworksByKey(Collection<UUID> networkIds, String accessKey) throws SQLException;
}
