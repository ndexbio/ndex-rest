package org.ndexbio.common.models.dao.postgresql;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.ndexbio.model.exceptions.DuplicateObjectException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.object.NetworkSet;

public class NetworkSetDAO extends NdexDBDAO {

	public NetworkSetDAO() throws SQLException {
		super();
	}

	public void createNetworkSet(UUID setId, String name, String desc, UUID ownerId) throws SQLException, DuplicateObjectException {
		Timestamp t = new Timestamp(System.currentTimeMillis());
		
		checkDuplicateName(name,ownerId);
				
		String sqlStr = "insert into network_set (\"UUID\", creation_time, modification_time, is_deleted, owner_id, name, description) values"
				+ "(?, ?, ?, false, ?,?, ?) ";
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setObject(1, setId);
			pst.setTimestamp(2, t);
			pst.setTimestamp(3, t);
			pst.setObject(4, ownerId);
			pst.setString(5, name);
			pst.setString(6, desc);			
			pst.executeUpdate();
		}	
	}

	private void checkDuplicateName(String name, UUID ownerId) throws SQLException, DuplicateObjectException {
		
		String sqlStr = "select 1 from network_set where  owner_id = ? and name =?";
		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
			p.setObject(1, ownerId);
			p.setString(2, name);
			try ( ResultSet rs = p.executeQuery()) {
				if ( rs.next()) 
					throw new DuplicateObjectException("Network set with this name already exists for this user.");
			}
		}
	}
	
	public void updateNetworkSet(UUID setId, String name, String desc, UUID ownerId) throws SQLException, DuplicateObjectException {
		Timestamp t = new Timestamp(System.currentTimeMillis());
		
		checkDuplicateName(name, ownerId);
		
		String sqlStr = "update network_set set modification_time = ?, name = ?, description = ?  where \"UUID\"=?";
				
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setTimestamp(1, t);
			pst.setString(2, name);
			pst.setString(3, desc);
			pst.setObject(4, setId);
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
	
	public NetworkSet getNetworkSet(UUID setId, UUID userId) throws SQLException, ObjectNotFoundException {
		
		NetworkSet result = new NetworkSet();
		String sqlStr = "select creation_time, modification_time, owner_id, name, description from network_set  where \"UUID\"=? and is_deleted=false";
				
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
				} else
					throw new ObjectNotFoundException("Network set" + setId + " not found in db.");
			}
		}
		
		sqlStr = "select nm.network_id from network_set_member nm, network n where nm.set_id =? and n.\"UUID\"=nm.network_id and " + NetworkDAO.createIsReadableConditionStr(userId);
		
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

	
	public List<NetworkSet> getNetworkSetsByUserId(UUID userId, UUID signedInUserId) throws SQLException {
		
		List<NetworkSet> result = new ArrayList<>();
		
		String sqlStr = "select creation_time, modification_time, \"UUID\", name, description from network_set  where owner_id=? and is_deleted=false";
				
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

	
}
