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
import java.util.HashMap;
import java.util.Map;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrQuery.ORDER;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.NamedList;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.rest.Configuration;

public class GroupIndexManager implements AutoCloseable{

	private String solrUrl ;
	
	protected static final String coreName = 
			"ndex-groups" ; 
	protected HttpSolrClient client;
	
	public static final String UUID = "uuid";
	private static final String NAME = "groupName";
	private static final String DESC = "description";
	
	public GroupIndexManager() {
		solrUrl = Configuration.getInstance().getSolrURL();
		client = new HttpSolrClient.Builder(solrUrl).build();
	}
	
	public void createCoreIfNotExists() throws SolrServerException, IOException, NdexException {
			
		CoreAdminResponse foo = CoreAdminRequest.getStatus(coreName,client);	
		if (foo.getStatus() != 0 ) {
			throw new NdexException ("Failed to get status of solrIndex for " + coreName + ". Error: " + foo.getResponseHeader().toString());
		}
		NamedList<Object> bar = foo.getResponse();
		
		NamedList<Object> st = (NamedList<Object>)bar.get("status");
		
		NamedList<Object> core = (NamedList<Object>)st.get(coreName);
		if ( core.size() == 0 ) {
			System.out.println("Solr core " + coreName + " doesn't exist. Creating it now ....");

			CoreAdminRequest.Create creator = new CoreAdminRequest.Create(); 
			creator.setCoreName(coreName);
			creator.setConfigSet( coreName); 
			foo = creator.process(client);				
			if ( foo.getStatus() != 0 ) {
				throw new NdexException ("Failed to create solrIndex for " + coreName + ". Error: " + foo.getResponseHeader().toString());
			}
			System.out.println("Done.");		
		}
		else {
			System.out.println("Found core "+ coreName + " in Solr.");	
		}
		
	}
	
	public SolrDocumentList searchGroups (String searchTerms, int limit, int offset) 
			throws SolrServerException, IOException, NdexException {
		client.setBaseURL(solrUrl+ "/" + coreName);

		SolrQuery solrQuery = new SolrQuery();	
			
		solrQuery.setQuery(searchTerms).setFields(UUID);
		if ( searchTerms.equalsIgnoreCase("*:*"))
			solrQuery.setSort(UUID, ORDER.desc);
		if ( offset >=0)
		  solrQuery.setStart(Integer.valueOf(offset));
		if ( limit >0 )
			solrQuery.setRows(Integer.valueOf(limit));
		
    	solrQuery.set("defType", "edismax");
    	solrQuery.set("qf","uuid^10 groupName^4 description");
		
			
		try {
			QueryResponse rsp = client.query(solrQuery);		
			SolrDocumentList dds = rsp.getResults();
			return dds;
		} catch (HttpSolrClient.RemoteSolrException e) {
			throw NetworkGlobalIndexManager.convertException(e, coreName);
		}
		
	}
	
	
	public void addGroup(String grpId, String grpName, String desc) throws SolrServerException, IOException {
		client.setBaseURL(solrUrl + "/" + coreName);

		SolrInputDocument doc = new SolrInputDocument();

		doc.addField(UUID, grpId );
		doc.addField(NAME, grpName);
	
		if ( desc !=null)
			doc.addField(DESC, desc);
		
		Collection<SolrInputDocument> docs = new ArrayList<>(1);
		docs.add(doc);
		client.add(docs);
		//client.commit();
		docs.clear();
		doc = null;

	}
	

	
	public void deleteGroup(String grpId) throws SolrServerException, IOException {
		client.setBaseURL(solrUrl + "/" + coreName);
		client.deleteById(grpId);
//		client.commit();
	}	

	
	public void updateGrp(String grpId, String grpName, String description) throws SolrServerException, IOException {
		client.setBaseURL(solrUrl + "/" + coreName);
		SolrInputDocument tmpdoc = new SolrInputDocument();
		tmpdoc.addField(UUID, grpId);
		 
	
			Map<String,String> cmd = new HashMap<>();
			cmd.put("set", grpName);
			tmpdoc.addField(NAME, cmd);
		
		
			cmd = new HashMap<>();
			cmd.put("set", description);
			tmpdoc.addField(DESC, cmd);

		Collection<SolrInputDocument> docs = new ArrayList<>(1);
		docs.add(tmpdoc);
		client.add(docs);
	//	client.commit();

	}

	@Override
	public void close() throws IOException  {
		this.client.close();
	}
	
		
	

	

}
