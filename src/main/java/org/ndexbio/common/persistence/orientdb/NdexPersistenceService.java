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
package org.ndexbio.common.persistence.orientdb;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import org.ndexbio.common.NdexClasses;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.models.dao.postgresql.Helper;
import org.ndexbio.common.models.dao.postgresql.NdexDBDAO;
import org.ndexbio.common.models.object.network.RawCitation;
import org.ndexbio.common.models.object.network.RawEdge;
import org.ndexbio.common.models.object.network.RawNamespace;
import org.ndexbio.common.models.object.network.RawSupport;
import org.ndexbio.common.util.NdexUUIDFactory;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.object.NdexPropertyValuePair;
import org.ndexbio.model.object.SimplePropertyValuePair;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.TaskType;
import org.ndexbio.model.object.network.FunctionTerm;
import org.ndexbio.model.object.network.Namespace;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.task.NdexServerQueue;

import com.google.common.base.Preconditions;
import com.orientechnologies.common.concur.ONeedRetryException;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
import com.tinkerpop.blueprints.impls.orient.OrientVertex;


/*
 * An implementation of the NDExPersistenceService interface that uses a 
 * in-memory cache to provide persistence for new ndex doain objects
 * 
 * 
 * Utilizes a Google Guava cache that uses Jdexids as keys and VertexFrame implementations 
 * as values
 */

public class NdexPersistenceService extends PersistenceService {
	
	public static final String URICitationType="URI";
	public static final String pmidPrefix = "pmid:";


	// key is the element_id of a BaseTerm, value is the id of the node which this BaseTerm represents
    private Map<Long, Long> baseTermNodeIdMap;
	// Store the mapping in memory to have better performance.
//	private NdexDatabase database;
 // key is the edge id which this term reified.
    private Map<Long,Long>  edgeIdReifiedEdgeTermIdMap;
	// maps an external node id to new node id created in Ndex.
    private Map<String, Long> externalIdNodeMap; 
	//key is a function term Id, value is the node id which uses 
    // that function as represents term
    private Map<Long,Long> functionTermIdNodeIdMap;

	// maps a node name to Node Id.
    private Map<String, Long> namedNodeMap;

    private Map<RawEdge, Long> edgeMap;
    
    private String ownerAccount;
    
    private Map<RawCitation, Long>           rawCitationMap;
    
    
    private Map<FunctionTerm, Long> rawFunctionTermFunctionTermIdMap; 
    
    private Map<RawSupport, Long>  rawSupportMap;
    
    // key is a "rawFunctionTerm", which has element id as -1. This table
    // matches the key to a functionTerm that has been stored in the db.
    
    private Map<Long, Long> reifiedEdgeTermIdNodeIdMap;
  //  private LoadingCache<Long, Node> reifiedEdgeTermNodeCache;
    
    //key is the name of the node. This cache is for loading simple SIF 
    // for now
//    private LoadingCache<String, Node> namedNodeCache;
    
    
    /*
     * Currently, the procces flow of this class is:
     * 
     * 1. create object 
     * 2. Create New network
     */
    
	public NdexPersistenceService(NdexDatabase db) throws NdexException {
		super(db);

		this.network = null;
		this.ownerAccount = null;
		
		this.rawCitationMap  = new TreeMap <> ();
        this.baseTermNodeIdMap = new TreeMap <> ();
		this.namedNodeMap  = new TreeMap <> ();
		this.reifiedEdgeTermIdNodeIdMap = new HashMap<>(100);
		this.edgeIdReifiedEdgeTermIdMap = new HashMap<>(100);
		this.rawFunctionTermFunctionTermIdMap = new TreeMap<> ();
		this.rawSupportMap  = new TreeMap<> ();
		this.edgeMap = new TreeMap<>();
		this.functionTermIdNodeIdMap = new HashMap<>(100);
		// intialize caches.
		
		externalIdNodeMap = new TreeMap<>(); 

	    logger = Logger.getLogger(NdexPersistenceService.class.getName());

	}

	
	public NdexPersistenceService(NdexDatabase db, UUID networkID) throws NdexException  {
		super(db);
		
		this.networkDoc = this.networkDAO.getNetworkDocByUUID(networkID);
		this.networkVertex = graph.getVertex(this.networkDoc);
		this.network = networkDAO.getNetworkSummary(networkDoc);
		
		
		this.rawCitationMap  = new TreeMap <> ();
        this.baseTermNodeIdMap = new TreeMap <> ();
		this.namedNodeMap  = new TreeMap <> ();
		this.reifiedEdgeTermIdNodeIdMap = new HashMap<>(100);
		this.edgeIdReifiedEdgeTermIdMap = new HashMap<>(100);
		this.rawFunctionTermFunctionTermIdMap = new TreeMap<> ();
		this.rawSupportMap  = new TreeMap<> ();
		this.functionTermIdNodeIdMap = new HashMap<>(100);
		// intialize caches.
		
		externalIdNodeMap = new TreeMap<>(); 

	    logger = Logger.getLogger(NdexPersistenceService.class.getName());

	}
	
	
	public void abortTransaction() throws ObjectNotFoundException, NdexException {
		System.out.println(this.getClass().getName()
				+ ".abortTransaction has been invoked.");

		logger.info("Deleting partial network "+ network.getExternalId().toString() + " in order to rollback in response to error");
		this.networkDAO.logicalDeleteNetwork(network.getExternalId().toString());
		graph.commit();
		Task task = new Task();
		task.setTaskType(TaskType.SYSTEM_DELETE_NETWORK);
		task.setResource(network.getExternalId().toString());
		NdexServerQueue.INSTANCE.addSystemTask(task);
		logger.info("Partial network "+ network.getExternalId().toString() + " is deleted.");
	}
	
