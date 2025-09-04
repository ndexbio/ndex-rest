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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrQuery.ORDER;
import org.apache.solr.client.solrj.SolrRequest.METHOD;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.NamedList;
import org.ndexbio.common.NdexClasses;
import org.ndexbio.model.tools.SearchUtilities;
import org.ndexbio.model.tools.TermUtilities;
import org.ndexbio.common.util.Util;
import org.ndexbio.cx2.aspect.element.core.CxNetworkAttribute;
import org.ndexbio.cx2.aspect.element.core.CxNode;
import org.ndexbio.cx2.aspect.element.core.DeclarationEntry;
import org.ndexbio.cxio.aspects.datamodels.ATTRIBUTE_DATA_TYPE;
import org.ndexbio.cxio.aspects.datamodels.NetworkAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NodeAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NodesElement;
import org.ndexbio.model.cx.FunctionTermElement;
import org.ndexbio.model.exceptions.BadRequestException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.rest.Configuration;

public class PublicNFSIndexManager implements AutoCloseable{

	SolrClientWrapper client;

	SolrInputDocument doc ;
	
	private final String coreName = "public-nfs";
		
	// holds the mapping between node ID and member attributes. 
	// members will be added to the represent field as additional lists.
	Map<Long, Set<String>> nodeMembers;  
	
	public static final String UUID = "uuid";
	public static final String NAME = "name";
	public static final String DESC = "description";
	public static final String VERSION = "version";
	public static final String NODE_NAME = "nodeName";
	
	public static final String REPRESENTS = "represents";
	public static final String ALIASES = "alias";
	public static final String MODIFICATION_TIME = "modificationTime";
	public static final String VISIBILITY = "visibility";
	
	public static final String EDGE_COUNT = "edgeCount";
	
	public static final String NODE_COUNT = "nodeCount";
	public static final String CREATION_TIME = "creationTime";
	public static final String NDEX_SCORE = "ndexScore";

	// user required indexing fields. hardcoded for now. Will turn them into configurable list in 1.4.
	
