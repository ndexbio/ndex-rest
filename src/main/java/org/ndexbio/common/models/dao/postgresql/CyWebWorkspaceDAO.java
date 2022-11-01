package org.ndexbio.common.models.dao.postgresql;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.ndexbio.common.util.NdexUUIDFactory;
import org.ndexbio.model.exceptions.DuplicateObjectException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.object.CyWebWorkspace;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;

public class CyWebWorkspaceDAO extends NdexDBDAO {

	public CyWebWorkspaceDAO() throws SQLException {
		super();
	}

	
	
	/**************************************************************************
	    * Create a new Cytoscape Web workspace
	    * 
	    * @param workspace
	    *            A workspace object, from the NDEx Object Model
	    * @param ownerUUID
	    * 			UUID for logged in user
	    * @throws NdexException
	    *            Attempting to save an ODocument to the database
	    * @throws IllegalArgumentException
	    * 			 The newUser does not contain proper fields
	    * @throws DuplicateObjectException
	    * 			 The account name and/or email already exist
	 
	 * @throws IOException 
	 * @throws SQLException 
	 * @throws JsonMappingException 
	 * @throws JsonParseException 
	    * @returns CyWebWorkspace object, from the NDEx Object Model
    **************************************************************************/
	public CyWebWorkspace createWorkspace(CyWebWorkspace workspace, UUID ownerUUID)
			throws NdexException, IllegalArgumentException, DuplicateObjectException, JsonParseException, JsonMappingException, SQLException, IOException {

			Preconditions.checkArgument(null != workspace, 
					"A workspace object is required");

			String insertStr = "insert into cyweb_workspace (\"UUID\", creation_time, modification_time,is_deleted,"+
					"name, options, owner_uuid) values (?,?,?,false,?,? ::json,?)";

			ObjectMapper mapper = new ObjectMapper();
			UUID id = NdexUUIDFactory.INSTANCE.createNewNDExUUID();
			
			try (PreparedStatement st = db.prepareStatement(insertStr) ) {
				workspace.setExternalId(id);
				Timestamp current = new Timestamp(Calendar.getInstance().getTimeInMillis());
				workspace.setCreationTime(current);
				workspace.setModificationTime(current);
				
				st.setObject(1, id);
				st.setTimestamp(2, current);
				st.setTimestamp(3, current);
				st.setString ( 4, workspace.getName());
				if ( workspace.getOptions()!=null) {
					String s = mapper.writeValueAsString( workspace.getOptions());
					st.setString(5, s);
				} else {
					st.setString(5, null);
				}
				st.setObject(6, ownerUUID);
				
				int rowsInserted = st.executeUpdate();
				if ( rowsInserted != 1)
					throw new NdexException ( "Failed to save workspace " + workspace.getName() + " in database.");
			}
			
			if ( workspace.getNetworkIDs()!=null && !workspace.getNetworkIDs().isEmpty()) {
			insertStr = "insert into cyweb_workspace_network (workspace_id,network_id) values(?,?)";
			try (PreparedStatement st = db.prepareStatement(insertStr) ) {
			
				for ( UUID netId : workspace.getNetworkIDs()) {
					st.setObject(1, id);
					st.setObject(2, netId);
				
				int rowsInserted = st.executeUpdate();
				if ( rowsInserted != 1)
					throw new NdexException ( "Failed to save workspace network ids " +  workspace.getName()  + " to database.");
			}   }
			}
			
			return workspace;

		}
	
