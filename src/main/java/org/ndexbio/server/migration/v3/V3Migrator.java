package org.ndexbio.server.migration.v3;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.apache.solr.client.solrj.SolrServerException;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.models.dao.DAOFactory;
import org.ndexbio.common.models.dao.FolderDAO;
import org.ndexbio.common.models.dao.NetworkDAO;
import org.ndexbio.common.models.dao.ShortcutDAO;
import org.ndexbio.common.models.dao.postgresql.*;
import org.ndexbio.common.persistence.CX2NetworkLoader;
import org.ndexbio.common.solr.*;
import org.ndexbio.common.util.NdexUUIDFactory;
import org.ndexbio.cx2.aspect.element.core.CxAttributeDeclaration;
import org.ndexbio.cx2.aspect.element.core.CxNetworkAttribute;
import org.ndexbio.cx2.aspect.element.core.CxNode;
import org.ndexbio.cx2.aspect.element.core.DeclarationEntry;
import org.ndexbio.cxio.aspects.datamodels.ATTRIBUTE_DATA_TYPE;
import org.ndexbio.cxio.aspects.datamodels.NetworkAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NodeAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NodesElement;
import org.ndexbio.cxio.core.AspectIterator;
import org.ndexbio.model.cx.FunctionTermElement;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.*;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.Configuration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ndexbio.task.NdexServerQueue;
import org.ndexbio.task.SolrIndexScope;
import org.ndexbio.task.SolrTaskDeleteFile;
import org.ndexbio.task.SolrTaskRebuildFileIdx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class V3Migrator implements AutoCloseable {


	protected final static Logger logger = LoggerFactory.getLogger(V3Migrator.class.getSimpleName());

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


	//todo if duplicate group admins contain preferred user, make them folder admin, if no preferred take first. all others have read/write
	//todo v2 endpoints should correspond network sets with folders
	//todo
	private List<String> preferredUsers = List.of("dexterpratt", "churas");
	private List<UUID> preferredUserIds;
	private final DAOFactory daoFactory;

	public V3Migrator() throws Exception {
		Configuration configuration = Configuration.createInstance();
		NdexDatabase.createNdexDatabase(configuration.getDBURL(), configuration.getDBUser(),
				configuration.getDBPasswd(), 10);
		this.db = NdexDatabase.getInstance().getConnection();
		this.db.setAutoCommit(false);
		this.mapper = new ObjectMapper();
		this.solrObjectFactory = Configuration.getInstance().getSolrObjectFactory();//new CachingSolrObjectFactoryImpl(configuration.getSolrURL());
		//this.daoFactory = new CachingPostgresDAOFactory();
		this.daoFactory = Configuration.getInstance().getDAOFactory();

		this.mapTypeRef = new TypeReference<>() {};
	}

	/**
	 * Run the full v3 migration in order.
	 */
	public void run() throws Exception {
		try (UserDAO userDAO = daoFactory.getUserDAO();
			 FolderDAO folderDAO = daoFactory.getFolderDAO();
			 ShortcutDAO shortcutDAO = daoFactory.getShortcutDAO();
			 NetworkDAO networkDAO = daoFactory.getNetworkDAO()) {

			DaoSet dao = new DaoSet(userDAO, folderDAO, shortcutDAO, networkDAO);

			logger.info("=== V3 Migration Starting ===");

			logger.info("--- Setup: Loading preferred Owners");
			setupCoresAndPreferredUsers(userDAO);

			logger.info("--- Phase 1: Network Sets -> Folders + Shortcuts ---");
			processNetworkSets(dao);

			logger.info("--- Phase 2: Groups -> Folders + Shortcuts + Flattened Permissions ---");
			processGroups(dao);

			logger.info("--- Phase 3: Networks -> Verify in Home Folder + Migrate Access Keys ---");
			processNetworks(dao);

			//logger.info("--- Phase 4: Users -> Set search opt-in default ---");
			//processUsers();

			logger.info("=== V3 Migration Complete ===");
			logger.info(String.format(
					"Stats: networkSets=%d, groups=%d, networks=%d, users=%d, shortcuts=%d, permsFlattened=%d",
					networkSetsProcessed, groupsProcessed, networksProcessed,
					usersProcessed, shortcutsCreated, permissionsFlattened));
		}
	}

	public void setupCoresAndPreferredUsers(UserDAO userDAO){
		preferredUserIds = new ArrayList<>();
		for (String username: preferredUsers){
            try {
                User user = userDAO.getUserByAccountName(username, false, false);
				preferredUserIds.add(user.getExternalId());
            } catch (NdexException | IOException | SQLException e) {
                throw new RuntimeException(e);
            }
        }
		try (FolderIndexManager indexManager = solrObjectFactory.getFolderIndexManager()){
			indexManager.createCoreIfNeeded();
		} catch (SolrServerException | IOException | NdexException e) {
            throw new RuntimeException(e);
        }

    }

	// ========================================================================
	// Phase 1: Network Sets -> Folders + Shortcuts
	// ========================================================================

	public void processNetworkSets(DaoSet dao) throws Exception {
		int totalSets = 0;
		try (PreparedStatement countPst = db.prepareStatement("SELECT COUNT(*) FROM network_set WHERE is_deleted = false");
			 ResultSet countRs = countPst.executeQuery()) {
			if (countRs.next()) totalSets = countRs.getInt(1);
		}
		logger.info("Found {} network sets to migrate.", totalSets);

		String sql = "SELECT creation_time, modification_time, \"UUID\", owner_id, name, description, "
				+ "other_attributes, access_key, access_key_is_on, showcased, ndexdoi "
				+ "FROM network_set WHERE is_deleted = false";

		try (PreparedStatement pst = db.prepareStatement(sql);
			 ResultSet rs = pst.executeQuery();
			 FolderIndexManager fim = solrObjectFactory.getFolderIndexManager();
			 ShortcutIndexManager sim = solrObjectFactory.getShortcutIndexManager()) {

			while (rs.next()) {
				UUID setId = (UUID) rs.getObject(3);
				try {
					NetworkSet set = readNetworkSet(rs, setId);
					logger.info("Starting for network set {}", setId);
					set.setNetworks(loadNetworkSetMembers(setId));

					User owner = set.getOwnerId() != null
							? dao.userDAO.getUserById(set.getOwnerId(), false, false) : null;

					NdexFolder folder = mapNetworkSetToFolder(set, owner);
					String accessKey = rs.getString(8);
					boolean accessKeyIsOn = rs.getBoolean(9) && !rs.wasNull();

					migrateNetworkSetToFolder(set, folder, accessKey, accessKeyIsOn, dao, owner, fim, sim);
					networkSetsProcessed++;
					logger.info("[Phase 1] {}/{} ({}%) - set {}",
							networkSetsProcessed, totalSets,
							(networkSetsProcessed * 100) / totalSets, setId);
				} catch (Exception e) {
					db.rollback();
					logger.info("Failed to migrate network set " + setId, e);
					throw new RuntimeException(e);
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
										   boolean accessKeyIsOn, DaoSet dao, User owner, FolderIndexManager fim,
										   ShortcutIndexManager sim) throws Exception {
		UUID ownerId = set.getOwnerId();

		// Create the folder
		logger.info("[{}] Creating db entry", set.getExternalId());
		dao.folderDAO.createFolder(folder.getExternalId(), ownerId, null,
				folder.getName(), folder.getDescription());
		dao.folderDAO.commit();

		//logger.info("[{}] Folder created", set.getExternalId());

		// Set access key
		setAccessKey("folder", folder.getExternalId(), accessKey, accessKeyIsOn);
		VisibilityType vis = determineFolderVisibility(set);

		logger.info("[{}] Creating index", set.getExternalId());
		// Index folder in Solr
		fim.createIndex(folder, vis, null, null);
		logger.info("[{}] Creating shortcuts for {} networks", set.getExternalId(), set.getNetworks().size());
		for (UUID networkId : set.getNetworks()) {
			try {
				createNetworkShortcut(networkId, folder.getExternalId(), ownerId, owner, dao, sim, null);
			} catch (Exception e){
				logger.info("Failed to create shortcut for network {} in set {}", networkId, set.getExternalId());
				throw new RuntimeException(e);
			}
		}
		dao.shortcutDAO.commit();

	}

	// ========================================================================
	// Phase 2: Groups -> Folders + Shortcuts + Flattened Permissions
	// ========================================================================

	public void processGroups(DaoSet dao) throws Exception {
		int totalGroups = 0;
		try (PreparedStatement countPst = db.prepareStatement("SELECT COUNT(*) FROM ndex_group WHERE is_deleted = false");
			 ResultSet countRs = countPst.executeQuery()) {
			if (countRs.next()) totalGroups = countRs.getInt(1);
		}
		logger.info("Found {} groups to migrate.", totalGroups);

		String sql = "SELECT \"UUID\", creation_time, modification_time, group_name, "
				+ "description, other_attributes, image_url, website_url "
				+ "FROM ndex_group WHERE is_deleted = false";

		try (PreparedStatement pst = db.prepareStatement(sql);
			 ResultSet rs = pst.executeQuery();
			 FolderIndexManager fim = solrObjectFactory.getFolderIndexManager();
			 ShortcutIndexManager sim = solrObjectFactory.getShortcutIndexManager()) {

			while (rs.next()) {
				UUID groupId = (UUID) rs.getObject(1);
				logger.info("Starting for group {}", groupId);
				try {
					migrateGroup(rs, groupId, dao, fim, sim);
					groupsProcessed++;
					logger.info("[Phase 2] {}/{} ({}%) - group {}",
							groupsProcessed, totalGroups,
							(groupsProcessed * 100) / totalGroups, groupId);
				} catch (Exception e) {
					db.rollback();
					logger.info("Failed to migrate group " + groupId, e);
				}
			}
		}
		logger.info("Processed " + groupsProcessed + " groups.");
	}

	private void migrateGroup(ResultSet rs, UUID groupId, DaoSet dao, FolderIndexManager fim, ShortcutIndexManager sim) throws Exception {
		// Find the group owner (admin user)

		UUID ownerId = findGroupOwner(groupId);
		if (ownerId == null) {
			logger.info("Group " + groupId + " has no admin user, skipping.");
			return;
		}
		User owner = dao.userDAO.getUserById(ownerId, false, false);

		// Create folder from group
		NdexFolder folder = new NdexFolder();
		//UUID folderId = NdexUUIDFactory.INSTANCE.createNewNDExUUID();
		folder.setExternalId(groupId);
		String groupName = rs.getString(4);
		if (groupName == null || groupName.isBlank()) {
			groupName = "(unnamed group)";
		}
		folder.setName(groupName);
		folder.setDescription(rs.getString(5));  // description
		folder.setCreationTime(rs.getTimestamp(2));
		folder.setModificationTime(rs.getTimestamp(3));
		if (owner != null) folder.setOwner(owner.getUserName());

		dao.folderDAO.createFolder(groupId, ownerId, null, groupName, folder.getDescription());
		dao.folderDAO.commit();
		logger.info("[{}] Created group's folder db entry", groupId);
		// Load group members
		List<GroupMember> members = loadGroupMembers(groupId);

		// Grant READ on the folder to all group members (except owner, who has ADMIN implicitly)
		List<String> folderUserReads = new ArrayList<>();
		logger.info("[{}] Adding member permissions", groupId);

		for (GroupMember member : members) {
			if (!member.userId.equals(ownerId)) {
				addFolderPermission(groupId, member.userId, "READ");
				String username = getUsernameById(member.userId, dao);
				if (username != null) folderUserReads.add(username);
			}
		}


		logger.info("[{}] Indexing folder", groupId);
		// Index folder with member read permissions
		fim.createIndex(folder, VisibilityType.PRIVATE, folderUserReads, null);
		// Get networks this group has access to
		List<GroupNetworkEntry> groupNetworks = loadGroupNetworks(groupId);

		logger.info("[{}] Creating {} network shortcuts.", groupId, groupNetworks.size());
		// Create shortcuts in the folder for each network
		for (GroupNetworkEntry entry : groupNetworks) {
			createNetworkShortcut(entry.networkId, groupId, ownerId, owner, dao, sim, folderUserReads);
		}



		logger.info("[{}] Flattening permissions.", groupId);
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
				+ "WHERE group_id = ? AND is_admin = true";
		List<UUID> admins = new ArrayList<>();
		try (PreparedStatement pst = db.prepareStatement(sql)) {
			pst.setObject(1, groupId);
			try (ResultSet rs = pst.executeQuery()) {
				while (rs.next()) {
					admins.add((UUID) rs.getObject(1));
				}
			}
		}
		if (admins.isEmpty()) return null;
		if (admins.size() == 1) return admins.get(0);
		return selectGroupOwnerFromList(admins);
	}

	private UUID selectGroupOwnerFromList(List<UUID> adminIds) {
		Set<UUID> adminSet = new HashSet<>(adminIds);
		UUID prefOwner = null;
		for (UUID pref: preferredUserIds){
			if (adminSet.contains(pref)){
				return pref;

			}
		}
        return adminIds.get(0);
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
		int totalNetworks = 0;
		try (PreparedStatement countPst = db.prepareStatement("SELECT COUNT(*) FROM network WHERE is_deleted = false");
			 ResultSet countRs = countPst.executeQuery()) {
			if (countRs.next()) totalNetworks = countRs.getInt(1);
		}
		logger.info("Found {} networks to reindex.", totalNetworks);

		String sql = "SELECT \"UUID\", owneruuid, \"owner\", visibility "
				+ "FROM network WHERE is_deleted = false";

		try (PreparedStatement pst = db.prepareStatement(sql);
			 ResultSet rs = pst.executeQuery();
			 GlobalNetworkIndexManager globalNetworkIndexManager = solrObjectFactory.getGlobalNetworkIndexManager()) {

			while (rs.next()) {
				UUID networkId = (UUID) rs.getObject(1);
				logger.info("Starting for {}", networkId);
				try {
					UUID ownerId = (UUID) rs.getObject(2);
					String ownerName = rs.getString(3);
					String visibility = rs.getString(4);

					reindexNetwork(networkId, ownerId, ownerName, visibility, dao,globalNetworkIndexManager);

					networksProcessed++;
					if (networksProcessed % 500 == 0) {
						logger.info("[Phase 3] {}/{} ({}%)",
								networksProcessed, totalNetworks,
								(networksProcessed * 100) / totalNetworks);
					}
				} catch (Exception e) {
					logger.info("Failed to reindex network " + networkId, e);
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
								String visibility, DaoSet dao, GlobalNetworkIndexManager globalNetworkIndexManager) throws Exception {
		NetworkSummary ns = dao.networkDAO.getNetworkSummaryById(networkId);
		if (ns == null) return;

		VisibilityType vis = VisibilityType.valueOf(visibility);
		rebuildNetworkIndex(networkId, false, false, globalNetworkIndexManager,
				dao.networkDAO);

		//NdexServerQueue.INSTANCE.addSystemTask(t);
		//t.run();

		/*
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

		 */


		/*
		try (GlobalNetworkIndexManager nim = solrObjectFactory.getGlobalNetworkIndexManager()) {

			// Delete old index entry from both cores, then recreate
			try { nim.delete(networkId.toString(), VisibilityType.PRIVATE); } catch (Exception ignored) {}
			try { nim.delete(networkId.toString(), VisibilityType.PUBLIC); } catch (Exception ignored) {}
			nim.createIndex(ns, vis, userReads, userEdits);
		}

		 */
	}

	private String getUsernameById(UUID userId, DaoSet dao) {
		try {
			User u = dao.userDAO.getUserById(userId, false, false);
			return u != null ? u.getUserName() : null;
		} catch (Exception e) {
			logger.info("Could not look up username for " + userId);
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
	// ============================================loadNetworkUserPermissions============================

	/**
	 * Create a shortcut pointing to a network inside a folder, and index it in Solr.
	 */
	private void createNetworkShortcut(UUID networkId, UUID parentFolderId, UUID ownerId,
									   User owner, DaoSet dao, ShortcutIndexManager sim,
									   List<String> userReads) throws Exception {
		String[] nameAndVis = getNetworkNameAndVisibility(networkId);
		if (nameAndVis == null) {
			logger.info("Network " + networkId + " not found, skipping shortcut.");
			return;
		}
		String netName = nameAndVis[0] != null ? nameAndVis[0] : "(unnamed network)";
		VisibilityType vis = VisibilityType.valueOf(nameAndVis[1]);

		UUID shortcutId = NdexUUIDFactory.INSTANCE.createNewNDExUUID();

		dao.shortcutDAO.createShortcut(shortcutId, ownerId, parentFolderId,
				netName, networkId, FileType.NETWORK);

		NdexShortcut shortcut = new NdexShortcut();
		shortcut.setExternalId(shortcutId);
		shortcut.setOwner(owner != null ? owner.getUserName() : null);
		shortcut.setName(netName);
		shortcut.setParent(parentFolderId);
		shortcut.setTarget(networkId);
		shortcut.setTargetType(FileType.NETWORK);
		//shortcut.setCreationTime(netName);
		//shortcut.setModificationTime(ns.getModificationTime());
		shortcut.setIsDeleted(false);

		//VisibilityType vis = VisibilityType.valueOf(ns.getVisibility().name());
		sim.createIndex(shortcut, vis, userReads, null);
		shortcutsCreated++;
	}
	private String[] getNetworkNameAndVisibility(UUID networkId) throws SQLException {
		String sql = "SELECT name, visibility FROM network WHERE \"UUID\" = ? AND is_deleted = false";
		try (PreparedStatement pst = db.prepareStatement(sql)) {
			pst.setObject(1, networkId);
			try (ResultSet rs = pst.executeQuery()) {
				if (rs.next()) {
					return new String[]{rs.getString(1), rs.getString(2)};
				}
				return null;
			}
		}
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

	private void rebuildNetworkIndex(UUID fileId, boolean createOnly, boolean ignoreCxFiles,
									 GlobalNetworkIndexManager globalNetworkIndexManager,
									 NetworkDAO dao) throws Exception {

		try {
			String id = fileId.toString();
			NetworkSummary summary = dao.getNetworkSummaryById(fileId);
			VisibilityType visibilityType = dao.getNetworkVisibility(fileId);
			SolrIndexScope idxScope;

			if (summary.getNodeCount() >= SingleNetworkSolrIdxManager.AUTOCREATE_THRESHHOLD)
				idxScope = SolrIndexScope.both;
			else
				idxScope = SolrIndexScope.global;

			// drop the old ones.
			if (!createOnly) {
				globalNetworkIndexManager.delete(id, visibilityType);

			}

			// build the solr document obj
			List<Map<Permissions, Collection<String>>> permissionTable = dao
					.getAllMembershipsOnNetwork(fileId);
			Map<Permissions, Collection<String>> userMemberships = permissionTable.get(0);
			globalNetworkIndexManager.prepareIndexDocument(summary, visibilityType,
					userMemberships.get(Permissions.READ), userMemberships.get(Permissions.WRITE));

			String pathPrefix = Configuration.getInstance().getNdexRoot() + "/data/";
			String cx2AspectPath = pathPrefix + id + "/" + CX2NetworkLoader.cx2AspectDirName + "/";
			File attrFile = new File(cx2AspectPath + CxNetworkAttribute.ASPECT_NAME);
			File functionAspectFile = new File(cx2AspectPath + FunctionTermElement.ASPECT_NAME);

			// Always index network attributes (META + ALL behavior)
			if (attrFile.exists() && !ignoreCxFiles) {

				File declFile = new File(cx2AspectPath + CxAttributeDeclaration.ASPECT_NAME);
				ObjectMapper om = new ObjectMapper();

				CxAttributeDeclaration[] declarations = om.readValue(declFile, CxAttributeDeclaration[].class);

				CxNetworkAttribute[] attrs = om.readValue(attrFile, CxNetworkAttribute[].class);
				attrs[0].extendToFullNode(declarations[0].getAttributesInAspect(CxNetworkAttribute.ASPECT_NAME));

				List<String> indexWarnings = globalNetworkIndexManager.addCX2NetworkAttrToIndex(attrs[0]);
				if (!indexWarnings.isEmpty())
					for (String warning : indexWarnings)
						System.err.println("Warning: " + warning);
			}
			else {
				try (AspectIterator<NetworkAttributesElement> it = new AspectIterator<>(id,
						NetworkAttributesElement.ASPECT_NAME, NetworkAttributesElement.class, pathPrefix)) {
					while (it.hasNext()) {
						NetworkAttributesElement e = it.next();
						if (!e.getName().equals(NFSIndexManager.NAME)){
							List<String> indexWarnings = globalNetworkIndexManager.addCXNetworkAttrToIndex(e);
							if (!indexWarnings.isEmpty())
								for (String warning : indexWarnings)
									System.err.println("Warning: " + warning);
						}

					}
				}
			}

			// Always index node attributes and nodes (ALL behavior)
			if (functionAspectFile.exists() && !ignoreCxFiles) {
				ObjectMapper om = new ObjectMapper();

				try (FileInputStream inputStream = new FileInputStream(cx2AspectPath + FunctionTermElement.ASPECT_NAME)) {

					Iterator<FunctionTermElement> it = om.readerFor(FunctionTermElement.class).readValues(inputStream);

					while (it.hasNext()) {
						FunctionTermElement fun = it.next();
						globalNetworkIndexManager.addFunctionTermToIndex(fun);
					}
				}

				processCx2Nodes(cx2AspectPath, om, globalNetworkIndexManager);

			} else {
				try (AspectIterator<FunctionTermElement> it = new AspectIterator<>(fileId.toString(),
						FunctionTermElement.ASPECT_NAME, FunctionTermElement.class, pathPrefix)) {
					while (it.hasNext()) {
						FunctionTermElement fun = it.next();
						globalNetworkIndexManager.addFunctionTermToIndex(fun);
					}
				}

				try (AspectIterator<NodeAttributesElement> it = new AspectIterator<>(fileId.toString(),
						NodeAttributesElement.ASPECT_NAME, NodeAttributesElement.class, pathPrefix)) {
					while (it.hasNext()) {
						NodeAttributesElement e = it.next();
						globalNetworkIndexManager.addCXNodeAttrToIndex(e);
					}
				}

				try (AspectIterator<NodesElement> it = new AspectIterator<>(fileId.toString(), NodesElement.ASPECT_NAME,
						NodesElement.class, pathPrefix)) {
					while (it.hasNext()) {
						NodesElement e = it.next();
						globalNetworkIndexManager.addCXNodeToIndex(e);
					}
				}
			}

			globalNetworkIndexManager.commit(visibilityType);


			try {
				dao.setFlag(fileId, "iscomplete", true);
				dao.commit();
			} catch (SQLException e) {
				throw new NdexException("DB error when setting iscomplete flag: " + e.getMessage(), e);
			}

		} catch (SQLException | IOException | NdexException | SolrServerException e1) {
			e1.printStackTrace();
			try {
				dao.setErrorMessage(fileId, "Failed to create Index on network."
						+ " Cause: " + e1.getMessage());
				dao.commit();
			} catch (Exception e2){
				e2.printStackTrace();
			}
			throw e1;
		}

	}

	private static void processCx2Nodes(String cx2AspectPath, ObjectMapper om, GlobalNetworkIndexManager globalIdx) throws JsonParseException, JsonMappingException, IOException {
		File declFile = new File(cx2AspectPath + CxAttributeDeclaration.ASPECT_NAME);
		if (!declFile.exists())
			return;

		CxAttributeDeclaration[] declarations = om.readValue(declFile,
				CxAttributeDeclaration[].class);

		if ( declarations.length == 0 || ! declarations[0].getDeclarations().containsKey(CxNode.ASPECT_NAME))
			return ;

		Map<String, DeclarationEntry> nodeAttributeDecls = declarations[0].getAttributesInAspect(CxNode.ASPECT_NAME);
		if ( nodeAttributeDecls.size() == 0 )
			return ;

		Map<String, Map.Entry<String,DeclarationEntry>> attributeNameMapping = new HashMap<> ();
		for ( Map.Entry<String,DeclarationEntry> entry: nodeAttributeDecls.entrySet()) {
			String attrName = entry.getKey();
			if (attrName.equals(CxNode.NAME)) {
				if ( entry.getValue().getDataType() == null ||
						entry.getValue().getDataType() == ATTRIBUTE_DATA_TYPE.STRING)
					attributeNameMapping.put (CxNode.NAME, entry);
			} else if (attrName.equals(CxNode.REPRESENTS) ) {
				if ( entry.getValue().getDataType() == null ||
						entry.getValue().getDataType() == ATTRIBUTE_DATA_TYPE.STRING)
					attributeNameMapping.put (CxNode.REPRESENTS, entry);
			} else if ( attrName.equalsIgnoreCase(SingleNetworkSolrIdxManager.ALIAS) ) {
				if ( entry.getValue().getDataType() == ATTRIBUTE_DATA_TYPE.LIST_OF_STRING) {
					attributeNameMapping.put (SingleNetworkSolrIdxManager.ALIAS, entry);
				}
			} else if ( attrName.equalsIgnoreCase(SingleNetworkSolrIdxManager.TYPE)) {
				if ( entry.getValue().getDataType() == null ||
						entry.getValue().getDataType() == ATTRIBUTE_DATA_TYPE.STRING)
					attributeNameMapping.put (SingleNetworkSolrIdxManager.TYPE, entry);
			} else if ( attrName.equalsIgnoreCase(SingleNetworkSolrIdxManager.MEMBER)) {
				if ( entry.getValue().getDataType() == null ||
						entry.getValue().getDataType() == ATTRIBUTE_DATA_TYPE.STRING)
					attributeNameMapping.put (SingleNetworkSolrIdxManager.MEMBER, entry);
			}

		}

		File nodeAspectFile = new File (cx2AspectPath + CxNode.ASPECT_NAME);
		if ( nodeAspectFile.exists()) {
			//go through node aspect
			try (FileInputStream inputStream = new FileInputStream(cx2AspectPath + "nodes")) {

				Iterator<CxNode> it = om.readerFor(CxNode.class).readValues(inputStream);

				while (it.hasNext()) {
					CxNode node = it.next();
					node.extendToFullNode(nodeAttributeDecls);

					globalIdx.addCX2NodeToIndex(node, attributeNameMapping);
				}
			}
		}

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
			logger.info("Migration failed", e);
			System.exit(1);
		}
	}
}