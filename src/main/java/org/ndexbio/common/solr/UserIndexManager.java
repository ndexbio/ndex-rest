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
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.SolrRequest.METHOD;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.NamedList;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.tools.SearchUtilities;
import org.ndexbio.rest.Configuration;

public class UserIndexManager implements AutoCloseable{

	private String solrUrl ;
	
	protected static final String coreName = 
			"ndex-users" ; 
	protected HttpSolrClient client;
	
	//private SolrInputDocument doc ;
	
	public static final String UUID = "uuid";
	private static final String NAME = "userName";
	private static final String DESC = "description";
	private static final String FIRSTNAME = "firstName";
	private static final String LASTNAME = "lastName";
	private static final String DISPLAYNAME = "displayName";		
	
	
	public UserIndexManager() {
		solrUrl = Configuration.getInstance().getSolrURL();
		client = new HttpSolrClient.Builder(solrUrl).build();
		//doc = new SolrInputDocument();

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
	
	public SolrDocumentList searchUsers (String searchTerms, int limit, int offset) 
			throws SolrServerException, IOException, NdexException {
		client.setBaseURL(solrUrl+ "/" + coreName);

		SolrQuery solrQuery = new SolrQuery();	
			
		solrQuery.setQuery(SearchUtilities.preprocessSearchTerm(searchTerms)).setFields(UUID);
		if ( searchTerms.equalsIgnoreCase("*:*"))
			solrQuery.setSort(UUID, ORDER.desc);
		if ( offset >=0)
		  solrQuery.setStart(offset);
		if ( limit >0 )
			solrQuery.setRows(limit);
    	solrQuery.set("defType", "edismax");
    	solrQuery.set("qf","uuid^10 userName^5 firstName^3 lastName^3 displayName^2 description");
		
	//	solrQuery.setFilterQueries(resultFilter) ;
		
		try {
			QueryResponse rsp = client.query(solrQuery, METHOD.POST);

			SolrDocumentList dds = rsp.getResults();

			return dds;
		} catch (BaseHttpSolrClient.RemoteSolrException e) {
			throw NetworkGlobalIndexManager.convertException(e, coreName);
		}
	}
	
	
	public void addUser(String userId, String userName, String firstName, String lastName, String displayName, String description) throws SolrServerException, IOException {
		client.setBaseURL(solrUrl + "/" + coreName);

		SolrInputDocument doc = new SolrInputDocument();

		doc.addField(UUID, userId );
		doc.addField(NAME, userName);
		if ( firstName !=null)
			doc.addField(FIRSTNAME, firstName);
		if ( lastName != null)
			doc.addField(LASTNAME, lastName);
		
		if ( displayName != null) 
			doc.addField(DISPLAYNAME, displayName);
		if ( description !=null)
			doc.addField(DESC, description);
		
		Collection<SolrInputDocument> docs = new ArrayList<>(1);
		docs.add(doc);
		client.add(docs);
	//	client.commit();
		docs.clear();
		doc = null;

	}
	

	
	public void deleteUser(String userId) throws SolrServerException, IOException {
		client.setBaseURL(solrUrl + "/" + coreName);
		client.deleteById(userId);
	//	client.commit();
	}
	
/*	public void commit () throws SolrServerException, IOException {
		client.setBaseURL(solrUrl + "/" + coreName);
		Collection<SolrInputDocument> docs = new ArrayList<>(1);
		docs.add(doc);
		client.add(docs);
		client.commit();
		docs.clear();
		doc = null;

	} */
	

	
	public void updateUser(String userId, String userName, String firstName, String lastName, String displayName, String description) throws SolrServerException, IOException {
		client.setBaseURL(solrUrl + "/" + coreName);
		SolrInputDocument tmpdoc = new SolrInputDocument();
		tmpdoc.addField(UUID, userId);
		 
	
			Map<String,String> cmd = new HashMap<>();
			cmd.put("set", userName);
			tmpdoc.addField(NAME, cmd);
		
		
			cmd = new HashMap<>();
			cmd.put("set", description);
			tmpdoc.addField(DESC, cmd);

		
		//String newVersion = table.get(NdexClasses.Network_P_version);
	//	if ( newVersion !=null) {
		//	Map<String,String>
			cmd = new HashMap<>();
			cmd.put("set", firstName);
			tmpdoc.addField(FIRSTNAME, cmd);
			
			cmd = new HashMap<>();
			cmd.put("set", lastName);
			tmpdoc.addField(LASTNAME, cmd);
			
			cmd = new HashMap<>();
			cmd.put("set", displayName);
			tmpdoc.addField(DISPLAYNAME, cmd);
	//	}
		
		Collection<SolrInputDocument> docs = new ArrayList<>(1);
		docs.add(tmpdoc);
		client.add(docs);
	//	client.commit();

	}

	@Override
	public void close() throws Exception {
		this.client.close();
	}
		
}