	public CyWebWorkspace getWorkspace(UUID workspaceId, UUID ownerUUID) throws ObjectNotFoundException, SQLException, JsonMappingException, JsonProcessingException {
		
		String sqlStr = "SELECT * FROM cyweb_workspace where \"UUID\" = '" + workspaceId + "' :: uuid and is_deleted = false and owner_uuid='" +ownerUUID.toString() + "' ::uuid";
		String sqlStr1 = "SELECT network_id FROM cyweb_workspace_network where workspace_id = '" + workspaceId + "' :: uuid";

		CyWebWorkspace result = new CyWebWorkspace();
		try (Statement st = db.createStatement()) {
			try (ResultSet rs = st.executeQuery(sqlStr) ) {
				if (rs.next()) {

					Helper.populateExternalObjectFromResultSet(result, rs);
					result.setName(rs.getString("name"));
					
					String o = rs.getString("options");
					if ( o != null) {
						ObjectMapper mapper = new ObjectMapper();
						Map<String, Object> attr = mapper.readValue(o, new TypeReference<Map<String,Object>>() {/**/}); 					
						result.setOptions(attr);
					} 

				} else 
					throw new ObjectNotFoundException("Workspace with UUID: " + workspaceId.toString() + " doesn't exist.");

			}
			
			
			try (ResultSet rs = st.executeQuery(sqlStr1) ) {
				while (rs.next()) {
					result.getNetworkIDs().add((UUID)rs.getObject(1));
				} 
			}
		}

		
		return result;
		
	}
	
	
	public void updateWorkspace(CyWebWorkspace workspace, UUID ownerUUID) throws SQLException, NdexException, JsonProcessingException {
		
		String updateStr = "update cyweb_workspace set modification_time = ?, name=?, options = ? ::json "
				+ " where \"UUID\" = '"+ workspace.getWorkspaceId() +	"' :: uuid and owner_uuid='" + ownerUUID
				+ "'::uuid and is_deleted=false";

		ObjectMapper mapper = new ObjectMapper();
		
		try (PreparedStatement st = db.prepareStatement(updateStr) ) {
			Timestamp current = new Timestamp(Calendar.getInstance().getTimeInMillis());
			
			st.setTimestamp(1, current);
			st.setString ( 2, workspace.getName());
			if ( workspace.getOptions()!=null) {
				String s = mapper.writeValueAsString( workspace.getOptions());
				st.setString(3, s);
			} else {
				st.setString(3, null);
			}
			
			int rowsInserted = st.executeUpdate();
			if ( rowsInserted == 0 ) {
				throw new ObjectNotFoundException("Workspace not found for this user."); 
			}
			if ( rowsInserted != 1)
				throw new NdexException ( "Failed to update workspace " + workspace.getName() + " to database.");
				
		}

		String delStr = "delete from cyweb_workspace_network where workspace_id = '" + workspace.getWorkspaceId() + "'::uuid";
		try (PreparedStatement st = db.prepareStatement(delStr) ) {
			st.executeUpdate();
		}	
		
		String insertStr = "insert into cyweb_workspace_network (workspace_id,network_id) values(?,?)";
		try (PreparedStatement st = db.prepareStatement(insertStr) ) {
			
			for ( UUID netId : workspace.getNetworkIDs()) {
				st.setObject(1, workspace.getWorkspaceId());
				st.setObject(2, netId);
			
			int rowsInserted = st.executeUpdate();
			if ( rowsInserted != 1)
				throw new NdexException ( "Failed to update workspace network ids " +  workspace.getName()  + " to database.");
		}   }
	}
	
	
	public void deleteWorkspace(UUID workspaceId, UUID ownerUUID) throws SQLException, NdexException, JsonProcessingException {
		

		String updStr = "update cyweb_workspace set is_deleted=true where \"UUID\" = '" + workspaceId + "'::uuid and owner_uuid='" + ownerUUID +"'::uuid";
		try (PreparedStatement st = db.prepareStatement(updStr) ) {
			int cnt = st.executeUpdate();
			if (cnt == 0 )
				throw new ObjectNotFoundException("Workspace not found for this user.");
		}	

		String delStr = "delete from cyweb_workspace_network where workspace_id = '" + workspaceId +"'::uuid";
		try (PreparedStatement st = db.prepareStatement(delStr) ) {
			st.executeUpdate();
		}	

		delStr = "delete from cyweb_workspace where \"UUID\" = '" + workspaceId + "'::uuid and owner_uuid='" + ownerUUID +"'::uuid";
		try (PreparedStatement st = db.prepareStatement(delStr) ) {
			st.executeUpdate();
		}	


	}

	public List<CyWebWorkspace> getWorkspaces(UUID ownerUUID) throws SQLException, JsonMappingException, ObjectNotFoundException, JsonProcessingException  {
		List<CyWebWorkspace> result = new ArrayList<>();
		
		String q1 = "select \"UUID\" from cyweb_workspace where owner_uuid='" + ownerUUID + "'::uuid and is_deleted=false";
		try (Statement st = db.createStatement()) {
			try (ResultSet rs = st.executeQuery(q1) ) {
				while ( rs.next()) {
					UUID workspace_id = rs.getObject(1, java.util.UUID.class);
					result.add( getWorkspace (   workspace_id, ownerUUID));
				}
			}
		}	
		return result;
	}
	
	
	public void renameWorkspace(UUID workspaceId, String newName, UUID ownerUUID) throws SQLException, NdexException, JsonProcessingException {
		
		String updateStr = "update cyweb_workspace set name=? where \"UUID\" = '"+ workspaceId + "' :: uuid and owner_uuid='" + ownerUUID
				+ "'::uuid and is_deleted=false";

		
		try (PreparedStatement st = db.prepareStatement(updateStr) ) {
			st.setString ( 1, newName);
			
			int rowsInserted = st.executeUpdate();
			if ( rowsInserted == 0 ) {
				throw new ObjectNotFoundException("Workspace not found for this user."); 
			}
			if ( rowsInserted != 1)
				throw new NdexException ( "Failed to rename workspace in database.");
				
		}

	}

	
	public void setWorkspaceNetworks(UUID workspaceId, Collection<UUID> networkIds,  UUID ownerUUID) throws SQLException, NdexException, JsonProcessingException {
		
		String queryStr = "select 1 from cyweb_workspace where \"UUID\" = '"+ workspaceId + "' :: uuid and owner_uuid='" + ownerUUID
				+ "'::uuid and is_deleted=false";

		
		try (Statement st = db.createStatement() ) {	
			try (ResultSet rs = st.executeQuery(queryStr) ) {
				if (!rs.next()) {
					throw new ObjectNotFoundException("Workspace not found for this user."); 					
				} 
			}				
		}

		String delStr = "delete from cyweb_workspace_network where workspace_id = '" + workspaceId + "'::uuid";
		try (PreparedStatement st = db.prepareStatement(delStr) ) {
			st.executeUpdate();
		}	
		
		String insertStr = "insert into cyweb_workspace_network (workspace_id,network_id) values(?,?)";
		try (PreparedStatement st = db.prepareStatement(insertStr) ) {
			
			for ( UUID netId : networkIds) {
				st.setObject(1, workspaceId);
				st.setObject(2, netId);
			
			int rowsInserted = st.executeUpdate();
			if ( rowsInserted != 1)
				throw new NdexException ( "Failed to update workspace network ids in database.");
		}   }
	}
}
