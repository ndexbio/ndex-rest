package org.ndexbio.common.models.dao.postgresql;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.ndexbio.model.exceptions.DuplicateObjectException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.Folder;
import org.ndexbio.model.object.NdexObjectUpdateStatus;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class FolderDAO extends NdexDBDAO {
	
	private static Logger logger = Logger.getLogger(FolderDAO.class.getName());

	public FolderDAO() throws SQLException {
		super();
	}
	
	public NdexObjectUpdateStatus createFolder(final UUID folderUUID, final UUID ownerId, final UUID parentUUID, final String name) throws SQLException {
		Timestamp t = new Timestamp(System.currentTimeMillis());
		
		String sqlStr = "insert into folder (\"UUID\", creation_time, modification_time, is_deleted, name, visibility, owneruuid, access_key_is_on, parent) values"
				+ "(?, ?, ?, false, ?, 'PRIVATE',?, false, ?) ";
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setObject(1, folderUUID);
			pst.setTimestamp(2, t);
			pst.setTimestamp(3, t);
			pst.setString(4, name);
			pst.setObject(5, ownerId);
			pst.setObject(6, parentUUID);
			
			pst.executeUpdate();
		}
		NdexObjectUpdateStatus result = new NdexObjectUpdateStatus();
		result.setModificationTime(t);
		result.setUuid(folderUUID);
		return result;
	}
	
	protected static String createIsReadableConditionStr(UUID userId) {
		// TODO !
		if ( userId == null)
			return "n.visibility='PUBLIC'";
		return "( n.visibility='PUBLIC' or n.owneruuid = '" + userId + "' ::uuid or " + 
			" exists ( select 1 from user_network_membership un1 where un1.network_id = n.\"UUID\" and un1.user_id = '"+ userId + "' limit 1) or " +
		    " exists ( select 1 from group_network_membership gn1, ndex_group_user gu where gn1.group_id = gu.group_id "
		    + "and gn1.network_id = n.\"UUID\" and gu.user_id = '"+ userId + "' limit 1) )";
	}
	
	public boolean isReadable(UUID folderID, UUID userId) throws SQLException, ObjectNotFoundException {
		return true;
//		String sqlStr = "select (" + createIsReadableConditionStr(userId) + ") from network n where n.\"UUID\" = ? and n.is_deleted=false ";		
//			
//		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
//			pst.setObject(1, networkID);
//
//			try ( ResultSet rs = pst.executeQuery()) {
//				if ( rs.next()) 
//					return rs.getBoolean(1);
//				 
//				throw new ObjectNotFoundException("Network", networkID);
//			}
//		}
	}
	
	public boolean accessKeyIsValid(UUID folderId, String accessKey) throws SQLException {
//		if ( accessKey ==null || accessKey.length() == 0)
//			return false;
//		
//		String sqlStr = "select 1 from network where (\"UUID\"=? and access_key_is_on and access_key = ?)" ;
//		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
//			p.setObject(1, networkId);
//			p.setString(2, accessKey);
//			try ( ResultSet rs = p.executeQuery()) {
//				 if (rs.next())
//					 return true;
//			}		
//		}
//		
//		sqlStr = "select 1 from network_set s, network_set_member sm where s.\"UUID\" = sm.set_id "
//                + "and sm.network_id = ? and s.access_key_is_on and s.access_key = ? and s.is_deleted=false";
//		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
//			p.setObject(1, networkId);
//			p.setString(2, accessKey);
//			try ( ResultSet rs = p.executeQuery()) {
//				 return rs.next();
//			}		
//		}
		return true;

	}
	
	public Folder getFolder(UUID folderId, UUID userId, String accessKey) throws SQLException, ObjectNotFoundException, UnauthorizedOperationException, JsonParseException, JsonMappingException, IOException {
		
		Folder result = new Folder();
		String sqlStr = "select creation_time, modification_time, name, parent, is_deleted from folder where \"UUID\"=?";
		
		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
			p.setObject(1, folderId);
			try ( ResultSet rs = p.executeQuery()) {
				if ( rs.next()) {
					result.setCreationTime(rs.getTimestamp(1));
					result.setModificationTime(rs.getTimestamp(2));
					result.setExternalId(folderId);
					result.setName(rs.getString(3));
					result.setParent((UUID)(rs.getObject(4)));
					result.setIsDeleted(rs.getBoolean(5));
				} else
					throw new ObjectNotFoundException("Folder" + folderId + " not found in db.");
			}
		}
		
		return result;
	}
	
	public boolean isFolderOwner(UUID folderId, UUID ownerId) throws SQLException {
		String sqlStr = "select 1 from folder where \"UUID\" = ? and owneruuid = ? and is_deleted=false";
		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
			p.setObject(1, folderId);
			p.setObject(2, ownerId);
			try ( ResultSet rs = p.executeQuery()) {
				return rs.next(); 
			}
		}
	}
	
	public void deleteFolder(UUID folderId) throws SQLException {
		Timestamp t = new Timestamp(System.currentTimeMillis());
		
		String sqlStr = "update folder set modification_time = ?, is_deleted = true  where \"UUID\"=?";
				
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setTimestamp(1, t);
			pst.setObject(2, folderId);
			pst.executeUpdate();
		}
		
	}
	
	public void updateFolder(UUID folderId, String name, UUID parentId, UUID ownerId) throws SQLException, DuplicateObjectException, JsonProcessingException {
		Timestamp t = new Timestamp(System.currentTimeMillis());
		
		//TODO: checkDuplicateName(name, ownerId, setId);
		
		// needs verification -- don't update if nothing passed in parent or name
		String sqlStr = "update folder set modification_time = ?, name = ?, parent = ? where \"UUID\"=? and is_deleted=false";
				
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setTimestamp(1, t);
			pst.setString(2, name);
			pst.setObject(3, parentId);
			pst.setObject(4, folderId);
			pst.executeUpdate();
		}	
	}

}
