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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
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
import org.ndexbio.common.NdexClasses;
import org.ndexbio.common.access.NdexDatabase;
//import org.ndexbio.common.models.dao.orientdb.NetworkSearchDAO.NetworkResultComparator;
import org.ndexbio.common.solr.NetworkGlobalIndexManager;
import org.ndexbio.common.solr.SingleNetworkSolrIdxManager;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.object.Membership;
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


public class NetworkDocDAO extends NdexDBDAO {

	private static Logger logger = Logger.getLogger(NetworkDocDAO.class.getName());
	
//	public static final String RESET_MOD_TIME = "resetMTime";


	public NetworkDocDAO () throws  SQLException {
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
	public void populateNetworkEntry(NetworkSummary networkSummary) throws SQLException, NdexException, JsonProcessingException {
		String sqlStr = "update network set name = ?, description = ?, version = ?, edgecount=?, nodecount=?, properties = ? ::jsonb,"
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
			
			pst.setObject(7, networkSummary.getExternalId());
			int i = pst.executeUpdate();
			if ( i != 1)
				throw new NdexException ("Failed to update network summary entry in db.");
		}
	}
	
	
	public boolean isReadable(UUID networkID, UUID userId) throws SQLException {
		String sqlStr = "select 1 from network n where n.\"UUID\" = ? and n.is_deleted=false and (n.visibility='PUBLIC'";
		
		if ( userId != null) {
			sqlStr += " or n.owneruuid = ? or "
				+ " exists ( select 1 from user_network_membership un1 where un1.network_id = n.\"UUID\" and un1.user_id = ? limit 1) or " +
				  " exists ( select 1 from group_network_membership gn1, ndex_group_user gu where gn1.group_id = gu.group_id and gn1.network_id = n.\"UUID\" and gu.user_id = ? limit 1) " ;
		} 
		sqlStr += ")";
			
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setObject(1, networkID);
			if (userId !=null) {
				pst.setObject(2, userId);
				pst.setObject(3, userId);
				pst.setObject(4, userId);
			}
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
	
	
	/**
	 * Set the islocked flag to true in the db.
	 * This is an atomic operation. Will commit the current transaction.
	 * @param networkID
	 * @throws ObjectNotFoundException 
	 */
	public void lockNetwork(String networkIDstr) throws ObjectNotFoundException {
	/*	ODocument nDoc = getNetworkDocByUUIDString(networkIDstr);
		nDoc.field(NdexClasses.Network_P_isLocked,true);
		nDoc.save();
		db.commit();*/
	}
	
	
	
	/**
	 * Set the islocked flag to false in the db.
	 * This is an atomic operation. Will commit the current transaction.
	 * @param networkID
	 * @throws ObjectNotFoundException 
	 */
	public void unlockNetwork (String networkIDstr) throws ObjectNotFoundException {
	/*	ODocument nDoc = getNetworkDocByUUIDString(networkIDstr);
		nDoc.field(NdexClasses.Network_P_isLocked,false);
		nDoc.save();
		db.commit(); */
	}
	
	public boolean networkIsLocked(String networkUUIDStr) throws ObjectNotFoundException {
/*		ODocument nDoc = getNetworkDocByUUIDString(networkUUIDStr);
		return nDoc.field(NdexClasses.Network_P_isLocked); */
		
		return false;
	}
	
	public ProvenanceEntity getProvenance(UUID networkId) throws JsonParseException, JsonMappingException, IOException, ObjectNotFoundException {
		// get the network document
	/*	ODocument nDoc = getNetworkDocByUUIDString(networkId.toString());
		// get the provenance string
		String provenanceString = nDoc.field(NdexClasses.Network_P_provenance);
		// deserialize it to create a ProvenanceEntity object
		if (provenanceString != null && provenanceString.length() > 0){
			ObjectMapper mapper = new ObjectMapper();
			return mapper.readValue(provenanceString, ProvenanceEntity.class); 
		}  */
		
		return new ProvenanceEntity();
		
	}
    
	public int setProvenance(UUID networkId, ProvenanceEntity provenance) throws JsonProcessingException, ObjectNotFoundException {
		// get the network document
/*		ODocument nDoc = getNetworkDocByUUIDString(networkId.toString());	
		// serialize the ProvenanceEntity
		ObjectMapper mapper = new ObjectMapper();
		String provenanceString = mapper.writeValueAsString(provenance);
		// store provenance string
		nDoc.field(NdexClasses.Network_P_provenance, provenanceString);
    //    nDoc.field(NdexClasses.ExternalObj_mTime, Calendar.getInstance().getTime());
		nDoc.save(); */
				
		return 1;
	}
	
/*	public ODocument getNetworkDocByUUIDString(String id) throws ObjectNotFoundException {
	     String query = "select from " + NdexClasses.Network + " where UUID='"
                +id+"' and (isDeleted = false) and (isComplete=true)";
        final List<ODocument> networks = db.query(new OSQLSynchQuery<ODocument>(query));
 
        if (networks.isEmpty())
	        throw new ObjectNotFoundException("Network " + id + " not found.");
        
        return networks.get(0);
   } */


/*	public  Edge getEdgeFromDocument(ODocument doc, Network network) throws NdexException {
		Edge e = new Edge();
		e.setId((long)doc.field(NdexClasses.Element_ID));
		SingleNetworkDAO.getPropertiesFromDoc(doc, e);
		
		ODocument s =  doc.field("in_"+NdexClasses.Edge_E_subject);
		Long subjectId = s.field(NdexClasses.Element_ID);
		e.setSubjectId( subjectId );
		
		if ( network !=null && 
				!network.getNodes().containsKey(subjectId)) {
			Node node = getNode (s,network);
			network.getNodes().put(subjectId, node);
		}
		
		//ODocument predicateDoc = (ODocument)doc.field("out_"+NdexClasses.Edge_E_predicate);
		Long predicateId = doc.field(NdexClasses.Edge_P_predicateId);
//		if(predicateId == null)
//			System.out.println(doc.toString());
		if ( predicateId !=null)
			e.setPredicateId(predicateId);
		else 
			e.setPredicateId(-1l);
		
		if ( predicateId !=null && network != null && !network.getBaseTerms().containsKey(predicateId)) {
    		   BaseTerm t = getBaseTerm(getDocumentByElementId(NdexClasses.BaseTerm, predicateId),network);
    		   network.getBaseTerms().put(t.getId(), t);
    	   }
		
		ODocument o = doc.field("out_"+NdexClasses.Edge_E_object);
		Long objectId = o.field(NdexClasses.Element_ID);
		e.setObjectId(objectId);
		
		if ( network !=null && 
				!network.getNodes().containsKey(objectId)) {
			Node node = getNode (o,network);
			network.getNodes().put(objectId, node);
		}

		//populate citations
		Set<Long> citationIds = doc.field(NdexClasses.Citation);
		if ( citationIds !=null && citationIds.size()>0) {
			e.setCitationIds(citationIds);

			if ( network != null) {
				for ( Long citationId : citationIds) {
					if (! network.getCitations().containsKey(citationId)) {
						ODocument citationDoc = this.getDocumentByElementId(NdexClasses.Citation,citationId);
						Citation t = getCitationFromDoc(citationDoc);
						network.getCitations().put(citationId, t);
					}
				}
			}

		} 
		
		//populate support
		Set<Long> supportIds = doc.field(NdexClasses.Support);
		if ( supportIds !=null && supportIds.size()>0) {
			e.setSupportIds(supportIds);

			if ( network != null) {
				for ( Long supportId : supportIds) {
					if (! network.getSupports().containsKey(supportId)) {
						ODocument supportDoc = this.getDocumentByElementId(NdexClasses.Support,supportId);
						Support t = getSupportFromDoc(supportDoc,network);
						network.getSupports().put(supportId, t);
					}
				}
			}

		}
		return e;
	} */

    /**
     *  Create a node object from a document. If network is not null, also  
     *  create dependent objects (term, namespace, citation etc) in the network object. 
     * @param nodeDoc
     * @param network
     * @return
     * @throws NdexException 
     */
/*    public Node getNode(ODocument nodeDoc, Network network) throws NdexException {
    	Node n = new Node();

    	n.setId((long)nodeDoc.field(NdexClasses.Element_ID));
    	n.setName((String)nodeDoc.field(NdexClasses.Node_P_name));

    	// Populate properties
    	SingleNetworkDAO.getPropertiesFromDoc(nodeDoc, n);

     	// populate baseterm
    	Long representsId = nodeDoc.field(NdexClasses.Node_P_represents);
    	
    	if( representsId !=null) {
    		n.setRepresents(representsId);
    		String termType = nodeDoc.field(NdexClasses.Node_P_representTermType);
    		n.setRepresentsTermType(termType);
    		if (network !=null) {
    			// populate objects in network
    			if ( termType.equals(NdexClasses.BaseTerm)) {
    				if ( !network.getBaseTerms().containsKey(representsId) ) {
    	    			ODocument o = this.getDocumentByElementId(NdexClasses.BaseTerm,representsId);
    					BaseTerm bTerm = getBaseTerm(o, network);
    					network.getBaseTerms().put(representsId, bTerm);
    				}
    			} else if (termType.equals(NdexClasses.ReifiedEdgeTerm)) {
    				if ( !network.getReifiedEdgeTerms().containsKey(representsId)) {
    	    			ODocument o = this.getDocumentByElementId(NdexClasses.ReifiedEdgeTerm,representsId);
    					ReifiedEdgeTerm reTerm = getReifiedEdgeTermFromDoc(o,network);
    					network.getReifiedEdgeTerms().put(representsId, reTerm);
    				}
    			} else if (termType.equals(NdexClasses.FunctionTerm)) {
    				if ( !network.getFunctionTerms().containsKey(representsId)) {
    	    			ODocument o = this.getDocumentByElementId(NdexClasses.FunctionTerm,representsId);
    					FunctionTerm funcTerm = getFunctionTermfromDoc(o, network);
    					network.getFunctionTerms().put(representsId, funcTerm);
    				}
    			} else 
    				throw new NdexException ("Unsupported term type '" + termType + 
    						"' found for term Id:" + representsId);
    		}
    	}
		
    	//populate aliases
    	Set<Long> aliases = nodeDoc.field(NdexClasses.Node_P_alias);
    	if ( aliases !=null && aliases.size() > 0 ) {
    		n.setAliases(aliases);
    	
    		if ( network != null) {
    			for ( Long alias : aliases) {
    				if (! network.getBaseTerms().containsKey(alias)) {
    					ODocument doc = this.getDocumentByElementId(NdexClasses.BaseTerm,alias);
    					BaseTerm t = getBaseTerm(doc,network);
    					network.getBaseTerms().put(alias, t);
    				}
    			}
    		}
    	}
    	
    	//populate related terms
		Set<Long> relateTos = nodeDoc.field(NdexClasses.Node_P_relatedTo);
		if ( relateTos !=null && relateTos.size()> 0 ) {
			n.setRelatedTerms(relateTos);
		
			if ( network != null) {
				for ( Long relatedTermId : relateTos) {
					if (! network.getBaseTerms().containsKey(relatedTermId)) {
						ODocument doc = this.getDocumentByElementId(NdexClasses.BaseTerm,relatedTermId);
						BaseTerm t = getBaseTerm(doc,network);
						network.getBaseTerms().put(relatedTermId, t);
					}
				}
			}
		}
    	
		//populate citations
		Set<Long> citations = nodeDoc.field(NdexClasses.Citation);
		if ( citations != null && citations.size() >0 ) { 
			n.setCitationIds(citations);
		
			if ( network != null) {
				for ( Long citationId : citations) {
					if (! network.getCitations().containsKey(citationId)) {
						ODocument doc = this.getDocumentByElementId(NdexClasses.Citation, citationId);
						Citation t = getCitationFromDoc(doc);
						network.getCitations().put(citationId, t);
					}
				}
			}
		}
			
		//populate support
		Set<Long> supports = nodeDoc.field(NdexClasses.Support);
		if ( supports !=null && supports.size() > 0 ) { 
			n.setSupportIds(supports);
		
			if ( network != null) {
				for ( Long supportId : supports) {
					if (! network.getSupports().containsKey(supportId)) {
						ODocument doc = this.getDocumentByElementId(NdexClasses.Support,supportId);
						Support t = getSupportFromDoc(doc,network);
						network.getSupports().put(supportId, t);
					}
				}
			}
		}
		
    	return n;
    } */

    
	/**
	 *  This function returns the citations in this network.
	 * @param networkUUID
	 * @return
	 * @throws NdexException 
	 */
	public Collection<Citation> getNetworkCitations(String networkUUID) throws NdexException {
		ArrayList<Citation> citations = new ArrayList<>();
		
	/*	ODocument networkDoc = getNetworkDocByUUIDString(networkUUID);
		
		for ( ODocument doc : Helper.getNetworkElements(networkDoc, NdexClasses.Network_E_Citations)) {
    			citations.add(getCitationFromDoc(doc));
    	} */
    	return citations; 
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
     * This funciton return a self-contained sub network from a given citation. It is mainly for the XBel exporter.
     * No networkSummary values are populated from the db in the result.
     * @param networkUUID
     * @param citationId
     * @return
     * @throws NdexException
     */

/*	private BaseTerm getBaseTerm(ODocument o, Network network) throws NdexException {
		BaseTerm t = new BaseTerm();
		t.setId((long)o.field(NdexClasses.Element_ID));
		String name = o.field(NdexClasses.BTerm_P_name);
		
		String prefix = o.field(NdexClasses.BTerm_P_prefix);
		if ( prefix !=null) {
			name = prefix+name;
		}
		t.setName(name);

		Long nsId = o.field(NdexClasses.BTerm_NS_ID);
		if ( nsId !=null) {
		   t.setNamespaceId(nsId);

		   if ( nsId >0) {
			   if ( network != null &&
					 ! network.getNamespaces().containsKey(nsId)) {
					Namespace ns = getNamespace(getDocumentByElementId(NdexClasses.Namespace, nsId));
					network.getNamespaces().put(nsId, ns);
				}
		   }
		}
		return t;
	} */
	
	//TODO: make a better implementation for this function.
/*	public ODocument getDocumentByElementId(long elementID) throws NdexException {
		ODocument result = getDocumentByElementId(NdexClasses.Node, elementID);
		if ( result != null) return result;
		
		result = getDocumentByElementId(NdexClasses.Edge, elementID);
		if ( result != null) return result;
		
		result = getDocumentByElementId(NdexClasses.BaseTerm, elementID);
		if ( result != null) return result;

		result = getDocumentByElementId(NdexClasses.Citation, elementID);
		if ( result != null) return result;
		result = getDocumentByElementId(NdexClasses.FunctionTerm, elementID);
		if ( result != null) return result;
		result = getDocumentByElementId(NdexClasses.Namespace, elementID);
		if ( result != null) return result;
		result = getDocumentByElementId(NdexClasses.ReifiedEdgeTerm, elementID);
		if ( result != null) return result;
		result = getDocumentByElementId(NdexClasses.Support, elementID);
		if ( result != null) return result;
		
		throw new NdexException ("ElementId " + elementID + " was not found in database.");
	} */

    /**
     * Check if an account has a certain privilege on a network.
     * @param accountName account name to be checked.
     * @param UUIDStr  id of the network
     * @param permission  permission to be verified.
     * @return true if the account has that privilege.
     * @throws NdexException 
     * @throws ObjectNotFoundException 
     */
	
/*	public boolean checkPrivilege(String accountName, String UUIDStr, Permissions permission) throws ObjectNotFoundException, NdexException {
		
		ODocument d = this.getRecordByUUID(UUID.fromString(UUIDStr), NdexClasses.Network);
		
		String vstr = d.field(NdexClasses.Network_P_visibility);
		
		VisibilityType v = VisibilityType.valueOf(vstr);
		
		if ( v == VisibilityType.PUBLIC) return true;

		if ( accountName == null ) return false;
		return Helper.checkPermissionOnNetworkByAccountName(db,UUIDStr, accountName, permission);
	} */
	
	/**
	 * Check if a user has access to a network summary.
	 * @param accountName
	 * @param UUIDStr
	 * @return
	 * @throws ObjectNotFoundException
	 * @throws NdexException
	 */
/*	public boolean networkSummaryIsReadable(String accountName, String UUIDStr) throws ObjectNotFoundException, NdexException {
		
		ODocument d = this.getRecordByUUID(UUID.fromString(UUIDStr), NdexClasses.Network);
		
		String vstr = d.field(NdexClasses.Network_P_visibility);
		
		VisibilityType v = VisibilityType.valueOf(vstr);
		
		if ( v != VisibilityType.PRIVATE ) return true;

		if ( accountName == null ) return false;
		return Helper.checkPermissionOnNetworkByAccountName(db,UUIDStr, accountName, Permissions.READ);
	} */
	
	
	
/*	public ODocument getDocumentByElementId(String NdexClassName, long elementID) {
		return Helper.getDocumentByElementId(db, elementID, NdexClassName);
	} */

/*	private static String getBaseTermStrForBaseTerm(BaseTerm bterm, Network n) {
		String localName = bterm.getName();
		
		if ( bterm.getNamespaceId() > 0 && ( n != null )) {
			Namespace ns = n.getNamespaces().get(bterm.getNamespaceId());
			String prefix = ns.getPrefix();
			if ( prefix != null)
				return prefix + ":" + localName;
			return  ns.getUri() + localName;
		}
		return localName;
	}
*/
 /*   private  Namespace getNamespace(ODocument ns)  {
        Namespace rns = new Namespace();
        rns.setId((long)ns.field("id"));
        rns.setPrefix((String)ns.field(NdexClasses.ns_P_prefix));
        rns.setUri((String)ns.field(NdexClasses.ns_P_uri));
        
        SingleNetworkDAO.getPropertiesFromDoc(ns, rns);
        
        return rns;
     } 
     

   */ 
    

	
	

/*
    public static NetworkSummary getNetworkSummary(ODocument doc)  {
    	NetworkSummary networkSummary = new NetworkSummary();
    	setNetworkSummary(doc,networkSummary);
    	return networkSummary;
    }


	public NetworkSummary getNetworkSummaryById (String networkUUIDStr) throws ObjectNotFoundException {
		ODocument doc = getNetworkDocByUUIDString(networkUUIDStr);
		if ( doc == null) return null;
		return getNetworkSummary(doc);
	}
 
	
	public boolean networkIsReadOnly(String networkUUIDStr) throws ObjectNotFoundException {
		ODocument doc = getNetworkDocByUUIDString(networkUUIDStr);
		Long commitId = doc.field(NdexClasses.Network_P_readOnlyCommitId );
		return commitId != null && commitId.longValue() >0 ;
	}
	
    public ODocument getNetworkDocByUUID(UUID id) throws ObjectNotFoundException {
    	return getNetworkDocByUUIDString(id.toString());
    }

    */

    
	
/*    
    protected static  NetworkSummary setNetworkSummary(ODocument doc, NetworkSummary nSummary)  {
    	
	Helper.populateExternalObjectFromResultSet (nSummary, doc);

    	nSummary.setName((String)doc.field(NdexClasses.Network_P_name));
    	nSummary.setDescription((String)doc.field(NdexClasses.Network_P_desc));
    	nSummary.setEdgeCount((int)doc.field(NdexClasses.Network_P_edgeCount));
    	nSummary.setNodeCount((int)doc.field(NdexClasses.Network_P_nodeCount));
    	nSummary.setVersion((String)doc.field(NdexClasses.Network_P_version));
        nSummary.setVisibility(VisibilityType.valueOf((String)doc.field(NdexClasses.Network_P_visibility)));
        
        nSummary.setOwner((String) doc.field(NdexClasses.Network_P_owner));
        Boolean isComplete = doc.field(NdexClasses.Network_P_isComplete);
        if ( isComplete != null)
        	nSummary.setIsComplete(isComplete.booleanValue());
        else 
        	nSummary.setIsComplete(false);
        
        nSummary.setEdgeCount((int)doc.field(NdexClasses.Network_P_edgeCount));

        Long ROcommitId = doc.field(NdexClasses.Network_P_readOnlyCommitId);
        if ( ROcommitId !=null)
        	nSummary.setReadOnlyCommitId(ROcommitId);
        
        Long ROCacheId = doc.field(NdexClasses.Network_P_cacheId);
        if ( ROCacheId !=null)
        	nSummary.setReadOnlyCacheId(ROCacheId);
        
        nSummary.setIsLocked((boolean)doc.field(NdexClasses.Network_P_isLocked));
        nSummary.setURI(NdexDatabase.getURIPrefix()+ "/network/" + nSummary.getExternalId().toString());

        List<NdexPropertyValuePair> props = doc.field(NdexClasses.ndexProperties);
    	if (props != null && props.size()> 0) {
    		for (NdexPropertyValuePair p : props)
    			nSummary.getProperties().add(p);
    	}
        
		NetworkSourceFormat fmt = Helper.getSourceFormatFromNetworkDoc(doc);
		if ( fmt !=null) {
			NdexPropertyValuePair p = new NdexPropertyValuePair(NdexClasses.Network_P_source_format,fmt.toString());
			nSummary.getProperties().add(p);
		} 
        
        return nSummary;
    }
*/
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

		List<String> groupNames = null;
		if ( loggedInUser !=null && simpleNetworkQuery.getIncludeGroups()) {
			try (UserDAO userDao = new UserDAO() ) {
				for ( Membership m : userDao.getUserGroupMemberships(loggedInUser.getExternalId(), Permissions.MEMBER,0,0) ) {
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
		String sqlStr = "select creation_time, modification_time, name,description,version,"
				+ "edgecount,nodecount,visibility,owner,owneruuid,"
				+ " properties,sourceformat,is_validated,readonly "
				+ "from network where \"UUID\" = ? and is_deleted= false";
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
		    sqlStr += " where \"UUID\" = '" + networkId + "' ::uuid and is_deleted=false and islocked=false";
		    
		    try (PreparedStatement p = db.prepareStatement(sqlStr)) {
		    	for ( int i = 0 ; i < values.size(); i++) {
		    		p.setString(i, values.get(i));
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
	
	public void updateNetworkVisibility (UUID networkId, VisibilityType v) throws SQLException, NdexException, SolrServerException, IOException {
		 String sqlStr = "update network set visibility = " + v.toString() + " where \"UUID\" = ? and is_deleted=false and islocked=false";
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
	
}
