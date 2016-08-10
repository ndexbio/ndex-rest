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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;
import java.util.logging.Logger;

import org.ndexbio.common.NdexClasses;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.SimpleNetworkQuery;
import org.ndexbio.model.object.User;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.orientechnologies.orient.core.command.traverse.OTraverse;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.db.record.OIdentifiable;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.index.OIndex;
import com.orientechnologies.orient.core.index.OIndexException;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.filter.OSQLPredicate;

@Deprecated

public class NetworkSearchDAO extends NdexDBDAO{
	
	private static final Logger logger = Logger.getLogger(NetworkSearchDAO.class.getName());
	
	/**************************************************************************
	    * NetworkSearchDAO
	    * 
	    * @param db
	    *            Database instance from the Connection pool, should be opened
	    **************************************************************************/
	public NetworkSearchDAO (ODatabaseDocumentTx dbConnection) {
		super( dbConnection);
		
	}
	
/*	
	public Collection<NetworkSummary> findNetworks(SimpleNetworkQuery simpleNetworkQuery, int skipBlocks, int top, User loggedInUser) 
			throws NdexException, IllegalArgumentException {
		
		Preconditions.checkArgument(null != simpleNetworkQuery, 
				"A query is required");

		// treat "*" and "" the same way
		if (simpleNetworkQuery.getSearchString().equals("*") )
			simpleNetworkQuery.setSearchString("");

		ORID userRID = null;
		if( loggedInUser != null ) {
				OIndex<?> accountNameIdx = this.db.getMetadata().getIndexManager().getIndex(NdexClasses.Index_accountName);
				OIdentifiable user = (OIdentifiable) accountNameIdx.get( loggedInUser.getAccountName() ); // account to traverse by
				userRID = user.getIdentity();
		}
		
		
		
		if ( simpleNetworkQuery.getSearchString().equals(""))
			return findAllNetworks (simpleNetworkQuery,skipBlocks, top, userRID);
		
		return findNetworksV2 (simpleNetworkQuery,skipBlocks, top, userRID);
	}
	
	private List<NetworkSummary> findAllNetworks(SimpleNetworkQuery simpleNetworkQuery, int skipBlocks, int top, ORID userORID) 
			throws NdexException, IllegalArgumentException {
		
		List<NetworkSummary> finalResult = new ArrayList<>(top);

		int offset = skipBlocks * top;
		
		NetworkDAO dao = new NetworkDAO(this.getDBConnection());
		// has account name
		if(!Strings.isNullOrEmpty(simpleNetworkQuery.getAccountName())) {

			OIndex<?> accountNameIdx = this.db.getMetadata().getIndexManager().getIndex(NdexClasses.Index_accountName);
			OIdentifiable nAccount = (OIdentifiable) accountNameIdx.get(simpleNetworkQuery.getAccountName()); // account to traverse by
				
			if(nAccount == null) 
				throw new NdexException("Invalid accountName "+ simpleNetworkQuery.getAccountName() + " to filter by");
				
			
			OTraverse traverser = new OTraverse().target(nAccount).
					field ( "out_" + NdexClasses.E_admin);

			Permissions permission = simpleNetworkQuery.getPermission();
			if ( permission== null ) permission = Permissions.READ; 
					
			if ( permission == Permissions.WRITE) {
				  traverser = traverser.field("out_" + NdexClasses.account_E_canEdit);
			} else if ( permission == Permissions.READ) {
				  traverser = traverser.field("out_" + NdexClasses.account_E_canEdit)
						      .field("out_" + NdexClasses.account_E_canRead);
			} else if ( permission == Permissions.GROUPADMIN || 
					      permission == Permissions.MEMBER)
				  throw new NdexException ( "Unsupported perimission type in Network search: " + permission.toString() );
			  
			if ( simpleNetworkQuery.getIncludeGroups()) {
				  traverser = traverser.field("out_" + NdexClasses.GRP_E_admin)
						  .field("out_" + NdexClasses.GRP_E_member)
						  .predicate( new OSQLPredicate("$depth <= 2"));
						  
			} else 
				  traverser = traverser.predicate( new OSQLPredicate("$depth <= 1"));

			final TreeSet<NetworkSummary> foundNetworks = new TreeSet<>(new NetworkResultComparator());
			
			if ( userORID != null && userORID.equals(nAccount)) {   // same account
				ODocument networkDoc = null;
				for (OIdentifiable reifiedTRec : traverser) {
		   			  if ( reifiedTRec instanceof ORecordId ) 
			    	    networkDoc = new ODocument((ORecordId)reifiedTRec);
			    	  else if ( ((ODocument)reifiedTRec).getClassName().endsWith(NdexClasses.Network))
			    		networkDoc = (ODocument)reifiedTRec;
			    	  if ( networkDoc !=null) {
						NetworkSummary network =NetworkDocDAO.getNetworkSummary(networkDoc); 
						if ( network.getIsComplete() && !network.getIsDeleted())
							foundNetworks .add(network);
			    	  }
			    }
			} else {     // different account 
		    
				for (OIdentifiable reifiedTRec : traverser) {
		   			if ( reifiedTRec instanceof ORecordId || reifiedTRec instanceof ODocument) {
				       ODocument networkDoc = (reifiedTRec instanceof ORecordId) ? 
				    		   new ODocument((ORecordId)reifiedTRec) :
				    			   (ODocument) reifiedTRec;             
					   if ( networkDoc.getClassName().equals(NdexClasses.Network) &&
		    			  (  (simpleNetworkQuery.getCanRead() ? 
		    					  VisibilityType.valueOf((String)networkDoc.field(NdexClasses.Network_P_visibility))== VisibilityType.PUBLIC :
		    					  VisibilityType.valueOf((String)networkDoc.field(NdexClasses.Network_P_visibility))!= VisibilityType.PRIVATE)
    					   || networkIsReadableByAccount(networkDoc, userORID))) {
							NetworkSummary network =dao.getNetworkSummary(networkDoc); 
							if ( network.getIsComplete() && !network.getIsDeleted())
								foundNetworks .add(network);
					  }
		   			}
				}
			}   

			int i = 0; 
			for( NetworkSummary s: foundNetworks ) {
				if ( i >= offset) 
					finalResult.add(s);
				i++;
				if( finalResult.size()>=top)
					break;
			}
			return finalResult;
		} 

		// doesn't have accountName
		LinkedList<NetworkSummary> resultHolder = new LinkedList<>(); 
		
		for (final ODocument networkDoc : db.browseClass(NdexClasses.Network)) {
			Boolean isComplete = networkDoc.field(NdexClasses.Network_P_isComplete);
			Boolean isDeleted = networkDoc.field(NdexClasses.ExternalObj_isDeleted);
			if ( isComplete !=null && isComplete.booleanValue() && !isDeleted.booleanValue()) {
				VisibilityType visibility = 
							  VisibilityType.valueOf((String)networkDoc.field(NdexClasses.Network_P_visibility));
				if ((simpleNetworkQuery.getCanRead()? 
						visibility == VisibilityType.PUBLIC: 
						visibility != VisibilityType.PRIVATE )
					|| networkIsReadableByAccount(networkDoc, userORID) )	{	
						resultHolder.addFirst(dao.getNetworkSummary(networkDoc));
				}
			}
		} 
		
		for( int i = 0 ; i <top+offset && !resultHolder.isEmpty() ; i++ ) {
			NetworkSummary s = resultHolder.remove();
			if ( i >= offset) 
				finalResult.add(s);
			
		}
		return finalResult;
			
	}

	private Collection<NetworkSummary> findNetworksV2(SimpleNetworkQuery simpleNetworkQuery, int skipBlocks, int top, ORID userRID) 
			throws IllegalArgumentException, NdexException {
		
		Collection<NetworkSummary> resultList =  new ArrayList<>(top);
		
		TreeSet<ORID> resultIDSet = new TreeSet<> ();
		
		int counter = 0;
		int offset = skipBlocks * top;
		
		ORID adminUserRID = null;
		if( !Strings.isNullOrEmpty(simpleNetworkQuery.getAccountName()) ) {
				OIndex<?> accountNameIdx = this.db.getMetadata().getIndexManager().getIndex(NdexClasses.Index_accountName);
				OIdentifiable user = (OIdentifiable) accountNameIdx.get( simpleNetworkQuery.getAccountName()  ); // account to traverse by

				if(user == null) 
					throw new NdexException("Invalid accountName "+ simpleNetworkQuery.getAccountName() + " to filter by");
				
				adminUserRID = user.getIdentity();
		}
		
//	    Permissions p = simpleNetworkQuery.getPermission();

	NetworkDAO dao = new NetworkDAO(this.getDBConnection());

	try {
	
		
		// search network first.
		OIndex<?> networkIdx = db.getMetadata().getIndexManager().getIndex(NdexClasses.Index_network_name_desc);
		
		String searchStr = simpleNetworkQuery.getSearchString();
        
		Collection<OIdentifiable> networkIds =  (Collection<OIdentifiable>) networkIdx.get( searchStr); 


		for ( OIdentifiable dId : networkIds) {
			ODocument doc = dId.getRecord();
			Boolean isDeleted = doc.field(NdexClasses.ExternalObj_isDeleted);
			Boolean isComplete = doc.field(NdexClasses.Network_P_isComplete);
			if ( isComplete != null && isComplete.booleanValue() &&
					 (	isDeleted == null || !isDeleted.booleanValue() )) {
				if (isSearchable(doc, userRID, adminUserRID, simpleNetworkQuery.getCanRead(), 
						simpleNetworkQuery.getIncludeGroups(), simpleNetworkQuery.getPermission())) {
					resultIDSet.add(dId.getIdentity());
					if ( counter >= offset) {
						NetworkSummary network =dao.getNetworkSummary(doc); 
						if ( network.getIsComplete() && !network.getIsDeleted())
							resultList .add(network);
					}
					counter ++;
					if ( resultList.size()>= top)
						return resultList;
				}
			}
		}
		
		
		// search baseterms
		OIndex<?> basetermIdx = db.getMetadata().getIndexManager().getIndex(NdexClasses.Index_BTerm_name);
		
		Collection<OIdentifiable> bTermIds =  (Collection<OIdentifiable>) basetermIdx.get( searchStr); 

		 for ( OIdentifiable btid : bTermIds ) {
	 		ODocument bTermDoc = btid.getRecord();
	 		ODocument doc = bTermDoc.field("in_" + NdexClasses.Network_E_BaseTerms);
	 			
			ORID id = doc.getIdentity();
			if ( !resultIDSet.contains(id))	{
				Boolean isComplete = doc.field(NdexClasses.Network_P_isComplete);
				Boolean isDeleted = doc.field(NdexClasses.ExternalObj_isDeleted);
	            if ( isComplete !=null && isComplete.booleanValue() && 
	            	 (	isDeleted == null || !isDeleted.booleanValue() ) && 
	            	  isSearchable(doc, userRID, adminUserRID,simpleNetworkQuery.getCanRead(), 
	  						simpleNetworkQuery.getIncludeGroups(), simpleNetworkQuery.getPermission())) {
						resultIDSet.add(id);
						if ( counter >= offset) {
							NetworkSummary network =NetworkDocDAO.getNetworkSummary(doc); 
							resultList .add(network);
						}
						counter ++;
						if ( resultList.size()>= top)
							return resultList;
				}
			}
		 }
        		
		  // search node.name
 		  OIndex<?> nodeNameIdx = db.getMetadata().getIndexManager().getIndex(NdexClasses.Index_node_name);
			
 		  Collection<OIdentifiable> nodeIds =  (Collection<OIdentifiable>) nodeNameIdx.get( searchStr); 

 		  for ( OIdentifiable nid : nodeIds ) {
 			ODocument nodeDoc = nid.getRecord();
 			ODocument doc = nodeDoc.field("in_" + NdexClasses.Network_E_Nodes);
 			
			ORID id = doc.getIdentity();
			if ( ! resultIDSet.contains(id)) {
			    Boolean isComplete = doc.field(NdexClasses.Network_P_isComplete);
				Boolean isDeleted = doc.field(NdexClasses.ExternalObj_isDeleted);
	            if( isComplete !=null && isComplete.booleanValue() &&
	                (isDeleted == null || !isDeleted.booleanValue() ) &&		
	               (isSearchable(doc, userRID,adminUserRID,simpleNetworkQuery.getCanRead(), 
							simpleNetworkQuery.getIncludeGroups(), simpleNetworkQuery.getPermission())) ) {
						resultIDSet.add(id);
						if ( counter >= offset) {
							NetworkSummary network =NetworkDocDAO.getNetworkSummary(doc); 
							if ( network.getIsComplete())
								resultList .add(network);
						}
						counter ++;
						if ( resultList.size()>= top)
							return resultList;
				}
			}
 		  }
		return resultList;
	  } catch (OIndexException e1) {
		  throw new NdexException ("Invalid search string. " + e1.getCause().getMessage());
	  }
	}
	*/
	
