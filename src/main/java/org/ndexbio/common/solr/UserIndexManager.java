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
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.tools.SearchUtilities;

public class UserIndexManager {

	protected static final String coreName = 
			"ndex-users" ; 
	private final SolrClientWrapper client;
	
	public static final String UUID = "uuid";
	private static final String NAME = "userName";
	private static final String DESC = "description";
	private static final String FIRSTNAME = "firstName";
	private static final String LASTNAME = "lastName";
	private static final String DISPLAYNAME = "displayName";		
	
	
	public UserIndexManager(SolrClientWrapper client) {
		this.client = client;
	}
	
	public void createCoreIfNotExists() throws SolrServerException, IOException, NdexException {
		client.createCoreIfNeeded(coreName);
	}
	
	/**
	 * Removes index from SOLR completely (tosses filesystem data)
	 * @throws IOException
	 * @throws SolrServerException
	 * @throws NdexException 
	 */
	public void dropIndex() throws IOException, SolrServerException, NdexException {
		client.dropCore(coreName);
	}
	
	public SolrDocumentList searchUsers (String searchTerms, int limit, int offset) 
			throws SolrServerException, IOException, NdexException {

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
		
		return client.query(coreName, solrQuery).getResults();
	}
	
	
	public void addUser(String userId, String userName, String firstName, String lastName, String displayName, String description) throws SolrServerException, IOException, NdexException {

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
		client.add(coreName, docs);
	}
	

	
	public void deleteUser(String userId) throws SolrServerException, IOException, NdexException {
		client.delete(coreName, userId, false);
	}
	
	public void updateUser(String userId, String userName, String firstName, String lastName, String displayName, String description) throws SolrServerException, IOException, NdexException {
		SolrInputDocument tmpdoc = new SolrInputDocument();
		tmpdoc.addField(UUID, userId);
		 
	
			Map<String,String> cmd = new HashMap<>();
			cmd.put("set", userName);
			tmpdoc.addField(NAME, cmd);
		
		
			cmd = new HashMap<>();
			cmd.put("set", description);
			tmpdoc.addField(DESC, cmd);

			cmd = new HashMap<>();
			cmd.put("set", firstName);
			tmpdoc.addField(FIRSTNAME, cmd);
			
			cmd = new HashMap<>();
			cmd.put("set", lastName);
			tmpdoc.addField(LASTNAME, cmd);
			
			cmd = new HashMap<>();
			cmd.put("set", displayName);
			tmpdoc.addField(DISPLAYNAME, cmd);
		
		Collection<SolrInputDocument> docs = new ArrayList<>(1);
		docs.add(tmpdoc);
		client.add(coreName, docs);
	}
		
}
