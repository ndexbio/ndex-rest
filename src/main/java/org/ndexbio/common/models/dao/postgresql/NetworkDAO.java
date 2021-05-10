/**
 * Copyright (c) 2013, 2016, The Regents of the University of California, The Cytoscape Consortium
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */
package org.ndexbio.common.models.dao.postgresql;

import java.io.IOException;
import java.security.SecureRandom;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.DatatypeConverter;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.ndexbio.common.NdexClasses;
import org.ndexbio.common.persistence.CX2NetworkLoader;
import org.ndexbio.common.solr.NetworkGlobalIndexManager;
import org.ndexbio.cx2.aspect.element.core.CxMetadata;
import org.ndexbio.cxio.metadata.MetaDataCollection;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.NetworkConcurrentModificationException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.Membership;
import org.ndexbio.model.object.MembershipType;
import org.ndexbio.model.object.NdexPropertyValuePair;
import org.ndexbio.model.object.NetworkSearchResult;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.ProvenanceEntity;
import org.ndexbio.model.object.SimpleNetworkQuery;
import org.ndexbio.model.object.User;
import org.ndexbio.model.object.network.NetworkIndexLevel;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.object.network.VisibilityType;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;


public class NetworkDAO extends NdexDBDAO {

	private static Logger logger = Logger.getLogger(NetworkDAO.class.getName());
	
//	public static final String RESET_MOD_TIME = "resetMTime";
	
	private  static List<String> emptyStringList = new ArrayList<>(1); 
	
	private static Pattern uuidSearchPattern = 
			Pattern.compile("^(uuid: *)?(([0-9A-F]{8}-[0-9A-F]{4}-[1-5][0-9A-F]{3}-[89AB][0-9A-F]{3}-[0-9A-F]{12})|(\\\"([0-9A-F]{8}-[0-9A-F]{4}-[1-5][0-9A-F]{3}-[89AB][0-9A-F]{3}-[0-9A-F]{12})\\\"))$",
			Pattern.CASE_INSENSITIVE);



    /* define this to reuse in different functions to keep the order of the fields so that the populateNetworkSummaryFromResultSet function can be shared.*/
	private static final String networkSummarySelectClause = "select n.creation_time, n.modification_time, n.name,n.description,n.version,"
			+ "n.edgecount,n.nodecount,n.visibility,n.owner,n.owneruuid,"
			+ " n.properties, n.\"UUID\", n.is_validated, n.error, n.readonly, n.warnings, n.show_in_homepage,"
			+ "n.subnetworkids,n.solr_idx_lvl, n.iscomplete, n.ndexdoi, n.certified, n.has_layout, n.has_sample, n.cxformat, n.cx_file_size, n.cx2_file_size "; 
	
	public static final String PENDING = "Pending";
	
	public NetworkDAO () throws  SQLException {
	    super();
	}