	// alias is treated as a baseTerm
	public void addAliasToNode(long nodeId, String[] aliases) throws ExecutionException, NdexException {
		ODocument nodeDoc = elementIdCache.get(nodeId);

		List<Long> newAliases = new ArrayList<>(aliases.length);
		
		for (String alias : aliases) {
			Long b= this.getBaseTermId(alias);
		    Long repNodeId = this.baseTermNodeIdMap.get(b);
			if ( repNodeId != null && repNodeId.equals(nodeId)) {
		    	logger.warning("Alias '" + alias + "' is also the represented base term of node " + 
			    nodeId +". Alias ignored.");
		    } else {
		    	newAliases.add(b);
		    }
		}
		
		Collection<Long> oldAliases = nodeDoc.field(NdexClasses.Node_P_alias);
		if ( oldAliases !=null && oldAliases.size() > 0 )
			oldAliases.addAll(newAliases);
		else 
			oldAliases = newAliases;
		nodeDoc.field(NdexClasses.Node_P_alias, oldAliases).save();
		
		elementIdCache.put(nodeId, nodeDoc);
	}

	public void addAliasToNode(long nodeId, Collection<String> aliases) throws ExecutionException, NdexException {
		ODocument nodeDoc = elementIdCache.get(nodeId);

		List<Long> newAliases = new ArrayList<>(aliases.size());
		
		for (String alias : aliases) {
			Long b= this.getBaseTermId(alias);
		    Long repNodeId = this.baseTermNodeIdMap.get(b);
			if ( repNodeId != null && repNodeId.equals(nodeId)) {
		    	logger.warning("Alias '" + alias + "' is also the represented base term of node " + 
			    nodeId +". Alias ignored.");
		    } else {
		    	newAliases.add(b);
		    }
		}
		
		Collection<Long> oldAliases = nodeDoc.field(NdexClasses.Node_P_alias);
		if ( oldAliases !=null && oldAliases.size() > 0 )
			oldAliases.addAll(newAliases);
		else 
			oldAliases = newAliases;
		nodeDoc.field(NdexClasses.Node_P_alias, oldAliases).save();
		
		elementIdCache.put(nodeId, nodeDoc);
	}
	
	
	
/*	No longer valid in 1.3			
	public void addCitationToElement(long elementId, Long citationId, String className) throws ExecutionException, NdexException{
		ODocument elementRec = elementIdCache.get(elementId);
		OrientVertex nodeV = graph.getVertex(elementRec);
		
		ODocument citationRec = elementIdCache.get(citationId);
		OrientVertex citationV = graph.getVertex(citationRec);
		
		if ( className.equals(NdexClasses.Node) ) {
 	       	nodeV.addEdge(NdexClasses.Citation, graph.getVertex(citationV));
		} else if ( className.equals(NdexClasses.Edge) ) {
			nodeV.addEdge(NdexClasses.Citation, graph.getVertex(citationV));
		} else {
			throw new NdexException ("Citation can only be added to node or edges of network, can't added to " + className);
		}
		
		elementIdCache.put(citationId, citationV.getRecord());
		ODocument o = nodeV.getRecord();
//		o.reload();
		elementIdCache.put(elementId, o);
	}
	*/
	public void addCitationsToElement(long elementId, Collection<Long> newCitationIds) throws ExecutionException{
		
		if( newCitationIds == null || newCitationIds.size() == 0 ) return;
		
		ODocument elementRec = elementIdCache.get(elementId);

		Set<Long> citationIds = elementRec.field(NdexClasses.Citation);
		if ( citationIds == null)
			citationIds = new HashSet<>(100);
		
		citationIds.addAll(newCitationIds);
		
		elementRec.field(NdexClasses.Citation, citationIds).save();
		
		elementIdCache.put(elementId, elementRec);
	}

	
	public void setReferencesOnNode(Long nodeId, Collection<Long> newCitations, Collection<Long> newRelatedTerms, Collection<Long> newAliases) 
			throws ExecutionException {
		
		ODocument nodeRec = elementIdCache.get(nodeId);

		if( newCitations !=null && newCitations.size()>0) {
		    nodeRec.field(NdexClasses.Citation, newCitations);
		}
		
		if ( newRelatedTerms !=null && newRelatedTerms.size()> 0 ) {
			nodeRec.field(NdexClasses.Node_P_relatedTo, newRelatedTerms);
		}
		
		if ( newAliases !=null && newAliases.size()>0) 
			nodeRec.field(NdexClasses.Node_P_alias, newAliases);
		
		nodeRec = nodeRec.save();
		
		elementIdCache.put(nodeId, nodeRec);
		
	}
	
