package org.ndexbio.server.migration.v3;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.models.dao.FolderDAO;
import org.ndexbio.common.models.dao.NetworkDAO;
import org.ndexbio.common.models.dao.ShortcutDAO;
import org.ndexbio.common.models.dao.postgresql.PostgresFolderDAO;
import org.ndexbio.common.models.dao.postgresql.PostgresNetworkDAO;
import org.ndexbio.common.models.dao.postgresql.PostgresShortcutDAO;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.common.solr.FolderIndexManager;
import org.ndexbio.common.solr.GlobalNetworkIndexManager;
import org.ndexbio.common.solr.ShortcutIndexManager;
import org.ndexbio.common.solr.SolrObjectFactory;
import org.ndexbio.common.util.NdexUUIDFactory;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.*;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.Configuration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class V3Migrator implements AutoCloseable {

	private static final Logger logger = Logger.getLogger(V3Migrator.class.getName());

	private static final Map<String, Integer> PERM_RANK = Map.of(
			"READ", 1, "WRITE", 2, "ADMIN", 3
	);

	private final Connection db;
	private final ObjectMapper mapper;
	private final TypeReference<Map<String, Object>> mapTypeRef;
	private final SolrObjectFactory solrObjectFactory;

	// --- Migration stats ---
	private int networkSetsProcessed = 0;
	private int groupsProcessed = 0;
	private int networksProcessed = 0;
	private int usersProcessed = 0;
	private int shortcutsCreated = 0;
	private int permissionsFlattened = 0;

	public V3Migrator() throws Exception {
		Configuration configuration = Configuration.createInstance();
		NdexDatabase.createNdexDatabase(configuration.getDBURL(), configuration.getDBUser(),
				configuration.getDBPasswd(), 10);
		this.db = NdexDatabase.getInstance().getConnection();
		this.db.setAutoCommit(false);
		this.mapper = new ObjectMapper();
		this.solrObjectFactory = configuration.getSolrObjectFactory();
		this.mapTypeRef = new TypeReference<>() {};
	}

	/**
	 * Run the full v3 migration in order.
	 */
	public void run() throws Exception {
		try (UserDAO userDAO = new UserDAO();
			 FolderDAO folderDAO = new PostgresFolderDAO();
			 ShortcutDAO shortcutDAO = new PostgresShortcutDAO();
			 NetworkDAO networkDAO = new PostgresNetworkDAO()) {

			DaoSet dao = new DaoSet(userDAO, folderDAO, shortcutDAO, networkDAO);

			logger.info("=== V3 Migration Starting ===");

			logger.info("--- Phase 1: Network Sets -> Folders + Shortcuts ---");
			processNetworkSets(dao);

			logger.info("--- Phase 2: Groups -> Folders + Shortcuts + Flattened Permissions ---");
			processGroups(dao);

			logger.info("--- Phase 3: Networks -> Verify in Home Folder + Migrate Access Keys ---");
			processNetworks(dao);

			logger.info("--- Phase 4: Users -> Set search opt-in default ---");
			processUsers();

			logger.info("=== V3 Migration Complete ===");
			logger.info(String.format(
					"Stats: networkSets=%d, groups=%d, networks=%d, users=%d, shortcuts=%d, permsFlattened=%d",
					networkSetsProcessed, groupsProcessed, networksProcessed,
					usersProcessed, shortcutsCreated, permissionsFlattened));
		}
	}

	// ========================================================================
	// Phase 1: Network Sets -> Folders + Shortcuts
	// ========================================================================

	public void processNetworkSets(DaoSet dao) throws Exception {
		String sql = "SELECT creation_time, modification_time, \"UUID\", owner_id, name, description, "
				+ "other_attributes, access_key, access_key_is_on, showcased, ndexdoi "
				+ "FROM network_set WHERE is_deleted = false";

		try (PreparedStatement pst = db.prepareStatement(sql);
			 ResultSet rs = pst.executeQuery()) {

			while (rs.next()) {
				UUID setId = (UUID) rs.getObject(3);
				try {
					NetworkSet set = readNetworkSet(rs, setId);
					set.setNetworks(loadNetworkSetMembers(setId));

					User owner = set.getOwnerId() != null
							? dao.userDAO.getUserById(set.getOwnerId(), false, false) : null;

					NdexFolder folder = mapNetworkSetToFolder(set, owner);
					String accessKey = rs.getString(8);
					boolean accessKeyIsOn = rs.getBoolean(9) && !rs.wasNull();

					migrateNetworkSetToFolder(set, folder, accessKey, accessKeyIsOn, dao, owner);
					networkSetsProcessed++;
				} catch (Exception e) {
					db.rollback();
					logger.log(Level.SEVERE, "Failed to migrate network set " + setId, e);
				}
			}
		}
		logger.info("Processed " + networkSetsProcessed + " network sets.");
	}

	private NetworkSet readNetworkSet(ResultSet rs, UUID setId) throws SQLException, IOException {
		NetworkSet set = new NetworkSet();
		set.setCreationTime(rs.getTimestamp(1));
		set.setModificationTime(rs.getTimestamp(2));
		set.setExternalId(setId);
		set.setOwnerId((UUID) rs.getObject(4));
		set.setName(rs.getString(5));
		set.setDescription(rs.getString(6));

		String propStr = rs.getString(7);
		if (propStr != null) {
			set.setProperties(mapper.readValue(propStr, mapTypeRef));
		}

		boolean showcased = rs.getBoolean(10);
		if (!rs.wasNull()) set.setShowcased(showcased);

		String doi = rs.getString(11);
		if (doi != null) set.setDoi(doi);

		return set;
	}

	private List<UUID> loadNetworkSetMembers(UUID setId) throws SQLException {
		List<UUID> networks = new ArrayList<>();
		String sql = "SELECT nm.network_id FROM network_set_member nm "
				+ "JOIN network n ON n.\"UUID\" = nm.network_id "
				+ "WHERE nm.set_id = ? AND n.is_deleted = false";
		try (PreparedStatement pst = db.prepareStatement(sql)) {
			pst.setObject(1, setId);
			try (ResultSet rs = pst.executeQuery()) {
				while (rs.next()) {
					networks.add((UUID) rs.getObject(1));
				}
			}
		}
		return networks;
	}

	private void migrateNetworkSetToFolder(NetworkSet set, NdexFolder folder, String accessKey,
										   boolean accessKeyIsOn, DaoSet dao, User owner) throws Exception {
		UUID ownerId = set.getOwnerId();

		// Create the folder
		dao.folderDAO.createFolder(folder.getExternalId(), ownerId, null,
				folder.getName(), folder.getDescription());

		// Set access key
		setAccessKey("folder", folder.getExternalId(), accessKey, accessKeyIsOn);

		// TODO: migrate network_set permissions to folder_permission if applicable
		// migrateNetworkSetPermissions(set.getExternalId(), folder.getExternalId());

		// Index folder in Solr
		try (FolderIndexManager fim = solrObjectFactory.getFolderIndexManager()) {
			VisibilityType vis = determineFolderVisibility(set);
			fim.createIndex(folder, vis, null, null);
		}

		// Create shortcuts for each network in the set
		try (ShortcutIndexManager sim = solrObjectFactory.getShortcutIndexManager()) {
			for (UUID networkId : set.getNetworks()) {
				createNetworkShortcut(networkId, folder.getExternalId(), ownerId, owner, dao, sim);
			}
		}

		db.commit();
	}

	// ========================================================================
	// Phase 2: Groups -> Folders + Shortcuts + Flattened Permissions
	// ========================================================================

	public void processGroups(DaoSet dao) throws Exception {
		String sql = "SELECT \"UUID\", creation_time, modification_time, group_name, "
				+ "description, other_attributes, image_url, website_url "
				+ "FROM ndex_group WHERE is_deleted = false";

		try (PreparedStatement pst = db.prepareStatement(sql);
			 ResultSet rs = pst.executeQuery()) {

			while (rs.next()) {
				UUID groupId = (UUID) rs.getObject(1);
				try {
					migrateGroup(rs, groupId, dao);
					groupsProcessed++;
				} catch (Exception e) {
					db.rollback();
					logger.log(Level.SEVERE, "Failed to migrate group " + groupId, e);
				}
			}
		}
		logger.info("Processed " + groupsProcessed + " groups.");
	}

	private void migrateGroup(ResultSet rs, UUID groupId, DaoSet dao) throws Exception {
		// Find the group owner (admin user)
		UUID ownerId = findGroupOwner(groupId);
		if (ownerId == null) {
			logger.warning("Group " + groupId + " has no admin user, skipping.");
			return;
		}
		User owner = dao.userDAO.getUserById(ownerId, false, false);

		// Create folder from group
		NdexFolder folder = new NdexFolder();
		UUID folderId = NdexUUIDFactory.INSTANCE.createNewNDExUUID();
		folder.setExternalId(folderId);
		folder.setName(rs.getString(4));         // group_name
		folder.setDescription(rs.getString(5));  // description
		folder.setCreationTime(rs.getTimestamp(2));
		folder.setModificationTime(rs.getTimestamp(3));
		if (owner != null) folder.setOwner(owner.getUserName());

		dao.folderDAO.createFolder(folderId, ownerId, null, folder.getName(), folder.getDescription());

		// Load group members
		List<GroupMember> members = loadGroupMembers(groupId);

		// Grant READ on the folder to all group members (except owner, who has ADMIN implicitly)
		List<String> folderUserReads = new ArrayList<>();
		for (GroupMember member : members) {
			if (!member.userId.equals(ownerId)) {
				addFolderPermission(folderId, member.userId, "READ");
				String username = getUsernameById(member.userId, dao);
				if (username != null) folderUserReads.add(username);
			}
		}

		// Index folder with member read permissions
		try (FolderIndexManager fim = solrObjectFactory.getFolderIndexManager()) {
			fim.createIndex(folder, VisibilityType.PRIVATE, folderUserReads, null);
		}

		// Get networks this group has access to
		List<GroupNetworkEntry> groupNetworks = loadGroupNetworks(groupId);

		// Create shortcuts in the folder for each network
		try (ShortcutIndexManager sim = solrObjectFactory.getShortcutIndexManager()) {
			for (GroupNetworkEntry entry : groupNetworks) {
				createNetworkShortcut(entry.networkId, folderId, ownerId, owner, dao, sim);
			}
		}

		// Flatten group permissions onto individual member users
		for (GroupNetworkEntry entry : groupNetworks) {
			for (GroupMember member : members) {
				flattenPermission(member.userId, entry.networkId, entry.permission);
			}
		}

		db.commit();
	}

	private UUID findGroupOwner(UUID groupId) throws SQLException {
		String sql = "SELECT user_id FROM ndex_group_user "
				+ "WHERE group_id = ? AND is_admin = true LIMIT 1";
		try (PreparedStatement pst = db.prepareStatement(sql)) {
			pst.setObject(1, groupId);
			try (ResultSet rs = pst.executeQuery()) {
				return rs.next() ? (UUID) rs.getObject(1) : null;
			}
		}
	}

	private List<GroupNetworkEntry> loadGroupNetworks(UUID groupId) throws SQLException {
		List<GroupNetworkEntry> entries = new ArrayList<>();
		String sql = "SELECT gnm.network_id, gnm.permission_type "
				+ "FROM group_network_membership gnm "
				+ "JOIN network n ON n.\"UUID\" = gnm.network_id "
				+ "WHERE gnm.group_id = ? AND n.is_deleted = false";
		try (PreparedStatement pst = db.prepareStatement(sql)) {
			pst.setObject(1, groupId);
			try (ResultSet rs = pst.executeQuery()) {
				while (rs.next()) {
					entries.add(new GroupNetworkEntry(
							(UUID) rs.getObject(1),
							rs.getString(2)
					));
				}
			}
		}
		return entries;
	}

	private List<GroupMember> loadGroupMembers(UUID groupId) throws SQLException {
		List<GroupMember> members = new ArrayList<>();
		String sql = "SELECT user_id, is_admin FROM ndex_group_user WHERE group_id = ?";
		try (PreparedStatement pst = db.prepareStatement(sql)) {
			pst.setObject(1, groupId);
			try (ResultSet rs = pst.executeQuery()) {
				while (rs.next()) {
					members.add(new GroupMember(
							(UUID) rs.getObject(1),
							rs.getBoolean(2)
					));
				}
			}
		}
		return members;
	}

	/**
	 * Flatten a group permission onto an individual user.
	 * Only upgrades — never downgrades an existing permission.
	 * ADMIN from group maps to WRITE on the individual level.
	 */
	private void flattenPermission(UUID userId, UUID networkId, String groupPerm) throws SQLException {
		// Map ADMIN -> WRITE for individual permissions
		String effectivePerm = "ADMIN".equals(groupPerm) ? "WRITE" : groupPerm;

		// Check existing individual permission
		String existing = getUserNetworkPermission(userId, networkId);

		if (existing != null && permRank(existing) >= permRank(effectivePerm)) {
			return; // existing is same or higher, skip
		}

		if (existing != null) {
			// Upgrade existing permission
			String sql = "UPDATE user_network_membership SET permission_type = ?::ndex_permission_type "
					+ "WHERE user_id = ? AND network_id = ?";
			try (PreparedStatement pst = db.prepareStatement(sql)) {
				pst.setString(1, effectivePerm);
				pst.setObject(2, userId);
				pst.setObject(3, networkId);
				pst.executeUpdate();
			}
		} else {
			// Insert new permission
			String sql = "INSERT INTO user_network_membership (user_id, network_id, permission_type) "
					+ "VALUES (?, ?, ?::ndex_permission_type)";
			try (PreparedStatement pst = db.prepareStatement(sql)) {
				pst.setObject(1, userId);
				pst.setObject(2, networkId);
				pst.setString(3, effectivePerm);
				pst.executeUpdate();
			}
		}
		permissionsFlattened++;
	}

	private String getUserNetworkPermission(UUID userId, UUID networkId) throws SQLException {
		String sql = "SELECT permission_type FROM user_network_membership "
				+ "WHERE user_id = ? AND network_id = ?";
		try (PreparedStatement pst = db.prepareStatement(sql)) {
			pst.setObject(1, userId);
			pst.setObject(2, networkId);
			try (ResultSet rs = pst.executeQuery()) {
				return rs.next() ? rs.getString(1) : null;
			}
		}
	}

	private int permRank(String perm) {
		return PERM_RANK.getOrDefault(perm, 0);
	}

	private void addFolderPermission(UUID folderId, UUID userId, String permission) throws SQLException {
		String sql = "INSERT INTO folder_permission (folder_id, user_id, permission) VALUES (?, ?, ?) "
				+ "ON CONFLICT (folder_id, user_id) DO UPDATE SET permission = "
				+ "CASE WHEN EXCLUDED.permission > folder_permission.permission "
				+ "THEN EXCLUDED.permission ELSE folder_permission.permission END";
		try (PreparedStatement pst = db.prepareStatement(sql)) {
			pst.setObject(1, folderId);
			pst.setObject(2, userId);
			pst.setString(3, permission);
			pst.executeUpdate();
		}
	}

	// ========================================================================
	// Phase 3: Networks -> Home Folder + Access Keys
	// ========================================================================

	public void processNetworks(DaoSet dao) throws Exception {
		String sql = "SELECT \"UUID\", owneruuid, \"owner\", visibility "
				+ "FROM network WHERE is_deleted = false";

		try (PreparedStatement pst = db.prepareStatement(sql);
			 ResultSet rs = pst.executeQuery()) {

			while (rs.next()) {
				UUID networkId = (UUID) rs.getObject(1);
				try {
					UUID ownerId = (UUID) rs.getObject(2);
					String ownerName = rs.getString(3);
					String visibility = rs.getString(4);

					// Rebuild Solr index for this network with current permissions
					reindexNetwork(networkId, ownerId, ownerName, visibility, dao);

					networksProcessed++;
					if (networksProcessed % 500 == 0) {
						logger.info("Reindexed " + networksProcessed + " networks so far...");
					}
				} catch (Exception e) {
					logger.log(Level.SEVERE, "Failed to reindex network " + networkId, e);
				}
			}
		}
		logger.info("Reindexed " + networksProcessed + " networks total.");
	}

	/**
	 * Rebuild the Solr index for a single network using the new permission model.
	 * Reads user_network_membership to build the permission map for the index.
	 */
	private void reindexNetwork(UUID networkId, UUID ownerId, String ownerName,
								String visibility, DaoSet dao) throws Exception {
		NetworkSummary ns = dao.networkDAO.getNetworkSummaryById(networkId);
		if (ns == null) return;

		VisibilityType vis = VisibilityType.valueOf(visibility);

		// Gather individual user permissions (including flattened group perms from phase 2)
		Map<String, String> userPerms = loadNetworkUserPermissions(networkId);

		// Split into read and edit collections for Solr index
		List<String> userReads = new ArrayList<>();
		List<String> userEdits = new ArrayList<>();
		for (Map.Entry<String, String> entry : userPerms.entrySet()) {
			String userId = entry.getKey();
			// Look up username for this userId
			String username = getUsernameById(UUID.fromString(userId), dao);
			if (username == null) continue;

			switch (entry.getValue()) {
				case "READ":
					userReads.add(username);
					break;
				case "WRITE":
				case "ADMIN":
					userEdits.add(username);
					break;
			}
		}

		try (GlobalNetworkIndexManager nim = solrObjectFactory.getGlobalNetworkIndexManager()) {
			// Delete old index entry from both cores, then recreate
			try { nim.delete(networkId.toString(), VisibilityType.PRIVATE); } catch (Exception ignored) {}
			try { nim.delete(networkId.toString(), VisibilityType.PUBLIC); } catch (Exception ignored) {}
			nim.createIndex(ns, vis, userReads, userEdits);
		}
	}

	private String getUsernameById(UUID userId, DaoSet dao) {
		try {
			User u = dao.userDAO.getUserById(userId, false, false);
			return u != null ? u.getUserName() : null;
		} catch (Exception e) {
			logger.warning("Could not look up username for " + userId);
			return null;
		}
	}

	/**
	 * Load all user permissions for a network from user_network_membership.
	 * Returns map of userId.toString() -> permission string.
	 */
	private Map<String, String> loadNetworkUserPermissions(UUID networkId) throws SQLException {
		Map<String, String> perms = new HashMap<>();
		String sql = "SELECT user_id, permission_type FROM user_network_membership WHERE network_id = ?";
		try (PreparedStatement pst = db.prepareStatement(sql)) {
			pst.setObject(1, networkId);
			try (ResultSet rs = pst.executeQuery()) {
				while (rs.next()) {
					perms.put(rs.getObject(1).toString(), rs.getString(2));
				}
			}
		}
		return perms;
	}

	// ========================================================================
	// Phase 4: Users -> Search Opt-in
	// ========================================================================

	public void processUsers() throws Exception {
		// User search opt-in: not yet implemented, ndex_user schema changes pending.
		// TODO: add is_searchable column to ndex_user and set default to false
		logger.info("User search opt-in migration skipped (schema not ready).");
	}

	// ========================================================================
	// Shared helpers
	// ========================================================================

	/**
	 * Create a shortcut pointing to a network inside a folder, and index it in Solr.
	 */
	private void createNetworkShortcut(UUID networkId, UUID parentFolderId, UUID ownerId,
									   User owner, DaoSet dao, ShortcutIndexManager sim) throws Exception {
		NetworkSummary ns = dao.networkDAO.getNetworkSummaryById(networkId);
		if (ns == null) {
			logger.warning("Network " + networkId + " not found, skipping shortcut.");
			return;
		}

		UUID shortcutId = NdexUUIDFactory.INSTANCE.createNewNDExUUID();

		dao.shortcutDAO.createShortcut(shortcutId, ownerId, parentFolderId,
				ns.getName(), networkId, FileType.NETWORK);

		NdexShortcut shortcut = new NdexShortcut();
		shortcut.setExternalId(shortcutId);
		shortcut.setOwner(owner != null ? owner.getUserName() : null);
		shortcut.setName(ns.getName());
		shortcut.setParent(parentFolderId);
		shortcut.setTarget(networkId);
		shortcut.setTargetType(FileType.NETWORK);
		shortcut.setCreationTime(ns.getCreationTime());
		shortcut.setModificationTime(ns.getModificationTime());
		shortcut.setIsDeleted(false);

		// Inherit visibility from target network
		VisibilityType vis = VisibilityType.valueOf(ns.getVisibility().name());
		sim.createIndex(shortcut, vis, null, null);
		shortcutsCreated++;
	}

	/**
	 * Set access key on a folder or network via direct SQL.
	 */
	private void setAccessKey(String table, UUID entityId, String accessKey, boolean accessKeyIsOn)
			throws SQLException {
		if (accessKey == null || accessKey.isBlank()) return;

		String sql = "UPDATE " + table + " SET access_key = ?, access_key_is_on = ? WHERE \"UUID\" = ?";
		try (PreparedStatement pst = db.prepareStatement(sql)) {
			pst.setString(1, accessKey);
			pst.setBoolean(2, accessKeyIsOn);
			pst.setObject(3, entityId);
			pst.executeUpdate();
		}
	}

	public NdexFolder mapNetworkSetToFolder(NetworkSet set, User owner) {
		NdexFolder folder = new NdexFolder();
		folder.setExternalId(set.getExternalId());
		folder.setName(set.getName());
		folder.setDescription(set.getDescription());
		folder.setCreationTime(set.getCreationTime());
		folder.setModificationTime(set.getModificationTime());
		if (owner != null) folder.setOwner(owner.getUserName());
		return folder;
	}

	/**
	 * Determine folder visibility based on the original network set.
	 * TODO: adjust logic based on whether network_set had a visibility column.
	 * For now defaults to PRIVATE.
	 */
	private VisibilityType determineFolderVisibility(NetworkSet set) {
		// network_set table doesn't have a visibility column, so default to PRIVATE
		return VisibilityType.PRIVATE;
	}

	@Override
	public void close() throws Exception {
		if (db != null && !db.isClosed()) {
			db.close();
		}
		NdexDatabase.close();
	}

	// ========================================================================
	// Inner helper classes
	// ========================================================================

	static class DaoSet {
		final UserDAO userDAO;
		final FolderDAO folderDAO;
		final ShortcutDAO shortcutDAO;
		final NetworkDAO networkDAO;

		DaoSet(UserDAO u, FolderDAO f, ShortcutDAO s, NetworkDAO n) {
			this.userDAO = u;
			this.folderDAO = f;
			this.shortcutDAO = s;
			this.networkDAO = n;
		}
	}

	static class GroupNetworkEntry {
		final UUID networkId;
		final String permission;

		GroupNetworkEntry(UUID networkId, String permission) {
			this.networkId = networkId;
			this.permission = permission;
		}
	}

	static class GroupMember {
		final UUID userId;
		final boolean isAdmin;

		GroupMember(UUID userId, boolean isAdmin) {
			this.userId = userId;
			this.isAdmin = isAdmin;
		}
	}

	// ========================================================================
	// Main entry point
	// ========================================================================

	public static void main(String[] args) {
		try (V3Migrator migrator = new V3Migrator()) {
			migrator.run();
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Migration failed", e);
			System.exit(1);
		}
	}
}