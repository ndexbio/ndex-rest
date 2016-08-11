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
import java.util.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

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
import org.ndexbio.common.NdexClasses;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.NdexPropertyValuePair;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.Configuration;

public class NetworkGlobalIndexManager {

	private String solrUrl ;
	
	private static final String coreName = 
			"ndex-networks" ; 
	private HttpSolrClient client;
	
	private SolrInputDocument doc ;
	
	public static final String UUID = "uuid";
	private static final String NAME = "name";
	private static final String DESC = "description";
	private static final String VERSION = "version";
	private static final String USER_READ= "userRead";
	private static final String USER_EDIT = "userEdit";
	private static final String USER_ADMIN = "userAdmin";
	private static final String GRP_READ = "grpRead";
	private static final String GRP_EDIT = "grpEdit";
	private static final String GRP_ADMIN = "grpAdmin";
	
	private static final String VISIBILITY = "visibility";
	
	private static final String EDGE_COUNT = "edgeCount";
	
	private static final String NODE_COUNT = "nodeCount";
	private static final String CREATION_TIME = "creationTime";
	private static final String MODIFICATION_TIME = "modificationTime";
	
	
	private static final String NODE_NAME = "nodeName";
	
	public static final String NCBI_GENE_ID = "NCBIGeneID";
	public static final String GENE_SYMBOL = "geneSymbol";
	
	private static final String REPRESENTS = "represents";
	private static final String ALIASES = "alias";
	private static final String RELATED_TO = "relatedTo";
	
	// user required indexing fields. hardcoded for now. Will turn them into configurable list in 1.4.
	
