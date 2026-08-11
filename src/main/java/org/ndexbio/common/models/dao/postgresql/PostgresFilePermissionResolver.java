package org.ndexbio.common.models.dao.postgresql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.ndexbio.common.models.dao.FilePermissionResolver;
import org.ndexbio.common.models.dao.SearchScope;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.Permissions;

/**
 * PostgreSQL implementation of {@link FilePermissionResolver}.
 *
 * <p>Effective permission is computed live with recursive CTEs over {@code folder.parent}, mirroring
 * {@link org.ndexbio.common.models.dao.AccessKeyResolver}'s treatment of access keys. Permissions are
 * ranked internally so the "most permissive wins" rule is a plain {@code max()}: ownership beats a
 * write grant, which beats a read grant, which beats public visibility.</p>
 *
 * <p>Only {@code READ} and {@code WRITE} are recognized as grants. Any other value stored in the
 * unvalidated {@code folder_permission.permission} column is ignored rather than guessed at, so the
 * resolver fails closed.</p>
 *
 * <p>This collaborator shares the JDBC {@link Connection} of the DAO that owns it (constructor
 * injection, same seam as {@code NdexDBDAO}).</p>
 */
public class PostgresFilePermissionResolver implements FilePermissionResolver {

	/** Internal permission ranking; higher wins. Owner is modelled above any grant. */
	private static final int RANK_NONE  = 0;
	private static final int RANK_READ  = 1;
	private static final int RANK_WRITE = 2;
	private static final int RANK_OWNER = 3;

	/** Only these two values are honoured as grants; anything else fails closed. */
	private static final String GRANT_VALUES = "('READ','WRITE')";

	private final Connection db;

	public PostgresFilePermissionResolver(Connection db) {
		this.db = db;
	}

	// ── single-object resolution ────────────────────────────────────────────────

	@Override
	public Permissions effectiveFolderPermission(UUID folderId, UUID userId) throws SQLException {
		if (folderId == null)
			return null;

		if (userId == null) {
			// Anonymous: visibility is the only possible source of access.
			return publicRead("folder", folderId);
		}

		// Chain seeds at the folder itself so a direct grant on it counts, then walks up live ancestors.
		String sql = "WITH RECURSIVE chain AS ("
				+ "  SELECT f.\"UUID\" AS fid, f.parent FROM folder f"
				+ "   WHERE f.\"UUID\" = ? AND f.is_deleted = false"
				+ "  UNION"
				+ "  SELECT pf.\"UUID\", pf.parent FROM folder pf JOIN chain c ON pf.\"UUID\" = c.parent"
				+ "   WHERE pf.is_deleted = false"
				+ "), cand AS ("
				+ "  SELECT " + RANK_OWNER + " AS rank FROM folder"
				+ "   WHERE \"UUID\" = ? AND is_deleted = false AND owneruuid = ?"
				+ "  UNION ALL"
				+ "  SELECT " + RANK_READ + " FROM folder"
				+ "   WHERE \"UUID\" = ? AND is_deleted = false AND visibility IN ('PUBLIC','UNLISTED')"
				+ "  UNION ALL"
				+ "  SELECT CASE WHEN upper(fp.permission) = 'WRITE' THEN " + RANK_WRITE + " ELSE " + RANK_READ + " END"
				+ "    FROM chain JOIN folder_permission fp ON fp.folder_id = chain.fid"
				+ "   WHERE fp.user_id = ? AND upper(fp.permission) IN " + GRANT_VALUES
				+ ") SELECT coalesce(max(rank), 0) FROM cand";

		try (PreparedStatement p = db.prepareStatement(sql)) {
			p.setObject(1, folderId);
			p.setObject(2, folderId);
			p.setObject(3, userId);
			p.setObject(4, folderId);
			p.setObject(5, userId);
			return rankToPermission(firstInt(p));
		}
	}