	//TODO: generalize this function so that createEdge(....) can use it.
	public void addMetaDataToNode (Long subjectNodeId, Long supportId, Long citationId,  Map<String,String> annotations) 
			throws ExecutionException {
        
		ODocument nodeDoc = this.elementIdCache.get(subjectNodeId);
		        
        if ( supportId != null) {
    		Set<Long> supportIds = nodeDoc.field(NdexClasses.Support);
    		if ( supportIds == null)
    			supportIds = new HashSet<>(10);
    		
    		supportIds.add(supportId);
    		nodeDoc.field(NdexClasses.Support,supportIds);
        }

	    if (citationId != null) {
    		Set<Long> citationIds = nodeDoc.field(NdexClasses.Citation);
    		if ( citationIds == null)
    			citationIds = new HashSet<>(10);

    		citationIds.add(citationId);
    		nodeDoc.field(NdexClasses.Citation, citationIds);
	    }

		if ( annotations != null) {
			List<NdexPropertyValuePair> props =nodeDoc.field(NdexClasses.ndexProperties); 
			if ( props == null)		
				props = new ArrayList<>(annotations.size());
			
			for (Map.Entry<String, String> e : annotations.entrySet()) {
                props.add(new NdexPropertyValuePair(e.getKey(),e.getValue()));
			}
			nodeDoc.field(NdexClasses.ndexProperties,props);
		}
		
		nodeDoc.save();
		
		this.elementIdCache.put(subjectNodeId, nodeDoc);

	}

/*	
	private OrientVertex addPropertyToVertex(OrientVertex v, NdexPropertyValuePair p) 
			throws ExecutionException, NdexException {

        OrientVertex pV = this.createNdexPropertyVertex(p);
        
       	v.addEdge(NdexClasses.E_ndexProperties, pV);
       	return v;
	}  */

	// related term is assumed to be a base term
	public void setRelatedTermsOnNode(long nodeId, String[] relatedTerms) throws ExecutionException, NdexException {
		ODocument nodeDoc = elementIdCache.get(nodeId);
		
		List<Long> newRelateToIds = new ArrayList<> (relatedTerms.length);
		
		for (String rT : relatedTerms) {
			Long bID= this.getBaseTermId(rT);
		
		    Long repNodeId = this.baseTermNodeIdMap.get(bID);
			if ( repNodeId != null && repNodeId.equals(nodeId)) {
		    	logger.warning("Related term '" + rT + "' is also the represented base term of node " + 
			    nodeId +". This related term will be ignored.");
		    } else {
		    	newRelateToIds.add(bID);
		    }
		}
		
/*		List<Long> relateTos = nodeDoc.field(NdexClasses.Node_P_relateTo);
		if ( relateTos !=null && relateTos.size() > 0 )
			relateTos.addAll(newRelateToIds);
		else 
			relateTos = newRelateToIds; */
		nodeDoc.field(NdexClasses.Node_P_relatedTo, newRelateToIds).save();

		elementIdCache.put(nodeId, nodeDoc);
	}

