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
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.logging.Logger;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.ndexbio.common.NdexClasses;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.solr.NetworkGlobalIndexManager;
import org.ndexbio.common.solr.SingleNetworkSolrIdxManager;
import org.ndexbio.model.exceptions.*;
import org.ndexbio.model.object.Membership;
import org.ndexbio.model.object.MembershipType;
import org.ndexbio.model.object.NdexPropertyValuePair;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.PropertiedObject;
import org.ndexbio.model.object.SimplePropertyValuePair;
import org.ndexbio.model.object.network.Edge;
import org.ndexbio.model.object.network.FunctionTerm;
import org.ndexbio.model.object.network.Namespace;
import org.ndexbio.model.object.network.Network;
import org.ndexbio.model.object.network.NetworkSourceFormat;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.object.network.Node;
import org.ndexbio.model.object.network.PropertyGraphEdge;
import org.ndexbio.model.object.network.PropertyGraphNetwork;
import org.ndexbio.model.object.network.PropertyGraphNode;
import org.ndexbio.model.object.network.ReifiedEdgeTerm;
import org.ndexbio.model.object.network.VisibilityType;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;


public class NetworkDAO extends NetworkDocDAO {
			

	
    private static final String[] networkElementType = {NdexClasses.Network_E_BaseTerms, NdexClasses.Network_E_Nodes, NdexClasses.Network_E_Citations,
    		NdexClasses.Network_E_Edges, NdexClasses.Network_E_FunctionTerms, NdexClasses.Network_E_Namespace,
    		NdexClasses.Network_E_ReifiedEdgeTerms, NdexClasses.Network_E_Supports
  //  		,	NdexClasses.E_ndexPresentationProps, NdexClasses.E_ndexProperties
    		};
	
	static Logger logger = Logger.getLogger(NetworkDAO.class.getName());
	
	@Deprecated
	public NetworkDAO () throws NdexException, SQLException {
	    super();

	}

	
	 
/*	private List<String> getOpaqueAspectEdges (ODocument networkDoc) {
		List<String> result = new ArrayList<>();
		Map<String, String> opaqueAspectEdgeTable = networkDoc.field(NdexClasses.Network_P_opaquEdgeTable);
		if ( opaqueAspectEdgeTable == null )
			return result;
		
		result.addAll(opaqueAspectEdgeTable.values());
		
		return result;
	} */
	
	/**
	 * Delete up to CLEANUP_BATCH_SIZE vertices in a network. This function is for cleaning up a logically 
	 * deleted network in the database. 
	 * @param networkDoc
	 * @return the number of vertices being deleted. 
	 * @throws NdexException 
	 * @throws ObjectNotFoundException 
	 */
/*	private int cleanupNetworkElements(ODocument networkDoc) throws ObjectNotFoundException, NdexException {
        int counter = 0;

        List<String> edgesToBeDeleted = getOpaqueAspectEdges(networkDoc);
        for ( String ndexEdges : networkElementType)
           edgesToBeDeleted.add(ndexEdges);
        
        for ( String fieldName : edgesToBeDeleted) {
        	counter = cleanupElementsByEdge(networkDoc, fieldName, counter);
        	if ( counter >= CLEANUP_BATCH_SIZE) {
        		return counter;
        	}
        }
        
        return counter;
	} */
	