	@Override
	public Permissions effectiveNetworkPermission(UUID networkId, UUID userId) throws SQLException {
		if (networkId == null)
			return null;

		if (userId == null) {
			return publicRead("network", networkId);
		}

		// Two seed arms, matching AccessKeyResolver.isNetworkKeyValid: the network's own parent folder,
		// and the parent folder of any live same-owner NETWORK shortcut pointing at it. The same-owner
		// guard blocks a folder owner from sharing out a network they do not own.
		String sql = "WITH RECURSIVE chain AS ("
				+ "  SELECT f.\"UUID\" AS fid, f.parent FROM folder f"
				+ "   WHERE f.is_deleted = false"
				+ "     AND ( f.\"UUID\" = (SELECT parent FROM network WHERE \"UUID\" = ?)"
				+ "           OR f.\"UUID\" IN ("
				+ "                SELECT s.parent FROM shortcut s"
				+ "                 WHERE s.target = ? AND s.target_type = 'NETWORK' AND s.is_deleted = false"
				+ "                   AND s.owneruuid = (SELECT owneruuid FROM network WHERE \"UUID\" = ?) ) )"
				+ "  UNION"
				+ "  SELECT pf.\"UUID\", pf.parent FROM folder pf JOIN chain c ON pf.\"UUID\" = c.parent"
				+ "   WHERE pf.is_deleted = false"
				+ "), cand AS ("
				+ "  SELECT " + RANK_OWNER + " AS rank FROM network"
				+ "   WHERE \"UUID\" = ? AND is_deleted = false AND owneruuid = ?"
				+ "  UNION ALL"
				+ "  SELECT " + RANK_READ + " FROM network"
				+ "   WHERE \"UUID\" = ? AND is_deleted = false AND visibility IN ('PUBLIC','UNLISTED')"
				+ "  UNION ALL"
				+ "  SELECT CASE WHEN un.permission_type::text = 'WRITE' THEN " + RANK_WRITE + " ELSE " + RANK_READ + " END"
				+ "    FROM user_network_membership un"
				+ "   WHERE un.network_id = ? AND un.user_id = ? AND un.permission_type::text IN " + GRANT_VALUES
				+ "  UNION ALL"
				+ "  SELECT CASE WHEN upper(fp.permission) = 'WRITE' THEN " + RANK_WRITE + " ELSE " + RANK_READ + " END"
				+ "    FROM chain JOIN folder_permission fp ON fp.folder_id = chain.fid"
				+ "   WHERE fp.user_id = ? AND upper(fp.permission) IN " + GRANT_VALUES
				+ ") SELECT coalesce(max(rank), 0) FROM cand";

		try (PreparedStatement p = db.prepareStatement(sql)) {
			p.setObject(1, networkId);
			p.setObject(2, networkId);
			p.setObject(3, networkId);
			p.setObject(4, networkId);
			p.setObject(5, userId);
			p.setObject(6, networkId);
			p.setObject(7, networkId);
			p.setObject(8, userId);
			p.setObject(9, userId);
			return rankToPermission(firstInt(p));
		}
	}