	public NetworkSummary CreateCloneNetworkEntry(UUID networkUUID, UUID ownerId, String ownerUserName, long fileSize, UUID srcUUID) throws SQLException {
		Timestamp t = new Timestamp(System.currentTimeMillis());
		
		String sqlStr = "insert into network (\"UUID\", creation_time, modification_time, is_deleted, name, description, edgecount,nodecount,"
				+ " islocked, iscomplete, visibility,owneruuid,owner, sourceformat,properties,cxmetadata, version,is_validated, readonly, warnings, subnetworkids, cx_file_size,"
				+ "has_layout, has_sample, cx2metadata, cxformat, cx2_file_size) "
				+ "select ?, current_timestamp, current_timestamp, false, 'Copy of ' || n.name, n.description, n.edgecount, n.nodecount, "
				+ "false, false, 'PRIVATE',?,?,n.sourceformat, n.properties, n.cxmetadata, n.version,true,false,n.warnings, n.subnetworkids,?, n.has_layout,n.has_sample, n.cx2metadata, n.cxformat, n.cx2_file_size from network n where n.\"UUID\" = ? and is_deleted = false";
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setObject(1, networkUUID);
			pst.setObject(2, ownerId);
			pst.setString(3, ownerUserName);
			pst.setLong(4, fileSize);
			pst.setObject(5, srcUUID);
			pst.executeUpdate();
		}
		NetworkSummary result = new NetworkSummary();
		result.setCreationTime(t);
		result.setModificationTime(t);
		result.setExternalId(networkUUID);
		return result;
	}
	
	public NetworkSummary CreateEmptyNetworkEntry(UUID networkUUID, UUID ownerId, String ownerUserName, long fileSize,
			String networkName, String cxformat) throws SQLException {
		Timestamp t = new Timestamp(System.currentTimeMillis());
		
		String sqlStr = "insert into network (\"UUID\", creation_time, modification_time, is_deleted, islocked,visibility,owneruuid,owner,readonly, cx_file_size, name, cxformat, cx2_file_size) values"
				+ "(?, ?, ?, false, true, 'PRIVATE',?,?,false,?,?,?,?) ";
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setObject(1, networkUUID);
			pst.setTimestamp(2, t);
			pst.setTimestamp(3, t);
			pst.setObject(4, ownerId);
			pst.setString(5, ownerUserName);
			if (cxformat !=null && cxformat.equals(CX2NetworkLoader.cx2Format)) {
				pst.setLong (6,0);
				pst.setLong(9, fileSize);
			} else {
				pst.setLong(6, fileSize);
				pst.setLong(9, 0);
			}	
			pst.setString(7, networkName);
			pst.setString(8, cxformat);
			
			pst.executeUpdate();
		}
		NetworkSummary result = new NetworkSummary();
		result.setCreationTime(t);
		result.setModificationTime(t);
		result.setExternalId(networkUUID);
		return result;
	}
	

	
	public void setNetworkFileSize(UUID networkID, long fileSize) throws SQLException, NdexException {
		String sqlStr = "update network set cx_file_size =? where \"UUID\" = ? and is_deleted=false";
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setObject(2, networkID);
			pst.setLong(1, fileSize);
			int cnt = pst.executeUpdate();
			if ( cnt !=1) {
				throw new NdexException ("Failed to Update network file size in db. Reason could be invalid UUID.");
			}
		}
	}

	public void setCX2FileSize(UUID networkID, long cx2FileSize) throws SQLException, NdexException {
		String sqlStr = "update network set cx2_file_size =? where \"UUID\" = ? and is_deleted=false";
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setObject(2, networkID);
			pst.setLong(1, cx2FileSize);
			int cnt = pst.executeUpdate();
			if ( cnt !=1) {
				throw new NdexException ("Failed to Update cx2 file size in db. Reason could be invalid UUID.");
			}
		}
	}
	
	public void setNetworkFileSizes(UUID networkID, long cxfileSize, long cx2FileSize) throws SQLException, NdexException {
		String sqlStr = "update network set cx_file_size =?, cx2_file_size = ? where \"UUID\" = ? and is_deleted=false";
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setObject(3, networkID);
			pst.setLong(1, cxfileSize);
			pst.setLong(2, cx2FileSize);
			int cnt = pst.executeUpdate();
			if ( cnt !=1) {
				throw new NdexException ("Failed to Update network file size in db. Reason could be invalid UUID.");
			}
		}
	}

	
	public void deleteNetwork(UUID networkId, UUID userId) throws SQLException, NdexException {
		String sqlStr = "update network set is_deleted=true,"
				+ " modification_time = localtimestamp where \"UUID\" = ? and owneruuid = ? and is_deleted=false and isLocked = false and readonly=false";
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setObject(1, networkId);
			pst.setObject(2, userId);
			int cnt = pst.executeUpdate();
			if ( cnt !=1) {
				throw new NdexException ("Failed to delete network. Reason could be invalid UUID, user is not the owner of the network, the network is Locked or the network is readonly.");
			}
		}
		
		
		String[] sqlCmds = {
				"insert into user_network_membership_arc (user_id, network_id, permission_type) " + 
						" select user_id, network_id, permission_type from user_network_membership where network_id = ?",
				"delete from user_network_membership where network_id = ?",
				"insert into group_network_membership_arc (group_id, network_id, permission_type) " + 
						" select group_id, network_id, permission_type from group_network_membership where network_id = ?",
				"delete from group_network_membership where network_id = ?",
				"delete from network_set_member where network_id = ?"
			};

		for (String cmd : sqlCmds) {
			try (PreparedStatement st = db.prepareStatement(cmd) ) {
				st.setObject(1, networkId);
				st.executeUpdate();
			}		
		}
		
		//auto response to pending request.
		String sql = "update request set response ='DECLINED', responsemessage = 'NDEx auto response: network has been deleted.', responsetime = localtimestamp, " +
				" responder = ? where request_type <> 'JoinGroup' and is_deleted=false and response ='PENDING' and destinationuuid = ?"; 
		try (PreparedStatement st = db.prepareStatement(sql) ) {
			st.setObject(1, userId);
			st.setObject(2, networkId);
			st.executeUpdate();
		}		
		
	}
	

	public UUID getNetworkOwner(UUID networkId) throws SQLException, NdexException {
		String sqlStr = "select owneruuid from network where  is_deleted=false and  \"UUID\" = ? ";
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setObject(1, networkId);
			try (ResultSet rs = pst.executeQuery()) {
				if ( rs.next()) {
					return (UUID)rs.getObject(1);
				}
				throw new ObjectNotFoundException("Network "+ networkId + " not found.");
			}
		}	
	}
	
	public String getNetworkName(UUID networkId) throws SQLException, NdexException {
		return getStringField(networkId, "name");
	}

	public String getNetworkDOI(UUID networkId) throws SQLException, NdexException {
		return getStringField(networkId, "ndexdoi");
	}
	
	private String getStringField(UUID networkId, String fieldName) throws SQLException, ObjectNotFoundException {
		String sqlStr = "select " + fieldName + " from network where  is_deleted=false and  \"UUID\" = ? ";
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setObject(1, networkId);
			try (ResultSet rs = pst.executeQuery()) {
				if ( rs.next()) {
					return rs.getString(1);
				}
				throw new ObjectNotFoundException("Network "+ networkId + " not found.");
			}
		}		
	}
	
	public Set<Long> getSubNetworkId(UUID networkId) throws SQLException, ObjectNotFoundException {
		String sqlStr = "select subnetworkids from network where  is_deleted=false and  \"UUID\" = ? ";
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setObject(1, networkId);
			try (ResultSet rs = pst.executeQuery()) {
				if ( rs.next()) {
					
					Array subNetworkIds = rs.getArray(1);
					if ( subNetworkIds != null) {
						Long[] subNetIds = (Long[]) subNetworkIds.getArray();
						return new HashSet<> (Arrays.asList(subNetIds));
					}
					return new HashSet<>();
				}
				throw new ObjectNotFoundException("Network "+ networkId + " not found.");
			}
		}	
	}
	
	
	public int getNodeCount(UUID networkId) throws SQLException, ObjectNotFoundException {
		String sqlStr = "select nodecount from network where  is_deleted=false and  \"UUID\" = ? ";
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setObject(1, networkId);
			try (ResultSet rs = pst.executeQuery()) {
				if ( rs.next()) {
					return rs.getInt(1);
				}
				throw new ObjectNotFoundException("Network "+ networkId + " not found.");
			}
		}	
	}
	
	
	
	public VisibilityType getNetworkVisibility(UUID networkId) throws SQLException, NdexException {
		String sqlStr = "select n.visibility from network n where  is_deleted=false and  \"UUID\" = ? ";
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setObject(1, networkId);
			try (ResultSet rs = pst.executeQuery()) {
				if ( rs.next()) {
				   return VisibilityType.valueOf(rs.getString(1));
				}
				throw new ObjectNotFoundException("Network "+ networkId + " not found.");
			}
		}	
	}
	
	
	public String getNetworkOwnerAcc(UUID networkId) throws SQLException, NdexException {
		String sqlStr = "select owner from network where  is_deleted=false and  \"UUID\" = ? ";
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setObject(1, networkId);
			try (ResultSet rs = pst.executeQuery()) {
				if ( rs.next()) {
					return rs.getString(1);
				}
				throw new ObjectNotFoundException("Network "+ networkId + " not found in NDEx.");
			}
		}	
	}
	
	public Timestamp getNetworkCreationTime(UUID networkId) throws SQLException, ObjectNotFoundException {
		String sqlStr = "select creation_time from network where \"UUID\" = ? and is_deleted=false";
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setObject(1, networkId);
			try (ResultSet rs = pst.executeQuery()) {
				if ( rs.next()) {
					return rs.getTimestamp(1);
				}
				throw new ObjectNotFoundException("Network "+ networkId + " not found in NDEx.");
			}
		}	
	}
	
	/**
	 * Set a flag in the network entry. 
	 * @param fieldName
	 * @param value
	 * @throws SQLException 
	 * @throws NdexException 
	 */
	
	public void setFlag(UUID networkId, String fieldName, boolean value) throws SQLException {
		String sqlStr = "update network set "
				+ fieldName + "=" + value + " where \"UUID\" = ? and is_deleted = false";
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setObject(1, networkId);
			int i = pst.executeUpdate();
			if ( i != 1)
				throw new SQLException ("Failed to set flag " + fieldName + "="  + value + " in db for network " + networkId.toString());
		}
	}

	
	/**
	 * Remove the network summary information for the given network. Modification_time will be updated and the network will be set to the un-validated 
	 * state. All previous error and warnings are removed from db. 
	 * @param fieldName
	 * @param value
	 * @throws SQLException 
	 * @throws NdexException 
	 */
	
	public void clearNetworkSummary(UUID networkId, long fileSize) throws SQLException, NdexException {
		String sqlStr = "update network set modification_time = localtimestamp, name = null,"
				+ "description = null, edgeCount = null, nodeCount = null, isComplete=false,"
				+ " properties = null, cxmetadata = null, cx2metadata = null,"
				+ "version = null, is_validated = false, error = null, warnings = null,subnetworkids = null, cx_file_size = ? where \"UUID\" ='" +
				 networkId.toString() + "' and is_deleted = false";
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setLong(1, fileSize);
			int i = pst.executeUpdate();
			if ( i != 1)
				throw new NdexException ("Failed to reset network "+ networkId + "'s entry in db.");
		}
	
	}
	
	
	/**
	 * Pass in a partial summary to initialize the db entry. Only the name, description, version, edge and node counts are used
	 * in this function.
	 * @param networkSummary
	 * @throws SQLException 
	 * @throws NdexException 
	 * @throws JsonProcessingException 
	 */
	public void saveNetworkEntry(NetworkSummary networkSummary, MetaDataCollection metadata, boolean setModificationTime) throws SQLException, NdexException, JsonProcessingException {
		String sqlStr = "update network set name = ?, description = ?, version = ?, edgecount=?, nodecount=?, "
				+ "properties = ? ::jsonb, cxmetadata = ? :: json, warnings = ?, subnetworkids = ?, "
				+ (setModificationTime? "modification_time = localtimestamp, " : "") 
				+ " is_validated =true where \"UUID\" = ? and is_deleted = false";
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setString(1,networkSummary.getName());
			pst.setString(2, networkSummary.getDescription());
			pst.setString(3, networkSummary.getVersion());
			pst.setInt(4, networkSummary.getEdgeCount());
			pst.setInt(5, networkSummary.getNodeCount());
			
			ObjectMapper mapper = new ObjectMapper();
			if ( networkSummary.getProperties()!=null && networkSummary.getProperties().size() >0 ) {
		        String s = mapper.writeValueAsString( networkSummary.getProperties());
				pst.setString(6, s);
			} else {
				pst.setString(6, null);
			}
					
			if (metadata !=null) {
		        String s = mapper.writeValueAsString( metadata);
				pst.setString(7, s);
			}	else 
				pst.setString(7, null);
			
			// set warnings
			String[] warningArray = networkSummary.getWarnings().toArray(new String[0]);
			Array arrayWarnings = db.createArrayOf("text", warningArray);
			pst.setArray(8, arrayWarnings);
			
			//set subnetworkIds
			Long[] subNetIds = networkSummary.getSubnetworkIds().toArray(new Long[0]);
			Array subNetworkIds = db.createArrayOf("bigint", subNetIds);
			pst.setArray(9, subNetworkIds);
			
			pst.setObject(10, networkSummary.getExternalId());
			int i = pst.executeUpdate();
			if ( i != 1)
				throw new NdexException ("Failed to update network summary entry in db.");
		}
	}

	/**
	 * Pass in a partial summary to initialize the db entry. Only the name, description, version, edge and node counts are used
	 * in this function.
	 * @param networkSummary
	 * @throws SQLException 
	 * @throws NdexException 
	 * @throws JsonProcessingException 
	 */
	public void saveCX2NetworkEntry(NetworkSummary networkSummary, Map<String,CxMetadata> metadata, boolean setModificationTime) throws SQLException, NdexException, JsonProcessingException {
		String sqlStr = "update network set name = ?, description = ?, version = ?, edgecount=?, nodecount=?, "
				+ "properties = ? ::jsonb, cx2metadata = ? :: json, warnings = ?, subnetworkids = ?, cxformat= ?, "
				+ (setModificationTime? "modification_time = localtimestamp, " : "") 
				+ " is_validated =true where \"UUID\" = ? and is_deleted = false";
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setString(1,networkSummary.getName());
			pst.setString(2, networkSummary.getDescription());
			pst.setString(3, networkSummary.getVersion());
			pst.setInt(4, networkSummary.getEdgeCount());
			pst.setInt(5, networkSummary.getNodeCount());
			
			ObjectMapper mapper = new ObjectMapper();
			if ( networkSummary.getProperties()!=null && networkSummary.getProperties().size() >0 ) {
		        String s = mapper.writeValueAsString( networkSummary.getProperties());
				pst.setString(6, s);
			} else {
				pst.setString(6, null);
			}
					
			if (metadata !=null) {
		        String s = mapper.writeValueAsString( new ArrayList<>(metadata.values()));
				pst.setString(7, s);
			}	else 
				pst.setString(7, null);
			
			// set warnings
			String[] warningArray = networkSummary.getWarnings().toArray(new String[0]);
			Array arrayWarnings = db.createArrayOf("text", warningArray);
			pst.setArray(8, arrayWarnings);
			
			//set subnetworkIds
			Long[] subNetIds = networkSummary.getSubnetworkIds().toArray(new Long[0]);
			Array subNetworkIds = db.createArrayOf("bigint", subNetIds);
			pst.setArray(9, subNetworkIds);
			
			pst.setString(10, networkSummary.getCxFormat());
			
			pst.setObject(11, networkSummary.getExternalId());
			int i = pst.executeUpdate();
			if ( i != 1)
				throw new NdexException ("Failed to update network summary entry in db.");
		} 
	}
	
	
/*
	public void saveNetworkMetaData(UUID networkId, MetaDataCollection metadata) throws SQLException, NdexException, JsonProcessingException {
		String sqlStr = "update network set  cxmetadata = ? :: json where \"UUID\" = ? and is_deleted = false";
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
					
			if (metadata !=null) {
				ObjectMapper mapper = new ObjectMapper();
		        String s = mapper.writeValueAsString( metadata);
				pst.setString(1, s);
			}	else 
				pst.setString(1, null);
			pst.setObject(2, networkId);
			int i = pst.executeUpdate();
			if ( i != 1)
				throw new NdexException ("Failed to update network metadata entry in db.");
		}
	}
*/	
	/**
	 * Only update fields that relates to the network content. Permission related fields are not updated.
	 * in this function.
	 * @param networkSummary
	 * @throws SQLException 
	 * @throws NdexException 
	 * @throws JsonProcessingException 
	 */
