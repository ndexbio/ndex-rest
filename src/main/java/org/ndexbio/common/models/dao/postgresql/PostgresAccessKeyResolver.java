package org.ndexbio.common.models.dao.postgresql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.ndexbio.common.models.dao.AccessKeyResolver;

/**
 * PostgreSQL implementation of {@link AccessKeyResolver}.
 *
 * <p>Validity is computed live with an upward recursive CTE over {@code folder.parent}, mirroring the
 * downward propagation of folder permissions but for the binary READ grant that an access key
 * represents. See the "Access key propagation through folders" section of
 * {@code docs/specifications/FOLDER_SHORTCUT_SPECIFICATION.md}. For networks the CTE seed also includes
 * the parent folders of same-owner live {@code NETWORK} shortcuts pointing at the network, so a key
 * stranded on a migrated networkset folder (full of shortcuts) still grants read to its target networks
 * (issue #133; the previously-deferred #137 edge case). The same-owner guard mirrors
 * {@code DbMigrationTool}'s cross-owner skip.</p>
 *
 * <p>This collaborator shares the JDBC {@link Connection} of the DAO that owns it (constructor
 * injection, same seam as {@code NdexDBDAO}), so tests can supply a mock connection — or a mock of
 * this interface can be injected into the consuming DAO so its unit tests never run the real query.</p>
 */
public class PostgresAccessKeyResolver implements AccessKeyResolver {

	private final Connection db;

	public PostgresAccessKeyResolver(Connection db) {
		this.db = db;
	}

	@Override
	public boolean isFolderKeyValid(UUID folderId, String accessKey) throws SQLException {
		if (accessKey == null || accessKey.isEmpty())
			return false;

		// Seed the chain with the folder itself (no is_deleted filter on the subject, preserving the
		// prior accessKeyIsValid behavior), then walk up through live ancestors.
		String sql = "WITH RECURSIVE chain AS ("
				+ "  SELECT f.\"UUID\", f.parent, f.access_key_is_on, f.access_key"
				+ "    FROM folder f WHERE f.\"UUID\" = ?"
				+ "  UNION"
				+ "  SELECT pf.\"UUID\", pf.parent, pf.access_key_is_on, pf.access_key"
				+ "    FROM folder pf JOIN chain c ON pf.\"UUID\" = c.parent"
				+ "   WHERE pf.is_deleted = false"
				+ ") SELECT 1 FROM chain WHERE access_key_is_on AND access_key = ? LIMIT 1";

		try (PreparedStatement p = db.prepareStatement(sql)) {
			p.setObject(1, folderId);
			p.setString(2, accessKey);
			try (ResultSet rs = p.executeQuery()) {
				return rs.next();
			}
		}
	}

	@Override
	public boolean isNetworkKeyValid(UUID networkId, String accessKey) throws SQLException {
		if (accessKey == null || accessKey.isEmpty())
			return false;

		// The network's own key, OR any live ancestor folder starting from either the network's own
		// parent OR the parent folder of a same-owner live NETWORK shortcut pointing at this network.
		// The shortcut seed restores access keys that the v3 networkset migration left stranded on a
		// folder full of shortcuts (issue #133; the previously-deferred #137 edge case). The same-owner
		// guard (shortcut.owneruuid = network.owneruuid) mirrors DbMigrationTool's cross-owner skip and
		// prevents a keyed folder from granting anonymous read to a network its owner does not own.
		String sql = "WITH RECURSIVE chain AS ("
				+ "  SELECT f.\"UUID\", f.parent, f.access_key_is_on, f.access_key"
				+ "    FROM folder f"
				+ "   WHERE f.is_deleted = false"
				+ "     AND ( f.\"UUID\" = (SELECT parent FROM network WHERE \"UUID\" = ?)"
				+ "           OR f.\"UUID\" IN ("
				+ "                SELECT s.parent FROM shortcut s"
				+ "                 WHERE s.target = ? AND s.target_type = 'NETWORK' AND s.is_deleted = false"
				+ "                   AND s.owneruuid = (SELECT owneruuid FROM network WHERE \"UUID\" = ?) ) )"
				+ "  UNION"
				+ "  SELECT pf.\"UUID\", pf.parent, pf.access_key_is_on, pf.access_key"
				+ "    FROM folder pf JOIN chain c ON pf.\"UUID\" = c.parent"
				+ "   WHERE pf.is_deleted = false"
				+ ") "
				+ "SELECT 1 FROM network WHERE \"UUID\" = ? AND access_key_is_on AND access_key = ? "
				+ "UNION ALL "
				+ "SELECT 1 FROM chain WHERE access_key_is_on AND access_key = ? "
				+ "LIMIT 1";

		try (PreparedStatement p = db.prepareStatement(sql)) {
			p.setObject(1, networkId);
			p.setObject(2, networkId);
			p.setObject(3, networkId);
			p.setObject(4, networkId);
			p.setString(5, accessKey);
			p.setString(6, accessKey);
			try (ResultSet rs = p.executeQuery()) {
				return rs.next();
			}
		}
	}

