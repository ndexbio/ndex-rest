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

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.CollectionAdminRequest;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.request.schema.SchemaRequest;
import org.apache.solr.client.solrj.response.CollectionAdminResponse;
import org.apache.solr.client.solrj.response.CoreAdminResponse;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.schema.SchemaResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.cxio.aspects.datamodels.ATTRIBUTE_DATA_TYPE;
import org.cxio.aspects.datamodels.NodeAttributesElement;
import org.cxio.aspects.datamodels.NodesElement;
import org.ndexbio.common.cx.AspectIterator;
import org.ndexbio.model.cx.FunctionTermElement;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.rest.Configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SingleNetworkSolrIdxManager implements AutoCloseable{

	private String solrUrl;
	
	private String collectionName; 
	private HttpSolrClient client;
	
	static private final  int batchSize = 2000;
	private int counter ; 
	private Collection<SolrInputDocument> docs ;
	
	public static final String ID = "id";
	private static final String NAME = "nodeName";
	private static final String REPRESENTS = "represents";
	private static final String ALIAS= "alias";
//	private static final String RELATEDTO = "relatedTo";
		
	public SingleNetworkSolrIdxManager(String networkUUID) {
		collectionName = networkUUID;
		solrUrl = Configuration.getInstance().getSolrURL();
		client = new HttpSolrClient.Builder(solrUrl).build();
	}
	
	public SolrDocumentList getNodeIdsByQuery(String query, int limit) throws SolrServerException, IOException {
		client.setBaseURL(solrUrl+ "/" + collectionName);

		SolrQuery solrQuery = new SolrQuery();
		
		solrQuery.setQuery(query).setFields(ID);
		solrQuery.setStart(0);
		if (limit >0)
			solrQuery.setRows(limit);
		QueryResponse rsp = client.query(solrQuery);
		
		SolrDocumentList  dds = rsp.getResults();
		
		return dds;
		
	}
	
	public void createIndex(Set<String> extraIndexFields) throws SolrServerException, IOException, NdexException {
		//CollectionAdminRequest.Create creator = CollectionAdminRequest.createCollection(collectionName,"ndex-nodes",1 , 1); 
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
		
		
		Map<Long,NodeIndexEntry> tab = createIndexDocs();
		for ( NodeIndexEntry e : tab.values()) {
			addNodeIndex(e.getId(), e.getName(),e.getRepresents() ,e.getAliases());
		}
		
		commit();
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
	private Map<Long,NodeIndexEntry> createIndexDocs() throws JsonProcessingException, IOException {
		Map<Long,NodeIndexEntry> result = new TreeMap<> ();
		
		String pathPrefix = Configuration.getInstance().getNdexRoot() + "/data/" + collectionName + "/aspects/"; 
	
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
		try (AspectIterator<NodeAttributesElement> it = new AspectIterator<>(UUID.fromString(collectionName),NodeAttributesElement.ASPECT_NAME, NodeAttributesElement.class)) {
	//	try (FileInputStream inputStream = new FileInputStream(pathPrefix + NodeAttributesElement.ASPECT_NAME)) {

	//		Iterator<NodeAttributesElement> it = new ObjectMapper().readerFor(NodeAttributesElement.class).readValues(inputStream);

			while (it.hasNext()) {
	        	NodeAttributesElement attr = it.next();
	        	if ( attr.getName().equals("alias")) {
	        		List<String>  l = getIndexableTerms(attr);
	        		if ( l.size() > 0 ) {
	        			NodeIndexEntry e = result.get(attr.getPropertyOf());
	        			if ( e == null) {
	        				e = new NodeIndexEntry(attr.getPropertyOf(), null);
	        				result.put(attr.getPropertyOf(), e);
	        			}
	        			e.setAliases(l);
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
}