	/**
	 * Cleanup up to CLEANUP_BATCH_SIZE vertices in the out going edge of fieldName. 
	 * @param doc The ODocument record to be clean up on.
	 * @param fieldName
	 * @param currentCounter
	 * @return the number of vertices being deleted. 
	 * @throws NdexException 
	 */
/*	private int cleanupElementsByEdge(ODocument doc, String fieldName, int currentCounter) throws NdexException {
		
		Object f = doc.field("out_"+fieldName);
		if ( f != null ) {
			if ( f instanceof ORidBag ) {
				ORidBag e = (ORidBag)f;
				int counter = currentCounter;
				for ( OIdentifiable rid : e) {
					if(rid !=null) {
						counter = cleanupElement((ODocument)rid, counter);
						if ( counter >= CLEANUP_BATCH_SIZE) {
							return counter;
						}
					} else 
						throw new NdexException ("Db traversing on " + fieldName + " got a null value in the ORidBag.");
				}
				return  counter;
			} 
			return cleanupElement((ODocument)f, currentCounter);
		}
		return currentCounter;
	}
	
	private int cleanupElement(ODocument doc, int currentCount) {
		int counter = currentCount;
		doc.reload();

		for	(int retry = 0;	retry <	NdexDatabase.maxRetries;	++retry)	{
			try	{
				graph.removeVertex(graph.getVertex(doc));
				break;
			} catch(ONeedRetryException	e)	{
				logger.warning("Retry: "+ e.getMessage());
				doc.reload();
			}
		}
		counter ++;
		if ( counter % 200 == 0 ) {
			graph.commit();
			if (counter % 1000 == 0 ) {
				logger.info("Deleted " + counter + " vertexes from network during cleanup.");
			}
		}
		return counter;
	}
*/
	/*public int logicalDeleteNetwork (String uuid) throws ObjectNotFoundException, NdexException {
		ODocument networkDoc = getRecordByUUID(UUID.fromString(uuid), NdexClasses.Network);

		if ( networkDoc != null) {
		   networkDoc.fields(NdexClasses.ExternalObj_isDeleted,true,
				   NdexClasses.ExternalObj_mTime, new Date()).save();
		} 
		commit(); 

		// remove the solr Index
		SingleNetworkSolrIdxManager idxManager = new SingleNetworkSolrIdxManager(uuid);
		NetworkGlobalIndexManager globalIdx = new NetworkGlobalIndexManager();
		try {
			idxManager.dropIndex();
			globalIdx.deleteNetwork(uuid);
		} catch (SolrServerException | HttpSolrClient.RemoteSolrException | IOException se ) {
			logger.warning("Failed to delete Solr Index for network " + uuid + ". Please clean it up manually from solr. Error message: " + se.getMessage());
		}
		
 		return 1;
	} */
	
/*	@Deprecated
	private int deleteNetworkElements(String UUID) {
		int counter = 0;
		
		String query = "traverse * from ( traverse out_networkNodes,out_BaseTerms,out_networkNS from (select from network where UUID='"
				+ UUID + "')) while @class <> 'network'";
        final List<ODocument> elements = db.query(new OSQLSynchQuery<ODocument>(query));
        
        for ( ODocument element : elements ) {
        	element.reload();
        	graph.removeVertex(graph.getVertex(element));
        	counter ++;
        	if ( counter % 1500 == 0 ) {
        		graph.commit();
        		if (counter % 6000 == 0 ) {
        			logger.info("Deleted " + counter + " vertexes from network during cleanup." + UUID);
        		}
        	}

        }
        return counter;
	} */
	
	/** 
	 * delete all ndex and presentation properties from a network record.
	 * Properities on network elements won't be deleted.
	 */
/*	public static void deleteNetworkProperties(ODocument networkDoc) {

		networkDoc.removeField(NdexClasses.ndexProperties);
		networkDoc.save();

	} */
	
 

	
	