	private static final Set<String> otherAttributes = 
			new HashSet<>(Arrays.asList("objectCategory", "organism",
	"platform",
	"graphPropertiesHash",
	"networkType",
	"disease",
	"tissue",
	 "rightsHolder",
	 "author",
	 "createdAt",
	 "methods",
	 "subnetworkType","subnetworkFilter","graphHash","rights"));
	
//	private static  Map<String,String> attTable = null;
		
//	private int counter;
	
	
	public NetworkGlobalIndexManager() throws NdexException {
		// TODO Auto-generated constructor stub
		solrUrl = Configuration.getInstance().getSolrURL();
		client = new HttpSolrClient(solrUrl);
		doc = null;
/*		if ( attTable == null) { 
			attTable = new HashMap<>(otherAttributes.size());
			for ( String att : otherAttributes) {
				attTable.put(att.toLowerCase(), att);
			}
		} */
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
				throw new NdexException ("Failed to create solrIndex for network " + coreName + ". Error: " + foo.getResponseHeader().toString());
			}
			System.out.println("Done.");		
		}
		else {
			System.out.println("Found core "+ coreName + " in Solr.");	
		}
		
	}
	
	public SolrDocumentList searchForNetworks (String searchTerms, String userAccount, int limit, int offset, String adminedBy, Permissions permission, boolean canReadOnly,
			   List<String> groupNames) 
			throws SolrServerException, IOException {
		client.setBaseURL(solrUrl+ "/" + coreName);

		SolrQuery solrQuery = new SolrQuery();
		
		//create the result filter
	//	String visibilityFilter = // canReadOnly? 
//				/* (VISIBILITY + ":PUBLIC") :( "(NOT " + */ VISIBILITY + ":PRIVATE";
		
		String adminFilter = "";		
		if ( adminedBy !=null) {
			adminedBy = "\"" + adminedBy + "\"";
			adminFilter = " AND (" + USER_ADMIN + ":" + adminedBy + " OR " + GRP_ADMIN + ":" + adminedBy + ")";
		}
		
		String resultFilter = "";
		if ( userAccount !=null) {     // has a signed in user.
			userAccount = "\"" + userAccount +"\"";
			if ( permission == null) {
				resultFilter =  VISIBILITY + ":PRIVATE";
				resultFilter += " AND -(" + USER_ADMIN + ":" + userAccount + ") AND -(" +
						USER_EDIT + ":" + userAccount + ") AND -("+ USER_READ + ":" + userAccount + ")";
				if ( groupNames!=null) {
					for (String groupName : groupNames) {
					  groupName = "\"" + groupName + "\"";	
					  resultFilter +=  " AND -(" + GRP_ADMIN + ":" + groupName + ") AND -(" +
							  GRP_EDIT + ":" + groupName + ") AND -("+ GRP_READ + ":" + groupName + ")";
					}
				}
				resultFilter = "-("+ resultFilter + ")";
			} 
			else if ( permission == Permissions.READ) {
				resultFilter = "(" + USER_ADMIN + ":" + userAccount + ") OR (" +
						USER_EDIT + ":" + userAccount + ") OR ("+ USER_READ + ":" + userAccount + ")";
				if ( groupNames!=null) {
					for (String groupName : groupNames) {
						  groupName = "\"" + groupName + "\"";	
						  resultFilter +=  " OR (" + GRP_ADMIN + ":" + groupName + ") OR (" +
							  GRP_EDIT + ":" + groupName + ") OR ("+ GRP_READ + ":" + groupName + ")";
					}
				}
			} else if ( permission == Permissions.WRITE) {
				resultFilter = "(" + USER_ADMIN + ":" + userAccount + ") OR (" +
						USER_EDIT + ":" + userAccount + ")";
				if ( groupNames !=null) {
					for ( String groupName : groupNames )  {
						  groupName = "\"" + groupName + "\"";	
						  resultFilter += " OR (" + GRP_ADMIN + ":" + groupName + ") OR (" +
							GRP_EDIT + ":" + groupName + ")" ;
					}
				} 
			}/* else if ( permission == Permissions.ADMIN)  {
			resultFilter =  " -(" + USER_ADMIN + ":" + userAccount + ")";
		    if ( groupNames!=null ) {
		    	for ( String grpName : groupNames)
		    	  resultFilter  +=  " AND -(" + GRP_ADMIN +":" + grpName  + ")";
//		    	resultFilter = "(" + resultFilter + ")";
		    }	
//		    resultFilter = resultFilter + adminFilter;
		} */
		}  else {
			resultFilter = VISIBILITY + ":PUBLIC";
		}
			
		resultFilter = resultFilter + adminFilter;
		
			
		solrQuery.setQuery(searchTerms).setFields(UUID);
		if ( searchTerms.equalsIgnoreCase("*:*"))
			solrQuery.setSort(MODIFICATION_TIME, ORDER.desc);
		if ( offset >=0)
		  solrQuery.setStart(offset);
		if ( limit >0 )
			solrQuery.setRows(limit);
		
		solrQuery.setFilterQueries(resultFilter) ;
		
		QueryResponse rsp = client.query(solrQuery);		
			
		SolrDocumentList  dds = rsp.getResults();
		
		return dds;	
		
	}
	
	
	
	public void createIndexDocFromSummary(NetworkSummary summary) throws SolrServerException, IOException, NdexException, SQLException {
		client.setBaseURL(solrUrl + "/" + coreName);
		doc = new SolrInputDocument();
	
		doc.addField(UUID,  summary.getExternalId().toString() );
		doc.addField(EDGE_COUNT, summary.getEdgeCount());
		doc.addField(NODE_COUNT, summary.getNodeCount());
		doc.addField(VISIBILITY, summary.getVisibility().toString());
		
		doc.addField(CREATION_TIME, summary.getCreationTime());
		doc.addField(MODIFICATION_TIME, summary.getModificationTime());

		if ( summary.getName() != null ) 
			doc.addField(NAME, summary.getName());
		if ( summary.getDescription() !=null)
			doc.addField(DESC, summary.getDescription());
		if ( summary.getVersion() !=null)
			doc.addField(VERSION, summary.getVersion());
				
		
		// dynamic fields from property table.
		List<NdexPropertyValuePair> props = summary.getProperties();
		
		for ( NdexPropertyValuePair prop : props) {
		//	String attrName = attTable.get(prop.getPredicateString().toLowerCase()) ;
			if ( otherAttributes.contains(prop.getPredicateString()) ) {
				doc.addField(prop.getPredicateString(), prop.getValue());
			}
		}
		
		try (NetworkDAO dao = new NetworkDAO()) {
			Map<String,Map<Permissions, Set<String>>> members = dao.getAllMembershipsOnNetwork(summary.getExternalId().toString());
			doc.addField(USER_READ, members.get(NdexClasses.User).get(Permissions.READ));
			doc.addField(USER_EDIT, members.get(NdexClasses.User).get(Permissions.WRITE));
			doc.addField(USER_ADMIN, members.get(NdexClasses.User).get(Permissions.ADMIN));
			doc.addField(GRP_ADMIN, members.get(NdexClasses.Group).get(Permissions.ADMIN));
			doc.addField(GRP_READ, members.get(NdexClasses.Group).get(Permissions.READ));
			doc.addField(GRP_EDIT, members.get(NdexClasses.Group).get(Permissions.WRITE));
		}

//		counter = 0;
	}
	
	
	

    public void addNodeToIndex(String name, List<String> represents, List<String> alias, /*List<String> relatedTerms, */
    				List<String> geneSymbol, List<String> NCBIGeneID)  {
				
		if ( name != null && name.length() >2 ) 
			doc.addField(NODE_NAME, name);
		if ( represents !=null ) {
			for ( String term : represents)
				doc.addField(REPRESENTS, term);
		}	
		if ( alias !=null ) {
			for (String term : alias )
				doc.addField(ALIASES, term);
		}	
/*		if ( relatedTerms !=null && ! relatedTerms.isEmpty() ) { 
			for ( String term : relatedTerms)
				doc.addField(RELATED_TO, term);
		} */
		if ( geneSymbol != null && !geneSymbol.isEmpty()) {
			for ( String gs : geneSymbol)
				doc.addField(GENE_SYMBOL, gs);
		}
		
		if ( NCBIGeneID !=null && !NCBIGeneID.isEmpty()) {
			for ( String geneId : NCBIGeneID)
				doc.addField(NCBI_GENE_ID, geneId);
		}
		
	//	docs.add(doc);
		
/*		counter ++;
		if ( counter == batchSize) {
			client.add(docs);
			client.commit();
			docs.clear();
			counter = 0;
		}  */

	}

	
	public void deleteNetwork(String networkId) throws SolrServerException, IOException {
		client.setBaseURL(solrUrl + "/" + coreName);
		client.deleteById(networkId);
		client.commit();
	}
	
	public void commit () throws SolrServerException, IOException {
		Collection<SolrInputDocument> docs = new ArrayList<>(1);
		docs.add(doc);
		client.add(docs);
		client.commit();
		docs.clear();
		doc = null;

	}
	
	public void updateNetworkProperties (String networkId, Collection<NdexPropertyValuePair> props, Date updateTime) throws SolrServerException, IOException {
		client.setBaseURL(solrUrl + "/" + coreName);
		SolrInputDocument tmpdoc = new SolrInputDocument();
		tmpdoc.addField(UUID, networkId);

	
		Set<String> indexedAttributes = new TreeSet<> ();	
		for ( NdexPropertyValuePair prop : props) {
			if ( otherAttributes.contains(prop.getPredicateString()) ) {
				indexedAttributes.add(prop.getPredicateString());
				Map<String,String> cmd = new HashMap<>();
				cmd.put("set", prop.getValue());
				tmpdoc.addField(prop.getPredicateString(), cmd);
			}
		}

		for ( String attr : otherAttributes) {
			if ( !indexedAttributes.contains(attr)) {
				Map<String,String> cmd = new HashMap<>();
				cmd.put("set", null);
				tmpdoc.addField(attr, cmd);
			}
		}
		
		Map<String,Timestamp> cmd = new HashMap<>();
		cmd.put("set",  new java.sql.Timestamp(updateTime.getTime()));
		tmpdoc.addField(MODIFICATION_TIME, cmd);
		
		Collection<SolrInputDocument> docs = new ArrayList<>(1);
		docs.add(tmpdoc);
		client.add(docs);
		client.commit();
	}
	
	
	public void updateNetworkProfile(String networkId, Map<String,Object> table) throws SolrServerException, IOException {
		client.setBaseURL(solrUrl + "/" + coreName);
		SolrInputDocument tmpdoc = new SolrInputDocument();
		tmpdoc.addField(UUID, networkId);
		 
		String newTitle = (String)table.get(NdexClasses.Network_P_name); 
		if ( newTitle !=null) {
			Map<String,String> cmd = new HashMap<>();
			cmd.put("set", newTitle);
			tmpdoc.addField(NAME, cmd);
		}
		
		String newDesc =(String) table.get(NdexClasses.Network_P_desc);
		if ( newDesc != null) {
			Map<String,String> cmd = new HashMap<>();
			cmd.put("set", newDesc);
			tmpdoc.addField(DESC, cmd);
		}
		
		String newVersion = (String)table.get(NdexClasses.Network_P_version);
		if ( newVersion !=null) {
			Map<String,String> cmd = new HashMap<>();
			cmd.put("set", newVersion);
			tmpdoc.addField(VERSION, cmd);
		}
		
		if ( table.get(NetworkDAO.RESET_MOD_TIME)!=null) {
			Map<String,Timestamp> cmd = new HashMap<>();
			java.util.Date now = Calendar.getInstance().getTime();
			cmd.put("set",  new java.sql.Timestamp(now.getTime()));
			tmpdoc.addField(MODIFICATION_TIME, cmd);
		}
		
		VisibilityType  vt = (VisibilityType)table.get(NdexClasses.Network_P_visibility);		
		if ( vt!=null) {
			Map<String,String> cmd = new HashMap<>();
			cmd.put("set",  vt.toString());
			tmpdoc.addField(VISIBILITY, cmd);
		}
		
		Collection<SolrInputDocument> docs = new ArrayList<>(1);
		docs.add(tmpdoc);
		client.add(docs);
		client.commit();

	}
	
	public void revokeNetworkPermission(String networkId, String accountName, Permissions p, boolean isUser) 
			throws NdexException, SolrServerException, IOException {
		client.setBaseURL(solrUrl + "/" + coreName);
		SolrInputDocument tmpdoc = new SolrInputDocument();
		tmpdoc.addField(UUID, networkId);
		 
		Map<String,String> cmd = new HashMap<>();
		cmd.put("remove", accountName);

		switch ( p) {
		case ADMIN : 
			tmpdoc.addField( isUser? USER_ADMIN: GRP_ADMIN, cmd);
			break;
		case WRITE:
			tmpdoc.addField( isUser? USER_EDIT: GRP_EDIT, cmd);
			break;
		case READ:
			tmpdoc.addField( isUser? USER_READ: GRP_READ, cmd);
			break;
			
		default: 
			throw new NdexException ("Invalid permission type " + p + " received in network previlege revoke.");
		}
		
		Collection<SolrInputDocument> docs = new ArrayList<>(1);
		docs.add(tmpdoc);
		client.add(docs);
		client.commit();

	}
	
	public void grantNetworkPermission(String networkId, String accountName, Permissions newPermission, 
			 Permissions oldPermission, boolean isUser) 
			throws NdexException, SolrServerException, IOException {
		client.setBaseURL(solrUrl + "/" + coreName);
		SolrInputDocument tmpdoc = new SolrInputDocument();
		tmpdoc.addField(UUID, networkId);
		 
		Map<String,String> cmd = new HashMap<>();
		cmd.put("add", accountName);

		switch ( newPermission) {
		case ADMIN : 
			tmpdoc.addField( isUser? USER_ADMIN: GRP_ADMIN, cmd);
			break;
		case WRITE:
			tmpdoc.addField( isUser? USER_EDIT: GRP_EDIT, cmd);
			break;
		case READ:
			tmpdoc.addField( isUser? USER_READ: GRP_READ, cmd);
			break;		
		default: 
			throw new NdexException ("Invalid permission type " + newPermission
					+ " received in network previlege revoke.");
		}
		
		if ( oldPermission !=null ) {
			Map<String,String> rmCmd = new HashMap<>();
			rmCmd.put("remove", accountName);

			switch ( oldPermission) {
			case ADMIN : 
				tmpdoc.addField( isUser? USER_ADMIN: GRP_ADMIN, rmCmd);
				break;
			case WRITE:
				tmpdoc.addField( isUser? USER_EDIT: GRP_EDIT, rmCmd);
				break;
			case READ:
				tmpdoc.addField( isUser? USER_READ: GRP_READ, rmCmd);
				break;
				
			default: 
				throw new NdexException ("Invalid permission type " + oldPermission + " received in network previlege revoke.");
			}
		}
		
		Collection<SolrInputDocument> docs = new ArrayList<>(1);
		docs.add(tmpdoc);
		client.add(docs);
		client.commit();

	}
}
