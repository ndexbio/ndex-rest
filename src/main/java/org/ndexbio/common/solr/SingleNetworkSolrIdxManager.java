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
package org.ndexbio.common.solr;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.ConfigSetAdminRequest;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.request.CoreStatus;
import org.apache.solr.client.solrj.SolrRequest.METHOD;
import org.apache.solr.client.solrj.response.ConfigSetAdminResponse;
import org.apache.solr.client.solrj.response.CoreAdminResponse;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.NamedList;
import org.ndexbio.common.persistence.CX2NetworkLoader;
import org.ndexbio.cx2.aspect.element.core.CxAttributeDeclaration;
import org.ndexbio.cx2.aspect.element.core.CxNode;
import org.ndexbio.cx2.aspect.element.core.DeclarationEntry;
import org.ndexbio.cxio.aspects.datamodels.ATTRIBUTE_DATA_TYPE;
import org.ndexbio.cxio.aspects.datamodels.NodeAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NodesElement;
import org.ndexbio.cxio.core.AspectIterator;
import org.ndexbio.model.cx.FunctionTermElement;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.tools.SearchUtilities;
import org.ndexbio.rest.Configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SingleNetworkSolrIdxManager implements AutoCloseable{

	private String solrUrl;
	
	private String collectionName; 
	private HttpSolrClient client;
	
	static private final  int batchSize = 2000;
	
	//NDEx will auto create index for networks with node count larger than this value
	// other wise it will delay the creation until the first time this network is queried.
	static public final int AUTOCREATE_THRESHHOLD= 1; //100;
	
	private int counter ; 
	private Collection<SolrInputDocument> docs ;
	
	public static final String ID = "id";
//	public  static final String NODE_NAME= "name";
	public  static final String TYPE = "type";
	public  static final String COMPLEX = "complex";
	public  static final String PROTEINFAMILY = "proteinfamily";
	public  static final String MEMBER= "member";
	
	private static final String NAME = "nodeName";
//	public static final String REPRESENTS = "represents";
	public static final String ALIAS= "alias";
		
	public SingleNetworkSolrIdxManager(String networkUUID) {
		collectionName = networkUUID;
		solrUrl = Configuration.getInstance().getSolrURL();
		client = new HttpSolrClient.Builder(solrUrl).build();
	}
	
	public SolrDocumentList getNodeIdsByQuery(String query, int limit) throws SolrServerException, IOException, NdexException {
				
		client.setBaseURL(solrUrl+ "/" + collectionName);

		SolrQuery solrQuery = new SolrQuery();
		
		solrQuery.setQuery(SearchUtilities.preprocessSearchTerm(query)).setFields(ID);
    	solrQuery.set("defType", "edismax");
		solrQuery.set("qf", NAME + " " + CxNode.REPRESENTS + " " + ALIAS);
		solrQuery.setStart(0);
		if (limit >0)
			solrQuery.setRows(limit);
		else 
			solrQuery.setRows(30000000);
		
		try {
			QueryResponse rsp = client.query(solrQuery, METHOD.POST);
			SolrDocumentList dds = rsp.getResults();
			return dds;
		} catch (BaseHttpSolrClient.RemoteSolrException e) {
			throw NetworkGlobalIndexManager.convertException(e, collectionName);
		}
	}
	
	public boolean isReady (boolean autoCreate) throws SolrServerException, IOException, NdexException {
		return coreIsReady(this, autoCreate);
	}
	private static synchronized boolean coreIsReady(SingleNetworkSolrIdxManager mgr, boolean autoCreate) throws SolrServerException, IOException, NdexException {
		CoreAdminResponse rp = CoreAdminRequest.getStatus(mgr.getNetworkId(), mgr.getClient());
		//	CoreStatus r = CoreAdminRequest.getCoreStatus(mgr.getNetworkId(), mgr.getClient());
		//int d = rp.getStatus();
		NamedList<NamedList<Object>> o1 = rp.getCoreStatus();
		//System.out.println(o1);
		NamedList<Object> o11 = o1.get(mgr.getNetworkId());
		//System.out.println(o11);
		
		//NamedList<Object> o2 = rp.getResponse();
		//System.out.println(o2);
		if ( o11.size() !=0)
			return true;
		if ( autoCreate) {
			mgr.createDefaultIndex(false);
			return true;
		}
		return false;
	}
	
	
	
	public void createIndex(Set<String> extraIndexFields) throws SolrServerException, IOException, NdexException {
		
		if ( extraIndexFields == null) {
			 createDefaultIndex(false);
			 return;
		}
		
		//create a configSet from template first.
		ConfigSetAdminRequest.Create confSetCreator = new ConfigSetAdminRequest.Create();
		confSetCreator.setBaseConfigSetName("ndex-nodes-template");
		confSetCreator.setConfigSetName(collectionName);
		
		ConfigSetAdminResponse cr = confSetCreator.process(client);
		
		if ( cr.getStatus() != 0 ) {
			throw new NdexException("Failed to create Solr ConfigSet " + collectionName);
		}
		
		//CollectionAdminRequest.Create creator = CollectionAdminRequest.createCollection(collectionName,"ndex-nodes",1 , 1); 
		CoreAdminRequest.Create creator = new CoreAdminRequest.Create(); 
		creator.setCoreName(collectionName);
		creator.setConfigSet(
				collectionName); 
		creator.setIsLoadOnStartup(Boolean.FALSE);
		creator.setIsTransient(Boolean.TRUE); 
		
	//	"data_driven_schema_configs");
		CoreAdminResponse foo = creator.process(client);	
	
		if ( foo.getStatus() != 0 ) {
			throw new NdexException ("Failed to create solrIndex for network " + collectionName + ". Error: " + foo.getResponseHeader().toString());
		}
		
		client.setBaseURL(solrUrl + "/" + collectionName);

		//extend the schema
	/*	if  ( extraIndexFields !=null ) {
			for (String fieldName: extraIndexFields ) {
				 Map<String, Object> fieldAttributes = new LinkedHashMap<>();
				 fieldAttributes.put("name", fieldName);
				 fieldAttributes.put("type", "text_ws");
				 fieldAttributes.put("stored", false);
				 fieldAttributes.put("required", false);
				 SchemaRequest.AddField addFieldUpdateSchemaRequest = new SchemaRequest.AddField(fieldAttributes);
				 SchemaResponse.UpdateResponse addFieldResponse = addFieldUpdateSchemaRequest.process(client);
				 System.out.println(addFieldResponse.getStatus());
			}
		}
		
		 SchemaRequest.Fields fieldsSchemaRequest = new SchemaRequest.Fields();
		 SchemaResponse.FieldsResponse currentFieldsResponse = fieldsSchemaRequest.process(client);
		 List<Map<String, Object>> currentFields = currentFieldsResponse.getFields();
		 System.out.println(currentFields);
	*/	
		counter = 0;
		docs = new ArrayList<>(batchSize);
			
		Map<Long,NodeIndexEntry> tab = createIndexDocs(collectionName);
		for ( NodeIndexEntry e : tab.values()) {
			addNodeIndex(e.getId(), e.getName(),e.getRepresents() ,e.getAliases());
		}
		
		commit();
	}
	
	public void createIndexFromCx2 (Set<String> extraIndexFields) throws NdexException, SolrServerException, IOException {
		if (extraIndexFields !=null) 
			throw new NdexException("Additional node attribute indexing is not implmented yet.");
		
		createDefaultIndex(true);
	} 
	
	
	private void createDefaultIndex(boolean isFromCx2) throws SolrServerException, IOException, NdexException {

		createNewCore();
		
		Map<Long,NodeIndexEntry> tab = isFromCx2 ? createIndexDocsFromCx2(collectionName)
				  :createIndexDocs(collectionName);
		for ( NodeIndexEntry e : tab.values()) {
			if ( e.isMemberIsToBeIndexed() && e.getMembers().size()>0) {
				e.getRepresents().addAll(e.getMembers());
			}
			addNodeIndex(e.getId(), e.getName(),e.getRepresents() ,e.getAliases());
		}
		
		commit();
	}

	private void createNewCore() throws SolrServerException, IOException, NdexException {
		CoreAdminRequest.Create creator = new CoreAdminRequest.Create(); 
		creator.setCoreName(collectionName);
		creator.setConfigSet(
				"ndex-nodes"); 
		creator.setIsLoadOnStartup(Boolean.FALSE);
		creator.setIsTransient(Boolean.TRUE); 
		
	//	"data_driven_schema_configs");
		CoreAdminResponse foo = creator.process(client);	
	
		if ( foo.getStatus() != 0 ) {
			throw new NdexException ("Failed to create solrIndex for network " + collectionName + ". Error: " + foo.getResponseHeader().toString());
		}
		
		client.setBaseURL(solrUrl + "/" + collectionName);

		counter = 0;
		docs = new ArrayList<>(batchSize);

	}
	

	
	public void dropIndex() throws IOException, SolrServerException, NdexException {
		try {
			client.setBaseURL(solrUrl);
		//	CollectionAdminRequest.deleteCollection(collectionName).process(client);
			
			CoreAdminRequest.unloadCore(collectionName, true, true, client);
		} catch (HttpSolrClient.RemoteSolrException e4) {
			System.out.println(e4.code() + " - " + e4.getMessage());
			if ( e4.getMessage().indexOf("Cannot unload non-existent core") == -1) {
				e4.printStackTrace();
				throw new NdexException("Unexpected Solr Exception: " + e4.getMessage());
			}	
		} 
		
		/**
		 * One reference implementation to check if a core exists
		 * CommonsHttpSolrServer adminServer = new
			CommonsHttpSolrServer(solrRootUrl);
			CoreAdminResponse status =
			CoreAdminRequest.getStatus(coreName, adminServer);

			return status.getCoreStatus(coreName).get("instanceDir") != null;
		 */
	}
	
	private void addNodeIndex(Long id, String name, Collection<String> represents, Collection<String> alias) throws SolrServerException, IOException {
		
		SolrInputDocument doc = new SolrInputDocument();
		doc.addField("id",  id );
		
		if ( name != null && name.length()>0 ) 
			doc.addField(NAME, name);
		if ( represents !=null && !represents.isEmpty()) {
			for ( String rterm : represents )
				doc.addField(CxNode.REPRESENTS, rterm.trim());
		}	
		if ( alias !=null && !alias.isEmpty()) {
			for ( String aTerm : alias )
				doc.addField(ALIAS, aTerm.trim());
		}	
//		if ( relatedTerms !=null && ! relatedTerms.isEmpty() ) 
//			doc.addField(RELATEDTO, relatedTerms);
		
		docs.add(doc);
	//	client.add(doc);
		counter ++;
		if ( counter % batchSize == 0 ) {
			client.add(docs);
		//	client.commit();
			docs.clear();
		}

	}

/*	private void addNodeIndex(long id, String name, List<String> represents) throws SolrServerException, IOException {
		
		SolrInputDocument doc = new SolrInputDocument();
		doc.addField("id",  id );
		
		if ( name != null ) 
			doc.addField(NAME, name);
		if ( represents !=null && !represents.isEmpty()) {
			for ( String rterm : represents )
				doc.addField(REPRESENTS, rterm);
		}	
		
		docs.add(doc);
	//	client.add(doc);
		counter ++;
		if ( counter % batchSize == 0 ) {
			client.add(docs);
			client.commit();
			docs.clear();
		}

	}
	
	
	public void addNodeAlias(long id, String name, List<String> represents, List<String> alias) throws SolrServerException, IOException {
		
		SolrInputDocument doc = new SolrInputDocument();
		doc.addField("id",  id );
		
		if ( name != null ) 
			doc.addField(NAME, name);
		if ( represents !=null && !represents.isEmpty()) {
			for ( String rterm : represents )
				doc.addField(REPRESENTS, rterm);
		}	
		if ( alias !=null && !alias.isEmpty()) {
			for ( String aTerm : alias )
				doc.addField(ALIAS, aTerm);
		}	
//		if ( relatedTerms !=null && ! relatedTerms.isEmpty() ) 
//			doc.addField(RELATEDTO, relatedTerms);
		
		docs.add(doc);
	//	client.add(doc);
		counter ++;
		if ( counter % batchSize == 0 ) {
			client.add(docs);
			client.commit();
			docs.clear();
		}

	} */

	
	private void commit() throws SolrServerException, IOException {
		if ( docs.size()>0 ) {
			client.add(docs);
			client.commit(true,true);
			docs.clear();
		}
	}
	
	
	@Override
	public void close () {
		try {
			client.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private static Map<Long,NodeIndexEntry> createIndexDocs(String coreName) throws JsonProcessingException, IOException, NdexException {
		Map<Long,NodeIndexEntry> result = new TreeMap<> ();
		
		String pathPrefix = Configuration.getInstance().getNdexRoot() + "/data/" + coreName + "/aspects/"; 
	
		//go through node aspect
		try (FileInputStream inputStream = new FileInputStream(pathPrefix + "nodes")) {

			Iterator<NodesElement> it = new ObjectMapper().readerFor(NodesElement.class).readValues(inputStream);

			while (it.hasNext()) {
	        	NodesElement node = it.next();
	        	
	        	List<String> represents = getIndexableTerms(node.getNodeRepresents());
	        	if ( node.getNodeName() != null || represents.size() > 0) {
	        		NodeIndexEntry e = new NodeIndexEntry(node.getId(), node.getNodeName());
	        		if ( represents .size() > 0 ) 
	        			e.setRepresents(represents);	     
	        		result.put(node.getId(), e);
	        	}
			}
		}
		
		// go through Function Term if it exists
		java.nio.file.Path functionTermAspect = Paths.get(pathPrefix + FunctionTermElement.ASPECT_NAME);

		if ( Files.exists(functionTermAspect)) { 
			try (FileInputStream inputStream = new FileInputStream(pathPrefix + FunctionTermElement.ASPECT_NAME)) {

				Iterator<FunctionTermElement> it = new ObjectMapper().readerFor(FunctionTermElement.class).readValues(inputStream);

				while (it.hasNext()) {
		        	FunctionTermElement functionTerm = it.next();
		        	List<String> terms = NetworkGlobalIndexManager.getIndexableStringsFromFunctionTerm(functionTerm);
		        	if ( terms.size() > 0 ) {
			        	NodeIndexEntry e = result.get(functionTerm.getNodeID());
		        		if ( e == null ) {  // need to add a new entry
		        			e = new NodeIndexEntry(functionTerm.getNodeID(), null);
		        			result.put(functionTerm.getNodeID(), e);
		        		}	        	
		        		e.setRepresents(terms);
		        	}	
				}
				
				
			}	
		}
		
		//go through node attributes to find aliases

		try (AspectIterator<NodeAttributesElement> it = new AspectIterator<>(coreName,NodeAttributesElement.ASPECT_NAME, 
				   NodeAttributesElement.class, Configuration.getInstance().getNdexRoot() + "/data/")) {
	//	try (FileInputStream inputStream = new FileInputStream(pathPrefix + NodeAttributesElement.ASPECT_NAME)) {

	//		Iterator<NodeAttributesElement> it = new ObjectMapper().readerFor(NodeAttributesElement.class).readValues(inputStream);

			while (it.hasNext()) {
	        	NodeAttributesElement attr = it.next();
	        	String attrName = attr.getName().toLowerCase(); // doing case insensitive check
	        	if ( attrName.equals("alias")) {
	        		List<String>  l = getIndexableTerms(attr);
	        		if ( l.size() > 0 ) {
	        			NodeIndexEntry e = result.get(attr.getPropertyOf());
	        			if ( e == null) {
	        				e = new NodeIndexEntry(attr.getPropertyOf(), null);
	        				result.put(attr.getPropertyOf(), e);
	        			}
	        			e.setAliases(l);
	        		}
	        	} else if ( attrName.equals("type")) {
	        		if ( (attr.getDataType() == null || attr.getDataType() == ATTRIBUTE_DATA_TYPE.STRING) && 
	        				attr.getValue() != null && attr.getValue().length()>0 ) {
	        			String typeStr = attr.getValue().trim().toLowerCase();
	        			if ( typeStr.equals("complex") || typeStr.equals("proteinfamily")) {
		        			NodeIndexEntry e = result.get(attr.getPropertyOf());
		        			if ( e != null) {
		        				e.setMemberIsToBeIndexed(true);
		        			} else {
		        				throw new NdexException("Node " + attr.getPropertyOf() + " is not found in node Aspect.");
		        			}
	        			}
	        		}	
	        	} else if ( attrName.equals("member")) {
	        		NodeIndexEntry e = result.get(attr.getPropertyOf());
        			if ( e != null) {
    	        		List<String>  l = getIndexableTerms(attr);
    	        		if ( l.size() > 0 ) 
    	        			e.setMembers(l);
        			} else {
        				throw new NdexException("Node " + attr.getPropertyOf() + " is not found in node Aspect.");
        			}
	        	}
			}
		}
		
		return result;
	}
	
	
	private  Map<Long,NodeIndexEntry> createIndexDocsFromCx2(String coreName) throws JsonProcessingException, IOException, NdexException {
		Map<Long,NodeIndexEntry> result = new TreeMap<> ();
		
		String pathPrefix = Configuration.getInstance().getNdexRoot() + "/data/" + coreName + "/" + CX2NetworkLoader.cx2AspectDirName + "/"; 
	
		ObjectMapper om = new ObjectMapper();
		
		//create the attribute name mapping table from attribute declaration
		File declFile = new File(pathPrefix + CxAttributeDeclaration.ASPECT_NAME);
		if (!declFile.exists())
			return result;
		
		CxAttributeDeclaration[] declarations = om.readValue(declFile, 
				CxAttributeDeclaration[].class); 
		
		if ( declarations.length == 0 || ! declarations[0].getDeclarations().containsKey(CxNode.ASPECT_NAME))
			return result;
		
		Map<String,DeclarationEntry> nodeAttributeDecls = declarations[0].getAttributesInAspect(CxNode.ASPECT_NAME);
		if ( nodeAttributeDecls.size() == 0 )
			return result;
		
		//key is lowercased attribute name that we need to index, 
		//value is the actual attribute name in the node. This is for doing a case-insensitive lookup of attribute value.
		//TODO: if 2 attribute names are the same in the case-insensitive lookup, we should raise an error.  
		Map<String, Map.Entry<String,DeclarationEntry>> attributeNameMapping = new HashMap<> ();
		for ( Map.Entry<String,DeclarationEntry> entry: nodeAttributeDecls.entrySet()) {
			String attrName = entry.getKey();
			if (attrName.equals(CxNode.NAME)) {
				if ( entry.getValue().getDataType() == null || 
						entry.getValue().getDataType() == ATTRIBUTE_DATA_TYPE.STRING)
					attributeNameMapping.put (CxNode.NAME, entry);
			} else if (attrName.equals(CxNode.REPRESENTS) ) {
				if ( entry.getValue().getDataType() == null || 
						entry.getValue().getDataType() == ATTRIBUTE_DATA_TYPE.STRING)
					attributeNameMapping.put (CxNode.REPRESENTS, entry);
			} else if ( attrName.equalsIgnoreCase(ALIAS) ) {
				if ( entry.getValue().getDataType() == ATTRIBUTE_DATA_TYPE.LIST_OF_STRING) {
					attributeNameMapping.put (ALIAS, entry);					
				}
			} else if ( attrName.equalsIgnoreCase(TYPE)) {
				if ( entry.getValue().getDataType() == null || 
						entry.getValue().getDataType() == ATTRIBUTE_DATA_TYPE.STRING)
					attributeNameMapping.put (TYPE, entry);
			} else if ( attrName.equalsIgnoreCase(MEMBER)) {
				if ( entry.getValue().getDataType() == null || 
						entry.getValue().getDataType() == ATTRIBUTE_DATA_TYPE.STRING)
					attributeNameMapping.put (MEMBER, entry);
			}
				
		}
		
		//go through node aspect
		try (FileInputStream inputStream = new FileInputStream(pathPrefix + "nodes")) {

			Iterator<CxNode> it = om.readerFor(CxNode.class).readValues(inputStream);

			while (it.hasNext()) {
	        	CxNode node = it.next();
	        	
	        	node.extendToFullNode(nodeAttributeDecls);
	        	
	        	//Map<String, Object> attrs = node.getAttributes();
	        	NodeIndexEntry e = null;
	        	String name = getSingleIndexableTermFromNode(CxNode.NAME,node,attributeNameMapping);
	        	if ( name !=null) {
	        		e =  new NodeIndexEntry(node.getId(), name);
	        	}
	        	List<String> represents = getSplitableTerms(CxNode.REPRESENTS, node, attributeNameMapping);
	        	if ( represents.size()>0) {
	        		if ( e ==null)
	        			e = new NodeIndexEntry(node.getId(),null);
	        		e.setRepresents(represents);	     
	        	}

	        	// process alias
	        	List<String> aliases =  getSplitableTerms(ALIAS, node, attributeNameMapping);
	        	if ( aliases.size() > 0 ) {
	        		if ( e==null)
	        			e= new  NodeIndexEntry(node.getId(),null);
	        		e.setAliases(aliases);
	        	}
	        	
	        	//process members
	        	String nodeType = getSingleIndexableTermFromNode(TYPE, node, attributeNameMapping);
	        	if ( nodeType != null && (nodeType.equalsIgnoreCase(PROTEINFAMILY) || 
	        			nodeType.equalsIgnoreCase(COMPLEX) )) {
	        		List<String> memberGenes = getSplitableTerms (MEMBER, node, attributeNameMapping);
	        		if( e==null)
	        			e = new NodeIndexEntry ( node.getId(),null);
	        		e.setMembers(memberGenes);
	        		e.setMemberIsToBeIndexed(true);
	        	}
	        	
	        	if ( e!=null)
	        		result.put(node.getId(), e);

			}
		}
		
		// go through Function Term if it exists
		java.nio.file.Path functionTermAspect = Paths.get(pathPrefix + FunctionTermElement.ASPECT_NAME);

		if ( Files.exists(functionTermAspect)) { 
			try (FileInputStream inputStream = new FileInputStream(pathPrefix + FunctionTermElement.ASPECT_NAME)) {

				Iterator<FunctionTermElement> it = new ObjectMapper().readerFor(FunctionTermElement.class).readValues(inputStream);

				while (it.hasNext()) {
		        	FunctionTermElement functionTerm = it.next();
		        	List<String> terms = NetworkGlobalIndexManager.getIndexableStringsFromFunctionTerm(functionTerm);
		        	if ( terms.size() > 0 ) {
			        	NodeIndexEntry e = result.get(functionTerm.getNodeID());
		        		if ( e == null ) {  // need to add a new entry
		        			e = new NodeIndexEntry(functionTerm.getNodeID(), null);
		        			result.put(functionTerm.getNodeID(), e);
		        		}	        	
		        		e.setRepresents(terms);
		        	}	
				}
				
				
			}	
		}
		
		
		return result;
	}

	
	protected static String getSingleIndexableTermFromNode (String attrName, CxNode node, 
			Map<String, Map.Entry<String,DeclarationEntry>> attributeNameMapping) {		
	
		Map.Entry<String,DeclarationEntry> entry = attributeNameMapping.get(attrName);
	
		if ( entry != null) {
			String actualAttrName = entry.getKey();
			ATTRIBUTE_DATA_TYPE t = entry.getValue().getDataType();
			if ( t == null || t== ATTRIBUTE_DATA_TYPE.STRING)  {
				return (String)node.getAttributes().get(actualAttrName);
			}
		
		}
	
		return null;
	}
	
	protected static List<String> getSplitableTerms (String attrName, CxNode node, 
				Map<String, Map.Entry<String,DeclarationEntry>> attributeNameMapping) {		
	
		List<String> result = new ArrayList<>();

		Map.Entry<String,DeclarationEntry> entry = attributeNameMapping.get(attrName);
		
		if ( entry != null) {
			String actualAttrName = entry.getKey();
			ATTRIBUTE_DATA_TYPE t = entry.getValue().getDataType();
			
			if ( t == null || t == ATTRIBUTE_DATA_TYPE.STRING) {
				String v = (String)node.getAttributes().get(actualAttrName);
				for ( String indexableString : NetworkGlobalIndexManager.getIndexableString(v) ){
					result.add( indexableString);
				}
			} else if (t == ATTRIBUTE_DATA_TYPE.LIST_OF_STRING) {
				List<String> vl = (List<String>)node.getAttributes().get(actualAttrName);
				for ( String v : vl) {
					for ( String indexableString : NetworkGlobalIndexManager.getIndexableString(v) ){
						result.add( indexableString);
					}
				}
			} 
						
		}
		
		return result;

	}
	
	private static List<String> getIndexableTerms (String originalString) {		
		if (originalString == null) return new ArrayList<>(1);
		return NetworkGlobalIndexManager.getIndexableString(originalString);
	}
	
	private static List<String> getIndexableTerms (NodeAttributesElement e) {		
		List<String> result = new ArrayList<>();
		
		if ( e.getDataType() == ATTRIBUTE_DATA_TYPE.LIST_OF_STRING 	&& !e.getValues().isEmpty()) {
			for ( String v : e.getValues()) {
				for ( String indexableString : NetworkGlobalIndexManager.getIndexableString(v) ){
					result.add( indexableString);
				}
			}
		} else if ( e.getDataType() == ATTRIBUTE_DATA_TYPE.STRING) {
			String v = e.getValue();
			for ( String indexableString : NetworkGlobalIndexManager.getIndexableString(v) ){
				result.add( indexableString);
			}
		}
		
		return result;
	}
	
	protected String getNetworkId () {return collectionName;}
	protected HttpSolrClient getClient() {return this.client;}
}