	@Override
	public Permissions effectiveShortcutPermission(UUID shortcutId, UUID userId) throws SQLException {
		if (shortcutId == null)
			return null;

		// A shortcut holds no permission of its own. Readability is a conjunction: the shortcut must be
		// reachable (public, owned, or sitting in a folder the caller can read) AND its target must be
		// reachable. The containment arm keeps this in step with search, which has no traversal to gate
		// it — without it, knowing an id would reveal an entry inside a folder the caller cannot open.
		UUID target = null;
		String targetType = null;
		String visibility = null;
		UUID owner = null;
		UUID parent = null;
		try (PreparedStatement p = db.prepareStatement(
				"SELECT target, target_type, visibility, owneruuid, parent"
				+ " FROM shortcut WHERE \"UUID\" = ? AND is_deleted = false")) {
			p.setObject(1, shortcutId);
			try (ResultSet rs = p.executeQuery()) {
				if (rs.next()) {
					target = (UUID) rs.getObject(1);
					targetType = rs.getString(2);
					visibility = rs.getString(3);
					owner = (UUID) rs.getObject(4);
					parent = (UUID) rs.getObject(5);
				}
			}
		}
		if (target == null || targetType == null)
			return null; // dangling or deleted shortcut resolves to no access, never an error

		boolean reachable = "PUBLIC".equalsIgnoreCase(visibility)
				|| (userId != null && userId.equals(owner))
				|| effectiveFolderPermission(parent, userId) != null;
		if (!reachable)
			return null;

		if (FileType.FOLDER.toString().equalsIgnoreCase(targetType))
			return effectiveFolderPermission(target, userId);
		if (FileType.NETWORK.toString().equalsIgnoreCase(targetType))
			return effectiveNetworkPermission(target, userId);
		return null;
	}

	// ── batch resolution for listing endpoints ──────────────────────────────────

	@Override
	public Set<UUID> grantedFolderIds(UUID userId, Permissions atLeast) throws SQLException {
		Set<UUID> ids = new HashSet<>();
		if (userId == null)
			return ids;

		// Seed from folders the user was granted AND folders the user owns, then expand DOWNWARD to
		// every descendant, since access to a folder covers everything nested beneath it. One query
		// regardless of tree size.
		//
		// The ownership arm is essential: an owner has no folder_permission row for their own folder,
		// so seeding from grants alone leaves the set empty for them. That is precisely the reciprocal
		// half of #165 — the person who owns and shared a folder could not see what collaborators put
		// in it, because every child is owned by someone else and nothing else matched.
		String grantFilter = (atLeast == Permissions.WRITE)
				? "upper(fp.permission) = 'WRITE'"
				: "upper(fp.permission) IN " + GRANT_VALUES;

		String sql = "WITH RECURSIVE sub AS ("
				+ "  SELECT fp.folder_id AS fid FROM folder_permission fp"
				+ "   WHERE fp.user_id = ? AND " + grantFilter
				+ "  UNION"
				+ "  SELECT f.\"UUID\" FROM folder f WHERE f.owneruuid = ? AND f.is_deleted = false"
				+ "  UNION"
				+ "  SELECT f.\"UUID\" FROM folder f JOIN sub s ON f.parent = s.fid"
				+ "   WHERE f.is_deleted = false"
				+ ") SELECT fid FROM sub";

		try (PreparedStatement p = db.prepareStatement(sql)) {
			p.setObject(1, userId);
			p.setObject(2, userId);
			try (ResultSet rs = p.executeQuery()) {
				while (rs.next())
					ids.add((UUID) rs.getObject(1));
			}
		}
		return ids;
	}

	@Override
	public Set<UUID> reachableNetworkIds(UUID userId, Set<UUID> grantedFolderIds, Permissions atLeast)
			throws SQLException {
		Set<UUID> ids = new HashSet<>();
		if (userId == null)
			return ids;

		// Two arms, both outside folder containment:
		//   1. a direct per-network grant
		//   2. a same-owner NETWORK shortcut sitting in a granted folder — the target lives elsewhere in
		//      the tree, so its own parent is not in the granted set. The same-owner guard prevents a
		//      folder owner from widening access to a network they do not own.
		String grantedIn = folderIdSetSql(grantedFolderIds);

		// Narrow to write grants when a write search asked for it, exactly as conditionSql does for the
		// SQL surfaces. The folder arm is already narrowed — grantedFolderIds was resolved at the same
		// level — so leaving this one open would return networks the caller can only read.
		String grantFilter = (atLeast == Permissions.WRITE)
				? "m.permission_type::text = 'WRITE'"
				: "m.permission_type::text IN " + GRANT_VALUES;

		StringBuilder sql = new StringBuilder(
				"SELECT m.network_id FROM user_network_membership m"
				+ " JOIN network n ON n.\"UUID\" = m.network_id AND n.is_deleted = false"
				+ " WHERE m.user_id = ? AND " + grantFilter);

		if (grantedIn != null) {
			sql.append(" UNION SELECT n.\"UUID\" FROM network n"
					+ " JOIN shortcut sc ON sc.target = n.\"UUID\" AND sc.target_type = 'NETWORK'"
					+ "   AND sc.is_deleted = false AND sc.owneruuid = n.owneruuid"
					+ " WHERE n.is_deleted = false AND sc.parent IN ").append(grantedIn);
		}

		try (PreparedStatement p = db.prepareStatement(sql.toString())) {
			p.setObject(1, userId);
			try (ResultSet rs = p.executeQuery()) {
				while (rs.next())
					ids.add((UUID) rs.getObject(1));
			}
		}
		return ids;
	}

