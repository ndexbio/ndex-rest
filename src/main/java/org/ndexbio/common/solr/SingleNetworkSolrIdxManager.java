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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.rest.Configuration;

public class SingleNetworkSolrIdxManager {

	private String solrUrl;
	
	private String coreName; 
	private HttpSolrClient client;
	
	static private final  int batchSize = 2000;
	private int counter ; 
	private Collection<SolrInputDocument> docs ;
	
	public static final String ID = "id";
	private static final String NAME = "name";
	private static final String REPRESENTS = "represents";
	private static final String ALIAS= "alias";
//	private static final String RELATEDTO = "relatedTo";
		
	public SingleNetworkSolrIdxManager(String networkUUID) throws NdexException {
		coreName = networkUUID;
		solrUrl = Configuration.getInstance().getSolrURL();
		client = new HttpSolrClient(solrUrl);
	}
	
	public SolrDocumentList getNodeIdsByQuery(String query, int limit) throws SolrServerException, IOException {
		client.setBaseURL(solrUrl+ "/" + coreName);

		SolrQuery solrQuery = new SolrQuery();
		
		solrQuery.setQuery(query).setFields(ID);
		solrQuery.setStart(0);
		solrQuery.setRows(limit);
		QueryResponse rsp = client.query(solrQuery);
		
		SolrDocumentList  dds = rsp.getResults();
		
		return dds;
		
	}
	
	public void createIndex() throws SolrServerException, IOException, NdexException {
		CoreAdminRequest.Create creator = new CoreAdminRequest.Create(); 
		creator.setCoreName(coreName);
		creator.setConfigSet(
				"ndex-nodes"); 
	//	"data_driven_schema_configs");
		CoreAdminResponse foo = creator.process(client);	
			
		if ( foo.getStatus() != 0 ) {
			throw new NdexException ("Failed to create solrIndex for network " + coreName + ". Error: " + foo.getResponseHeader().toString());
		}
		counter = 0;
		docs = new ArrayList<>(batchSize);
		
		client.setBaseURL(solrUrl + "/" + coreName);
	}
	
	public void dropIndex() throws SolrServerException, IOException {
		client.setBaseURL(solrUrl);
		CoreAdminRequest.unloadCore(coreName, true, true, client);
	}
	
	public void addNodeIndex(long id, String name, List<String> represents, List<String> alias) throws SolrServerException, IOException {
		
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

	}

	public void commit() throws SolrServerException, IOException {
		if ( docs.size()>0 ) {
			client.add(docs);
			client.commit();
			docs.clear();
		}
	}
}