	/**
	 *  Look up in the current context, if an edge with the same subject,predicate and object exists, return that edge,
	 *  otherwise create a new edge and return the id of the new edge.  
	 * @param subjectNodeId
	 * @param objectNodeId
	 * @param predicateId
	 * @param supportId
	 * @param citationId
	 * @param annotation
	 * @return
	 * @throws NdexException
	 * @throws ExecutionException
	 */
	public Long getEdge(Long subjectNodeId, Long objectNodeId, Long predicateId, 
			 Long supportId, Long citationId, Map<String,String> annotation ) throws NdexException, ExecutionException {
		RawEdge rawEdge = new RawEdge(subjectNodeId, predicateId, objectNodeId );
		Long edgeId = this.edgeMap.get(rawEdge);
		if ( edgeId != null) return edgeId;
		
		return createEdge(subjectNodeId, objectNodeId, predicateId, supportId, citationId, annotation);
	}	

	/**
	 *  Create an edge in the database.
	 * @param subjectNodeId
	 * @param objectNodeId
	 * @param predicateId
	 * @param supportId
	 * @param citationId
	 * @param annotation
	 * @return  The element id of the created edge.
	 * @throws NdexException
	 * @throws ExecutionException
	 */
	public Long createEdge(Long subjectNodeId, Long objectNodeId, Long predicateId, 
			 Long supportId, Long citationId, Map<String,String> annotation )
			throws NdexException, ExecutionException {

		List<NdexPropertyValuePair> props = null;
		if ( annotation != null && annotation.size()>0) {
			props = new ArrayList<>(annotation.size());
			for (Map.Entry<String, String> e : annotation.entrySet()) {
				props.add(new NdexPropertyValuePair(e.getKey(),e.getValue()));
			}
		} 
		
		return createEdge(subjectNodeId,objectNodeId,predicateId, supportId, citationId, props);
	}
	
	public Long createEdge(Long subjectNodeId, Long objectNodeId, Long predicateId, 
			 Long support, Long citation, List<NdexPropertyValuePair> properties )
			throws NdexException, ExecutionException {
		if (null != objectNodeId && null != subjectNodeId && null != predicateId) {
			
			Long edgeId = database.getNextId(localConnection);
/*			Edge edge = new Edge();
			edge.setId(database.getNextId());
			edge.setSubjectId(subjectNodeId);
			edge.setObjectId(objectNodeId);
			edge.setPredicateId(predicateId); */
			
			ODocument subjectNodeDoc = elementIdCache.get(subjectNodeId) ;
			ODocument objectNodeDoc  = elementIdCache.get(objectNodeId) ;
			
			ODocument edgeDoc = new ODocument(NdexClasses.Edge);
			edgeDoc = edgeDoc.fields(NdexClasses.Element_ID, edgeId,
									NdexClasses.Edge_P_predicateId,predicateId);
			
		    // add citation.
		    if (citation != null) {
		    	Set<Long> citationList = new HashSet<> (1);
		    	citationList.add(citation);
		    	edgeDoc.field(NdexClasses.Citation, citationList);
		    }
		    
		    if ( support != null) {
		    	Set<Long> supportSet = new HashSet<> (1);
		    	supportSet.add(support);
		    	edgeDoc.field(NdexClasses.Support, supportSet);
		    }
		    
		    if ( properties !=null && properties.size()>0)
		    	edgeDoc.field(NdexClasses.ndexProperties,properties);
		    
		    edgeDoc.save();

			OrientVertex edgeVertex = this.graph.getVertex(edgeDoc);
			
			this.networkVertex.addEdge(NdexClasses.Network_E_Edges, edgeVertex);
			OrientVertex objectV = this.graph.getVertex(objectNodeDoc);
			edgeVertex.addEdge(NdexClasses.Edge_E_object, objectV);
			OrientVertex subjectV = this.graph.getVertex(subjectNodeDoc);
			subjectV.addEdge(NdexClasses.Edge_E_subject, edgeVertex);

		    this.network.setEdgeCount(this.network.getEdgeCount()+1);
		    
		    elementIdCache.put(edgeId, edgeVertex.getRecord());
		    elementIdCache.put(subjectNodeId, subjectV.getRecord());
		    elementIdCache.put(objectNodeId, objectV.getRecord());
			return edgeId;
		} 
		throw new NdexException("Null value for one of the parameter when creating Edge.");
	}

	