	public static final Set<String> otherAttributes = 
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
	 "subnetworkType","subnetworkFilter","graphHash","rights", "labels"));
	
	public PublicNFSIndexManager(SolrClientWrapper client) {
		this.client = client;
		doc = new SolrInputDocument();
		nodeMembers = new TreeMap<>();
	}
	
	public void createCoreIfNeeded() throws SolrServerException, IOException, NdexException {
			client.createCoreIfNeeded(coreName);
	}
	
	public void createIndexDocFromSummary(NetworkSummary summary, String ownerUserName, Collection<String> userReads,Collection<String> userEdits,
			Collection<String> grpReads, Collection<String> grpEdits) {
		
	}
	

	public void addCXNodeToIndex(NodesElement node)  {
			
		   if ( node.getNodeName() != null ) 
			   doc.addField(NODE_NAME, node.getNodeName());
		   if ( node.getNodeRepresents() !=null ) {
			   for (String indexableString : getIndexableString(node.getNodeRepresents())) {
				   doc.addField(REPRESENTS, indexableString);
			   }
		//	   if( indexableString !=null)
		   }	
	

	}
	
	public void addCX2NodeToIndex(CxNode node, Map<String, Map.Entry<String,DeclarationEntry>> attributeNameMapping)  {
		
		Map<String,Object> nodeAttrs = node.getAttributes();
		Object nodeName = nodeAttrs.get(CxNode.NAME);
		if ( nodeName != null) {
			   doc.addField(NODE_NAME, nodeName);			
		}
		Object represents= nodeAttrs.get(CxNode.REPRESENTS);
		if ( represents != null) {
			for (String indexableString : getIndexableString((String)represents)) {
				   doc.addField(REPRESENTS, indexableString);
			}
		}
	
		if ( attributeNameMapping.get(SingleNetworkSolrIdxManager.ALIAS)!=null) {
			
			for (String v : SingleNetworkSolrIdxManager.getSplitableTerms(SingleNetworkSolrIdxManager.ALIAS, node,
					attributeNameMapping) ){
				doc.addField(ALIASES, v);
			}
			
		} 
		
		String nodeType = SingleNetworkSolrIdxManager.getSingleIndexableTermFromNode(SingleNetworkSolrIdxManager.TYPE,
				node, attributeNameMapping);
		
		if ( nodeType != null && (nodeType.equalsIgnoreCase(SingleNetworkSolrIdxManager.PROTEINFAMILY) || 
    			nodeType.equalsIgnoreCase(SingleNetworkSolrIdxManager.COMPLEX) )) {
    		List<String> memberGenes = SingleNetworkSolrIdxManager.getSplitableTerms (SingleNetworkSolrIdxManager.MEMBER,
    				node, attributeNameMapping);
    		for ( String memberIdStr : memberGenes) {
				for ( String indexableString : getIndexableString(memberIdStr) ){
					doc.addField(REPRESENTS, indexableString);
				}
			}
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
		} else if ( e.getName().toLowerCase().equals("type")) {
			if ( e.getDataType() == ATTRIBUTE_DATA_TYPE.STRING) {
				String v = e.getValue().toLowerCase();
				if ( v.equals("complex") || v.equals("proteinfamily")) {
					Set<String> members = nodeMembers.get(e.getPropertyOf());
					if ( members != null) {  // saw the member attribute on this node before
						for ( String memberIdStr : members) {
							for ( String indexableString : getIndexableString(memberIdStr) ){
								doc.addField(REPRESENTS, indexableString);
							}
						}
						nodeMembers.remove(e.getPropertyOf());
					}
					else {
						nodeMembers.put(e.getPropertyOf(), new TreeSet<String>());
					}
				}
			}
		} else if (  e.getName().toLowerCase().equals("member")) {
			if ( e.getDataType() == ATTRIBUTE_DATA_TYPE.LIST_OF_STRING) {
				Set<String> members = nodeMembers.get(e.getPropertyOf());
				if ( members != null) {  // this node is proteinfamily or complex
					for ( String memberIdStr : e.getValues()) {
						for ( String indexableString : getIndexableString(memberIdStr) ){
							doc.addField(REPRESENTS, indexableString);
						}
					}
					nodeMembers.remove(e.getPropertyOf());
				} else {
					members = new HashSet<>(e.getValues());
					nodeMembers.put(e.getPropertyOf(), members);
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

	
	public List<String> addCXNetworkAttrToIndex(NetworkAttributesElement e)  {
		
		List<String> warnings = new ArrayList<>();
		if ( e.getName().equals(NdexClasses.Network_P_name) ) {
			addStringAttrFromAttributeElement(e, NAME, warnings);
		} else if ( e.getName().equals(NdexClasses.Network_P_desc ) ) {
			addStringAttrFromAttributeElement(e, DESC, warnings);
		} else if ( e.getName().equals(NdexClasses.Network_P_version)  ) {
			addStringAttrFromAttributeElement(e, VERSION, warnings);			
		} else {
			if ( otherAttributes.contains(e.getName())  ) {
				addStringListgAttribute(e, e.getName(), warnings);
			}			
		}
		
		return warnings;
		
	}
	
	public List<String> addCX2NetworkAttrToIndex(CxNetworkAttribute e)  {
		
		List<String> warnings = new ArrayList<>();
		if ( e.getNetworkName()!= null) {
			doc.addField(NAME, e.getNetworkName());
		} else if ( e.getNetworkDescription() !=null ) {
			doc.addField(DESC, e.getNetworkDescription());
		} else if ( e.getNetworkVersion() !=null) {
			doc.addField(VERSION, e.getNetworkVersion());			
		}
		
		for ( String otherIndexedName: otherAttributes) {
			if ( e.getAttributes().get(otherIndexedName) !=null) {
				addStringOrListgObj(e.getAttributes().get(otherIndexedName), otherIndexedName, warnings);
			}
		}
	
		return warnings;
		
	}

	private void addStringAttrFromAttributeElement(NetworkAttributesElement e, String solrFieldName, List<String>  warnings ) {
		if (e.getDataType() == ATTRIBUTE_DATA_TYPE.STRING) {
			if ( e.getValue() !=null && e.getValue().length()>0)
				doc.addField(solrFieldName, e.getValue());
		} else 
			warnings.add("Network attribute " + e.getName() + " is not indexed because its data type is not 'string'.");
	}
	
	private void addStringOrListgObj(Object e, String solrFieldName, List<String>  warnings ) {
		if (e instanceof String) {
			doc.addField(solrFieldName, e);
		} else if (e instanceof List<?>) {
			for ( Object value : ((List<?>)e)) {
				if ( value instanceof String)
					doc.addField(solrFieldName, value);
				else {
					warnings.add("Network attribute " + solrFieldName +  " is not indexed because its data type is not 'string' or 'list_of_string'.");
					break;
				}
			}
		} else 
			warnings.add("Network attribute " + solrFieldName + " is not indexed because its data type is not 'string' or 'list_of_string'.");
	}	
	
	private void addStringListgAttribute(NetworkAttributesElement e, String solrFieldName, List<String>  warnings ) {
		if (e.getDataType() == ATTRIBUTE_DATA_TYPE.STRING) {
			if ( e.getValue() !=null && e.getValue().length()>0)
				doc.addField(solrFieldName, e.getValue());
		} else if (e.getDataType() == ATTRIBUTE_DATA_TYPE.LIST_OF_STRING) {
			for ( String value : e.getValues()) {
				if ( value !=null && value.length()>0)
					doc.addField(solrFieldName, value);
			}
		} else 
			warnings.add("Network attribute " + e.getName() + " is not indexed because its data type is not 'string' or 'list_of_string'.");
	}	
	
	public void deleteNetwork(String networkId) throws SolrServerException, IOException {

	}
	
	public void commit () throws SolrServerException, IOException {


	}

	protected static List<String> getIndexableString(String termString) {
		
		// case 1 : termString is a URI
		// example: http://identifiers.org/uniprot/P19838
		// treat the last element in the URI as the identifier and the rest as
		// prefix string. Just to help the future indexing.
		//
	    List<String> result = new ArrayList<>(2) ;
		String identifier = null;
		if ( termString.length() > 10 && (termString.substring(0, 7).equalsIgnoreCase("http://") ||
				termString.substring(0, 8).equalsIgnoreCase("https://") )&&
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
	//		if ( !termStringComponents[0].contains(" "))
			  result.add(termString);
			result.add(termStringComponents[1]);
			return  result;
		} 
		
		// case 3: termString cannot be parsed, use it as the identifier.
		// so leave the prefix as null and return the string
		result.add(termString);
		return result;
			
	}
	
	@Override
	public void close () {
		client.close();
	}
}
