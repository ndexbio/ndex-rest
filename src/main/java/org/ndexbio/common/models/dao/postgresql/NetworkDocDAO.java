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
import java.sql.SQLException;
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
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;


public class NetworkDocDAO extends NdexDBDAO {

	private static Logger logger = Logger.getLogger(NetworkDocDAO.class.getName());
	
	

	public NetworkDocDAO () throws NdexException, SQLException {
	    super();
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
	    * @return A table as a map. Key is a string with value either 'user' or 'group'. value is another map which holds all the members under either the 
	    *  'user' or 'group' category. For the inner map, tts key is one of the permission type, value is a set of account names that have that permission.
	    *  If an account has a edit privilege on the network this function wont duplicate that account in the read permission list automatically.
	    * @throws ObjectNotFoundException
	    * @throws NdexException
	    */
		public Map<String,Map<Permissions, Set<String>>> getAllMembershipsOnNetwork(String networkId) 
				throws ObjectNotFoundException, NdexException {
			Preconditions.checkArgument(!Strings.isNullOrEmpty(networkId.toString()),	
					"A network UUID is required");

	/*		ODocument network = this.getNetworkDocByUUIDString(networkId);
			
			Map<Permissions,Set<String>> userMemberships = new HashMap<>();
			
			userMemberships.put(Permissions.ADMIN, new TreeSet<String> ());
			userMemberships.put(Permissions.WRITE, new TreeSet<String> ());
			userMemberships.put(Permissions.READ, new TreeSet<String> ());
			
			Map<Permissions, Set<String>> grpMemberships = new HashMap<>();
			grpMemberships.put(Permissions.ADMIN, new TreeSet<String> ());
			grpMemberships.put(Permissions.READ, new TreeSet<String> ());
			grpMemberships.put(Permissions.WRITE, new TreeSet<String> ()); */
			
			Map<String, Map<Permissions,Set<String>> > fullMembership = new HashMap <>();
		/*	fullMembership.put(NdexClasses.Group,grpMemberships);
			fullMembership.put(NdexClasses.User, userMemberships);
			
			String networkRID = network.getIdentity().toString();
				
			String traverseCondition = "in_" + Permissions.ADMIN + ",in_" + Permissions.READ + ",in_" + Permissions.WRITE;   
				
				OSQLSynchQuery<ODocument> query = new OSQLSynchQuery<>(
			  			"SELECT " + NdexClasses.account_P_accountName + "," +
			  					NdexClasses.ExternalObj_ID + ", $path, @class" +
			  					
				        " FROM"
			  			+ " (TRAVERSE "+ traverseCondition.toLowerCase() +" FROM"
			  				+ " " + networkRID
			  				+ "  WHILE $depth <=1)"
			  			+ " WHERE (@class = '" + NdexClasses.User + "'"
			  			+ " OR @class='" + NdexClasses.Group + "'" +  ") AND ( " + NdexClasses.ExternalObj_isDeleted + " = false) ");
				
				List<ODocument> records = this.db.command(query).execute(); 
				for(ODocument member: records) {
					
					String accountType = member.field("class");
					Permissions p = Helper.getNetworkPermissionFromInPath ((String)member.field("$path"));
					String accountName = member.field(NdexClasses.account_P_accountName);
			
					fullMembership.get(accountType).get(p).add(accountName);
						
//						userMemberships.get(p).add(accountName);	

				} */
				
				return fullMembership;
		}
		

/*	public Namespace getNamespace(String prefix, String URI, UUID networkID ) {
		String query = "select from (traverse out_" +
	    		  NdexClasses.Network_E_Namespace +" from (select from "
	    		  + NdexClasses.Network + " where " +
	    		  NdexClasses.Network_P_UUID + "='" + networkID + 
	    		  "')) where @class='"+  NdexClasses.Namespace + "' and ";
		if ( prefix != null) {
	      query = query + NdexClasses.ns_P_prefix + "='"+ prefix +"'";
		}   else {
		  query = query + NdexClasses.ns_P_uri + "='"+ URI +"'";	
		}	
	    final List<ODocument> nss = db.query(new OSQLSynchQuery<ODocument>(query));
	     
	     if (nss.isEmpty())
	    	 return null;
         Namespace result = getNamespace(nss.get(0));
         return result;
	}
*/

/*	public  Collection<Namespace> getNamespacesFromNetworkDoc(ODocument networkDoc)  {
		ArrayList<Namespace> namespaces = new ArrayList<>();
		
		for ( ODocument doc : Helper.getNetworkElements(networkDoc, NdexClasses.Network_E_Namespace)) {
    			namespaces.add(getNamespace(doc));
    	}
    	return namespaces;
	} */
	
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
		/*	NetworkSummary s = getNetworkSummaryById(id);
			if ( s !=null)
				results .add(s); */
		} 
		
		return new NetworkSearchResult ( solrResults.getNumFound(), solrResults.getStart(), results);
	}

	
	
	
}
