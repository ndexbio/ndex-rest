package org.ndexbio.common.models.dao.postgresql;

import java.io.IOException;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.xml.bind.DatatypeConverter;

import org.ndexbio.model.exceptions.DuplicateObjectException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.NetworkSet;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
	
	public NetworkSet getNetworkSet(UUID setId, UUID userId, String accessKey) throws SQLException, ObjectNotFoundException, UnauthorizedOperationException, JsonParseException, JsonMappingException, IOException {
		
		NetworkSet result = new NetworkSet();
		String sqlStr = "select creation_time, modification_time, owner_id, name, description, access_key, access_key_is_on, other_attributes,showcased from network_set  where \"UUID\"=? and is_deleted=false";
		
		String dbAccessKey = null;
		boolean dbKeyIsOn;
		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
			p.setObject(1, setId);
			try ( ResultSet rs = p.executeQuery()) {
				if ( rs.next()) {
					result.setCreationTime(rs.getTimestamp(1));
					result.setModificationTime(rs.getTimestamp(2));
					result.setExternalId(setId);
					result.setOwnerId((UUID)rs.getObject(3));
					result.setName(rs.getString(4));
					result.setDescription(rs.getString(5));
					dbAccessKey = rs.getString(6);
					dbKeyIsOn = rs.getBoolean(7);
					
					String propStr = rs.getString(8);
					
					if ( propStr != null) {
						ObjectMapper mapper = new ObjectMapper(); 
						TypeReference<HashMap<String,Object>> typeRef 
				            = new TypeReference<HashMap<String,Object>>() {};

				            HashMap<String,Object> o = mapper.readValue(propStr, typeRef); 		
				            result.setProperties(o);
					}
					
					result.setShowcased(rs.getBoolean(9));
				} else
					throw new ObjectNotFoundException("Network set" + setId + " not found in db.");
			}
		}
		
		boolean keyIsValid = false;
		if (dbKeyIsOn && accessKey!= null && dbAccessKey.equals(accessKey))
			keyIsValid = true;
		if (!keyIsValid && accessKey!=null)
			throw new UnauthorizedOperationException("In valid network set access key.");
		
		sqlStr = "select nm.network_id from network_set_member nm, network n where nm.set_id =? and n.\"UUID\"=nm.network_id and " + 
				( keyIsValid ? " true" : NetworkDAO.createIsReadableConditionStr(userId)) ;
		
		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
			p.setObject(1, setId);
			try ( ResultSet rs = p.executeQuery()) {
				List<UUID> networkIds = result.getNetworks();
				while ( rs.next()) {
					networkIds.add((UUID)rs.getObject(1));
				} 
			}
		}
		
		return result;
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

	
	public List<NetworkSet> getNetworkSetsByUserId(UUID userId, UUID signedInUserId) throws SQLException, JsonParseException, JsonMappingException, IOException {
		
		List<NetworkSet> result = new ArrayList<>();
		
		String sqlStr = "select creation_time, modification_time, \"UUID\", name, description, other_attributes,showcased from network_set  where owner_id=? and is_deleted=false";
				
		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
			p.setObject(1, userId);
			try ( ResultSet rs = p.executeQuery()) {
				while ( rs.next()) {
					NetworkSet entry = new NetworkSet();
					entry.setCreationTime(rs.getTimestamp(1));
					entry.setModificationTime(rs.getTimestamp(2));
					entry.setExternalId((UUID)rs.getObject(3));
					entry.setOwnerId(userId);
					entry.setName(rs.getString(4));
					entry.setDescription(rs.getString(5));
					
					String propStr = rs.getString(6);
					
					if ( propStr != null) {
						ObjectMapper mapper = new ObjectMapper(); 
						TypeReference<HashMap<String,Object>> typeRef 
				            = new TypeReference<HashMap<String,Object>>() {};

				            HashMap<String,Object> o = mapper.readValue(propStr, typeRef); 		
				            entry.setProperties(o);
					}
					
					entry.setShowcased(rs.getBoolean(7));
					entry.setOwnerId(userId);

					result.add(entry);
				} 
			}
		}
		
		sqlStr = "select network_id from network_set_member nm, network n where nm.set_id =? and nm.network_id = n.\"UUID\" and " + NetworkDAO.createIsReadableConditionStr(signedInUserId);
		
		for (NetworkSet entry : result) {
			try (PreparedStatement p = db.prepareStatement(sqlStr)) {
				p.setObject(1, entry.getExternalId());
				try ( ResultSet rs = p.executeQuery()) {
					List<UUID> networkIds = entry.getNetworks();
					while ( rs.next()) {
						networkIds.add((UUID)rs.getObject(1));
					} 
				}
			}
		}
		
		return result;
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
