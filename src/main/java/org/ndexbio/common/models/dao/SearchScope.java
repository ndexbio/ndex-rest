package org.ndexbio.common.models.dao;

import java.util.Set;
import java.util.UUID;

/**
 * What a single search request is allowed to reach, resolved from the live database immediately before
 * the query is issued.
 *
 * <p>Search deliberately stores <em>no</em> permission state in the index. Instead the caller resolves
 * these id sets once per request through {@code FilePermissionResolver} and they become
 * {@code {!terms}} clauses on the private-core filter. Because they are recomputed on every search, a
 * folder share takes effect immediately and a revoke hides its contents immediately — there is no
 * re-index fan-out and no staleness window. This is the search-side half of the live folder-permission
 * resolution introduced for issue #165; the object-fetch paths answer from the same resolver, so the two
 * cannot disagree.</p>
 *
 * <p><b>The sets are already permission-appropriate.</b> A search for {@code WRITE} must be built from a
 * {@code WRITE}-level granted-folder set, so the caller resolves at the level it is about to ask for.
 * This record does not re-check that — it carries a decision already made.</p>
 *
 * <p>Only the private core consults a scope. The public core's filter is deliberately independent of it,
 * because an UNLISTED item must stay unlisted for everyone but its owner no matter what folder grants
 * exist.</p>
 *
 * @param grantedFolderIds    folders the user can reach, already expanded downward through descendants.
 *                            A network is reachable when its parent is in this set; a folder when its
 *                            own id is.
 * @param reachableNetworkIds networks reachable <em>without</em> folder containment — a direct
 *                            per-network grant, or a same-owner shortcut in a granted folder pointing at
 *                            a network that lives elsewhere in the tree.
 * @param readableShortcutIds shortcuts satisfying the reachability conjunction: the shortcut itself is
 *                            reachable <em>and</em> so is its target.
 */
public record SearchScope(Set<UUID> grantedFolderIds,
                          Set<UUID> reachableNetworkIds,
                          Set<UUID> readableShortcutIds) {

	/** No folder propagation resolved: ownership alone decides. Correct for anonymous callers. */
	public static final SearchScope EMPTY = new SearchScope(Set.of(), Set.of(), Set.of());

	public SearchScope {
		grantedFolderIds = grantedFolderIds == null ? Set.of() : Set.copyOf(grantedFolderIds);
		reachableNetworkIds = reachableNetworkIds == null ? Set.of() : Set.copyOf(reachableNetworkIds);
		readableShortcutIds = readableShortcutIds == null ? Set.of() : Set.copyOf(readableShortcutIds);
	}
}