	@Override
	public Set<UUID> readableShortcutIds(UUID userId, Set<UUID> grantedFolderIds) throws SQLException {
		Set<UUID> ids = new HashSet<>();
		if (userId == null)
			return ids;

		// Conjunction: the shortcut itself must be reachable AND its target must be reachable. The
		// containment arm is what stops search revealing a shortcut inside a folder the caller cannot
		// open just because they can read what it points at.
		String grantedIn = folderIdSetSql(grantedFolderIds);
		String scInGranted = (grantedIn == null) ? "" : " OR s.parent IN " + grantedIn;
		String fldInGranted = (grantedIn == null) ? "" : " OR f.\"UUID\" IN " + grantedIn;

		// The target arm must recognise every way a network becomes reachable, including the same-owner
		// shortcut seed — otherwise a shortcut is judged unreadable even though the seed makes its own
		// target readable, and the two resolvers disagree. Keep this in step with reachableNetworkIds.
		String netInGranted = (grantedIn == null) ? ""
				: " OR n.parent IN " + grantedIn
				+ " OR EXISTS ( SELECT 1 FROM shortcut sc2"
				+ "              WHERE sc2.target = n.\"UUID\" AND sc2.target_type = 'NETWORK'"
				+ "                AND sc2.is_deleted = false AND sc2.owneruuid = n.owneruuid"
				+ "                AND sc2.parent IN " + grantedIn + " )";

		String sql = "SELECT s.\"UUID\" FROM shortcut s"
				+ " WHERE s.is_deleted = false"
				+ "   AND ( s.visibility = 'PUBLIC' OR s.owneruuid = ?" + scInGranted + " )"
				+ "   AND ( EXISTS ( SELECT 1 FROM network n"
				+ "                   WHERE n.\"UUID\" = s.target AND s.target_type = 'NETWORK'"
				+ "                     AND n.is_deleted = false"
				+ "                     AND ( n.visibility IN ('PUBLIC','UNLISTED') OR n.owneruuid = ?"
				+ netInGranted
				+ "                           OR EXISTS ( SELECT 1 FROM user_network_membership m"
				+ "                                        WHERE m.network_id = n.\"UUID\" AND m.user_id = ?"
				+ "                                          AND m.permission_type::text IN " + GRANT_VALUES + " ) ) )"
				+ "      OR EXISTS ( SELECT 1 FROM folder f"
				+ "                   WHERE f.\"UUID\" = s.target AND s.target_type = 'FOLDER'"
				+ "                     AND f.is_deleted = false"
				+ "                     AND ( f.visibility IN ('PUBLIC','UNLISTED') OR f.owneruuid = ?"
				+ fldInGranted + " ) ) )";

		try (PreparedStatement p = db.prepareStatement(sql)) {
			p.setObject(1, userId);
			p.setObject(2, userId);
			p.setObject(3, userId);
			p.setObject(4, userId);
			try (ResultSet rs = p.executeQuery()) {
				while (rs.next())
					ids.add((UUID) rs.getObject(1));
			}
		}
		return ids;
	}