	@Override
	public Set<UUID> filterNetworksByKey(Collection<UUID> networkIds, String accessKey) throws SQLException {
		Set<UUID> granted = new HashSet<>();
		if (accessKey == null || accessKey.isEmpty() || networkIds == null || networkIds.isEmpty())
			return granted;

		// Inline the candidate UUIDs (validated UUID objects, injection-safe) and parameterize the key.
		StringBuilder idList = new StringBuilder();
		for (UUID id : networkIds) {
			if (idList.length() > 0)
				idList.append(',');
			idList.append('\'').append(id.toString()).append('\'');
		}

		// One query: map each candidate to the parent folders that could carry its key — its own parent
		// folder AND the parent folder of any same-owner live NETWORK shortcut pointing at it (seed_folders)
		// — walk those folder chains up (chain), then union networks matched by their own key with networks
		// matched by an ancestor folder's key. The shortcut seed is the batch twin of isNetworkKeyValid's
		// shortcut resolution (issue #133/#137); the same-owner guard (sc.owneruuid = n.owneruuid) blocks
		// cross-owner escalation.
		String sql = "WITH RECURSIVE seed AS ("
				+ "  SELECT n.\"UUID\" AS net_id, n.parent AS folder_id, n.owneruuid AS net_owner FROM network n"
				+ "   WHERE n.\"UUID\" IN (" + idList + ")"
				+ "), seed_folders AS ("
				+ "  SELECT net_id, folder_id FROM seed WHERE folder_id IS NOT NULL"
				+ "  UNION"
				+ "  SELECT sd.net_id, sc.parent"
				+ "    FROM seed sd JOIN shortcut sc"
				+ "      ON sc.target = sd.net_id AND sc.target_type = 'NETWORK'"
				+ "     AND sc.is_deleted = false AND sc.owneruuid = sd.net_owner"
				+ "   WHERE sc.parent IS NOT NULL"
				+ "), chain AS ("
				+ "  SELECT sf.net_id, f.parent, f.access_key_is_on, f.access_key"
				+ "    FROM seed_folders sf JOIN folder f ON f.\"UUID\" = sf.folder_id AND f.is_deleted = false"
				+ "  UNION"
				+ "  SELECT c.net_id, pf.parent, pf.access_key_is_on, pf.access_key"
				+ "    FROM folder pf JOIN chain c ON pf.\"UUID\" = c.parent"
				+ "   WHERE pf.is_deleted = false"
				+ ") "
				+ "SELECT s.net_id FROM seed s JOIN network n ON n.\"UUID\" = s.net_id"
				+ "  WHERE n.access_key_is_on AND n.access_key = ? "
				+ "UNION "
				+ "SELECT net_id FROM chain WHERE access_key_is_on AND access_key = ?";

		try (PreparedStatement p = db.prepareStatement(sql)) {
			p.setString(1, accessKey);
			p.setString(2, accessKey);
			try (ResultSet rs = p.executeQuery()) {
				while (rs.next()) {
					granted.add((UUID) rs.getObject(1));
				}
			}
		}
		return granted;
	}
}