    public int grantPrivilege(String networkUUID, String accountUUID, Permissions permission) throws NdexException, SolrServerException, IOException {
    	// check if the edge already exists?

    /*	Permissions p = Helper.getNetworkPermissionByAccout(db,networkUUID, accountUUID);

        if ( p!=null && p == permission) {
        	logger.info("Permission " + permission + " already exists between account " + accountUUID + 
        			 " and network " + networkUUID + ". Igore grant request."); 
        	return 0;
        }
        
        //check if this network has other admins
        if ( permission != Permissions.ADMIN && !Helper.canRemoveAdmin(db, networkUUID, accountUUID)) {
        	
        	throw new NdexException ("Privilege change failed. Network " + networkUUID +" will not have an administrator if permission " +
        	    permission + " are granted to account " + accountUUID);
        }
        
        ODocument networkdoc = this.getNetworkDocByUUID(UUID.fromString(networkUUID));
        ODocument accountdoc = this.getRecordByUUID(UUID.fromString(accountUUID), null);
        
        if ( permission == Permissions.ADMIN && accountdoc.getClassName().equals(NdexClasses.Group) )
        	throw new NdexException ("Groups are not allowed to administer a network, only individual accounts are allowed.");
        
        String className = accountdoc.getClassName();
        String accountName = accountdoc.field(NdexClasses.account_P_accountName);
        OrientVertex networkV = graph.getVertex(networkdoc);
        OrientVertex accountV = graph.getVertex(accountdoc);
        
        for ( com.tinkerpop.blueprints.Edge e : accountV.getEdges(networkV, Direction.OUT)) { 
    		for	(int retry = 0;	retry <	NdexDatabase.maxRetries;	++retry)	{
    			try	{
    	          	graph.removeEdge(e);
    				break;
    			} catch(ONeedRetryException	ex)	{
    				logger.warning("Retry removing edge between account and network: " + ex.getMessage());
    		        networkdoc.reload();
    		        accountdoc.reload();
//    		       networkV.reload();
//    		       accountV.reload();
    			}
    		}
        }

        networkdoc.reload();
        accountdoc.reload();
        
		for	(int retry = 0;	retry <	NdexDatabase.maxRetries;	++retry)	{
			try	{
		        accountV.addEdge(permission.toString().toLowerCase(), networkV);
				break;
			} catch(ONeedRetryException	e)	{
				logger.warning("Retry adding edge between account and network: " + e.getMessage());
		        networkdoc.reload();
		        accountdoc.reload();
			//	taskV.getRecord().removeField("out_"+ NdexClasses.Task_E_owner);
			}
		}

		//update solr index
		NetworkGlobalIndexManager networkIdx = new NetworkGlobalIndexManager();
		
		networkIdx.grantNetworkPermission(networkUUID, accountName, permission, p,
				className.equals(NdexClasses.User)); */
               
    	return 1;
    }

    public int revokePrivilege(String networkUUID, String accountUUID) throws NdexException, SolrServerException, IOException {
    	// check if the edge exists?

   /* 	Permissions p = Helper.getNetworkPermissionByAccout(this.db,networkUUID, accountUUID);

        if ( p ==null ) {
        	logger.info("Permission doesn't exists between account " + accountUUID + 
        			 " and network " + networkUUID + ". Igore revoke request."); 
        	return 0;
        }
        
        //check if this network has other admins
        if ( p == Permissions.ADMIN && !Helper.canRemoveAdmin(this.db, networkUUID, accountUUID)) {
        	
        	throw new NdexException ("Privilege revoke failed. Network " + networkUUID +" only has account " + accountUUID
        			+ " as the administrator.");
        }
        
        ODocument networkdoc = this.getNetworkDocByUUID(UUID.fromString(networkUUID));
        ODocument accountdoc = this.getRecordByUUID(UUID.fromString(accountUUID), null);
        
        String className = accountdoc.getClassName();
        String accountName = accountdoc.field(NdexClasses.account_P_accountName);
        OrientVertex networkV = graph.getVertex(networkdoc);
        OrientVertex accountV = graph.getVertex(accountdoc);
        
        for ( com.tinkerpop.blueprints.Edge e : accountV.getEdges(networkV, Direction.OUT)) { 
        	
    		for	(int retry = 0;	retry <	NdexDatabase.maxRetries;	++retry)	{
    			try	{
    	          	graph.removeEdge(e);
    				break;
    			} catch(ONeedRetryException	ex)	{
    				logger.warning("Retry removing edge between account and network: " + ex.getMessage());
    		       networkV.reload();
    		       accountV.reload();
    			}
    		}
          	break;
        }

		//update solr index
		NetworkGlobalIndexManager networkIdx = new NetworkGlobalIndexManager();
		networkIdx.revokeNetworkPermission(networkUUID, accountName, p, 
				className.equals(NdexClasses.User));
        */
        
    	return 1;
    }

	
/*	public void rollback() {
		graph.rollback();		
	} */

    
	
	
}