	@Override
	public SearchScope searchScope(UUID userId, Permissions atLeast) throws SQLException {
		if (userId == null)
			return SearchScope.EMPTY;

		// One downward expansion of the hierarchy, reused by both id queries rather than walked again.
		Set<UUID> granted = grantedFolderIds(userId, atLeast);
		return new SearchScope(granted, reachableNetworkIds(userId, granted, atLeast),
				readableShortcutIds(userId, granted));
	}

	@Override
	public String readableConditionSql(FileType type, String alias, UUID userId, Set<UUID> grantedFolderIds) {
		return conditionSql(type, alias, userId, grantedFolderIds, false);
	}

	@Override
	public String writableConditionSql(FileType type, String alias, UUID userId, Set<UUID> grantedFolderIds) {
		return conditionSql(type, alias, userId, grantedFolderIds, true);
	}

	private String conditionSql(FileType type, String alias, UUID userId, Set<UUID> granted, boolean write) {
		String a = alias;
		// Anonymous callers can only ever reach public content, and never for write.
		if (userId == null) {
			if (write)
				return "(false)";
			switch (type) {
				case FOLDER:   return "(" + a + ".visibility IN ('PUBLIC','UNLISTED'))";
				case NETWORK:  return "(" + a + ".visibility IN ('PUBLIC','UNLISTED'))";
				case SHORTCUT: return shortcutDelegation(a, null, granted, false);
				default:       return "(false)";
			}
		}

		String uid = "'" + userId + "'::uuid";
		String inGranted = folderIdSetSql(granted);

		switch (type) {
			case FOLDER: {
				StringBuilder sb = new StringBuilder("( " + a + ".owneruuid = " + uid);
				if (!write)
					sb.append(" OR " + a + ".visibility IN ('PUBLIC','UNLISTED')");
				if (inGranted != null)
					sb.append(" OR " + a + ".\"UUID\" IN " + inGranted);
				return sb.append(" )").toString();
			}
			case NETWORK: {
				StringBuilder sb = new StringBuilder("( " + a + ".owneruuid = " + uid);
				if (!write)
					sb.append(" OR " + a + ".visibility IN ('PUBLIC','UNLISTED')");
				sb.append(" OR EXISTS ( SELECT 1 FROM user_network_membership un"
						+ " WHERE un.network_id = " + a + ".\"UUID\" AND un.user_id = " + uid
						+ (write ? " AND un.permission_type::text = 'WRITE'" : "")
						+ " LIMIT 1 )");
				if (inGranted != null) {
					sb.append(" OR " + a + ".parent IN " + inGranted);
					// same-owner shortcut seed: a network referenced from a granted folder by one of its
					// owner's own shortcuts is reachable, mirroring the access-key rule (#133/#137)
					sb.append(" OR EXISTS ( SELECT 1 FROM shortcut sc"
							+ " WHERE sc.target = " + a + ".\"UUID\" AND sc.target_type = 'NETWORK'"
							+ " AND sc.is_deleted = false AND sc.owneruuid = " + a + ".owneruuid"
							+ " AND sc.parent IN " + inGranted + " LIMIT 1 )");
				}
				return sb.append(" )").toString();
			}
			case SHORTCUT:
				return shortcutDelegation(a, userId, granted, write);
			default:
				return "(false)";
		}
	}