	public void createNamespace2(String prefix, String URI) throws NdexException {
		RawNamespace r = new RawNamespace(prefix, URI);
		getNamespace(r);
	}
	
    private NetworkSummary createNetwork(String title, String version, UUID uuid){
    	logger.info("Creating network with UUID:" + uuid.toString());
		this.network = new NetworkSummary();
		this.network.setExternalId(uuid);
		this.network.setURI(NdexDatabase.getURIPrefix()+ "/network/"+ uuid.toString());
		this.network.setName(title);
		this.network.setVisibility(VisibilityType.PRIVATE);
		this.network.setIsLocked(false);
		this.network.setIsComplete(false);
		this.network.setOwner(this.ownerAccount);

        
		this.networkDoc = new ODocument (NdexClasses.Network)
		  .fields(NdexClasses.Network_P_UUID,this.network.getExternalId().toString(),
		  	NdexClasses.ExternalObj_cTime, this.network.getCreationTime(),
		  	NdexClasses.ExternalObj_mTime, this.network.getModificationTime(),
		  	NdexClasses.ExternalObj_isDeleted, false,
		  	NdexClasses.Network_P_name, this.network.getName(),
		  	NdexClasses.Network_P_desc, "",
		  	NdexClasses.Network_P_isLocked, this.network.getIsLocked(),
		  	NdexClasses.Network_P_isComplete, this.network.getIsComplete(),
		  	NdexClasses.Network_P_visibility, this.network.getVisibility().toString(),
		  	NdexClasses.Network_P_cacheId, this.network.getReadOnlyCacheId(),
		  	NdexClasses.Network_P_readOnlyCommitId, this.network.getReadOnlyCommitId(),
		  	NdexClasses.Network_P_owner,  this.ownerAccount);
		
		if ( version != null) {
			this.network.setVersion(version);
			this.networkDoc.field(NdexClasses.Network_P_version, version);
		}
			
		this.networkDoc =this.networkDoc.save();
		
		this.networkVertex = this.graph.getVertex(getNetworkDoc());
		
		return this.network;
	}

	
	/*
	 * public method to allow xbel parsing components to rollback the
	 * transaction and close the database connection if they encounter an error
	 * situation
	 */
/*
	public void createNewNetwork(String ownerName, String networkTitle, String version) throws Exception {
		createNewNetwork(ownerName, networkTitle, version,NdexUUIDFactory.INSTANCE.getNDExUUID() );
	}
*/
	/*
	 * public method to persist INetwork to the orientdb database using cache
	 * contents.
	 */

	public void createNewNetwork(String ownerName, String networkTitle, String version)  {
		Preconditions.checkNotNull(ownerName,"A network owner name is required");
		Preconditions.checkNotNull(networkTitle,"A network title is required");
		

		UUID uuid = NdexUUIDFactory.INSTANCE.createNewNDExUUID() ;
		// find the network owner in the database
		this.ownerAccount = ownerName;

				
		createNetwork(networkTitle,version, uuid);

		logger.info("A new NDex network titled: " +network.getName()
				+" owned by " +ownerName +" has been created");
		
	}


	/**
	 * 
	 * @param id the node id that was assigned by external source. 
	 * @param name the name of the node. If the value is null, no node name will be 
	 *        created in Ndex.
	 * @return
	 */
	public Long findOrCreateNodeIdByExternalId(String id, String name) {
		Long nodeId = this.externalIdNodeMap.get(id);
		if ( nodeId != null) return nodeId;
		
		//create a node for this external id.
		
		nodeId = database.getNextId(localConnection);

		ODocument nodeDoc = new ODocument(NdexClasses.Node);

		nodeDoc.field(NdexClasses.Element_ID, nodeId);
		if ( name != null) 
			nodeDoc.field(NdexClasses.Node_P_name, name);
		
		nodeDoc = nodeDoc.save();
		
		OrientVertex nodeV = graph.getVertex(nodeDoc);
		
		networkVertex.addEdge(NdexClasses.Network_E_Nodes,nodeV);
		
		network.setNodeCount(network.getNodeCount()+1);
		
		elementIdCache.put(nodeId, nodeV.getRecord());
		
		externalIdNodeMap.put(id, nodeId);
		return nodeId;

	}
	