/*	public void updateNetworkCoreInfo(NetworkSummary networkSummary, ProvenanceEntity provenance, MetaDataCollection metadata) throws SQLException, NdexException, JsonProcessingException {
		String sqlStr = "update network set name = ?, description = ?, version = ?, edgecount=?, nodecount=?, "
				+ "properties = ? ::jsonb, provenance = ? :: jsonb, cxmetadata = ? :: json, warnings = ?, "
				+ " is_validated =true where \"UUID\" = ?";
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setString(1,networkSummary.getName());
			pst.setString(2, networkSummary.getDescription());
			pst.setString(3, networkSummary.getVersion());
			pst.setInt(4, networkSummary.getEdgeCount());
			pst.setInt(5, networkSummary.getNodeCount());
			
			if ( networkSummary.getProperties()!=null && networkSummary.getProperties().size() >0 ) {
				ObjectMapper mapper = new ObjectMapper();
		        String s = mapper.writeValueAsString( networkSummary.getProperties());
				pst.setString(6, s);
			} else {
				pst.setString(6, null);
			}
			
			if ( provenance != null ) {
				ObjectMapper mapper = new ObjectMapper();
		        String s = mapper.writeValueAsString( provenance);
				pst.setString(7, s);
			} else 
				pst.setString(7, null);
				
			if (metadata !=null) {
				ObjectMapper mapper = new ObjectMapper();
		        String s = mapper.writeValueAsString( metadata);
				pst.setString(8, s);
			}	else 
				pst.setString(8, null);
			
			// set warnings
			String[] warningArray = networkSummary.getWarnings().toArray(new String[0]);
			Array arrayWarnings = db.createArrayOf("text", warningArray);
			pst.setArray(9, arrayWarnings);
			
			pst.setObject(10, networkSummary.getExternalId());
			int i = pst.executeUpdate();
			if ( i != 1)
				throw new NdexException ("Failed to update network summary entry in db.");
		}
	} */
	
	
	/**
	 * We assume the alias of network table is n in this function. so make sure this is true when using this function to construct your sql.
	 * @param userId
	 * @return
	 */
	protected static String createIsReadableConditionStr(UUID userId) {
		if ( userId == null)
			return "n.visibility='PUBLIC'";
		return "( n.visibility='PUBLIC' or n.owneruuid = '" + userId + "' ::uuid or " + 
			" exists ( select 1 from user_network_membership un1 where un1.network_id = n.\"UUID\" and un1.user_id = '"+ userId + "' limit 1) or " +
		    " exists ( select 1 from group_network_membership gn1, ndex_group_user gu where gn1.group_id = gu.group_id "
		    + "and gn1.network_id = n.\"UUID\" and gu.user_id = '"+ userId + "' limit 1) )";
	}

	
	public boolean isReadable(UUID networkID, UUID userId) throws SQLException, ObjectNotFoundException {
		String sqlStr = "select (" + createIsReadableConditionStr(userId) + ") from network n where n.\"UUID\" = ? and n.is_deleted=false ";		
			
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setObject(1, networkID);

			try ( ResultSet rs = pst.executeQuery()) {
				if ( rs.next()) 
					return rs.getBoolean(1);
				 
				throw new ObjectNotFoundException("Network", networkID);
			}
		}
	}
	
	public boolean isWriteable(UUID networkID, UUID userId) throws SQLException {
		String sqlStr = "select 1 from network n where n.\"UUID\" = ? and n.is_deleted=false and (";
		
		sqlStr += " n.owneruuid = ? or "
				+ " exists ( select 1 from user_network_membership un1 where un1.network_id = n.\"UUID\" and un1.user_id = ? and un1.permission_type = 'WRITE' limit 1) or " +
				  " exists ( select 1 from group_network_membership gn1, ndex_group_user gu where gn1.group_id = gu.group_id "
				  + "   and gn1.network_id = n.\"UUID\" and gu.user_id = ? and gn1.permission_type = 'WRITE' limit 1) " ;

		sqlStr += ")";
			
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setObject(1, networkID);
				pst.setObject(2, userId);
				pst.setObject(3, userId);
				pst.setObject(4, userId);
			
			try ( ResultSet rs = pst.executeQuery()) {
				return rs.next();
			}
		}
	}
	
	public boolean isReadOnly(UUID networkID) throws SQLException, ObjectNotFoundException {
		return getBooleanFlag (networkID, "readonly");		
	}
	
	public boolean hasDOI(UUID networkID) throws SQLException, ObjectNotFoundException {
		return getBooleanFlag (networkID, "ndexdoi is not null");		
	}
	
	public boolean isCertified (UUID networkID) throws ObjectNotFoundException, SQLException {
		return getBooleanFlag (networkID, "certified");		
	}
	
	private boolean getBooleanFlag(UUID networkID, String fieldName) throws SQLException, ObjectNotFoundException {
		String sqlStr = "select " + fieldName + " from network n where n.\"UUID\" = ? and n.is_deleted=false ";
			
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setObject(1, networkID);
			try ( ResultSet rs = pst.executeQuery()) {
				if( rs.next() ) {
					return rs.getBoolean(1);
				}
				throw new ObjectNotFoundException("Network", networkID );
			}
		}
	}	
	
	
	/** 
	 * return true if the network is showcased in the owner's account.
	 * @param networkID
	 * @return
	 * @throws SQLException
	 * @throws ObjectNotFoundException
	 */
	public boolean isShowCased(UUID networkID) throws SQLException, ObjectNotFoundException {
		return getBooleanFlag(networkID, "show_in_homepage");
		
	}

	/*
	@Deprecated
	private boolean hasSolrIndex(UUID networkID) throws SQLException, ObjectNotFoundException {
		return getBooleanFlag(networkID, "solr_indexed");
	} */	
	
	public NetworkIndexLevel getIndexLevel (UUID networkId) throws SQLException, ObjectNotFoundException {
		String sqlStr = "select solr_idx_lvl from network n where n.\"UUID\" = ? and n.is_deleted=false ";
		
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setObject(1, networkId);
			try ( ResultSet rs = pst.executeQuery()) {
				if( rs.next() ) {
					return NetworkIndexLevel.valueOf(rs.getString(1));
				}
				throw new ObjectNotFoundException("Network", networkId );
			}
		}
	}
	
	public void setIndexLevel(UUID networkId, NetworkIndexLevel lvl) throws SQLException, NdexException {
		String sqlStr = "update network set solr_idx_lvl =? where \"UUID\" = ? and is_deleted=false";
		
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setObject(2, networkId);
			pst.setString(1, lvl.toString());
			int cnt = pst.executeUpdate();
			if ( cnt !=1) {
				throw new NdexException ("Failed to Update network index level in db. Invalid network UUID.");
			}
		}
	}
	
	
	public boolean isAdmin(UUID networkID, UUID userId) throws SQLException {
		String sqlStr = "select 1 from network n where n.\"UUID\" = ? and n.is_deleted=false and n.owneruuid= ?";
			
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setObject(1, networkID);
			pst.setObject(2, userId);
			try ( ResultSet rs = pst.executeQuery()) {
				return rs.next() ;
			}
		}
	}	
	
	
	/**
	 * Set the islocked flag to true in the db.
	 * This is an atomic operation. Will commit the current transaction.
	 * @param networkID
	 * @throws SQLException 
	 * @throws InterruptedException 
	 * @throws NdexException 
	 */
	public void lockNetwork(UUID networkId) throws SQLException, NetworkConcurrentModificationException {
		//setNetworkLock(networkId,true);
		
		String sql = "update network set islocked= true where \"UUID\" = ? and is_deleted=false and islocked =false";
		try ( PreparedStatement p = db.prepareStatement(sql)) {
			p.setObject(1, networkId);
			for( int j = 0 ; j < 3 ; j++ )  {
				int i = p.executeUpdate();
				if ( i ==1) {
					db.commit();
					return;
				}
				try {
					Thread.sleep(400);
				} catch (InterruptedException e) {
					logger.info("Interrrupted when trying to lock network " + networkId + ": " + e.getMessage()+
							".");
					break;
				}
			}
			throw new NetworkConcurrentModificationException();
		}
	}
	
	
	private void setNetworkLock(UUID networkId, boolean lock) throws SQLException {
		String sql = "update network set islocked=? where \"UUID\" = ? and is_deleted=false";
		try ( PreparedStatement p = db.prepareStatement(sql)) {
			p.setBoolean(1, lock);
			p.setObject(2, networkId);
			p.executeUpdate();
			//if ( i !=1)
			//	throw new ObjectNotFoundException("network",networkId);
		}
	}
	
	
	/**
	 * Set the islocked flag to false in the db.
	 * This is an atomic operation. Will commit the current transaction.
	 * @param networkID
	 * @throws SQLException 
	 */
	public void unlockNetwork (UUID networkId) throws  SQLException {
		setNetworkLock(networkId,false);
		db.commit(); 
	}
	
	public boolean networkIsLocked(UUID networkUUID) throws ObjectNotFoundException, SQLException {
		return getBooleanFlag(networkUUID, "islocked");
	}

	// max retry is set at 10 by this function. Retry interval is 0.5 second
	public boolean networkIsLocked(UUID networkUUID,int retry) throws ObjectNotFoundException, SQLException, InterruptedException {
		String sql = "select islocked from network where \"UUID\" = ? and is_deleted = false";
		try(PreparedStatement p = db.prepareStatement(sql)){
			p.setObject(1, networkUUID);
			try ( ResultSet rs = p.executeQuery()) {
				if ( rs.next()) {
					boolean islocked =  rs.getBoolean(1);
					if ( !islocked || retry <=0 ) {
						return islocked;
					} 
					Thread.sleep(500);
					return networkIsLocked(networkUUID, (retry>10 ? 10: retry -1));					
				}
				throw new ObjectNotFoundException ("network",networkUUID);
			}
		}
	}
	
	public boolean networkIsValid(UUID networkUUID) throws ObjectNotFoundException, SQLException {
		return getBooleanFlag(networkUUID,"is_validated");
	}
	
	
	@Deprecated
	public ProvenanceEntity getProvenance(UUID networkId) throws JsonParseException, JsonMappingException, IOException, ObjectNotFoundException, SQLException {
		String sql = "select provenance from network where \"UUID\" = ? and is_deleted = false";
		try (PreparedStatement p = db.prepareStatement(sql)) {
			p.setObject(1, networkId);
			try (ResultSet rs = p.executeQuery()) {
				if ( rs.next()) {
					String s = rs.getString(1);
					if ( s != null) {
						ObjectMapper mapper = new ObjectMapper(); 
				        ProvenanceEntity o = mapper.readValue(s, ProvenanceEntity.class); 	
				        return o;
					}
					return new ProvenanceEntity();
				}
				throw new ObjectNotFoundException ("network",networkId);
			}
		}
		
	}
    
	@Deprecated
	public int setProvenance(UUID networkId, ProvenanceEntity provenance) throws SQLException, NdexException, IOException {
		
/*		MetaDataCollection metadata = getMetaDataCollection (networkId);
		if ( provenance == null) {
			metadata.remove(Provenance.ASPECT_NAME);
		} else {
			MetaDataElement e = metadata.getMetaDataElement(Provenance.ASPECT_NAME);
			if ( e == null) {
				e = new MetaDataElement (Provenance.ASPECT_NAME,"1.0");
				
				long cg = 1;
				if (metadata.getMetaDataElement(NodesElement.ASPECT_NAME) != null) {
					Long cgL = metadata.getMetaDataElement(NodesElement.ASPECT_NAME).getConsistencyGroup();
					if (cgL !=null)
						cg = cgL.longValue();
				}
				e.setConsistencyGroup(Long.valueOf(cg));
				e.setElementCount(Long.valueOf(1L));
				metadata.addAt(0, e);
			}		
//			metadata.setLastUpdate(Provenance.ASPECT_NAME, Calendar.getInstance().getTimeInMillis());
		} */
			
		// get the network document
		String sql = " update network set provenance = ? :: jsonb where \"UUID\"=? and is_deleted=false";
		try (PreparedStatement p = db.prepareStatement(sql)) {
			
			if ( provenance != null) {
				ObjectMapper mapper = new ObjectMapper();
				String s = mapper.writeValueAsString(provenance);
				p.setString(1, s);
			} else 
				p.setString(1, null);
			
		/*	ObjectMapper mapper = new ObjectMapper();
		    String s = mapper.writeValueAsString( metadata);
		    p.setString(2, s); */
			
			p.setObject(2, networkId);
			int cnt = p.executeUpdate();
			if ( cnt != 1) {
				throw new NdexException ("Failed to update db, network not found or locked by another update process");
			}
			return cnt;
		}
		
		
	}
	



	/**************************************************************************
	    * getAllAdminUsers on a network
	    *
	    * @param networkId
	    *            UUID for network
	    * @throws NdexException
	    *            Invalid parameters or an error occurred while accessing the database
	    * @throws ObjectNotFoundException
	    * 			Invalid groupId
	    **************************************************************************/
	