	/** A shortcut is readable/writable exactly when its target is; its own parent is never consulted. */
	private String shortcutDelegation(String a, UUID userId, Set<UUID> granted, boolean write) {
		// Conjunction: the shortcut must itself be reachable AND its target must be reachable.
		//
		// The containment arm tests that the shortcut's PARENT FOLDER IS READABLE — deliberately not
		// "parent is in grantedFolderIds". The two differ for a PUBLIC folder viewed anonymously, where
		// the granted set is empty; using the set there would hide every shortcut in a public folder from
		// anonymous callers, since createShortcut always stores visibility PRIVATE.
		String parentReadable = conditionSql(FileType.FOLDER, "pf", userId, granted, false);
		StringBuilder containment = new StringBuilder("( " + a + ".visibility = 'PUBLIC'");
		if (userId != null)
			containment.append(" OR ").append(a).append(".owneruuid = '").append(userId).append("'::uuid");
		containment.append(" OR EXISTS ( SELECT 1 FROM folder pf WHERE pf.\"UUID\" = ").append(a)
				.append(".parent AND pf.is_deleted = false AND ").append(parentReadable).append(" LIMIT 1 ) )");

		String netCond = conditionSql(FileType.NETWORK, "tn", userId, granted, write);
		String fldCond = conditionSql(FileType.FOLDER, "tf", userId, granted, write);
		String targetReachable = "( EXISTS ( SELECT 1 FROM network tn WHERE tn.\"UUID\" = " + a + ".target"
				+ " AND " + a + ".target_type = 'NETWORK' AND tn.is_deleted = false AND " + netCond + " LIMIT 1 )"
				+ " OR EXISTS ( SELECT 1 FROM folder tf WHERE tf.\"UUID\" = " + a + ".target"
				+ " AND " + a + ".target_type = 'FOLDER' AND tf.is_deleted = false AND " + fldCond + " LIMIT 1 ) )";

		return "( " + containment + " AND " + targetReachable + " )";
	}

	/**
	 * Renders a granted-folder set as a SQL {@code IN} list, or null when the set is empty so callers
	 * omit the clause entirely rather than emitting an invalid {@code IN ()}. Ids are UUID objects and
	 * therefore injection-safe to inline, which avoids binding thousands of parameters.
	 */
	private static String folderIdSetSql(Set<UUID> granted) {
		if (granted == null || granted.isEmpty())
			return null;
		StringBuilder sb = new StringBuilder("(");
		boolean first = true;
		for (UUID id : granted) {
			if (!first)
				sb.append(',');
			sb.append('\'').append(id.toString()).append("'::uuid");
			first = false;
		}
		return sb.append(')').toString();
	}

	// ── Solr audience ───────────────────────────────────────────────────────────