	public Long getNodeIdByBaseTermId(Long bTermId) {
		Long nodeId = this.baseTermNodeIdMap.get(bTermId);
		
		if (nodeId != null) 
			return nodeId;
		
		// otherwise insert Node.
		
		nodeId = createNodeFromTermId(bTermId, NdexClasses.BaseTerm);

		this.baseTermNodeIdMap.put(bTermId, nodeId);
		return nodeId;
	}
	
	
	/**
     * Find a user based on account name.
     * @param accountName
     * @return ODocument object that hold data for this user account
     * @throws NdexException
     */

	private ODocument findUserByAccountName(String accountName)
			throws NdexException
			{
		if (accountName == null	)
			throw new NdexException("No accountName was specified.");


		final String query = "select * from " + NdexClasses.Account + 
				  " where accountName = '" + accountName + "'";
				
		List<ODocument> userDocumentList = localConnection
					.query(new OSQLSynchQuery<ODocument>(query));

		if ( ! userDocumentList.isEmpty()) {
				return userDocumentList.get(0);
				
		}
		return null;
	}

	
	public Long getBaseTermId (Namespace namespace, String localTerm) throws NdexException, ExecutionException {
		if ( namespace.getPrefix() != null ) {
			return getBaseTermId(namespace.getPrefix()+":"+localTerm);
		}
		
		return getBaseTermId(namespace.getUri()+localTerm);
	}
	
	
	
	public Long getCitationId(String title, String idType, String identifier, 
			List<String> contributors) throws NdexException, ExecutionException {
		
		RawCitation rCitation = new RawCitation(title, idType, identifier, contributors);
		Long citationId = rawCitationMap.get(rCitation);

		if ( citationId != null ) {
	        return citationId;
		}
		
		// persist the citation object in db.
		citationId = createCitation(title, idType, identifier, contributors, null);
				
		rawCitationMap.put(rCitation, citationId);
		return citationId; 
	}
	
	
	// input parameter is a "rawFunctionTerm", which has element_id as -1;
	public Long getFunctionTermId(Long baseTermId, List<Long> termList) throws ExecutionException {
		
		FunctionTerm func = new FunctionTerm();
		func.setFunctionTermId(baseTermId);
			
		for ( Long termId : termList) {
			  func.getParameterIds().add( termId);
		}		  
	
		Long functionTermId = this.rawFunctionTermFunctionTermIdMap.get(func);
		if ( functionTermId != null) return functionTermId;
		
		functionTermId = createFunctionTerm(baseTermId, termList);
        this.rawFunctionTermFunctionTermIdMap.put(func, functionTermId);
        return functionTermId;
	}
	
	
	public ODocument getNetworkDoc() {
		return networkDoc;
	}
	
	/**
	 * Create or Find a node from a baseTerm.
	 * @param termString
	 * @return
	 * @throws ExecutionException
	 * @throws NdexException
	 */
	public Long getNodeIdByBaseTerm(String termString) throws ExecutionException, NdexException {
		Long id = this.getBaseTermId(termString);
		return this.getNodeIdByBaseTermId(id);
	}


	public Long getNodeIdByFunctionTermId(Long funcTermId) {
		Long nodeId = this.functionTermIdNodeIdMap.get(funcTermId) ;
		
		if (nodeId != null) return nodeId;
		
		// otherwise insert Node.
		nodeId = createNodeFromTermId(funcTermId, NdexClasses.FunctionTerm);
		
		this.functionTermIdNodeIdMap.put(funcTermId, nodeId);
		return nodeId;
	}
	
	/**
	 * This function doesn't check if a node with same semantic meaning exists. It doesn't register the created 
	 * node in the lookup table either. The only external usage of this function is to create orphan node in XBEL networks 
	 * 
	 * @param funcTermId
	 * @return
	 */
	
