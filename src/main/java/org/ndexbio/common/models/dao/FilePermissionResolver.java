package org.ndexbio.common.models.dao;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.Permissions;

/**
 * Resolves the effective {@code READ}/{@code WRITE} permission a user holds on a v3 file, following the
 * folder hierarchy.
 *
 * <p>Folder permissions propagate <em>downward</em>: per the "Sharing" section of
 * {@code docs/specifications/FOLDER_SHORTCUT_SPECIFICATION.md}, a permission list on a folder is
 * inherited by every <strong>Network and Folder</strong> nested beneath it, recursively, and re-parenting
 * an object changes what it inherits. Inheritance is computed <em>live at request time</em> by walking
 * {@code folder.parent} upward from the subject — the same shape {@link AccessKeyResolver} uses for
 * access keys. It is deliberately not materialized: a copy-down snapshot silently drifts the moment the
 * tree changes, which is the defect behind issue #165.</p>
 *
 * <p><b>Effective permission is the most permissive of</b> ownership (always write), a direct grant on
 * the object itself, any grant inherited from an ancestor folder, and public/unlisted visibility (read).
 * This mirrors the "most permissive wins" rule the specification describes, and it preserves direct
 * per-network grants issued through the network sharing endpoints.</p>
 *
 * <p><b>Shortcuts are aliases, never permission holders.</b> The specification states a shortcut
 * "inherits permission of what the Shortcut targets" and that "it is not possible to set permission on
 * Shortcuts". A shortcut is therefore readable exactly when its target is readable, and its own parent
 * folder is never consulted. A shortcut whose target has been removed resolves to no permission rather
 * than an error.</p>
 *
 * <p><b>Same-owner shortcut seeding.</b> For networks the ancestry is additionally seeded by the parent
 * folders of live {@code NETWORK} shortcuts whose owner matches the network's owner. Without this, a
 * folder of shortcuts — which is what the v3 migration turned every network set into — grants a user
 * nothing when shared. The same-owner guard prevents a folder owner from granting access to a network
 * they do not own, and mirrors the identical rule in {@link AccessKeyResolver} (issues #133/#137).</p>
 *
 * <p><b>Two evaluation modes.</b> Single-object endpoints use the per-subject methods, which walk the
 * chain once for one subject. Listing endpoints must instead call {@link #grantedFolderIds} <em>once per
 * request</em> and pass the result to the SQL-fragment builders, so a listing evaluates one recursive
 * query rather than one per candidate row. Folders may hold very large numbers of items, so the
 * per-row form does not scale there.</p>
 *
 * <p>Implementations share the JDBC connection of the DAO that owns them (constructor injection, the
 * same seam as {@code NdexDBDAO}), so tests can supply a mock connection — or a mock of this interface
 * can be injected into the consuming DAO so its unit tests never run the real query.</p>
 */
public interface FilePermissionResolver {

	/**
	 * @return the effective permission on {@code folderId}, or null when the user has none. Considers
	 *         ownership, a direct {@code folder_permission} row on the folder itself, any row on an
	 *         ancestor folder, and public/unlisted visibility. A null {@code userId} (anonymous) is
	 *         resolved from visibility alone.
	 */
	Permissions effectiveFolderPermission(UUID folderId, UUID userId) throws SQLException;

	/**
	 * @return the effective permission on {@code networkId}, or null when the user has none. Considers
	 *         ownership, a direct {@code user_network_membership} row, any {@code folder_permission} row
	 *         on the network's ancestor chain, the ancestry of a folder holding a same-owner
	 *         {@code NETWORK} shortcut to it, and public/unlisted visibility.
	 */
	Permissions effectiveNetworkPermission(UUID networkId, UUID userId) throws SQLException;

	/**
	 * @return the effective permission on the shortcut's <em>target</em>, which is by definition the
	 *         shortcut's own permission. Returns null for a dangling or deleted target.
	 */
	Permissions effectiveShortcutPermission(UUID shortcutId, UUID userId) throws SQLException;

	/**
	 * Batch form for listing endpoints: the ids of every folder on which {@code userId} holds at least
	 * {@code atLeast}, whether granted directly or inherited from an ancestor. Evaluated in a single
	 * query. Returns an empty set for a null user or when no grant exists.
	 *
	 * @param atLeast {@code READ} matches any grant; {@code WRITE} matches write grants only.
	 */
	Set<UUID> grantedFolderIds(UUID userId, Permissions atLeast) throws SQLException;

	/**
	 * A SQL boolean fragment deciding whether the row aliased {@code alias} is readable by
	 * {@code userId}, for embedding in a larger query. Tests membership of the pre-resolved
	 * {@code grantedFolderIds} rather than re-walking the hierarchy, so the caller must obtain that set
	 * once per request via {@link #grantedFolderIds}.
	 *
	 * @param type  which table {@code alias} refers to, selecting the correct ownership/visibility columns
	 * @param alias the outer table alias, e.g. {@code "n"}, {@code "f"} or {@code "s"}
	 */
	String readableConditionSql(FileType type, String alias, UUID userId, Set<UUID> grantedFolderIds);

	/** Write-permission counterpart of {@link #readableConditionSql}. */
	String writableConditionSql(FileType type, String alias, UUID userId, Set<UUID> grantedFolderIds);

	/**
	 * The effective audience of an object, for populating the Solr access-control fields.
	 *
	 * <p>Keys mirror {@code NetworkDAO.getAllMembershipsOnNetwork} so the indexers are unaffected:
	 * {@code ADMIN} holds the owner, {@code WRITE} and {@code READ} hold granted users. Values are
	 * user names. Unlike the direct-row lookups this replaces, the returned sets include users whose
	 * access is <em>inherited</em> from an ancestor folder; without that, an object nested in a shared
	 * folder would be indexed with an empty access list and become unfindable to the very users who can
	 * open it.</p>
	 */
	Map<Permissions, Collection<String>> effectiveMembers(UUID objectId, FileType type) throws SQLException;
}