	@Override
	public Map<Permissions, Collection<String>> effectiveMembers(UUID objectId, FileType type) throws SQLException {
		Map<Permissions, Collection<String>> members = new HashMap<>();
		members.put(Permissions.ADMIN, new ArrayList<String>());
		members.put(Permissions.WRITE, new ArrayList<String>());
		members.put(Permissions.READ, new ArrayList<String>());
		if (objectId == null || type == null)
			return members;

		String sql;
		int binds;
		if (type == FileType.NETWORK) {
			sql = "WITH RECURSIVE chain AS ("
					+ "  SELECT f.\"UUID\" AS fid, f.parent FROM folder f"
					+ "   WHERE f.is_deleted = false"
					+ "     AND ( f.\"UUID\" = (SELECT parent FROM network WHERE \"UUID\" = ?)"
					+ "           OR f.\"UUID\" IN ("
					+ "                SELECT s.parent FROM shortcut s"
					+ "                 WHERE s.target = ? AND s.target_type = 'NETWORK' AND s.is_deleted = false"
					+ "                   AND s.owneruuid = (SELECT owneruuid FROM network WHERE \"UUID\" = ?) ) )"
					+ "  UNION"
					+ "  SELECT pf.\"UUID\", pf.parent FROM folder pf JOIN chain c ON pf.\"UUID\" = c.parent"
					+ "   WHERE pf.is_deleted = false"
					+ "), acc AS ("
					+ "  SELECT owneruuid AS user_id, " + RANK_OWNER + " AS rank FROM network WHERE \"UUID\" = ?"
					+ "  UNION ALL"
					+ "  SELECT un.user_id, CASE WHEN un.permission_type::text = 'WRITE' THEN " + RANK_WRITE
					+ "         ELSE " + RANK_READ + " END"
					+ "    FROM user_network_membership un"
					+ "   WHERE un.network_id = ? AND un.permission_type::text IN " + GRANT_VALUES
					+ "  UNION ALL"
					+ "  SELECT fp.user_id, CASE WHEN upper(fp.permission) = 'WRITE' THEN " + RANK_WRITE
					+ "         ELSE " + RANK_READ + " END"
					+ "    FROM chain JOIN folder_permission fp ON fp.folder_id = chain.fid"
					+ "   WHERE upper(fp.permission) IN " + GRANT_VALUES
					+ ") SELECT u.user_name, max(acc.rank) FROM acc JOIN ndex_user u ON u.\"UUID\" = acc.user_id"
					+ " GROUP BY u.user_name";
			binds = 5;
		} else if (type == FileType.FOLDER) {
			sql = "WITH RECURSIVE chain AS ("
					+ "  SELECT f.\"UUID\" AS fid, f.parent FROM folder f"
					+ "   WHERE f.\"UUID\" = ? AND f.is_deleted = false"
					+ "  UNION"
					+ "  SELECT pf.\"UUID\", pf.parent FROM folder pf JOIN chain c ON pf.\"UUID\" = c.parent"
					+ "   WHERE pf.is_deleted = false"
					+ "), acc AS ("
					+ "  SELECT owneruuid AS user_id, " + RANK_OWNER + " AS rank FROM folder WHERE \"UUID\" = ?"
					+ "  UNION ALL"
					+ "  SELECT fp.user_id, CASE WHEN upper(fp.permission) = 'WRITE' THEN " + RANK_WRITE
					+ "         ELSE " + RANK_READ + " END"
					+ "    FROM chain JOIN folder_permission fp ON fp.folder_id = chain.fid"
					+ "   WHERE upper(fp.permission) IN " + GRANT_VALUES
					+ ") SELECT u.user_name, max(acc.rank) FROM acc JOIN ndex_user u ON u.\"UUID\" = acc.user_id"
					+ " GROUP BY u.user_name";
			binds = 2;
		} else {
			// Shortcuts carry no audience of their own; the target's document holds the access list.
			return members;
		}

		try (PreparedStatement p = db.prepareStatement(sql)) {
			for (int i = 1; i <= binds; i++)
				p.setObject(i, objectId);
			try (ResultSet rs = p.executeQuery()) {
				while (rs.next()) {
					String userName = rs.getString(1);
					switch (rs.getInt(2)) {
						case RANK_OWNER: members.get(Permissions.ADMIN).add(userName); break;
						case RANK_WRITE: members.get(Permissions.WRITE).add(userName); break;
						case RANK_READ:  members.get(Permissions.READ).add(userName);  break;
						default: break;
					}
				}
			}
		}
		return members;
	}

	// ── helpers ─────────────────────────────────────────────────────────────────

	/** Visibility-only lookup used for anonymous callers, so no permission tables are touched. */
	private Permissions publicRead(String table, UUID id) throws SQLException {
		String sql = "SELECT 1 FROM " + table + " WHERE \"UUID\" = ? AND is_deleted = false"
				+ " AND visibility IN ('PUBLIC','UNLISTED')";
		try (PreparedStatement p = db.prepareStatement(sql)) {
			p.setObject(1, id);
			try (ResultSet rs = p.executeQuery()) {
				return rs.next() ? Permissions.READ : null;
			}
		}
	}

	private static int firstInt(PreparedStatement p) throws SQLException {
		try (ResultSet rs = p.executeQuery()) {
			return rs.next() ? rs.getInt(1) : RANK_NONE;
		}
	}

	/** Owner and write both confer write; the ADMIN alias is deliberately not produced here. */
	private static Permissions rankToPermission(int rank) {
		if (rank >= RANK_WRITE)
			return Permissions.WRITE;
		if (rank == RANK_READ)
			return Permissions.READ;
		return null;
	}
}