	public Long createNodeFromTermId(Long funcTermId, String representTermType)  {
		Long nodeId = database.getNextId(localConnection);

		ODocument nodeDoc = new ODocument(NdexClasses.Node);

		nodeDoc = nodeDoc.fields(NdexClasses.Element_ID, nodeId,
					NdexClasses.Node_P_represents, funcTermId,
					NdexClasses.Node_P_representTermType, representTermType)
				.save();
		
		OrientVertex nodeV = graph.getVertex(nodeDoc);
		
		networkVertex.addEdge(NdexClasses.Network_E_Nodes,nodeV);
		
		network.setNodeCount(network.getNodeCount()+1);

		elementIdCache.put(nodeId, nodeV.getRecord());
		
		return nodeId;
		
	}
	
	
	public Long getNodeIdByName(String key) {
		Long nodeId = this.namedNodeMap.get(key);
		
		if ( nodeId !=null ) {
			return nodeId;
		}
		
		// otherwise insert Node.
		nodeId = database.getNextId(localConnection);

		ODocument nodeDoc = new ODocument(NdexClasses.Node)
		        .fields(NdexClasses.Element_ID, nodeId,
		        		NdexClasses.Node_P_name, key)
				.save();
		
		OrientVertex nodeV = graph.getVertex(nodeDoc);
		
		networkVertex.addEdge(NdexClasses.Network_E_Nodes,nodeV);
		
		network.setNodeCount(network.getNodeCount()+1);
		
		elementIdCache.put(nodeId, nodeV.getRecord());
		this.namedNodeMap.put(key,nodeId);
		return nodeId;
		
	}

	
	public Long getNodeIdByReifiedEdgeTermId(Long reifiedEdgeTermId)  {
		Long nodeId = this.reifiedEdgeTermIdNodeIdMap.get(reifiedEdgeTermId); 

		if (nodeId != null) 
			return nodeId;
		
		// otherwise insert Node.
		nodeId = createNodeFromTermId(reifiedEdgeTermId, NdexClasses.ReifiedEdgeTerm);
		this.reifiedEdgeTermIdNodeIdMap.put(reifiedEdgeTermId, nodeId);
		return nodeId;
	}
	
	
	public Long getReifiedEdgeTermIdFromEdgeId(Long edgeId) throws ExecutionException {
		Long reifiedEdgeTermId = this.edgeIdReifiedEdgeTermIdMap.get(edgeId);
				
		if (reifiedEdgeTermId != null) 	return reifiedEdgeTermId;
		
		// create new term
		reifiedEdgeTermId = this.database.getNextId(localConnection);
		
		ODocument eTermdoc = new ODocument (NdexClasses.ReifiedEdgeTerm);
		eTermdoc = eTermdoc.field(NdexClasses.Element_ID, reifiedEdgeTermId)
				.save();
		
		OrientVertex etV = graph.getVertex(eTermdoc);
		ODocument edgeDoc = elementIdCache.get(edgeId);
		
		OrientVertex edgeV = graph.getVertex(edgeDoc);
		etV.addEdge(NdexClasses.ReifiedEdge_E_edge, edgeV);
		networkVertex.addEdge(NdexClasses.Network_E_ReifiedEdgeTerms,
				etV);
		
		elementIdCache.put(reifiedEdgeTermId, etV.getRecord());
		elementIdCache.put(edgeId,edgeV.getRecord());
		this.edgeIdReifiedEdgeTermIdMap.put(edgeId, reifiedEdgeTermId);
		return reifiedEdgeTermId;
	}

	
	public Long getSupportId(String literal, Long citationId) throws NdexException {
		
		RawSupport r = new RawSupport(literal, (citationId !=null ? citationId.longValue(): -1));

		Long supportId = this.rawSupportMap.get(r);

		if ( supportId != null ) return supportId;
		
		// persist the support object in db.
		supportId = createSupport(literal, citationId,null);
		this.rawSupportMap.put(r, supportId);
		return supportId; 
	}
	
	public void persistNetwork() throws NdexException {
		try {
			
			network.setIsComplete(true);
			getNetworkDoc().fields(NdexClasses.Network_P_isComplete,true,
					NdexClasses.Network_P_edgeCount, network.getEdgeCount(),
			        NdexClasses.Network_P_nodeCount, network.getNodeCount(),
			        NdexClasses.ExternalObj_mTime, Calendar.getInstance().getTime() )
			  .save();
			
			this.localConnection.commit();
			
			if ( this.ownerAccount != null) {
				networkVertex.reload();
				ODocument ownerDoc =  findUserByAccountName(this.ownerAccount);		
				OrientVertex ownerV = this.graph.getVertex(ownerDoc);
				
		
				for	(int retry = 0;	retry <	NdexDatabase.maxRetries;	++retry)	{
					try	{
						ownerV.reload();
						ownerV.addEdge(NdexClasses.E_admin, this.networkVertex);
						break;
					} catch(ONeedRetryException	e)	{
						logger.warning("Retry - " + e.getMessage());
						//ownerV.reload();
//						networkVertex.reload();
					}
				}
			
				// create the solr index
				createSolrIndex(networkDoc);
				
				this.localConnection.commit();
			}

			logger.info("Finished loading network " + network.getName());
		} catch (Exception e) {
			e.printStackTrace();
			String msg = "unexpected error in persist network. Cause: " + e.getMessage();
			logger.severe(msg);
			throw new NdexException (msg);
		} /* finally {
			graph.shutdown();
	//		this.database.close();
			logger.info("Connection to orientdb database closed");
		} */
	}
	
