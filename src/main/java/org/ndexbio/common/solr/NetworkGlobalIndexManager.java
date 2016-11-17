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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
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
import org.cxio.aspects.datamodels.ATTRIBUTE_DATA_TYPE;
import org.cxio.aspects.datamodels.NetworkAttributesElement;
import org.cxio.aspects.datamodels.NodeAttributesElement;
import org.cxio.aspects.datamodels.NodesElement;
import org.ndexbio.common.NdexClasses;
import org.ndexbio.common.util.TermUtilities;
import org.ndexbio.model.cx.FunctionTermElement;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.NdexPropertyValuePair;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.network.NetworkSummary;
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
	private static final String LABELS = "labels";
	private static final String USER_READ= "userRead";
	private static final String USER_EDIT = "userEdit";
	private static final String USER_ADMIN = "userAdmin";
	private static final String GRP_READ = "grpRead";
	private static final String GRP_EDIT = "grpEdit";
//	private static final String GRP_ADMIN = "grpAdmin";
	
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
		doc = new SolrInputDocument();
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
	
	public SolrDocumentList searchForNetworks (String searchTerms, String userAccount, int limit, int offset, String adminedBy, Permissions permission,
			   List<String> groupNames) 
			throws SolrServerException, IOException {
		client.setBaseURL(solrUrl+ "/" + coreName);

		SolrQuery solrQuery = new SolrQuery();
		
		//create the result filter
		
		String adminFilter = "";		
		if ( adminedBy !=null) {
			adminFilter = " AND (" + USER_ADMIN + ":\"" + adminedBy +  "\")";
		}
		
		String resultFilter = "";
		if ( userAccount !=null) {     // has a signed in user.
			String userAccountStr = "\"" + userAccount +"\"";
			if ( permission == null) {
				resultFilter =  VISIBILITY + ":PRIVATE";
				resultFilter += " AND -(" + USER_ADMIN + ":" + userAccountStr + ") AND -(" +
						USER_EDIT + ":" + userAccountStr + ") AND -("+ USER_READ + ":" + userAccountStr + ")";
				if ( groupNames!=null) {
					for (String groupName : groupNames) {
					  groupName = "\"" + groupName + "\"";	
					  resultFilter +=  " AND -(" + GRP_EDIT + ":" + groupName + ") AND -("+ GRP_READ + ":" + groupName + ")";
					}
				}
				resultFilter = "-("+ resultFilter + ")";
			} 
			else if ( permission == Permissions.READ) {
				resultFilter = "(" + USER_ADMIN + ":" + userAccountStr + ") OR (" +
						USER_EDIT + ":" + userAccountStr + ") OR ("+ USER_READ + ":" + userAccountStr + ")";
				if ( groupNames!=null) {
					for (String groupName : groupNames) {
						  groupName = "\"" + groupName + "\"";	
						  resultFilter +=  " OR (" +
							  GRP_EDIT + ":" + groupName + ") OR ("+ GRP_READ + ":" + groupName + ")";
					}
				}
			} else if ( permission == Permissions.WRITE) {
				resultFilter = "(" + USER_ADMIN + ":" + userAccountStr + ") OR (" +
						USER_EDIT + ":" + userAccountStr + ")";
				if ( groupNames !=null) {
					for ( String groupName : groupNames )  {
						  groupName = "\"" + groupName + "\"";	
						  resultFilter += " OR (" +
							GRP_EDIT + ":" + groupName + ")" ;
					}
				} 
			}
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
	
	
/*	public void createIndexDocFromSummary(NetworkSummary summary) throws SolrServerException, IOException, NdexException, SQLException {
		client.setBaseURL(solrUrl + "/" + coreName);
	
		doc.addField(UUID,  summary.getExternalId().toString() );
		doc.addField(EDGE_COUNT, summary.getEdgeCount());
		doc.addField(NODE_COUNT, summary.getNodeCount());
		doc.addField(VISIBILITY, summary.getVisibility().toString());
		
		doc.addField(CREATION_TIME, summary.getCreationTime());
		doc.addField(MODIFICATION_TIME, summary.getModificationTime());
		
		try (NetworkDocDAO dao = new NetworkDocDAO()) {
			List<Map<Permissions, Collection<String>>> members = dao.getAllMembershipsOnNetwork(summary.getExternalId());
			doc.addField(USER_READ, members.get(0).get(Permissions.READ));
			doc.addField(USER_EDIT, members.get(0).get(Permissions.WRITE));
			doc.addField(USER_ADMIN, members.get(0).get(Permissions.ADMIN));
			doc.addField(GRP_READ, members.get(1).get(Permissions.READ));
			doc.addField(GRP_EDIT, members.get(1).get(Permissions.WRITE));
		}

	} */
	
	public void createIndexDocFromSummary(NetworkSummary summary, String ownerUserName, Collection<String> userReads,Collection<String> userEdits,
			Collection<String> grpReads, Collection<String> grpEdits) {
		client.setBaseURL(solrUrl + "/" + coreName);
	
		doc.addField(UUID,  summary.getExternalId().toString() );
		doc.addField(EDGE_COUNT, summary.getEdgeCount());
		doc.addField(NODE_COUNT, summary.getNodeCount());
		doc.addField(VISIBILITY, summary.getVisibility().toString());
		
		doc.addField(CREATION_TIME, summary.getCreationTime());
		doc.addField(MODIFICATION_TIME, summary.getModificationTime());
		
		doc.addField(USER_ADMIN, ownerUserName);
		
		if( userReads != null) {
			for(String userName : userReads) {
				doc.addField(USER_READ, userName);
			}
		}
		
		if ( userEdits !=null) {
			for ( String userName: userEdits) {
				doc.addField(USER_EDIT, userName);
			}
		}
		if ( grpReads !=null) {
			for ( String grpName : grpReads) {
				doc.addField(GRP_READ, grpName);
			}
		}
		if(grpEdits !=null) {
			for ( String grpName : grpEdits) {
				doc.addField(GRP_EDIT, grpName);
			}
		}

	}
	

	
	
	public void addCXNodeToIndex(NodesElement node)  {
			
		   if ( node.getNodeName() != null && node.getNodeName().length() >2 ) 
			   doc.addField(NODE_NAME, node.getNodeName());
		   if ( node.getNodeRepresents() !=null ) {
			   for (String indexableString : getIndexableString(node.getNodeRepresents())) {
				   doc.addField(REPRESENTS, indexableString);
			   }
		//	   if( indexableString !=null)
		   }	
	

	}
	
	public void addCXNodeAttrToIndex(NodeAttributesElement e)  {
		
		if ( e.getName().equals(NdexClasses.Node_P_alias)) {
			if ( e.getDataType() == ATTRIBUTE_DATA_TYPE.LIST_OF_STRING 	&& !e.getValues().isEmpty()) {
				for ( String v : e.getValues()) {
					for ( String indexableString : getIndexableString(v) ){
						doc.addField(ALIASES, indexableString);
					}
				}
			} else if ( e.getDataType() == ATTRIBUTE_DATA_TYPE.STRING) {
				String v = e.getValue();
				for ( String indexableString : getIndexableString(v) ){
					doc.addField(ALIASES, indexableString);
				}
			}
		}

	}

	
	public void addFunctionTermToIndex(FunctionTermElement e)  {
				
		for ( String term : getIndexableStringsFromFunctionTerm(e)) {
			 
			doc.addField(REPRESENTS, term);
		}

	}
	
	protected static List<String> getIndexableStringsFromFunctionTerm(FunctionTermElement e)  {
		
		List<String> terms = getIndexableString(e.getFunctionName());
		for (Object arg: e.getArgs()) {
			if (arg instanceof String ) {
				terms.addAll(getIndexableString((String)arg));
			} else if ( arg instanceof FunctionTermElement ){
				terms.addAll(getIndexableStringsFromFunctionTerm((FunctionTermElement)arg));
			}
		}
	
		return terms;
	}

	
	public void addCXNetworkAttrToIndex(NetworkAttributesElement e)  {
		
		if ( e.getName().equals(NdexClasses.Network_P_name) && e.getValue() !=null && e.getValue().length()>0) {
			doc.addField(NAME, e.getValue());
		} else if ( e.getName().equals(NdexClasses.Network_P_desc ) && e.getValue() !=null && e.getValue().length()>0 ) {
			doc.addField(DESC, e.getValue());
		} else if ( e.getName().equals(NdexClasses.Network_P_version) && e.getValue() !=null && e.getValue().length()>0 ) {
			doc.addField(VERSION, e.getValue());
		} else if (e.getName().equals(LABELS) && e.getValue() !=null && e.getValue().length()>0 ) {
			doc.addField(LABELS, e.getValue());
		} /* else if ( e.getName().equals("ndex:sourceFormat") && e.getValue() !=null && e.getValue().length()>0) {
			//TODO: check if we really need to index sourceFormat.
			try {
				NetworkSourceFormat.valueOf(e.getValue());
			} catch (IllegalArgumentException ex) {
				throw new NdexException("Unsupported source format " + 
						e.getValue() + 
						" received in network attribute ndex:sourceFormat" );
			}
			
		} */ else {
			if ( otherAttributes.contains(e.getName()) && e.getValue() != null && e.getValue().length()>0 ) {
				doc.addField(e.getName(), e.getValue());
			}
			
		}
		
	}
	
	
	
	public void deleteNetwork(String networkId) throws SolrServerException, IOException {
		client.setBaseURL(solrUrl + "/" + coreName);
		client.deleteById(networkId);
		client.commit();
	}
	
	public void commit () throws SolrServerException, IOException {
		client.setBaseURL(solrUrl + "/" + coreName);
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
	
	
	public void updateNetworkProfile(String networkId, Map<String,String> table) throws SolrServerException, IOException {
		client.setBaseURL(solrUrl + "/" + coreName);
		SolrInputDocument tmpdoc = new SolrInputDocument();
		tmpdoc.addField(UUID, networkId);
		 
		String newTitle = table.get(NdexClasses.Network_P_name); 
		if ( newTitle !=null) {
			Map<String,String> cmd = new HashMap<>();
			cmd.put("set", newTitle);
			tmpdoc.addField(NAME, cmd);
		}
		
		String newDesc =table.get(NdexClasses.Network_P_desc);
		if ( newDesc != null) {
			Map<String,String> cmd = new HashMap<>();
			cmd.put("set", newDesc);
			tmpdoc.addField(DESC, cmd);
		}
		
		String newVersion = table.get(NdexClasses.Network_P_version);
		if ( newVersion !=null) {
			Map<String,String> cmd = new HashMap<>();
			cmd.put("set", newVersion);
			tmpdoc.addField(VERSION, cmd);
		}
		
			Map<String,Timestamp> cmd = new HashMap<>();
			java.util.Date now = Calendar.getInstance().getTime();
			cmd.put("set",  new java.sql.Timestamp(now.getTime()));
			tmpdoc.addField(MODIFICATION_TIME, cmd);
	
		
		Collection<SolrInputDocument> docs = new ArrayList<>(1);
		docs.add(tmpdoc);
		client.add(docs);
		client.commit();

	}
	
	public void updateNetworkVisibility(String networkId, String vt) throws SolrServerException, IOException {
		client.setBaseURL(solrUrl + "/" + coreName);
		SolrInputDocument tmpdoc = new SolrInputDocument();
		tmpdoc.addField(UUID, networkId);
		 
		if ( vt!=null) {
			Map<String,String> cmd = new HashMap<>();
			cmd.put("set",  vt);
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
			if ( !isUser)
				throw new NdexException("Can't pass isUser=false for ADMIN permission when deleting Solr index.");
			tmpdoc.addField( USER_ADMIN, cmd);
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
			if ( !isUser)
				throw new NdexException("Can't pass isUser=false for ADMIN permission when creating Solr index.");
			tmpdoc.addField(  USER_ADMIN, cmd);
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
			
				tmpdoc.addField(  USER_ADMIN, rmCmd);
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
	
	
	
	protected static List<String> getIndexableString(String termString) {
		
		// case 1 : termString is a URI
		// example: http://identifiers.org/uniprot/P19838
		// treat the last element in the URI as the identifier and the rest as
		// prefix string. Just to help the future indexing.
		//
	    List<String> result = new ArrayList<>(2) ;
		String identifier = null;
		if ( termString.length() > 8 && termString.substring(0, 7).equalsIgnoreCase("http://") &&
				(!termString.endsWith("/"))) {
  		  try {
			URI termStringURI = new URI(termString);
				identifier = termStringURI.getFragment();
			
			    if ( identifier == null ) {
				    String path = termStringURI.getPath();
				    if (path != null && path.indexOf("/") != -1) {
				       int pos = termString.lastIndexOf('/');
					   identifier = termString.substring(pos + 1);
				    } else
				       return result; // the string is a URL in the format that we don't want to index it in Solr. 
			    } 
			    result.add(identifier);
			    return result;
			  
		  } catch (URISyntaxException e) {
			// ignore and move on to next case
		  }
		}
		
		String[] termStringComponents = TermUtilities.getNdexQName(termString);
		if (termStringComponents != null && termStringComponents.length == 2) {
			// case 2: termString is of the form (NamespacePrefix:)*Identifier
			if ( !termStringComponents[0].contains(" "))
			  result.add(termString);
			result.add(termStringComponents[1]);
			return  result;
		} 
		
		// case 3: termString cannot be parsed, use it as the identifier.
		// so leave the prefix as null and return the string
		result.add(termString);
		return result;
			
	}

}