/*	public Set<String> getAdminUsersOnNetwork(String networkId) 
			throws ObjectNotFoundException, NdexException {
		Preconditions.checkArgument(!Strings.isNullOrEmpty(networkId.toString()),
		
				"A network UUID is required");

		ODocument network = this.getRecordByUUIDStr(networkId, NdexClasses.Network);
		
		Set<String> adminUUIDStrs = new TreeSet<>();
			
		String networkRID = network.getIdentity().toString();
			
		String traverseCondition = "in_" + Permissions.ADMIN + ",in_" + Permissions.GROUPADMIN + ",in_" + Permissions.MEMBER;   
			
		OSQLSynchQuery<ODocument> query = new OSQLSynchQuery<>(
		  			"SELECT " +
		  					NdexClasses.ExternalObj_ID + ", $path" +
			        " FROM"
		  			+ " (TRAVERSE "+ traverseCondition.toLowerCase() +" FROM"
		  				+ " " + networkRID
		  				+ "  WHILE $depth <=3)"
		  			+ " WHERE @class = '" + NdexClasses.User + "' " +" AND  " + NdexClasses.ExternalObj_isDeleted + " = false ");
			
			List<ODocument> records = this.db.command(query).execute(); 
			for(ODocument member: records) {
				adminUUIDStrs.add( (String) member.field(NdexClasses.ExternalObj_ID) );
			}
			
			logger.info("Successfuly retrieved network-user memberships");
			return adminUUIDStrs;
	} */
	
	
	   /**
	    * Get all the direct membership on a network.
	    * @param networkId
	    * @return A 2 elements array. First element is a map about user permissions and the second is about group permissions. For the inner map, the
	    *  key is one of the permission type, value is a collection of account names that have that permission.
	    *  If an account has a write privilege on the network this function won't duplicate that account in the read permission list.
	    * @throws ObjectNotFoundException
	    * @throws NdexException
	 * @throws SQLException 
	    */
		public List<Map<Permissions, Collection<String>>> getAllMembershipsOnNetwork(UUID networkId) 
				throws ObjectNotFoundException, NdexException, SQLException {
			Preconditions.checkArgument(!Strings.isNullOrEmpty(networkId.toString()),	
					"A network UUID is required");
			
			Map<Permissions,Collection<String>> userMemberships = new HashMap<>();
			
			userMemberships.put(Permissions.ADMIN, new ArrayList<String> ());
			userMemberships.put(Permissions.WRITE, new ArrayList<String> ());
			userMemberships.put(Permissions.READ, new ArrayList<String> ());
			
			Map<Permissions, Collection<String>> grpMemberships = new HashMap<>();
			grpMemberships.put(Permissions.READ, new ArrayList<String> ());
			grpMemberships.put(Permissions.WRITE, new ArrayList<String> ()); 
			
		    ArrayList<Map<Permissions,Collection<String>>> fullMembership = new ArrayList<> (2);
		    fullMembership.add(0, userMemberships);
		    fullMembership.add(1,grpMemberships);

		    String sqlStr = "select u.user_name, b.per from  (select a.user_id, max(a.per) as per from "+
		    		"(select owneruuid as user_id, 'ADMIN' :: ndex_permission_type as per from network where \"UUID\" = ? "+
		    		 " union select user_id, permission_type as per from user_network_membership where network_id = ?) a "
		    		 + "group by a.user_id) b, ndex_user u where u.\"UUID\"= b.user_id";
			
		    try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
		    	pst.setObject(1, networkId);
		    	pst.setObject(2, networkId);
		    	try (ResultSet rs = pst.executeQuery()) {
		    		while ( rs.next()) {
		    			Collection<String> userSet = userMemberships.get(Permissions.valueOf(rs.getString(2)));
		    			userSet.add(rs.getString(1));
		    			
		    		}
		    	}
		    }
		    
		    sqlStr = "select group_id, permission_type from group_network_membership where network_id = ?";
			
		    try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
		    	pst.setObject(1, networkId);
		    	try (ResultSet rs = pst.executeQuery()) {
		    		while ( rs.next()) {
		    			Collection<String> grpSet = grpMemberships.get(Permissions.valueOf(rs.getString(2)));
		    			grpSet.add(rs.getString(1));
		    			
		    		}
		    	}
		    }
				
			return fullMembership;
		}
		
		/**
		 * This function only for importing network from ndex 1.3.
		 * @param networkId
		 * @param properties
		 * @return
		 * @throws ObjectNotFoundException
		 * @throws NdexException
		 * @throws SolrServerException
		 * @throws IOException
		 * @throws SQLException
		 */
		public int updateNetworkProperties (UUID networkId, Collection<NdexPropertyValuePair> properties
				 ) throws ObjectNotFoundException, NdexException, SolrServerException, IOException, SQLException {

			//filter out the source format attribute.
			List<NdexPropertyValuePair> props = new ArrayList<>(properties.size());
			for ( NdexPropertyValuePair p : properties ) {
				if (!p.getPredicateString().equals(NdexClasses.Network_P_source_format))
					props.add(p);
			}
						
			String sqlStr = "update network set properties = ? ::jsonb where \"UUID\" = ? and is_deleted = false";
			try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
				
				if ( props.size() > 0 ) {
					ObjectMapper mapper = new ObjectMapper();
			        String s = mapper.writeValueAsString( props);
					pst.setString(1, s);
				} else {
					pst.setString(1, null);
				}
				
				pst.setObject(2, networkId);
				int i = pst.executeUpdate();
				if ( i != 1)
					throw new NdexException ("Failed to update network property in db.");
			}

			return props.size();
		}

    /**
	 * This function sets network properties using the given property list. All Existing properties
	 * of the network will be deleted. 
	 * @param networkId
	 * @param properties
	 * @return
	 * @throws ObjectNotFoundException
	 * @throws NdexException
     * @throws IOException 
     * @throws SolrServerException 
     * @throws SQLException 
	 */
	public int setNetworkProperties (UUID networkId, Collection<NdexPropertyValuePair> properties
			) throws ObjectNotFoundException, NdexException, SolrServerException, IOException, SQLException {

		//filter out the source format attribute.
		List<NdexPropertyValuePair> props = new ArrayList<>(properties.size());
		for ( NdexPropertyValuePair p : properties ) {
			if (!p.getPredicateString().equals(NdexClasses.Network_P_source_format))
				props.add(p);
		}
		
//		Date updateTime = Calendar.getInstance().getTime();
		
		String sqlStr = "update network set properties = ? ::jsonb, modification_time = localtimestamp, iscomplete=false where \"UUID\" = ? and is_deleted = false";
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			
			if ( props.size() > 0 ) {
				ObjectMapper mapper = new ObjectMapper();
		        String s = mapper.writeValueAsString( props);
				pst.setString(1, s);
			} else {
				pst.setString(1, null);
			}
			
			pst.setObject(2, networkId);
			int i = pst.executeUpdate();
			if ( i != 1)
				throw new NdexException ("Failed to update network property in db.");
		}

		
		// update the solr Index
	/*	if (!ignoreIndex) {
			NetworkGlobalIndexManager globalIdx = new NetworkGlobalIndexManager();
			globalIdx.updateNetworkProperties(networkId.toString(), props, updateTime);
		} */
		return props.size();
	}
	
	
	
	
	public NetworkSearchResult findNetworks(SimpleNetworkQuery simpleNetworkQuery, int skipBlocks, int top, User loggedInUser) throws NdexException, SolrServerException, IOException, SQLException {
	
		String queryStr = simpleNetworkQuery.getSearchString().trim();
		
		// special check for UUID search
		UUID uuid = getUUIDFromQuery(queryStr);
		if ( uuid != null ) {
			NetworkSearchResult r = new NetworkSearchResult(); 
			r.setStart(0);
			try {
			if ( isReadable(uuid, (loggedInUser ==null? null : loggedInUser.getExternalId()))) {
					NetworkSummary s = getNetworkSummaryById(uuid);
					r.setNumFound(1);
					r.getNetworks().add(s);
					return r;
			} 
			} catch (@SuppressWarnings("unused") ObjectNotFoundException e) {/*do nothing*/}
			r.setNumFound(0);
			return r;
		}
		
		if (queryStr.equals("*") || queryStr.length() == 0)
			queryStr = "*:*";

		if (simpleNetworkQuery.getPermission() != null && simpleNetworkQuery.getPermission() == Permissions.ADMIN)
			throw new NdexException("Permission can only be WRITE or READ in this function.");

		try (NetworkGlobalIndexManager networkIdx = new NetworkGlobalIndexManager()) {

			// prepare the query.
			// if (simpleNetworkQuery.getPermission() == null)
			// simpleNetworkQuery.setPermission(Permissions.READ);

			List<UUID> groupUUIDs = new ArrayList<>();
			if (loggedInUser != null && simpleNetworkQuery.getIncludeGroups()) {
				try (UserDAO userDao = new UserDAO()) {
					for (Membership m : userDao.getUserGroupMemberships(loggedInUser.getExternalId(),
							Permissions.MEMBER, 0, 0, true)) {
						groupUUIDs.add(m.getResourceUUID());
					}
				}
			}

			SolrDocumentList solrResults = networkIdx.searchForNetworks(queryStr,
					(loggedInUser == null ? null : loggedInUser.getUserName()), top, skipBlocks * top,
					simpleNetworkQuery.getAccountName(), simpleNetworkQuery.getPermission(), groupUUIDs);

			List<NetworkSummary> results = new ArrayList<>(solrResults.size());
			for (SolrDocument d : solrResults) {
				String id = (String) d.get(NetworkGlobalIndexManager.UUID);
				try {
					NetworkSummary s = getNetworkSummaryById(UUID.fromString(id));

					if (s != null) {
						s.setWarnings(emptyStringList);
						results.add(s);
					}
				} catch (ObjectNotFoundException ne) {
					logger.warning("Network " + id + " was not found in db: " + ne.getMessage());
				}
			}

			return new NetworkSearchResult(solrResults.getNumFound(), solrResults.getStart(), results);
		}
	}
	

	private static UUID getUUIDFromQuery(String query) {
		 Matcher m = uuidSearchPattern.matcher(query);
		 if( m.matches() ) {
			String uuidStr = m.group(5);
			if ( uuidStr == null) 
				uuidStr = m.group(2);
			try {
				return UUID.fromString(uuidStr);
			} catch( @SuppressWarnings("unused") IllegalArgumentException e ) {
				// just to be safe 
				return null;
			}
		 }
		 return null;
	}
	
	public NetworkSummary getNetworkSummaryById (UUID networkId) throws SQLException, ObjectNotFoundException, JsonParseException, JsonMappingException, IOException {
		// be careful when modify the order or the select clause becaue populateNetworkSummaryFromResultSet function depends on the order.
		String sqlStr = networkSummarySelectClause + " from network n where n.\"UUID\" = ? and n.is_deleted= false";
		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
			p.setObject(1, networkId);
			try ( ResultSet rs = p.executeQuery()) {
				if ( rs.next()) {
					NetworkSummary result = new NetworkSummary();
					populateNetworkSummaryFromResultSet(result,rs);
					
					return result;
				}
				throw new ObjectNotFoundException("Network " + networkId + " not found in db.");
			}
		}
	}
	
	public int getNetworkEdgeCount (UUID networkId) throws SQLException, ObjectNotFoundException {
		String sqlStr = "select n.edgecount from network n where n.\"UUID\" = ? and n.is_deleted= false";
		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
			p.setObject(1, networkId);
			try ( ResultSet rs = p.executeQuery()) {
				if ( rs.next()) {
					return rs.getInt(1);
				}
				throw new ObjectNotFoundException("Network " + networkId + " not found in db.");
			}
		}
	}
		
	
	private static String cvtUUIDListToStr (List<UUID> uuids) {
		if (uuids == null) return null;
		
		StringBuffer cnd = new StringBuffer() ;
		for ( UUID id : uuids ) {
			if (cnd.length()>1)
				cnd.append(',');
			cnd.append('\'');
			cnd.append(id);
			cnd.append('\'');			
		}
		return cnd.toString();
	}
	
	public Map<String,String> getNetworkPermissionMapByNetworkIds(UUID userId, List<UUID> networkIds)
			throws SQLException {
	
		Map<String,String> result = new TreeMap<>();
		if ( networkIds.isEmpty())
			return result;

		String uuidListStr = cvtUUIDListToStr(networkIds);
		String queryStr = "select \"UUID\" as network_id, 'ADMIN' :: ndex_permission_type as permission_type " + 
						"from network n where n.is_deleted=false and owneruuid = '" + userId.toString() + "' :: uuid and n.\"UUID\" in ( " + uuidListStr + ")";
					
			queryStr = " select a.network_id, max(a.permission_type) as permission_type from (" +  queryStr + " union "   +
					" select un.network_id, un.permission_type " + 
					"from user_network_membership un where un.user_id = '"+ userId.toString() + "' :: uuid " +
					" union select gn.network_id, gn.permission_type from ndex_group_user ug, group_network_membership gn " + 
					" where ug.group_id = gn.group_id and ug.user_id = '" + userId + "' :: uuid " +" ) a where a.network_id in (" + 
					  uuidListStr + ") group by a.network_id ";
					

		try (PreparedStatement st = db.prepareStatement(queryStr))  {		
			try (ResultSet rs = st.executeQuery() ) {
				while (rs.next()) {
					result.put(rs.getObject(1).toString(), rs.getString(2));
				} 
			}
		}
		
		return result;
	}
	
	
	
	public List<NetworkSummary> getNetworkSummariesByIdStrList (List<String> networkIdstrList, UUID userId, String accessKey) throws SQLException, JsonParseException, JsonMappingException, IOException {
		// be careful when modify the order or the select clause becaue populateNetworkSummaryFromResultSet function depends on the order.
		
		List<NetworkSummary> result = new ArrayList<>(networkIdstrList.size());
		
		if ( networkIdstrList.isEmpty()) return result;
		
		StringBuffer cnd = new StringBuffer() ;
		for ( String idstr : networkIdstrList ) {
			if (cnd.length()>1)
				cnd.append(',');
			cnd.append('\'');
			cnd.append(idstr);
			cnd.append('\'');			
		}
		
		String sqlStr = accessKey == null ? (networkSummarySelectClause 
				+ " from network n where n.\"UUID\" in("+ cnd.toString() + ") and n.is_deleted= false  and " + createIsReadableConditionStr(userId))
				  : ( networkSummarySelectClause 
							+ "from network n where n.\"UUID\" in("+ cnd.toString() + ") and n.is_deleted= false  and ( (" + createIsReadableConditionStr(userId)
				            +  ") or ( n.access_key_is_on and n.access_key = '" + accessKey + "') or " + 
					                 " exists (select 1 from network_set s, network_set_member sm where s.\"UUID\" = sm.set_id "
							                 + "and sm.network_id = n.\"UUID\" and s.access_key_is_on and s.access_key = '"+ accessKey + "' and s.is_deleted=false))" );
		
		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
			try ( ResultSet rs = p.executeQuery()) {
				while ( rs.next()) {
					NetworkSummary s = new NetworkSummary();
					populateNetworkSummaryFromResultSet(s,rs);
					result.add(s);
				}
			}
		}
		return result;
	}
	
	/**
	 * Order in the ResultSet is critical.
	 * @param summary
	 * @param rs
	 * @throws SQLException 
	 * @throws IOException 
	 * @throws JsonMappingException 
	 * @throws JsonParseException 
	 */
	private static void populateNetworkSummaryFromResultSet (NetworkSummary result, ResultSet rs) throws SQLException, JsonParseException, JsonMappingException, IOException {
		result.setCreationTime(rs.getTimestamp(1));
		result.setModificationTime(rs.getTimestamp(2));
		result.setName(rs.getString(3));
		result.setDescription(rs.getString(4));
		result.setVersion(rs.getString(5));
		result.setEdgeCount(rs.getInt(6));
		result.setNodeCount(rs.getInt(7));
		result.setVisibility(VisibilityType.valueOf(rs.getString(8)));
		result.setOwner(rs.getString(9));
		result.setOwnerUUID((UUID)rs.getObject(10));
		String proptiesStr = rs.getString(11);
		
		if ( proptiesStr != null) {
			ObjectMapper mapper = new ObjectMapper(); 
			
			List<NdexPropertyValuePair> o = mapper.readValue(proptiesStr, new TypeReference<List<NdexPropertyValuePair>>() {/*do nothing*/}); 		
			if( o != null)
	         result.setProperties(o);  
		}
		
		result.setExternalId((UUID)rs.getObject(12));
		result.setIsValid(rs.getBoolean(13));
		result.setErrorMessage(rs.getString(14));
		result.setIsReadOnly(rs.getBoolean(15));
		Array warnings = rs.getArray(16);
		if ( warnings != null) {
			String[] wA = (String[]) warnings.getArray();
			List<String> warningList = Arrays.asList(wA);  
			result.setWarnings(warningList);
		}  
		
		result.setIsShowcase(rs.getBoolean(17));
	
		Array subNetworkIds = rs.getArray(18);
		if ( subNetworkIds != null) {
			Long[] subNetIds = (Long[]) subNetworkIds.getArray();
			result.setSubnetworkIds(new HashSet<> (Arrays.asList(subNetIds)));
		}
		
		result.setIndexLevel(NetworkIndexLevel.valueOf(rs.getString(19)));
		result.setCompleted(rs.getBoolean(20));
		result.setDoi(rs.getString(21));
		result.setIsCertified(rs.getBoolean(22));
		result.setHasLayout(rs.getBoolean(23));
		result.setHasSample(rs.getBoolean(24));
		result.setCxFormat(rs.getString(25));
		result.setCxFileSize(rs.getLong(26));
		result.setCx2FileSize(rs.getLong(27));
	}
	
	
	public void updateNetworkProfile(UUID networkId, Map<String,String> newValues) throws NdexException, SQLException {
	
	    	 //update db
		    String sqlStr = "update network set ";
		    List<String> values = new ArrayList<>(newValues.size());
		    for (Map.Entry<String,String> entry : newValues.entrySet()) {
		    		if (values.size() >0)
		    			sqlStr += ", ";
		    		sqlStr += entry.getKey() + " = ?";	
		    		values.add(entry.getValue());
		    }
		    sqlStr += ", modification_time = localtimestamp, iscomplete=false where \"UUID\" = '" + networkId + "' ::uuid and is_deleted=false";
		    
		    try (PreparedStatement p = db.prepareStatement(sqlStr)) {
		    	for ( int i = 0 ; i < values.size(); i++) {
		    		p.setString(i+1, values.get(i));
		    	}
		    	int cnt = p.executeUpdate();
		    	if ( cnt != 1 ) {
		    		throw new NdexException ("Failed to update. Network " + networkId + " might have been locked.");
		    	}
		    }
		
	}

	public void updateNetworkSummary(UUID networkId, NetworkSummary summary) throws NdexException, SQLException, JsonProcessingException {
		
	    String sqlStr = "update network set name =?, description=?, version = ?, properties =? :: jsonb, visibility = ?,"
	    		+ "modification_time = localtimestamp, iscomplete=false where \"UUID\" = '" + networkId + "' ::uuid and is_deleted=false";
	    
	    try (PreparedStatement p = db.prepareStatement(sqlStr)) {
	    	p.setString(1, summary.getName());
	    	p.setString(2, summary.getDescription());
	    	p.setString(3, summary.getVersion());
	    
	    	if ( summary.getProperties() != null && summary.getProperties().size() > 0 ) {
				ObjectMapper mapper = new ObjectMapper();
		        String s = mapper.writeValueAsString( summary.getProperties());
				p.setString(4, s);
			} else {
				p.setString(4, null);
			}
			
	    	p.setString(5, summary.getVisibility().toString());
	    	int cnt = p.executeUpdate();
	    	if ( cnt != 1 ) {
	    		throw new NdexException ("Failed to update. Network " + networkId + " might have been locked.");
	    	}
	    }
}
	
	
	
	public MetaDataCollection getMetaDataCollection(UUID networkId) throws SQLException, IOException, NdexException {
		String sqlStr = "select cxmetadata from network n where n.\"UUID\" =? and n.is_deleted= false" ;
		
		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
			p.setObject(1, networkId);
			try ( ResultSet rs = p.executeQuery()) {
				if ( rs.next()) {
					String s = rs.getString(1);
					if ( s != null) {
						return MetaDataCollection.createInstanceFromJson(s);
					}
					return new MetaDataCollection();
				}
				throw new NdexException ("No metadata found for network " + networkId + " in database.");
			}
		}
		
	}

	/**
	 * Return null if no metadata is found in the db.
	 * @param networkId
	 * @return
	 * @throws SQLException
	 * @throws IOException
	 * @throws NdexException
	 */
	public List<CxMetadata> getCx2MetaDataList(UUID networkId) throws SQLException, IOException, NdexException {
		String sqlStr = "select cx2metadata from network n where n.\"UUID\" =? and n.is_deleted= false" ;
		
		List<CxMetadata> result = new ArrayList<>();
		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
			p.setObject(1, networkId);
			try ( ResultSet rs = p.executeQuery()) {
				if ( rs.next()) {
					String s = rs.getString(1);
					
					if ( s != null) {
						ObjectMapper mapper = new ObjectMapper();
						result = mapper.readValue(s,new TypeReference<List<CxMetadata>>(){});
						
					}
				}
			}
		}
		return result;
		
	}

	public boolean hasCX2(UUID networkId) throws SQLException {
		String sqlStr = "select 1 from network n where n.\"UUID\" =? and n.is_deleted= false and cx2metadata is not null" ;
		
		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
			p.setObject(1, networkId);
			try ( ResultSet rs = p.executeQuery()) {
				if ( rs.next()) {
					 int s = rs.getInt(1);
					
					return s == 1;
				}
			}
		}
		return false;
		
	}
	
	public List<String> getWarnings(UUID networkId) throws SQLException {
		String sqlStr = "select warnings from network n where n.\"UUID\" =? and n.is_deleted= false" ;
		
		List<String> result = new ArrayList<>();
		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
			p.setObject(1, networkId);
			try ( ResultSet rs = p.executeQuery()) {
				if ( rs.next()) {
					
					Array warnings = rs.getArray(1);
					if ( warnings != null) {
						String[] wA = (String[]) warnings.getArray();
						List<String> warningList = Arrays.asList(wA);  
						return warningList;
					}  
					return result;
				}
			}
		}
		return result;
		
	}
	

	public void setCxMetadata(UUID networkId, List<CxMetadata> cx2metadata) throws SQLException, JsonProcessingException, NdexException {
		String sqlStr = "update network set cx2metadata = ? ::jsonb where \"UUID\" = ? and is_deleted=false";
		 try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			ObjectMapper mapper = new ObjectMapper();
		    String s = mapper.writeValueAsString( cx2metadata);
			 pst.setString(1, s);
			 pst.setObject(2, networkId);
			 int i = pst.executeUpdate();
			 if ( i !=1 )
				 throw new NdexException ("Failed to update cx2metadata of Network " + networkId + ". Db record might have been locked.");
		 }
	}
	
	
	public void updateMetadataColleciton(UUID networkId, MetaDataCollection metadata) throws SQLException, JsonProcessingException, NdexException {
		String sqlStr = "update network set cxmetadata = ? ::jsonb where \"UUID\" = ? and is_deleted=false";
		 try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			ObjectMapper mapper = new ObjectMapper();
		    String s = mapper.writeValueAsString( metadata);
			 pst.setString(1, s);
			 pst.setObject(2, networkId);
			 int i = pst.executeUpdate();
			 if ( i !=1 )
				 throw new NdexException ("Failed to update metadata of Network " + networkId + ". Db record might have been locked.");
		 }
	}
	
	public void updateNetworkVisibility (UUID networkId, VisibilityType v, boolean force) throws SQLException, NdexException {
		 String sqlStr = "update network set visibility = '" + v.toString() + "' where \"UUID\" = ? and is_deleted=false";
		 
		 if ( !force) 
			 sqlStr += " and islocked=false";
		 try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			 pst.setObject(1, networkId);
			 int i = pst.executeUpdate();
			 if ( i !=1 )
				 throw new NdexException ("Failed to update visibility. Network " + networkId + " might have been locked.");
		 }
		    	  	
		 //update solr index
	/*	 NetworkGlobalIndexManager networkIdx = new NetworkGlobalIndexManager();

		 networkIdx.updateNetworkVisibility(networkId.toString(), v.toString()); */
		    			
	}
	

	
	
	/**************************************************************************
	    * getNetworkUserMemberships
	    *
	    * @param networkId
	    *            UUID for network
	    * @param permission
	    * 			Type of memberships to retrieve, ADMIN, WRITE, or READ
	    * @param skipBlocks
	    * 			amount of blocks to skip
	    * @param blockSize
	    * 			The size of blocks to be skipped and retrieved
	    * @throws NdexException
	    *            Invalid parameters or an error occurred while accessing the database
	    * @throws ObjectNotFoundException
	    * 			Invalid groupId
	 * @throws SQLException 
	    **************************************************************************/
	
	public List<Membership> getNetworkUserMemberships(UUID networkId, Permissions permission, int skipBlocks, int blockSize) 
			throws ObjectNotFoundException, NdexException, SQLException {
		Preconditions.checkArgument(!Strings.isNullOrEmpty(networkId.toString()),
		
				"A network UUID is required");
		if ( permission !=null )
			Preconditions.checkArgument( 
				(permission.equals( Permissions.ADMIN) )
				|| (permission.equals( Permissions.WRITE ))
				|| (permission.equals( Permissions.READ )),
				"Valid permission required");
		List<Membership> memberships = new ArrayList<>();

		String sql = "select owneruuid as user_id, owner as user_name,name, 'ADMIN' from network where \"UUID\"=? and is_deleted=false";
		if ( permission == null ) {
			sql += " union select un.user_id, u.user_name, n.name, un.permission_type from user_network_membership un, network n, ndex_user u where u.\"UUID\" = un.user_id and "
					+ "n.\"UUID\" = un.network_id and network_id = '" + networkId.toString() + "'";
		}else if ( permission != Permissions.ADMIN) 
			sql = "select user_id, u.user_name, n.name, un.permission_type from user_network_membership un, network n, ndex_user u where u.\"UUID\" = un.user_id and n.\"UUID\" = un.network_id "
					+ "and network_id = ? and un.permission_type = '" + permission.toString() + "'";
		
		if ( skipBlocks>=0 && blockSize>0) {
			sql += " limit " + blockSize + " offset " + skipBlocks * blockSize;
		}
		try ( PreparedStatement p = db.prepareStatement(sql)) {
			p.setObject(1, networkId);
			try ( ResultSet rs = p.executeQuery()) {
				while ( rs.next()) {
					Membership membership = new Membership();
					membership.setMembershipType( MembershipType.NETWORK );
					membership.setMemberAccountName( rs.getString(2)  ); 
					membership.setMemberUUID((UUID) rs.getObject(1) );
					membership.setPermissions(  Permissions.valueOf(rs.getString(4)));
					membership.setResourceName(rs.getString(3) );
					membership.setResourceUUID( networkId );
				
					memberships.add(membership);
				}
			}
		}
		
			
		logger.info("Successfuly retrieved network-user memberships");
		return memberships;
	}


	public Map<String,String> getNetworkUserPermissions(UUID networkId, Permissions permission, int skipBlocks, int blockSize) 
			throws SQLException {
		
		if ( permission !=null )
			Preconditions.checkArgument( 
				(permission.equals( Permissions.ADMIN) )
				|| (permission.equals( Permissions.WRITE ))
				|| (permission.equals( Permissions.READ )),
				"Valid permission required");
		Map<String,String> memberships = new TreeMap<>();

		String sql = "select owneruuid as user_id, 'ADMIN' :: ndex_permission_type as permission_type from network where \"UUID\"=? and is_deleted=false";
		if ( permission == null ) {
			sql += " union select un.user_id, un.permission_type from user_network_membership un where un.network_id = '" + networkId.toString() + "'";
		}else if ( permission != Permissions.ADMIN) 
			sql = "select user_id, un.permission_type from user_network_membership un where un.network_id = ? and un.permission_type = '" + permission.toString() + "'";
		
		if ( skipBlocks>=0 && blockSize>0) {
			sql += " limit " + blockSize + " offset " + skipBlocks * blockSize;
		}
		try ( PreparedStatement p = db.prepareStatement(sql)) {
			p.setObject(1, networkId);
			try ( ResultSet rs = p.executeQuery()) {
				while ( rs.next()) {
					memberships.put(rs.getObject(1).toString(), rs.getString(2));
				}
			}
		}
				
		logger.info("Successfuly retrieved network-user permissions.");
		return memberships;
	}
	
	public Map<String,String> getNetworkGroupPermissions(UUID networkId, Permissions permission, int skipBlocks, int blockSize) 
			throws SQLException {
		
		if ( permission !=null )
			Preconditions.checkArgument( 
				 (permission.equals( Permissions.WRITE ))
				|| (permission.equals( Permissions.READ )),
				"Valid permission required");
		Map<String,String> memberships = new TreeMap<>();

		String sql = "select gn.group_id, gn.permission_type from group_network_membership gn where gn.network_id = '" + networkId.toString() + "'";
		if (permission != null )
			sql += " and gn.permission_type = '" + permission.toString() + "'";
		
		if ( skipBlocks>=0 && blockSize>0) {
			sql += " limit " + blockSize + " offset " + skipBlocks * blockSize;
		}
		
		try ( PreparedStatement p = db.prepareStatement(sql)) {
			try ( ResultSet rs = p.executeQuery()) {
				while ( rs.next()) {
					memberships.put(rs.getObject(1).toString(), rs.getString(2));
				}
			}
		}
				
	//	logger.info("Successfuly retrieved network-group permissions.");
		return memberships;
	}
		
	
	
	private Permissions getNetworkPermissionOnGroup(UUID networkId, UUID groupId) throws SQLException {
		String sql = "select permission_type from group_network_membership where network_id =? and group_id = ?";
		try ( PreparedStatement p = db.prepareStatement(sql)) {
			p.setObject(1, networkId);
			p.setObject(2, groupId);
			try ( ResultSet rs = p.executeQuery()) {
				if ( rs.next()) {
					return Permissions.valueOf(rs.getString(1));
				}
				return null;
			}
		}
	}
	
	private Permissions getNetworkNonAdminPermissionOnUser(UUID networkId, UUID userId) throws SQLException {
		String sql = "select permission_type from user_network_membership where network_id =? and user_id = ?";
		try ( PreparedStatement p = db.prepareStatement(sql)) {
			p.setObject(1, networkId);
			p.setObject(2, userId);
			try ( ResultSet rs = p.executeQuery()) {
				if ( rs.next()) {
					return Permissions.valueOf(rs.getString(1));
				}
				return null;
			}
		}
	}
	
	
	public void checkPermissionOperationCondition(UUID networkId, UUID userId) throws SQLException, ObjectNotFoundException, NdexException {
		if (!isAdmin(networkId,userId)) {
			throw new UnauthorizedOperationException("Unable to update network membership: user is not an admin of this network.");
		}

		if ( networkIsLocked(networkId)) {
			throw new  NetworkConcurrentModificationException();
		} 
	}
	
	//This function commits the current transaction, be careful when using it.
    public int grantPrivilegeToGroup(UUID networkUUID, UUID groupUUID, Permissions permission) throws NdexException, SQLException {
    
    	if (permission == Permissions.ADMIN)
    		throw new NdexException ("Groups are not allowed to administer a network, only users are allowed.");
    		
    	// check if the edge already exists?

    	Permissions p = getNetworkPermissionOnGroup(networkUUID, groupUUID);

        if ( p!=null && p == permission) {
        	logger.info("Permission " + permission + " already exists between group " + groupUUID + 
        			 " and network " + networkUUID + ". Igore grant request."); 
        	return 0;
        }
        
        String sql = "insert into group_network_membership (network_id, group_id, permission_type) values (?,?,'" + permission + "') "
        		+ "ON CONFLICT (group_id,network_id) DO UPDATE set permission_type = EXCLUDED.permission_type";
        
        try ( PreparedStatement pst = db.prepareStatement(sql)) {
        	pst.setObject(1, networkUUID);
        	pst.setObject(2, groupUUID);
        	pst.executeUpdate();
        }
        
        setFlag(networkUUID, "iscomplete",false);
        
        commit();
		//update solr index
	//	NetworkGlobalIndexManager networkIdx = new NetworkGlobalIndexManager();
		
	//	networkIdx.grantNetworkPermission(networkUUID.toString(), groupUUID.toString(), permission, p, false); 
               
    	return 1;
    }
	
    // This function commits the current transaction, so be careful when using it.
    public int grantPrivilegeToUser(UUID networkUUID, UUID userUUID, Permissions permission) throws NdexException, IOException, SQLException {
    	
    	UUID oldOwnerUUID = getNetworkOwner(networkUUID);
    	User oldUser ;
    	User newUser;
    	try ( UserDAO dao = new UserDAO ()) {
    		oldUser = dao.getUserById(oldOwnerUUID, true,false);
    		newUser = dao.getUserById(userUUID,true,false);
    	}
    	if ( oldOwnerUUID.equals(userUUID) ) {
    		if ( permission == Permissions.ADMIN)
    			return 0;
    		throw new NdexException ("Privilege change failed. Network " + networkUUID +" will not have an administrator if permission " +
            	    permission + " are granted to user " + userUUID);
    	}

    	Permissions p = getNetworkNonAdminPermissionOnUser(networkUUID, userUUID);
    	boolean showcased = isShowCased(networkUUID);
 //   	NetworkGlobalIndexManager networkIdx = new NetworkGlobalIndexManager();
    	if ( permission == Permissions.ADMIN) {
    		// grant admin to this user.
    		String sql = "update network set owneruuid = ?, owner = ?, iscomplete=false where \"UUID\" = ? and is_deleted = false";
    		try ( PreparedStatement pst = db.prepareStatement(sql)) {
    			pst.setObject(1, userUUID);
    			pst.setString(2, newUser.getUserName());
    			pst.setObject(3, networkUUID);
    			pst.executeUpdate();
    		}
    		
    		// remove any previous permission this user had.
    		if ( p != null) {
    			sql = "delete from user_network_membership where user_id = ? and network_id = ?";
    			try ( PreparedStatement pst = db.prepareStatement(sql)) {
    				pst.setObject(1, userUUID);
    				pst.setObject(2, networkUUID);
    				pst.executeUpdate();
    			}
    		}
    		
    		// auto downgrade the old user with a write permission
    		sql = "insert into user_network_membership (user_id,network_id, permission_type,show_in_homepage) values (?,?, '"+ Permissions.WRITE.toString() + "', ?)";
    		try ( PreparedStatement pst = db.prepareStatement(sql)) {
    			pst.setObject(1, oldUser.getExternalId());
    			pst.setObject(2, networkUUID);
    			pst.setBoolean(3, showcased);
    			pst.executeUpdate();
    		}
    		
    		// keep the old 
    		commit();
    		
    	} else {
    		String sql = "insert into user_network_membership ( user_id,network_id, permission_type) values (?,?,'"+ permission.toString() + "') "
    				+ "ON CONFLICT (user_id,network_id) DO UPDATE set permission_type = EXCLUDED.permission_type";
    		try ( PreparedStatement pst = db.prepareStatement(sql)) {
    			pst.setObject(1, userUUID);
    			pst.setObject(2, networkUUID);
    			pst.executeUpdate();
    		}
    		setFlag(networkUUID, "iscomplete", false);
    		commit();
    	}

		//update solr index	
	//	networkIdx.grantNetworkPermission(networkUUID.toString(), newUser.getUserName(), permission, p,true); 
               
    	return 1;
    }
	
    
    public int revokeGroupPrivilege(UUID networkUUID, UUID groupUUID) throws SQLException {
    
    	Permissions p = getNetworkPermissionOnGroup(networkUUID, groupUUID);

        if ( p ==null ) {
        	logger.info("Permission doesn't exists between group " + groupUUID + 
        			 " and network " + networkUUID + ". Igore revoke request."); 
        	return 0;
        }
        
        setFlag(networkUUID,"iscomplete",false);
        
        String sql = "delete from group_network_membership where network_id = ? and group_id = ?" ;
        try ( PreparedStatement pst = db.prepareStatement(sql)) {
        	pst.setObject(1, networkUUID);
        	pst.setObject(2,groupUUID);
        	int c = pst.executeUpdate();
        	commit();

        	return c;	
        }
        		
    }
    
    public int revokeUserPrivilege(UUID networkUUID, UUID userUUID) throws SQLException {
    	
    	Permissions p = getNetworkNonAdminPermissionOnUser(networkUUID, userUUID);

        if ( p ==null ) {
        	logger.info("Permission doesn't exists between user " + userUUID + 
        			 " and network " + networkUUID + ". Igore revoke request."); 
        	return 0;
        }
        
        setFlag(networkUUID, "iscomplete",false);
        
        String sql = "delete from user_network_membership where network_id = ? and user_id = ?" ;
        try ( PreparedStatement pst = db.prepareStatement(sql)) {
        	pst.setObject(1, networkUUID);
        	pst.setObject(2,userUUID);
        	int c = pst.executeUpdate();
        	commit();
        	//if ( c ==1 )  {
        	//	try (UserDAO dao = new UserDAO()) {
        	//		User g = dao.getUserById(userUUID, true,false);
        			
        	/*		//update solr index
            		NetworkGlobalIndexManager networkIdx = new NetworkGlobalIndexManager();
            		networkIdx.revokeNetworkPermission(networkUUID.toString(), g.getUserName(), p, true); */
        	//	}               
        //	} 
        	return c;	
        }
    }
    
    /**
     * Be careful when using this function, it commit the db connection of the current DAO object.
     * 
     * @param networkId
     * @param errorMessage
     */
    public void setErrorMessage(UUID networkId, String errorMessage) {
    	String sql = "update network set error = ? where \"UUID\" = ? and is_deleted=false";
    	
    	String trimmedMsg = errorMessage;
    	if ( trimmedMsg == null)
    		trimmedMsg = "null";
    	else if ( trimmedMsg.length() > 2000)
    		trimmedMsg = (errorMessage.substring(0, 1996) + "...");
    	
    	try ( PreparedStatement pst = db.prepareStatement(sql)) {
    		pst.setString(1, trimmedMsg);
    		pst.setObject(2, networkId);
    		int i = pst.executeUpdate();
    		if ( i !=1)
    			logger.severe("Update statement for network " + networkId + " doesn't returned row count " + i + ". sql=" + sql);
    		db.commit();
    	} catch (SQLException e) {
    		logger.severe("Failed to set error message for network " + networkId + ": " + e.getMessage());
    	}
    
    }
    
    public void setWarning(UUID networkId, List<String> warnings) throws SQLException, NdexException {
    	String sqlStr = "update network set  warnings = ? where \"UUID\" = ? and is_deleted = false";
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			
			// set warnings
			String[] warningArray = warnings.toArray(new String[0]);
			Array arrayWarnings = db.createArrayOf("text", warningArray);
			pst.setArray(1, arrayWarnings);
			
			pst.setObject(2, networkId);
			int i = pst.executeUpdate();
			if ( i != 1)
				throw new NdexException ("Failed to update network summary entry in db.");
		}
    
    }
    
    public void setSubNetworkIds(UUID networkId, Set<Long> subNetworkIds) throws SQLException, NdexException {
    	String sqlStr = "update network set  subnetworkids = ? where \"UUID\" = ? and is_deleted = false";
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			
			// set warnings
			Long[] subNetArray = subNetworkIds.toArray(new Long[0]);
			Array arraysubNets = db.createArrayOf("bigint", subNetArray);
			pst.setArray(1, arraysubNets);
			
			pst.setObject(2, networkId);
			int i = pst.executeUpdate();
			if ( i != 1)
				throw new NdexException ("Failed to update subnetworkid in network table.");
		}
    
    }
    
    public void setShowcaseFlag(UUID networkId, UUID userId, boolean bv) throws SQLException, UnauthorizedOperationException {
    	if ( isAdmin(networkId,userId)) {
    		String sql = "update network set show_in_homepage = ? where \"UUID\"=? and is_deleted=false";
        	try ( PreparedStatement pst = db.prepareStatement(sql)) {
        		pst.setBoolean(1, bv);
        		pst.setObject(2, networkId);
        		int i = pst.executeUpdate();
        		if ( i !=1)
        			logger.severe("Update statement for network " + networkId + " returned 0 row count " + i + ". sql=" + sql);
        	
        	}	
    	} else {
    		String sql = "update user_network_membership set show_in_homepage = ? where network_id = ? and user_id = ?";
        	try ( PreparedStatement pst = db.prepareStatement(sql)) {
        		pst.setBoolean(1, bv);
        		pst.setObject(2, networkId);
        		pst.setObject(3, userId);
        		int i = pst.executeUpdate();
        		if ( i !=1) {
        			logger.severe("Update statement for network " + networkId + " returned 0 row count " + i + ". sql=" + sql);
        			throw new UnauthorizedOperationException("User doesn't have explicit permission on this network.");
        		}
        	}	
    		
    	}
    	
    }

    
	public List<NetworkSummary> getUserShowCaseNetworkSummaries (UUID userId, UUID signedInUserId) throws SQLException, JsonParseException, JsonMappingException, IOException {
		// be careful when modify the order or the select clause becaue populateNetworkSummaryFromResultSet function depends on the order.
		
		List<NetworkSummary> result = new ArrayList<>(50);
				
		String sqlStr = " select * from (" + networkSummarySelectClause 
				+ " from network n where n.owneruuid = ? and show_in_homepage = true and n.is_deleted= false and is_validated = true and " 
				+ createIsReadableConditionStr(signedInUserId)
				+ " union " + networkSummarySelectClause 
				+ " from network n, user_network_membership un where un.network_id = n.\"UUID\" and un.user_id = ? and un.show_in_homepage = true "
				+ " and n.is_validated = true and n.is_deleted=false and " + 
				createIsReadableConditionStr(signedInUserId)
				 + ") k order by k.modification_time desc";
		
		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
			p.setObject(1, userId);
			p.setObject(2, userId);
			try ( ResultSet rs = p.executeQuery()) {
				while ( rs.next()) {
					NetworkSummary s = new NetworkSummary();
					populateNetworkSummaryFromResultSet(s,rs);
					result.add(s);
				}
			}
		}
		return result;
	}
     
	
	public List<NetworkSummary> getNetworkSummariesForMyAccountPage 
			(UUID userId, int offset, int limit) throws SQLException, JsonParseException, JsonMappingException, IOException {
		// be careful when modify the order or the select clause becaue populateNetworkSummaryFromResultSet function depends on the order.
		
		List<NetworkSummary> result = new ArrayList<>(50);
				
		String sqlStr = "select * from ( " + networkSummarySelectClause 
				+ " from network n where n.owneruuid = ? and n.is_deleted= false " 
				+ " union select n.creation_time, n.modification_time, n.name,n.description,n.version,"
				+ "n.edgecount,n.nodecount,n.visibility,n.owner,n.owneruuid,"
				+ " n.properties, n.\"UUID\", n.is_validated, n.error, n.readonly, n.warnings, un.show_in_homepage, n.subnetworkids,n.solr_idx_lvl,"
				+ " n.iscomplete, n.ndexdoi, n.certified,n.has_layout, n.has_sample, n.cxformat, n.cx_file_size, n.cx2_file_size "
				+ " from network n, user_network_membership un where un.network_id = n.\"UUID\" and un.user_id = ? ) k order by k.modification_time desc"; 

		if ( offset >=0 && limit >0) {
			sqlStr += " limit " + limit + " offset " + offset;
		}
		
		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
			p.setObject(1, userId);
			p.setObject(2, userId);
			try ( ResultSet rs = p.executeQuery()) {
				while ( rs.next()) {
					NetworkSummary s = new NetworkSummary();
					populateNetworkSummaryFromResultSet(s,rs);
					result.add(s);
				}
			}
		}
		return result;
	}
	
	
	public int getNumNetworksForMyAccountPage (UUID userId) throws SQLException, NdexException {

		
		String sqlStr = "select count(*) from ( select n.\"UUID\" " 
		+ " from network n where n.owneruuid = ? and n.is_deleted= false " 
		+ " union select n2.\"UUID\" from network n2, user_network_membership un where un.network_id = n2.\"UUID\" and un.user_id = ? ) k"; 

		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
			p.setObject(1, userId);
			p.setObject(2, userId);
			try ( ResultSet rs = p.executeQuery()) {
				if ( rs.next()) {
					return rs.getInt(1);
				}  
				throw new NdexException("Failed to get network count for user " + userId);	
			}
		}
	}

	
	public String getNetworkAccessKey( UUID networkId) throws SQLException, ObjectNotFoundException {
		String sqlStr = "select access_key, access_key_is_on from network where \"UUID\" = ? and is_deleted=false";
		
		String oldKey = null;
		boolean keyIsOn = false;
		
		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
			p.setObject(1, networkId);
			try ( ResultSet rs = p.executeQuery()) {
				if ( rs.next()) {
					oldKey = rs.getString(1);
					keyIsOn = rs.getBoolean(2);
				} else
					throw new ObjectNotFoundException("Network" , networkId);
				
			}
		}
	
		if ( keyIsOn) {
			return oldKey;
		}
		
		return null;

	}

	public String enableNetworkAccessKey( UUID networkId) throws SQLException, ObjectNotFoundException {
		String sqlStr = "select access_key, access_key_is_on from network where \"UUID\" = ? and is_deleted=false";
		
		String oldKey = null;
		boolean keyIsOn = false;
		
		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
			p.setObject(1, networkId);
			try ( ResultSet rs = p.executeQuery()) {
				if ( rs.next()) {
					oldKey = rs.getString(1);
					keyIsOn = rs.getBoolean(2);
				} else
					throw new ObjectNotFoundException("Network" , networkId);
				
			}
		}
	
		if ( oldKey !=null ) {
			if ( keyIsOn)
				return oldKey;
				
			//update db flag
			sqlStr = "update network set access_key_is_on = true where \"UUID\"=?";
			try ( PreparedStatement pst = db.prepareStatement(sqlStr)) {
	        	pst.setObject(1, networkId);
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
		 sqlStr = "update network set access_key_is_on = true, access_key=? where \"UUID\"=?";
		 try ( PreparedStatement pst = db.prepareStatement(sqlStr)) {
				pst.setString(1, newKey);
	        	pst.setObject(2, networkId);
	        	pst.executeUpdate();
	      }	
	     
	     return newKey;
	}

	public void setDOI (UUID networkId, String DOIStr) throws SQLException, NdexException {
		String sqlStr = "update network set ndexdoi= ? where \"UUID\" = ? and is_deleted = false";
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setString(1, DOIStr);
			pst.setObject(2, networkId);
			int i = pst.executeUpdate();
			if ( i != 1)
				throw new NdexException ("Failed to set network DOI in db.");
		}
	}
	
	public String requestDOI(UUID networkId, boolean isCertified) throws SQLException, NdexException {
		String accessKey = enableNetworkAccessKey(networkId);
		setFlag(networkId,"readonly",true); 
		setDOI (networkId, PENDING);
		setFlag(networkId, "certified", isCertified);
		if ( isCertified) {
			updateNetworkVisibility(networkId, VisibilityType.PUBLIC, true);
			setIndexLevel(networkId, NetworkIndexLevel.ALL);
		}	
		return accessKey;
	}
	
	public void cancelDOI(UUID networkId) throws SQLException, NdexException {
		/*if ( isCertified(networkId)) {
			updateNetworkVisibility(networkId, VisibilityType.PRIVATE, true);
		}	
		disableNetworkAccessKey(networkId); */
		setFlag(networkId,"readonly",false); 
		setDOI (networkId, null);
		setFlag(networkId, "certified", false);
	}
	
	public void disableNetworkAccessKey( UUID networkId) throws SQLException {
		//update db flag
		String sqlStr = "update network set access_key_is_on = false where \"UUID\"=?";
		try ( PreparedStatement pst = db.prepareStatement(sqlStr)) {
	        pst.setObject(1, networkId);
	        pst.executeUpdate();
	    }	
	}
	
	public boolean accessKeyIsValid(UUID networkId, String accessKey) throws SQLException {
		if ( accessKey ==null || accessKey.length() == 0)
			return false;
		
		String sqlStr = "select 1 from network where (\"UUID\"=? and access_key_is_on and access_key = ?)" ;
		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
			p.setObject(1, networkId);
			p.setString(2, accessKey);
			try ( ResultSet rs = p.executeQuery()) {
				 if (rs.next())
					 return true;
			}		
		}
		
		sqlStr = "select 1 from network_set s, network_set_member sm where s.\"UUID\" = sm.set_id "
                + "and sm.network_id = ? and s.access_key_is_on and s.access_key = ? and s.is_deleted=false";
		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
			p.setObject(1, networkId);
			p.setString(2, accessKey);
			try ( ResultSet rs = p.executeQuery()) {
				 return rs.next();
			}		
		}

	}
}
