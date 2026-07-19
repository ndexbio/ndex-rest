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
 * ancestor folder (full-chain accrual / OR semantics — no "nearest parent" override). Shortcuts are
 * intentionally not traversed (issue #133; edge case tracked as #137).</p>
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
	 * @return true if {@code accessKey} matches the network's own enabled access key, or an enabled
	 *         access key on any ancestor folder of the network (walking {@code network.parent} to the
	 *         root). Returns false for a null/empty key.
	 */
	boolean isNetworkKeyValid(UUID networkId, String accessKey) throws SQLException;

	/**
	 * Batch form of {@link #isNetworkKeyValid}: given a candidate set of network ids, return the subset
	 * for which {@code accessKey} is valid (own key or ancestor-folder chain). Evaluated in a single
	 * query. Returns an empty set for a null/empty key or empty input.
	 */
	Set<UUID> filterNetworksByKey(Collection<UUID> networkIds, String accessKey) throws SQLException;

	/**
	 * Sources an access key that grants anonymous READ of a network, walking the folder hierarchy: the
	 * network's own enabled access key if present, otherwise the <em>nearest</em> ancestor folder's
	 * enabled access key (closest first, walking {@code network.parent} to the root), or {@code null} if
	 * neither the network nor any ancestor folder has an enabled key.
	 *
	 * <p>Because the returned key is an enabled key on the network's own parent chain, it is guaranteed to
	 * satisfy {@link #isNetworkKeyValid} when forwarded back as an {@code accesskey}. Used to embed a
	 * working key in the DOI viewer URL of a private network.</p>
	 */
	String resolveNetworkAccessKey(UUID networkId) throws SQLException;
}