	@Override
	public void close () {
		graph.shutdown();
		logger.info("Connection to orientdb database closed");
	}
	
	//Add a collection of properties to a network.
	
	public void setNetworkProperties(Collection<NdexPropertyValuePair> properties, 
			Collection<SimplePropertyValuePair> presentationProperties) {
		if ( properties != null ) {
			this.network.getProperties().addAll(properties);
			this.networkDoc.field(NdexClasses.ndexProperties, properties).save();
		}
	
	//	if ( presentationProperties != null ) 
	//		this.network.getPresentationProperties().addAll(presentationProperties);

	}
	

	
	public void setNetworkVisibility(VisibilityType visibility) {

		this.networkDoc = this.networkDoc.field(NdexClasses.Network_P_visibility, visibility)
				.save();

	}
	
	public void setNetworkTitleAndDescription(String title, String description) {

	   this.network.setDescription( description != null ? description: "");
	   this.networkDoc = this.networkDoc.field(NdexClasses.Network_P_desc, this.network.getDescription()).save();
	   
	   if ( title != null) {
		   this.network.setName(title);
		   this.networkDoc = this.networkDoc.field(NdexClasses.Network_P_name, title).save();
	   }
	   
	}
	
	public void setNodeName(long nodeId, String name) throws ExecutionException {
		ODocument nodeDoc = elementIdCache.get(nodeId);
		
		nodeDoc = nodeDoc.field(NdexClasses.Node_P_name, name).save();
		
		elementIdCache.put(nodeId, nodeDoc);
	}

	public void addElementProperty(Long elementId, String key, String value, String type) throws ExecutionException {
/*		if ( elementId == null) {
			System.out.println("br");
		} else 
			System.out.println("Adding propperty to element " + elementId);  */
		
		ODocument elementDoc = this.elementIdCache.get(elementId);
		
		List<NdexPropertyValuePair> props = elementDoc.field(NdexClasses.ndexProperties);
		if ( props == null )
			props = new ArrayList<>(1);
		
		NdexPropertyValuePair p = new NdexPropertyValuePair(key,value);
		p.setDataType(type);
		props.add(p);
		elementDoc.field(NdexClasses.ndexProperties, props);
		elementDoc.save();

	}
	
	
	public void setNodeProperties(Long nodeId, Collection<NdexPropertyValuePair> properties, 
			Collection<SimplePropertyValuePair> presentationProperties) throws ExecutionException {
		setElementProperties(nodeId, properties);
	}
	
	public void setCitationProperties(Long citationId, Collection<NdexPropertyValuePair> properties, 
			Collection<SimplePropertyValuePair> presentationProperties) throws ExecutionException {
		setElementProperties(citationId, properties);
	}
	
	private void setElementProperties(Long elementId, Collection<NdexPropertyValuePair> properties) throws ExecutionException {
		ODocument elementDoc = this.elementIdCache.get(elementId);
		
		elementDoc.field(NdexClasses.ndexProperties, properties).save();
	} 
	
	/**
	 *  create a represent edge from a node to a term.
	 * @param nodeId
	 * @param termId
	 * @throws ExecutionException 
	 */
	public void setNodeRepresentBaseTerm(long nodeId, long termId) throws ExecutionException {
		ODocument nodeDoc = this.elementIdCache.get(nodeId);
		nodeDoc = nodeDoc.fields(NdexClasses.Node_P_represents,termId,
				                 NdexClasses.Node_P_representTermType,NdexClasses.BaseTerm).save();
		
		this.elementIdCache.put(nodeId, nodeDoc);
	}
	
	public void updateNetworkSummary() {
	   networkDoc = Helper.updateNetworkProfile(networkDoc, network);
	   
	   List<NdexPropertyValuePair> props = new ArrayList<> (network.getProperties().size());
	   for ( NdexPropertyValuePair p : network.getProperties()) {
		   if ( !p.getPredicateString().equals(NdexClasses.Network_P_source_format)) {
			   props.add(p);
		   }
	   }
	   networkDoc.field(NdexClasses.ndexProperties, props);
	   
	}
	
	
	
}
