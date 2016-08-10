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
import com.orientechnologies.common.concur.ONeedRetryException;
import com.orientechnologies.orient.core.command.traverse.OTraverse;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.db.record.OIdentifiable;
import com.orientechnologies.orient.core.db.record.ridbag.ORidBag;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.filter.OSQLPredicate;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.impls.orient.OrientGraph;
import com.tinkerpop.blueprints.impls.orient.OrientVertex;
import com.orientechnologies.orient.core.id.ORID;

public class NetworkDAO extends NetworkDocDAO {
		
	private OrientGraph graph;	
	
	private static final int CLEANUP_BATCH_SIZE = 10000;
	
	public static final String RESET_MOD_TIME = "resetMTime";
	
    private static final String[] networkElementType = {NdexClasses.Network_E_BaseTerms, NdexClasses.Network_E_Nodes, NdexClasses.Network_E_Citations,
    		NdexClasses.Network_E_Edges, NdexClasses.Network_E_FunctionTerms, NdexClasses.Network_E_Namespace,
    		NdexClasses.Network_E_ReifiedEdgeTerms, NdexClasses.Network_E_Supports
  //  		,	NdexClasses.E_ndexPresentationProps, NdexClasses.E_ndexProperties
    		};
	
	static Logger logger = Logger.getLogger(NetworkDAO.class.getName());
	
	
	public NetworkDAO (ODatabaseDocumentTx db) {
	    super(db);
		graph = new OrientGraph(this.db,false);
		graph.setAutoScaleEdgeType(true);
		graph.setEdgeContainerEmbedded2TreeThreshold(40);
		graph.setUseLightweightEdges(true);

	}

	public NetworkDAO () throws NdexException {
	    this(NdexDatabase.getInstance().getAConnection());
	}

		
	
	public int deleteNetwork (String UUID) throws ObjectNotFoundException, NdexException {
		int counter = 0, cnt = 0;
		
		do {
			cnt = cleanupDeleteNetwork(UUID);
			if (cnt <0 ) 
				counter += -1*cnt;
			else 
				counter += cnt;
		} while ( cnt < 0 ); 
 		return counter;
	}
	
	/** 
	 * Delete up to CLEANUP_BATCH_SIZE vertices in a network. This function is for cleaning up a logically 
	 * deleted network in the database. 
	 * @param uuid
	 * @return Number of vertices being deleted. If the returned number is negative, it means the elements
	 * of the network are not completely deleted yet, and the number of vertices deleted are abs(returned number).
	 * @throws ObjectNotFoundException
	 * @throws NdexException
	 */
	public int cleanupDeleteNetwork(String uuid) throws ObjectNotFoundException, NdexException {
		ODocument networkDoc = getRecordByUUID(UUID.fromString(uuid), NdexClasses.Network);
		
		int count = cleanupNetworkElements(networkDoc);
		if ( count >= CLEANUP_BATCH_SIZE) {
			return (-1) * count;
		}
		
		// remove the network node.
		networkDoc.reload();
		
		for	(int retry = 0;	retry <	NdexDatabase.maxRetries;	++retry)	{
			try	{
				graph.removeVertex(graph.getVertex(networkDoc));
				break;
			} catch(ONeedRetryException	e)	{
				logger.warning("Retry: "+ e.getMessage());
				networkDoc.reload();
			}
		}
		
		return count++;
	}
	 
	private List<String> getOpaqueAspectEdges (ODocument networkDoc) {
		List<String> result = new ArrayList<>();
		Map<String, String> opaqueAspectEdgeTable = networkDoc.field(NdexClasses.Network_P_opaquEdgeTable);
		if ( opaqueAspectEdgeTable == null )
			return result;
		
		result.addAll(opaqueAspectEdgeTable.values());
		
		return result;
	}
	
	/**
	 * Delete up to CLEANUP_BATCH_SIZE vertices in a network. This function is for cleaning up a logically 
	 * deleted network in the database. 
	 * @param networkDoc
	 * @return the number of vertices being deleted. 
	 * @throws NdexException 
	 * @throws ObjectNotFoundException 
	 */
	private int cleanupNetworkElements(ODocument networkDoc) throws ObjectNotFoundException, NdexException {
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
	}
	
