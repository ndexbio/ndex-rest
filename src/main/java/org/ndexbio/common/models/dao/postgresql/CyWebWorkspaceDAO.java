package org.ndexbio.common.models.dao.postgresql;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.UUID;

import org.ndexbio.common.util.NdexUUIDFactory;
import org.ndexbio.model.exceptions.DuplicateObjectException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.CyWebWorkspace;
import org.ndexbio.model.object.Group;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class CyWebWorkspaceDAO extends NdexDBDAO {

	public CyWebWorkspaceDAO() throws SQLException {
		super();
	}

	
	
	/**************************************************************************
	    * Create a new cw workspace
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
					throw new NdexException ( "Failed to save workspace " + workspace.getName() + " to database.");
			}
			
			if ( workspace.getNetworkIDs()!=null && !workspace.getNetworkIDs().isEmpty()) {
			insertStr = "insert into cyweb_workspace_network (workspaceid,networkid) values(?,?)";
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

}
