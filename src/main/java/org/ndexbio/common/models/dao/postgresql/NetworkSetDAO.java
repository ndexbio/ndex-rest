package org.ndexbio.common.models.dao.postgresql;

import java.io.IOException;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.xml.bind.DatatypeConverter;

import org.ndexbio.common.models.dao.FolderDAO;
import org.ndexbio.rest.Configuration;
import org.ndexbio.model.exceptions.DuplicateObjectException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.FileItemSummary;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.NetworkSet;
import org.ndexbio.model.object.NdexFolder;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class NetworkSetDAO extends NdexDBDAO {

	public NetworkSetDAO() throws SQLException {
		super();
	}

	public void createNetworkSet(UUID setId, String name, String desc, UUID ownerId, Map<String,Object> properties) throws SQLException, DuplicateObjectException, JsonProcessingException {
		Timestamp t = new Timestamp(System.currentTimeMillis());
		
		checkDuplicateName(name,ownerId, null);
				
		String sqlStr = "insert into network_set (\"UUID\", creation_time, modification_time, is_deleted, owner_id, name, description, other_attributes, showcased) values"
				+ "(?, ?, ?, false, ?,?, ?,?  :: jsonb, false ) ";
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setObject(1, setId);
			pst.setTimestamp(2, t);
			pst.setTimestamp(3, t);
			pst.setObject(4, ownerId);
			pst.setString(5, name);
			pst.setString(6, desc);		
			
			ObjectMapper mapper = new ObjectMapper();
	        String s = properties ==null ? null : mapper.writeValueAsString(properties);
			pst.setString(7, s);

			pst.executeUpdate();
		}	
	}

	private void checkDuplicateName(String name, UUID ownerId, UUID setId) throws SQLException, DuplicateObjectException {
		
		String sqlStr = "select 1 from network_set where  owner_id = ? and name =? and is_deleted = false";
		if (setId !=null)
			sqlStr += " and \"UUID\" <> '" + setId + "' :: uuid";
		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
			p.setObject(1, ownerId);
			p.setString(2, name);
			try ( ResultSet rs = p.executeQuery()) {
				if ( rs.next()) 
					throw new DuplicateObjectException("Network set with this name already exists for this user.");
			}
		}
	}
	
	public void updateNetworkSet(UUID setId, String name, String desc, UUID ownerId, Map<String,Object> properties) throws SQLException, DuplicateObjectException, JsonProcessingException {
		Timestamp t = new Timestamp(System.currentTimeMillis());
		
		checkDuplicateName(name, ownerId, setId);
		
		String sqlStr = "update network_set set modification_time = ?, name = ?, description = ?, other_attributes=? :: jsonb where \"UUID\"=? and is_deleted=false";
				
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setTimestamp(1, t);
			pst.setString(2, name);
			pst.setString(3, desc);
			
			ObjectMapper mapper = new ObjectMapper();
	        String s = properties ==null ? null : mapper.writeValueAsString(properties);
			pst.setString(4, s);

			pst.setObject(5, setId);
			pst.executeUpdate();
		}	
	}
	
	public boolean isNetworkSetOwner(UUID setId, UUID ownerId) throws SQLException {
		String sqlStr = "select 1 from network_set where \"UUID\" = ? and owner_id = ? and is_deleted=false";
		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
			p.setObject(1,setId);
			p.setObject(2, ownerId);
			try ( ResultSet rs = p.executeQuery()) {
				return rs.next(); 
			}
		}
	}
	
	public void deleteNetworkSet(UUID setId) throws SQLException {
		Timestamp t = new Timestamp(System.currentTimeMillis());
		
		String sqlStr = "update network_set set modification_time = ?, is_deleted = true  where \"UUID\"=?";
				
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setTimestamp(1, t);
			pst.setObject(2, setId);
			pst.executeUpdate();
		}
		
		sqlStr = "delete from network_set_member where set_id=?";
		
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setObject(1, setId);
			pst.executeUpdate();
		}
		
	}
	
	public NetworkSet getNetworkSet(UUID setId, UUID userId, String accessKey) throws SQLException, ObjectNotFoundException, UnauthorizedOperationException, JsonParseException, JsonMappingException, IOException, NdexException {
		
		// All network sets are now folder-backed. Query the folder using its UUID (same as old network_set UUID).
		try (FolderDAO folderDAO = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
			NdexFolder folder = folderDAO.getFolder(setId, userId, accessKey);
			
			NetworkSet result = new NetworkSet();
			result.setExternalId(setId);
			result.setCreationTime(folder.getCreationTime());
			result.setModificationTime(folder.getModificationTime());
			result.setOwnerId(UUID.fromString(folder.getOwner_id()));
			result.setName(folder.getName());
			result.setDescription(folder.getDescription());
			
			// Folders don't have showcased flag or DOI, so these are not set.
			// showcasedOnly is treated as no-op for migrated folder-backed sets.
			
			// Get folder members and normalize them according to v2 constraints:
			// - Include direct networks (FileType.NETWORK)
			// - Exclude folders (FileType.FOLDER)
			// - Exclude folder-type shortcuts (FileType.SHORTCUT with target_type='FOLDER')
			// - For network-type shortcuts: replace shortcut UUID with target network UUID (only if target is ACTIVE)
			// - Exclude deleted/in-trash network shortcuts
			// - Deduplicate network UUIDs, preserve first-seen order
			List<FileItemSummary> folderItems = folderDAO.listItemsInFolder(setId, true, FileType.NETWORK);
			
			Set<UUID> seenNetworkIds = new LinkedHashSet<>();
			for (FileItemSummary item : folderItems) {
				if (item.getType() == FileType.NETWORK) {
					// Direct network member
					seenNetworkIds.add(item.getUuid());
				} else if (item.getType() == FileType.SHORTCUT) {
					includeNetworkShortcut(item.getAttributes(), seenNetworkIds);
				}
			}
			
			result.setNetworks(new ArrayList<>(seenNetworkIds));
			return result;
		}
	}

	public void addNetworksToNetworkSet(UUID setId, Collection<UUID> networkIds) throws SQLException {
		
		String sqlStr = "insert into network_set_member (set_id, network_id) values (?, ?) on conflict  on CONSTRAINT \"network_set_member_pkey\" do nothing";

		for ( UUID networkId : networkIds) {
			try (PreparedStatement p = db.prepareStatement(sqlStr)) {
				p.setObject(1, setId);
				p.setObject(2, networkId);
				p.executeUpdate();
			}
		}
		
	}

	public void deleteNetworksToNetworkSet(UUID setId, Collection<UUID> networkIds) throws SQLException {
		
		String sqlStr = "delete from network_set_member where set_id = ? and  network_id = ?";

		for ( UUID networkId : networkIds) {
			try (PreparedStatement p = db.prepareStatement(sqlStr)) {
				p.setObject(1, setId);
				p.setObject(2, networkId);
				p.executeUpdate();
			}
		}
		
	}

	
	public List<NetworkSet> getNetworkSetsByUserId(UUID userId, UUID signedInUserId, int offset, int limit,
			boolean summaryOnly, boolean showcasedOnly
			) throws SQLException, JsonParseException, JsonMappingException, IOException, NdexException {
		
		// All network sets are now folder-backed. Query folders owned by userId.
		List<NetworkSet> result = new ArrayList<>();
		
		try (FolderDAO folderDAO = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
			// Fetch enough rows to support offset/limit semantics. In the previous implementation, limit<=0 meant "no limit".
			final int fetchLimit = (limit > 0 ? (offset > 0 ? offset + limit : limit) : Integer.MAX_VALUE);
			List<NdexFolder> userFolders = folderDAO.listFoldersOfUser(userId, fetchLimit);
			
			// Apply offset manually since FolderDAO doesn't support offset
			int startIdx = Math.max(0, offset >= 0 ? offset : 0);
			int endIdx = Math.min(userFolders.size(), offset >= 0 && limit > 0 ? offset + limit : userFolders.size());
			if (startIdx >= userFolders.size()) {
				return result;
			}
			
			List<NdexFolder> pagedFolders = userFolders.subList(startIdx, endIdx);
			
			for (NdexFolder folder : pagedFolders) {
				NetworkSet entry = new NetworkSet();
				entry.setExternalId(folder.getExternalId());
				entry.setCreationTime(folder.getCreationTime());
				entry.setModificationTime(folder.getModificationTime());
				entry.setOwnerId(userId);
				entry.setName(folder.getName());
				entry.setDescription(folder.getDescription());
				
				// Folders don't have showcased flag or DOI, so these are not set.
				// showcasedOnly is treated as no-op for migrated folder-backed sets.
				
				// Populate networks unless summaryOnly is true
				if (!summaryOnly) {
					// Get folder members and normalize them according to v2 constraints
					List<FileItemSummary> folderItems = folderDAO.listItemsInFolder(folder.getExternalId(), true, FileType.NETWORK);
					
					Set<UUID> seenNetworkIds = new LinkedHashSet<>();
					for (FileItemSummary item : folderItems) {
						if (item.getType() == FileType.NETWORK) {
							// Direct network member
							seenNetworkIds.add(item.getUuid());
						} else if (item.getType() == FileType.SHORTCUT) {
							includeNetworkShortcut(item.getAttributes(), seenNetworkIds);
						}
					}
					entry.setNetworks(new ArrayList<>(seenNetworkIds));
				}
				result.add(entry);
			}
		}
		
		return result;
	}

	private void includeNetworkShortcut(Map<String, Object> attrs, Set<UUID> seenNetworkIds) {
		if (attrs == null) {
			return;
		}

		String targetType = (String) attrs.get("target_type");
		if (!"NETWORK".equals(targetType)) {
			return;
		}

		String targetStatus = (String) attrs.get("target_status");
		if (!"ACTIVE".equals(targetStatus)) {
			return;
		}

		UUID targetId = (UUID) attrs.get("target");
		if (targetId != null) {
			seenNetworkIds.add(targetId);
		}
	}

	public int getNetworkSetCountByUserId(UUID userId) throws SQLException, NdexException {
		
		String sqlStr = "select count(*) from network_set  where owner_id=? and is_deleted=false";
				
		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
			p.setObject(1, userId);
			try ( ResultSet rs = p.executeQuery()) {
				if ( rs.next()) {
					return rs.getInt(1);
				} 
			}
		}
		throw new NdexException ("Failed to get network set count.");
	}	
	
	public String getNetworkSetAccessKey( UUID networkSetId) throws SQLException, ObjectNotFoundException {
		String sqlStr = "select access_key, access_key_is_on from network_set where \"UUID\" = ? and is_deleted=false";
		
		String oldKey = null;
		boolean keyIsOn = false;
		
		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
			p.setObject(1, networkSetId);
			try ( ResultSet rs = p.executeQuery()) {
				if ( rs.next()) {
					oldKey = rs.getString(1);
					keyIsOn = rs.getBoolean(2);
				} else
					throw new ObjectNotFoundException("Network" , networkSetId);
				
			}
		}
	
		if ( keyIsOn )
			return oldKey;
		
		return null;
		
	}
	
	
	public String enableNetworkSetAccessKey( UUID networkSetId) throws SQLException, ObjectNotFoundException {
		String sqlStr = "select access_key, access_key_is_on from network_set where \"UUID\" = ? and is_deleted=false";
		
		String oldKey = null;
		boolean keyIsOn = false;
		
		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
			p.setObject(1, networkSetId);
			try ( ResultSet rs = p.executeQuery()) {
				if ( rs.next()) {
					oldKey = rs.getString(1);
					keyIsOn = rs.getBoolean(2);
				} else
					throw new ObjectNotFoundException("Network" , networkSetId);
				
			}
		}
	
		if ( oldKey !=null ) {
			if ( keyIsOn)
				return oldKey;
				
			//update db flag
			sqlStr = "update network_set set access_key_is_on = true where \"UUID\"=?";
			try ( PreparedStatement pst = db.prepareStatement(sqlStr)) {
	        	pst.setObject(1, networkSetId);
	        	pst.executeUpdate();
	        }	
			return oldKey;
				
		}
		
		// create new Key
		 SecureRandom random = new SecureRandom();
	     byte bytes[] = new byte[256/8];
	     random.nextBytes(bytes);
	     String newKey= DatatypeConverter.printHexBinary(bytes).toLowerCase();
		
	     //update db record
		 sqlStr = "update network_set set access_key_is_on = true, access_key=? where \"UUID\"=?";
		 try ( PreparedStatement pst = db.prepareStatement(sqlStr)) {
				pst.setString(1, newKey);
	        	pst.setObject(2, networkSetId);
	        	pst.executeUpdate();
	      }		     
	     return newKey;
	}

	
	public void disableNetworkSetAccessKey( UUID networkSetId) throws SQLException {
		//update db flag
		String sqlStr = "update network_set set access_key_is_on = false where \"UUID\"=?";
		try ( PreparedStatement pst = db.prepareStatement(sqlStr)) {
	        pst.setObject(1, networkSetId);
	        pst.executeUpdate();
	    }	
	}
	
	
	   public void setShowcaseFlag(UUID networkSetId, boolean bv) throws SQLException {
	      String sql = "update network_set set showcased = ? where \"UUID\"=? and is_deleted=false";
	      
	      try ( PreparedStatement pst = db.prepareStatement(sql)) {
	        	pst.setBoolean(1, bv);
	        	pst.setObject(2, networkSetId);
	        	pst.executeUpdate();
	       }	
	    	
	    }
	
}