	public static boolean isSearchable(ODocument networkDoc, ORID userRID, ORID adminUserRID, boolean canRead, boolean includeGroups, Permissions permission)
			throws NdexException {
		
		if ( adminUserRID != null) {  // we only want networks administrated by that user.
			Permissions p = (permission == null? Permissions.READ: permission) ;
			return networkAdminedByAccount(networkDoc, userRID, adminUserRID, includeGroups, p, canRead);
		}
			
		
		VisibilityType visibility = VisibilityType.valueOf((String)networkDoc.field(NdexClasses.Network_P_visibility));
		
		boolean baseCriteria = canRead ? 
				(visibility == VisibilityType.PUBLIC) : (visibility != VisibilityType.PRIVATE);
		
		return  baseCriteria || ( userRID !=null && networkIsReadableByAccount(networkDoc,userRID)) ;

	 }
	
	  public static boolean networkIsReadableByAccount(ODocument networkDoc, 
				ORID userORID) {
	
		  for (OIdentifiable reifiedTRec : new OTraverse()
					.fields(	"in_" + NdexClasses.account_E_canEdit,
							"in_" + NdexClasses.account_E_canRead,
							"in_" + NdexClasses.E_admin,
							"in_" + NdexClasses.GRP_E_admin, 
							"in_" + NdexClasses.GRP_E_member)
					.target(networkDoc)
					.predicate( new OSQLPredicate("$depth <= 2"))) {

			  if ( reifiedTRec.getIdentity().equals(userORID)) 
				  return true;
		  }
		  return false;
		  
	   }