	/**
	 * Cleanup up to CLEANUP_BATCH_SIZE vertices in the out going edge of fieldName. 
	 * @param doc The ODocument record to be clean up on.
	 * @param fieldName
	 * @param currentCounter
	 * @return the number of vertices being deleted. 
	 * @throws NdexException 
	 */
	private int cleanupElementsByEdge(ODocument doc, String fieldName, int currentCounter) throws NdexException {
		
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

	public int logicalDeleteNetwork (String uuid) throws ObjectNotFoundException, NdexException {
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
	}
	
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
	public static void deleteNetworkProperties(ODocument networkDoc) {

		networkDoc.removeField(NdexClasses.ndexProperties);
		networkDoc.save();

	}
	
	
	public PropertyGraphNetwork getProperytGraphNetworkById (UUID networkID, int skipBlocks, int blockSize) throws NdexException {

   	    return new PropertyGraphNetwork( getNetwork(networkID,skipBlocks,blockSize)); 
	}

    
    
	public PropertyGraphNetwork getProperytGraphNetworkById(UUID id) throws NdexException {
		

		 return new PropertyGraphNetwork(this.getNetworkById(id)); 
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
	    **************************************************************************/
	
	public List<Membership> getNetworkUserMemberships(UUID networkId, Permissions permission, int skipBlocks, int blockSize) 
			throws ObjectNotFoundException, NdexException {
		Preconditions.checkArgument(!Strings.isNullOrEmpty(networkId.toString()),
		
				"A network UUID is required");
		if ( permission !=null )
			Preconditions.checkArgument( 
				(permission.equals( Permissions.ADMIN) )
				|| (permission.equals( Permissions.WRITE ))
				|| (permission.equals( Permissions.READ )),
				"Valid permission required");
		
		ODocument network = this.getRecordByUUID(networkId, NdexClasses.Network);
		
		final int startIndex = skipBlocks
				* blockSize;
		
			List<Membership> memberships = new ArrayList<>();
			
			String networkRID = network.getIdentity().toString();
			
			String traverseCondition = null;
			
			if ( permission != null) 
				traverseCondition = NdexClasses.Network +".in_"+ permission.name().toString();
			else 
				traverseCondition = "in_" + Permissions.ADMIN + ",in_" + Permissions.READ + ",in_" + Permissions.WRITE;   
			
			OSQLSynchQuery<ODocument> query = new OSQLSynchQuery<>(
		  			"SELECT " + NdexClasses.account_P_accountName + "," +
		  					NdexClasses.ExternalObj_ID + ", $path" +
			        " FROM"
		  			+ " (TRAVERSE "+ traverseCondition.toLowerCase() +" FROM"
		  				+ " " + networkRID
		  				+ "  WHILE $depth <=1)"
		  			+ " WHERE (@class = '" + NdexClasses.User + "'"
		  			+ " OR @class='" + NdexClasses.Group + "') " +" AND ( " + NdexClasses.ExternalObj_isDeleted + " = false) "
		 			+ " ORDER BY " + NdexClasses.ExternalObj_cTime + " DESC " + " SKIP " + startIndex
		 			+ " LIMIT " + blockSize);
			
			List<ODocument> records = this.db.command(query).execute(); 
			for(ODocument member: records) {
				
				Membership membership = new Membership();
				membership.setMembershipType( MembershipType.NETWORK );
				membership.setMemberAccountName( (String) member.field(NdexClasses.account_P_accountName) ); 
				membership.setMemberUUID( UUID.fromString( (String) member.field(NdexClasses.ExternalObj_ID) ) );
				membership.setPermissions( Helper.getNetworkPermissionFromInPath ((String)member.field("$path") ));
				membership.setResourceName( (String) network.field("name") );
				membership.setResourceUUID( networkId );
				
				memberships.add(membership);
			}
			
			logger.info("Successfuly retrieved network-user memberships");
			return memberships;
	}

	
	
    public int grantPrivilege(String networkUUID, String accountUUID, Permissions permission) throws NdexException, SolrServerException, IOException {
    	// check if the edge already exists?

    	Permissions p = Helper.getNetworkPermissionByAccout(db,networkUUID, accountUUID);

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
				className.equals(NdexClasses.User));
               
    	return 1;
    }

    public int revokePrivilege(String networkUUID, String accountUUID) throws NdexException, SolrServerException, IOException {
    	// check if the edge exists?

    	Permissions p = Helper.getNetworkPermissionByAccout(this.db,networkUUID, accountUUID);

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
        
        
    	return 1;
    }

	
/*	public void rollback() {
		graph.rollback();		
	} */

	@Override
	public void commit() {
		graph.commit();
		
	}
	
	@Override
	public void close() {
		graph.shutdown();
	}
    
	
	public void updateNetworkProfile(UUID networkId, NetworkSummary newSummary) throws NdexException, SolrServerException, IOException {
		ODocument doc = this.getNetworkDocByUUID(networkId);
		
		Helper.updateNetworkProfile(doc, newSummary);
		
		//update solr index
		NetworkGlobalIndexManager networkIdx = new NetworkGlobalIndexManager();
		
		Map<String,Object> newValues = new HashMap<> ();
		
		if ( newSummary.getName() != null) {
				 newValues.put(NdexClasses.Network_P_name, newSummary.getName());
				 newValues.put(RESET_MOD_TIME,"true");
		}
				
			  if ( newSummary.getDescription() != null) {
				newValues.put( NdexClasses.Network_P_desc, newSummary.getDescription());
				newValues.put(RESET_MOD_TIME, "true");
			  }
			
			  if ( newSummary.getVersion()!=null ) {
				newValues.put( NdexClasses.Network_P_version, newSummary.getVersion());
				newValues.put(RESET_MOD_TIME, "true") ;
			  }
			  
	    if ( newSummary.getVisibility()!=null )
				newValues.put( NdexClasses.Network_P_visibility, newSummary.getVisibility());
			 
		networkIdx.updateNetworkProfile(networkId.toString(), newValues);
	}
	
	
}



