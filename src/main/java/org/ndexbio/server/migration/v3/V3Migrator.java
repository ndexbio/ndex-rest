package org.ndexbio.server.migration.v3;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.ndexbio.common.solr.ShortcutIndexManager;
import org.ndexbio.common.solr.SolrClientWrapper;
import org.ndexbio.common.solr.SolrObjectFactory;
import org.ndexbio.common.util.NdexUUIDFactory;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.*;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.Configuration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ndexbio.task.NdexServerQueue;
import org.ndexbio.task.SolrTaskRebuildFileIdx;

public class V3Migrator implements AutoCloseable {

	private static final Logger logger = Logger.getLogger(V3Migrator.class.getName());

	private final Connection db;
	private final ObjectMapper mapper;
	private final TypeReference<Map<String, Object>> mapTypeRef;
    private final SolrObjectFactory solrObjectFactory;

	public V3Migrator() throws Exception {
		Configuration configuration = Configuration.createInstance();
		NdexDatabase.createNdexDatabase(configuration.getDBURL(), configuration.getDBUser(),
				configuration.getDBPasswd(), 10);
		this.db = NdexDatabase.getInstance().getConnection();
		this.mapper = new ObjectMapper();
        this.solrObjectFactory = configuration.getSolrObjectFactory();
		this.mapTypeRef = new TypeReference<Map<String, Object>>() {
		};
        try (UserDAO userDAO = new UserDAO(); FolderDAO folderDAO = new PostgresFolderDAO();
             ShortcutDAO shortcutDAO = new PostgresShortcutDAO(); NetworkDAO networkDAO = new PostgresNetworkDAO()){
            DaoSet daoSet = new DaoSet(userDAO, folderDAO, shortcutDAO, networkDAO);
            processNetworkSets(daoSet);

        }
	}

	@Override
	public void close() throws Exception {
		db.close();
		NdexDatabase.close();
	}


	public void processNetworkSets(DaoSet daoSet) throws Exception {
		String sql = "select creation_time, modification_time, \"UUID\", owner_id, name, description, "
				+ "other_attributes, access_key, access_key_is_on, showcased, ndexdoi "
				+ "from network_set where is_deleted=false";
		int count = 0;
		try (PreparedStatement pst = db.prepareStatement(sql)) {
			try (ResultSet rs = pst.executeQuery()) {
				while (rs.next()) {
					NetworkSet set = new NetworkSet();
					set.setCreationTime(rs.getTimestamp(1));
					set.setModificationTime(rs.getTimestamp(2));
					UUID setId = (UUID) rs.getObject(3);
					set.setExternalId(setId);
					set.setOwnerId((UUID) rs.getObject(4));
					set.setName(rs.getString(5));
					set.setDescription(rs.getString(6));

					String propStr = rs.getString(7);
					if (propStr != null) {
						Map<String, Object> props = mapper.readValue(propStr, mapTypeRef);
						set.setProperties(props);
					}
					String accessKey = rs.getString(8);
					Boolean accessKeyIsOn = null;
					boolean accessKeyIsOnValue = rs.getBoolean(9);
					if (!rs.wasNull()) {
						accessKeyIsOn = accessKeyIsOnValue;
					}
					boolean showcasedValue = rs.getBoolean(10);
					if (!rs.wasNull()) {
						set.setShowcased(showcasedValue);
					}
					String doi = rs.getString(11);
					if (doi != null) {
						set.setDoi(doi);
					}
					set.setNetworks(loadNetworkSetMembers(setId));
                    User owner = null;
                    if (set.getOwnerId() != null) {
                        owner = daoSet.userDAO.getUserById(set.getOwnerId(), false, false);
                    }

                    NdexFolder folder = mapNetworkSetToFolder(set, owner);
					handleMappedFolder(set, folder, accessKey, accessKeyIsOn != null && accessKeyIsOn,
                            daoSet, owner);
					count++;
				}
			}
		}

		logger.info("Processed " + count + " network sets from v2 schema.");
	}

	private List<UUID> loadNetworkSetMembers(UUID setId) throws SQLException {
		List<UUID> networks = new ArrayList<>();
		String memberSql = "select nm.network_id "
				+ "from network_set_member nm "
				+ "join network n on n.\"UUID\" = nm.network_id "
				+ "where nm.set_id = ? and n.is_deleted=false";
		try (PreparedStatement pst = db.prepareStatement(memberSql)) {
			pst.setObject(1, setId);
			try (ResultSet rs = pst.executeQuery()) {
				while (rs.next()) {
					networks.add((UUID) rs.getObject(1));
				}
			}
		}
		return networks;
	}

