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
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.logging.Logger;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.cxio.aspects.datamodels.ATTRIBUTE_DATA_TYPE;
import org.cxio.aspects.datamodels.NetworkAttributesElement;
import org.cxio.metadata.MetaDataCollection;
import org.ndexbio.common.NdexClasses;
import org.ndexbio.common.access.NdexDatabase;
//import org.ndexbio.common.models.dao.orientdb.NetworkSearchDAO.NetworkResultComparator;
import org.ndexbio.common.solr.NetworkGlobalIndexManager;
import org.ndexbio.common.solr.SingleNetworkSolrIdxManager;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.Group;
import org.ndexbio.model.object.Membership;
import org.ndexbio.model.object.MembershipType;
import org.ndexbio.model.object.NdexPropertyValuePair;
import org.ndexbio.model.object.NetworkSearchResult;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.ProvenanceEntity;
import org.ndexbio.model.object.SimpleNetworkQuery;
import org.ndexbio.model.object.User;
import org.ndexbio.model.object.network.BaseTerm;
import org.ndexbio.model.object.network.Citation;
import org.ndexbio.model.object.network.Edge;
import org.ndexbio.model.object.network.FunctionTerm;
import org.ndexbio.model.object.network.Namespace;
import org.ndexbio.model.object.network.Network;
import org.ndexbio.model.object.network.NetworkSourceFormat;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.object.network.Node;
import org.ndexbio.model.object.network.ReifiedEdgeTerm;
import org.ndexbio.model.object.network.Support;
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

    /* define this to reuse in different functions to keep the order of the fields so that the populateNetworkSummaryFromResultSet function can be shared.*/
	private static final String networkSummarySelectClause = "select creation_time, modification_time, name,description,version,"
			+ "edgecount,nodecount,visibility,owner,owneruuid,"
			+ " properties, \"UUID\", is_validated, error, readonly, warnings "; //sourceformat,is_validated, iscomplete, readonly,"
	
	public NetworkDAO () throws  SQLException {
	    super();
	}

	
	public void CreateEmptyNetworkEntry(UUID networkUUID, UUID ownerId, String ownerUserName) throws SQLException {
		String sqlStr = "insert into network (\"UUID\", creation_time, modification_time, is_deleted, islocked,visibility,owneruuid,owner,readonly) values"
				+ "(?, localtimestamp, localtimestamp, false, false, 'PRIVATE',?,?,false) ";
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setObject(1, networkUUID);
			pst.setObject(2, ownerId);
			pst.setString(3, ownerUserName);
			pst.executeUpdate();
		}
	}
	

	public void deleteNetwork(UUID netowrkId, UUID userId) throws SQLException, NdexException {
		String sqlStr = "update network set is_deleted=true,"
				+ " modification_time = localtimestamp where \"UUID\" = ? and owneruuid = ? and is_deleted=false and isLocked = false and readonly=false";
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setObject(1, netowrkId);
			pst.setObject(2, userId);
			int cnt = pst.executeUpdate();
			if ( cnt !=1) {
				throw new NdexException ("Failed to delete network. Reason could be invalid UUID, user is not the owner of the network, the network is Locked or the network is readonly.");
			}
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
	
	public void setFlag(UUID networkId, String fieldName, boolean value) throws SQLException, NdexException {
		String sqlStr = "update network set "
				+ fieldName + "=" + value + " where \"UUID\" = ? and is_deleted = false";
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setObject(1, networkId);
			int i = pst.executeUpdate();
			if ( i != 1)
				throw new NdexException ("Failed to set network flag entry in db.");
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
	public void saveNetworkEntry(NetworkSummary networkSummary, ProvenanceEntity provenance, MetaDataCollection metadata) throws SQLException, NdexException, JsonProcessingException {
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
	}
	
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
	
	public boolean isReadable(UUID networkID, UUID userId) throws SQLException {
		String sqlStr = "select 1 from network n where n.\"UUID\" = ? and n.is_deleted=false and " + createIsReadableConditionStr(userId);		
			
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setObject(1, networkID);

			try ( ResultSet rs = pst.executeQuery()) {
				return rs.next();
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
		String sqlStr = "select roid,cacheid from network n where n.\"UUID\" = ? and n.is_deleted=false ";
			
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setObject(1, networkID);
			try ( ResultSet rs = pst.executeQuery()) {
				if( rs.next() ) {
					return rs.getLong(1)>0 && rs.getLong(1) == rs.getLong(2);
				}
				throw new ObjectNotFoundException("Network", networkID );
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
	public void lockNetwork(UUID networkId) throws SQLException, NdexException {
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
			throw new NdexException("Failed to lock network. ");
		}
	}
	
	
	private void setNetworkLock(UUID networkId, boolean lock) throws SQLException, ObjectNotFoundException {
		String sql = "update network set islocked=? where \"UUID\" = ? and is_deleted=false";
		try ( PreparedStatement p = db.prepareStatement(sql)) {
			p.setBoolean(1, lock);
			p.setObject(2, networkId);
			int i = p.executeUpdate();
			if ( i !=1)
				throw new ObjectNotFoundException("network",networkId);
		}
	}
	
	
	/**
	 * Set the islocked flag to false in the db.
	 * This is an atomic operation. Will commit the current transaction.
	 * @param networkID
	 * @throws ObjectNotFoundException 
	 * @throws SQLException 
	 */
	public void unlockNetwork (UUID networkId) throws ObjectNotFoundException, SQLException {
		setNetworkLock(networkId,false);
		db.commit(); 
	}
	
	public boolean networkIsLocked(UUID networkUUID) throws ObjectNotFoundException, SQLException {
		String sql = "select islocked from network where \"UUID\" = ? and is_deleted = false";
		try(PreparedStatement p = db.prepareStatement(sql)){
			p.setObject(1, networkUUID);
			try ( ResultSet rs = p.executeQuery()) {
				if ( rs.next()) {
					return rs.getBoolean(1);
				}
				throw new ObjectNotFoundException ("network",networkUUID);
			}
		}
	}
	
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
					return null;
				}
				throw new ObjectNotFoundException ("network",networkId);
			}
		}
		
	}
    
	public int setProvenance(UUID networkId, ProvenanceEntity provenance) throws JsonProcessingException, SQLException, NdexException {
		// get the network document
		String sql = " update network set provenance = ? :: jsonb where \"UUID\"=? and is_deleted=false and islocked = false";
		try (PreparedStatement p = db.prepareStatement(sql)) {
			ObjectMapper mapper = new ObjectMapper();
			String s = mapper.writeValueAsString(provenance);
			p.setString(1, s);
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

		    String sqlStr = "select u.user_name, b.per from  (select a.user_id, max(a.per) from "+
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
	 * This function sets network properties using the given property list. All Existing properties
	 * of the network will be deleted. 
	 * @param networkId
	 * @param properties
	 * @return
	 * @throws ObjectNotFoundException
	 * @throws NdexException
     * @throws IOException 
     * @throws SolrServerException 
	 */
	public int setNetworkProperties (UUID networkId, Collection<NdexPropertyValuePair> properties
			 ) throws ObjectNotFoundException, NdexException, SolrServerException, IOException {

//		ODocument rec = this.getRecordByUUID(networkId, null);
		
		List<NdexPropertyValuePair> props = new ArrayList<>(properties.size());
		for ( NdexPropertyValuePair p : properties ) {
			if (!p.getPredicateString().equals(NdexClasses.Network_P_source_format))
				props.add(p);
		}
		
		Date updateTime = Calendar.getInstance().getTime();
	//	rec.fields(NdexClasses.ndexProperties, props,
	//				NdexClasses.ExternalObj_mTime, updateTime).save();

		
		// update the solr Index
		NetworkGlobalIndexManager globalIdx = new NetworkGlobalIndexManager();
		globalIdx.updateNetworkProperties(networkId.toString(), props, updateTime);
		
		return props.size();
	}
	
	
	public NetworkSearchResult findNetworks(SimpleNetworkQuery simpleNetworkQuery, int skipBlocks, int top, User loggedInUser) throws NdexException, SolrServerException, IOException, SQLException {
	
		String queryStr = simpleNetworkQuery.getSearchString().trim();
		if (queryStr.equals("*")  || queryStr.length() == 0 )
			queryStr = "*:*";
		
		if ( simpleNetworkQuery.getPermission() !=null && simpleNetworkQuery.getPermission() == Permissions.ADMIN)
			throw new NdexException("Permission can only be WRITE or READ in this function.");
		
		NetworkGlobalIndexManager networkIdx = new NetworkGlobalIndexManager();
		
		//prepare the query.
	//	if (simpleNetworkQuery.getPermission() == null) 
	//		simpleNetworkQuery.setPermission(Permissions.READ);

		List<String> groupNames = new ArrayList<>();
		if ( loggedInUser !=null && simpleNetworkQuery.getIncludeGroups()) {
			try (UserDAO userDao = new UserDAO() ) {
				for ( Membership m : userDao.getUserGroupMemberships(loggedInUser.getExternalId(), Permissions.MEMBER,0,0,true) ) {
					groupNames.add(m.getResourceName());
				}
			}
		}
			
		SolrDocumentList solrResults = networkIdx.searchForNetworks(queryStr, 
				(loggedInUser == null? null: loggedInUser.getUserName()), top, skipBlocks * top, 
						simpleNetworkQuery.getAccountName(), simpleNetworkQuery.getPermission(), simpleNetworkQuery.getCanRead(), groupNames);
		
		List<NetworkSummary> results = new ArrayList<>(solrResults.size());
		for ( SolrDocument d : solrResults) {
			String id = (String) d.get(NetworkGlobalIndexManager.UUID);
			NetworkSummary s = getNetworkSummaryById(UUID.fromString(id));
			if ( s !=null)
				results .add(s); 
		} 
		
		return new NetworkSearchResult ( solrResults.getNumFound(), solrResults.getStart(), results);
	}

	
	public NetworkSummary getNetworkSummaryById (UUID networkId) throws SQLException, ObjectNotFoundException, JsonParseException, JsonMappingException, IOException {
		// be careful when modify the order or the select clause becaue populateNetworkSummaryFromResultSet function depends on the order.
		String sqlStr = networkSummarySelectClause + " from network where \"UUID\" = ? and is_deleted= false";
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
	
	
	
	public List<NetworkSummary> getNetworkSummariesByIdStrList (List<String> networkIdstrList, UUID userId) throws SQLException, JsonParseException, JsonMappingException, IOException {
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
		
		String sqlStr = networkSummarySelectClause 
				+ "from network n where n.\"UUID\" in("+ cnd.toString() + ") and n.is_deleted= false and " + createIsReadableConditionStr(userId) ;
		
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
			
			List<NdexPropertyValuePair> o = mapper.readValue(proptiesStr, ArrayList.class); 		
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
			
	}
	
	
	public void updateNetworkProfile(UUID networkId, Map<String,String> newValues) throws NdexException, SolrServerException, IOException, SQLException {
	
	    	 //update db
		    String sqlStr = "update network set ";
		    List<String> values = new ArrayList<>(newValues.size());
		    for (Map.Entry<String,String> entry : newValues.entrySet()) {
		    		if (values.size() >0)
		    			sqlStr += ", ";
		    		sqlStr += entry.getKey() + " = ?";	
		    		values.add(entry.getValue());
		    }
		    sqlStr += " where \"UUID\" = '" + networkId + "' ::uuid and is_deleted=false";
		    
		    try (PreparedStatement p = db.prepareStatement(sqlStr)) {
		    	for ( int i = 0 ; i < values.size(); i++) {
		    		p.setString(i+1, values.get(i));
		    	}
		    	int cnt = p.executeUpdate();
		    	if ( cnt != 1 ) {
		    		throw new NdexException ("Failed to update. Network " + networkId + " might have been locked.");
		    	}
		    }
		    
	    	//update solr index
	    	NetworkGlobalIndexManager networkIdx = new NetworkGlobalIndexManager();

	    	networkIdx.updateNetworkProfile(networkId.toString(), newValues); 
		
	}
	
	public MetaDataCollection getMetaDataCollection(UUID networkId) throws SQLException, IOException, NdexException {
		String sqlStr = "select cxmetadata from network n where n.\"UUID\" =? and n.is_deleted= false" ;
		
		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
			p.setObject(1, networkId);
			try ( ResultSet rs = p.executeQuery()) {
				if ( rs.next()) {
					String s = rs.getString(1);
					MetaDataCollection metadata = MetaDataCollection.createInstanceFromJson(s);
					return metadata;
				}
				throw new NdexException ("No metadata found for network " + networkId + " in database.");
			}
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
	
	public void updateNetworkVisibility (UUID networkId, VisibilityType v) throws SQLException, NdexException, SolrServerException, IOException {
		 String sqlStr = "update network set visibility = '" + v.toString() + "' where \"UUID\" = ? and is_deleted=false and islocked=false";
		 try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			 pst.setObject(1, networkId);
			 int i = pst.executeUpdate();
			 if ( i !=1 )
				 throw new NdexException ("Failed to update visibility. Network " + networkId + " might have been locked.");
		 }
		    	  	
		 //update solr index
		 NetworkGlobalIndexManager networkIdx = new NetworkGlobalIndexManager();

		 networkIdx.updateNetworkVisibility(networkId.toString(), v.toString()); 
		    			
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
			sql = " union select un.user_id, u.user_name, n.name, un.ndex_permission_type from user_network_membership un, network n, ndex_user u where u.\"UUID\" = un.user_id and "
					+ "n.\"UUID\" = un.network_id and network_id = ?" ;
		}else if ( permission != Permissions.ADMIN) 
			sql = "select user_id, u.user_name, n.name, un.ndex_permission_type from user_network_membership un, network n, ndex_user u where u.\"UUID\" = un.user_id and n.\"UUID\" = un.network_id "
					+ "and network_id = ? and ndex_permission_type = '" + permission.toString() + "'";
		
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
	
	
	public void checkMembershipOperationPermission(UUID networkId, UUID userId) throws SQLException, ObjectNotFoundException, NdexException {
		if (!isAdmin(networkId,userId)) {
			throw new UnauthorizedOperationException("Unable to update network membership: user is not an admin of this network.");
		}

		if ( networkIsLocked(networkId)) {
			throw new NdexException ("Can't modify locked network. The network is currently locked by another updating thread.");
		} 
	}
	
    public int grantPrivilegeToGroup(UUID networkUUID, UUID groupUUID, Permissions permission) throws NdexException, SolrServerException, IOException, SQLException {
    
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
        
		//update solr index
		NetworkGlobalIndexManager networkIdx = new NetworkGlobalIndexManager();
		
		networkIdx.grantNetworkPermission(networkUUID.toString(), groupUUID.toString(), permission, p, false); 
               
    	return 1;
    }
	
    public int grantPrivilegeToUser(UUID networkUUID, UUID userUUID, Permissions permission) throws NdexException, SolrServerException, IOException, SQLException {
    	
    	UUID oldOwnerUUID = getNetworkOwner(networkUUID);
    	User oldUser ;
    	User newUser;
    	try ( UserDAO dao = new UserDAO ()) {
    		oldUser = dao.getUserById(oldOwnerUUID, true);
    		newUser = dao.getUserById(userUUID,true);
    	}
    	if ( oldOwnerUUID.equals(userUUID) ) {
    		if ( permission == Permissions.ADMIN)
    			return 0;
    		throw new NdexException ("Privilege change failed. Network " + networkUUID +" will not have an administrator if permission " +
            	    permission + " are granted to user " + userUUID);
    	}

    	Permissions p = getNetworkNonAdminPermissionOnUser(networkUUID, userUUID);
    	NetworkGlobalIndexManager networkIdx = new NetworkGlobalIndexManager();
    	if ( permission == Permissions.ADMIN) {
    		String sql = "update network set owneruuid = ?, owner = ? where \"UUID\" = ? and is_deleted = false";
    		try ( PreparedStatement pst = db.prepareStatement(sql)) {
    			pst.setObject(1, userUUID);
    			pst.setString(2, newUser.getUserName());
    			pst.setObject(3, networkUUID);
    			pst.executeUpdate();
    		}
    		
    		networkIdx.revokeNetworkPermission(networkUUID.toString(), oldUser.getUserName(), Permissions.ADMIN, true);
    		
    	} else {
    		String sql = "insert into user_network_membership ( user_id,network_id, permission_type) values (?,?,'"+ permission.toString() + "') "
    				+ "ON CONFLICT (user_id,network_id) DO UPDATE set permission_type = EXCLUDED.permission_type";
    		try ( PreparedStatement pst = db.prepareStatement(sql)) {
    			pst.setObject(1, userUUID);
    			pst.setObject(2, networkUUID);
    			pst.executeUpdate();
    		}
    	}

		//update solr index	
		networkIdx.grantNetworkPermission(networkUUID.toString(), newUser.getUserName(), permission, p,true); 
               
    	return 1;
    }
	
    
    public int revokeGroupPrivilege(UUID networkUUID, UUID groupUUID) throws NdexException, SolrServerException, IOException, SQLException {
    
    	Permissions p = getNetworkPermissionOnGroup(networkUUID, groupUUID);

        if ( p ==null ) {
        	logger.info("Permission doesn't exists between group " + groupUUID + 
        			 " and network " + networkUUID + ". Igore revoke request."); 
        	return 0;
        }
        
        String sql = "delete from group_network_membership where network_id = ? and group_id = ?" ;
        try ( PreparedStatement pst = db.prepareStatement(sql)) {
        	pst.setObject(1, networkUUID);
        	pst.setObject(2,groupUUID);
        	int c = pst.executeUpdate();
        	if ( c ==1 )  {
        		try (GroupDAO dao = new GroupDAO()) {
        			Group g = dao.getGroupById(groupUUID);
        			
        			//update solr index
            		NetworkGlobalIndexManager networkIdx = new NetworkGlobalIndexManager();
            		networkIdx.revokeNetworkPermission(networkUUID.toString(), g.getGroupName(), p, false);
        		}               
        	} 
        	return c;	
        }
        		
    }
    
    public int revokeUserPrivilege(UUID networkUUID, UUID userUUID) throws NdexException, SolrServerException, IOException, SQLException {
    	
    	Permissions p = getNetworkNonAdminPermissionOnUser(networkUUID, userUUID);

        if ( p ==null ) {
        	logger.info("Permission doesn't exists between user " + userUUID + 
        			 " and network " + networkUUID + ". Igore revoke request."); 
        	return 0;
        }
        
        String sql = "delete from user_network_membership where network_id = ? and user_id = ?" ;
        try ( PreparedStatement pst = db.prepareStatement(sql)) {
        	pst.setObject(1, networkUUID);
        	pst.setObject(2,userUUID);
        	int c = pst.executeUpdate();
        	if ( c ==1 )  {
        		try (UserDAO dao = new UserDAO()) {
        			User g = dao.getUserById(userUUID, true);
        			
        			//update solr index
            		NetworkGlobalIndexManager networkIdx = new NetworkGlobalIndexManager();
            		networkIdx.revokeNetworkPermission(networkUUID.toString(), g.getUserName(), p, true);
        		}               
        	} 
        	return c;	
        }
    }
    
    /**
     * Becareful when using this function, it commit the db connection of the current DAO object.
     * 
     * @param networkId
     * @param ErrorMessage
     */
    public void setErrorMessage(UUID networkId, String ErrorMessage) {
    	String sql = "update network set error = ? where \"UUID\" = ? and is_deleted=false";
    	try ( PreparedStatement pst = db.prepareStatement(sql)) {
    		pst.setString(1, ErrorMessage);
    		pst.setObject(2, networkId);
    		int i = pst.executeUpdate();
    		if ( i !=1)
    			logger.severe("Update statement for network " + networkId + " doesn't returned row count " + i + ". sql=" + sql);
    		db.commit();
    	} catch (SQLException e) {
    		logger.severe("Failed to set error messge for network " + networkId + ": " + e.getMessage());
    	}
    
    }
    
}