	  private static boolean networkAdminedByAccount(ODocument networkDoc,ORID userORID, ORID adminUserRID, boolean includeGroups,
			      Permissions permission, boolean canRead ) throws NdexException {
	
		  OTraverse traverser = new OTraverse()
			.field(	"in_" + NdexClasses.E_admin)
			.target(networkDoc);

		  if ( permission == Permissions.WRITE) {
			  traverser = traverser.field("in_" + NdexClasses.account_E_canEdit);
		  } else if ( permission == Permissions.READ) {
			  traverser = traverser.field("in_" + NdexClasses.account_E_canEdit)
					      .field("in_" + NdexClasses.account_E_canRead);
		  } else if ( permission == Permissions.GROUPADMIN || 
				      permission == Permissions.MEMBER)
			  throw new NdexException ( "Unsupported perimission type in Network search: " + permission.toString() );
		  
		  if ( includeGroups) {
			  traverser = traverser.field("in_" + NdexClasses.GRP_E_admin)
					  .field("in_" + NdexClasses.GRP_E_member)
					  .predicate( new OSQLPredicate("$depth <= 2"));
					  
		  } else 
			  traverser = traverser.predicate( new OSQLPredicate("$depth <= 1"));
		  
		  
		  if ( userORID != null && userORID.equals(adminUserRID)) {   // same account
			  for (OIdentifiable reifiedTRec : traverser) {
				  if ( reifiedTRec.getIdentity().equals(adminUserRID)) {
					  return true;
				  }
				  
			  }
			  return false;
		  } 
		  
		  boolean satisfiedAdminUser = false;
		  boolean satisfiedUser  = false;
		  for (OIdentifiable reifiedTRec : traverser) {
				  ORID id = reifiedTRec.getIdentity(); 
				  if ( id.equals(adminUserRID)) {
					  satisfiedAdminUser = true;
				  } else if (userORID!=null && id.equals(userORID))
					  satisfiedUser  = true;
		  }

		  if ( !satisfiedAdminUser)
				  return false;
			  
		  if ( satisfiedUser) return true;
			  
		  // check if the network attribute
		  VisibilityType visibility = VisibilityType.valueOf((String)networkDoc.field(NdexClasses.Network_P_visibility));
		  if ( visibility == VisibilityType.PUBLIC)
 				  return true;
	//	  if ( !canRead &&  visibility == VisibilityType.DISCOVERABLE) 
 	//			  return true;
 			  
		  return networkIsReadableByAccount(networkDoc,userORID);
		  		  
	   }

/*
	  public class NetworkResultComparator implements Comparator<NetworkSummary> {

		@Override
		public int compare(NetworkSummary o1, NetworkSummary o2) {
			return  o1.getModificationTime().compareTo(o2.getModificationTime()) * -1;
			 
		}
		  
	  }
	 */ 
}