	private void handleMappedFolder(NetworkSet set, NdexFolder folder, String accessKey,
                                    Boolean accessKeyIsOn, DaoSet daoSet, User owner) throws Exception {
        UUID ownerUUID = set.getOwnerId();
        daoSet.folderDAO.createFolder(folder.getExternalId(), ownerUUID, null, folder.getName(),
                folder.getDescription());
        daoSet.folderDAO.commit();
        setFolderAccessKeyIfNeeded(folder, accessKey, accessKeyIsOn);
        //daoSet.folderDAO.setFolderPermission()
        try (FolderIndexManager folderIndexManager = solrObjectFactory.getFolderIndexManager()){
            folderIndexManager.createIndex(folder, VisibilityType.PRIVATE, null, null);
        }
        try (ShortcutIndexManager shortcutIndexManager = solrObjectFactory.getShortcutIndexManager()){
            for (UUID networkId: set.getNetworks()){
                NetworkSummary networkSummary = daoSet.networkDAO.getNetworkSummaryById(networkId);

                UUID shortcutId = NdexUUIDFactory.INSTANCE.createNewNDExUUID();
                //todo should shortcut owner be network owner (networksummary.getowneruuid) or networkset owner (owner.getId)
                daoSet.shortcutDAO.createShortcut(shortcutId, networkSummary.getOwnerUUID(),
                        folder.getExternalId(), networkSummary.getName(), networkId, FileType.NETWORK);
                daoSet.shortcutDAO.commit();

                NdexShortcut shortcut = new NdexShortcut();
                shortcut.setOwner(owner.getUserName());
                shortcut.setName(networkSummary.getName());
                shortcut.setParent(folder.getExternalId());
                shortcut.setTarget(networkId);
                shortcut.setTargetType(FileType.NETWORK);
                shortcut.setCreationTime(networkSummary.getCreationTime());
                shortcut.setModificationTime(networkSummary.getModificationTime());
                shortcut.setIsDeleted(networkSummary.getIsDeleted());
                shortcut.setExternalId(shortcutId);
                //Ma<String,String> permss = daoSet.networkDAO.getNetworkUserPermissions(networkId, null,-1,-1);
                shortcutIndexManager.createIndex(shortcut,VisibilityType.PRIVATE, null, null);

            }

        }

		// Placeholder for creating folder + shortcuts; this keeps processing streaming-friendly.
	}

    private void setFolderAccessKeyIfNeeded(NdexFolder ndexFolder, String accessKey, boolean accessKeyIsOn) throws SQLException {
		if (accessKey == null || accessKey.isBlank()) {
			return;
		}
		String sql = "update folder set access_key = ?, access_key_is_on = ? where \"UUID\" = ?";
		try (PreparedStatement pst = db.prepareStatement(sql)) {
			pst.setString(1, accessKey);
			pst.setBoolean(2, accessKeyIsOn);
			pst.setObject(3, ndexFolder.getExternalId());
			pst.executeUpdate();
		}
		db.commit();
    }

	public NdexFolder mapNetworkSetToFolder(NetworkSet set, User owner) throws SQLException, IOException, NdexException {
		NdexFolder folder = new NdexFolder();
		folder.setExternalId(set.getExternalId());
		folder.setName(set.getName());
		folder.setDescription(set.getDescription());
		folder.setCreationTime(set.getCreationTime());
		folder.setModificationTime(set.getModificationTime());
		if (owner != null) {
			folder.setOwner(owner.getUserName());
		}
		return folder;
	}

    private void createFileIndex(UUID fileId, UUID userId, String username, VisibilityType visibility,
                                   FileType fileType, boolean createOnly) throws SQLException, NdexException, IOException {
        NdexServerQueue.INSTANCE.addSystemTask(new SolrTaskRebuildFileIdx(
                fileId, userId, username, visibility, fileType, createOnly));
    }

    class DaoSet {
        UserDAO userDAO;
        FolderDAO folderDAO;
        ShortcutDAO shortcutDAO;
        NetworkDAO networkDAO;
        public DaoSet(UserDAO u, FolderDAO f, ShortcutDAO s, NetworkDAO n){
            this.userDAO = u;
            this.folderDAO = f;
            this.shortcutDAO = s;
            this.networkDAO = n;
        }
    }

}
