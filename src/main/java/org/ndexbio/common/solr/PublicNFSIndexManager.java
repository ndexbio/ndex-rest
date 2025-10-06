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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;


import org.ndexbio.model.object.NdexFolder;
import org.ndexbio.model.object.NdexShortcut;


import org.apache.solr.client.solrj.SolrServerException;

import org.apache.solr.common.SolrInputDocument;
import org.ndexbio.common.NdexClasses;
import static org.ndexbio.common.solr.NetworkGlobalIndexManager.UUID;
import org.ndexbio.common.util.Util;
import org.ndexbio.model.tools.TermUtilities;
import org.ndexbio.cx2.aspect.element.core.CxNetworkAttribute;
import org.ndexbio.cx2.aspect.element.core.CxNode;
import org.ndexbio.cx2.aspect.element.core.DeclarationEntry;
import org.ndexbio.cxio.aspects.datamodels.ATTRIBUTE_DATA_TYPE;
import org.ndexbio.cxio.aspects.datamodels.NetworkAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NodeAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NodesElement;
import org.ndexbio.model.cx.FunctionTermElement;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.network.NetworkSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PublicNFSIndexManager implements AutoCloseable{

	private static final Logger logger = LoggerFactory.getLogger(SolrClientWrapperImpl.class.getName());
	SolrClientWrapper _client;

	SolrInputDocument doc ;
	
	public static final String CORE_NAME = "public-nfs";
		
	// holds the mapping between node ID and member attributes. 
	// members will be added to the represent field as additional lists.
	Map<Long, Set<String>> nodeMembers;  
	
	public static final String UUID = "uuid";
	public static final String NAME = "name";
	public static final String ENTITY_TYPE = "entityType";
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
		this._client = client;
		doc = new SolrInputDocument();
		nodeMembers = new TreeMap<>();
	}
	
	public void createCoreIfNeeded() throws SolrServerException, IOException, NdexException {
			_client.createCoreIfNeeded(CORE_NAME);
	}
	
	/**
	 * Given a network summary generate a document to be indexed for the network
	 * 
	 * @param summary
	 * @param ownerUserName
	 * @param userReads
	 * @param userEdits
	 * @param grpReads
	 * @param grpEdits
	 * @return 
	 */
	SolrInputDocument getIndexForDocument(NetworkSummary summary, String ownerUserName, Collection<String> userReads,Collection<String> userEdits,
			Collection<String> grpReads, Collection<String> grpEdits){
		doc = new SolrInputDocument();
		doc.addField(UUID,  summary.getExternalId().toString() );
		doc.addField(ENTITY_TYPE, FileType.NETWORK.toString());
		doc.addField(EDGE_COUNT, summary.getEdgeCount());
		doc.addField(NODE_COUNT, summary.getNodeCount());
		doc.addField(VISIBILITY, summary.getVisibility().toString());
		
		if ( summary.getName() !=null && summary.getName().length()>1) {
			doc.addField(NAME, summary.getName());
		}
		
		if (summary.getDescription() !=null && summary.getDescription().length()>1) {
			doc.addField(DESC, summary.getDescription());
		}
		
		if ( summary.getVersion() !=null && summary.getVersion().length()>1) {
			doc.addField(VERSION, summary.getVersion());
		}
		
		doc.addField(CREATION_TIME, summary.getCreationTime());
		doc.addField(MODIFICATION_TIME, summary.getModificationTime());
		
		doc.addField(NDEX_SCORE, Util.getNdexScoreFromSummary(summary));
		
		return doc;
	}
	
	/**
	 * Given a folder generate a document to be indexed
	 * @param folder
	 * @return 
	 */
	SolrInputDocument getIndexForDocument(NdexFolder folder){
		doc = new SolrInputDocument();
		doc.addField(UUID, folder.getExternalId().toString());
		doc.addField(ENTITY_TYPE, FileType.FOLDER.toString());
		
		if (folder.getName() != null && folder.getName().length()>1){
			doc.addField(NAME, folder.getName());
		}
		if (folder.getDescription() != null && folder.getDescription().length()>1){
			doc.addField(DESC, folder.getDescription());
		}
		// @TODO do we want to index parent uuid?
		
		doc.addField(CREATION_TIME, folder.getCreationTime());
		doc.addField(MODIFICATION_TIME, folder.getModificationTime());
		
		return doc;
	}
	
	/**
	 * Given a shortcut generate a document to be indexed
	 * @param shortcut
	 * @return 
	 */
	SolrInputDocument getIndexForDocument(NdexShortcut shortcut){
		doc = new SolrInputDocument();
		doc.addField(UUID, shortcut.getExternalId().toString());
		doc.addField(ENTITY_TYPE, FileType.SHORTCUT.toString());
		
		if (shortcut.getName() != null && shortcut.getName().length()>1){
			doc.addField(NAME, shortcut.getName());
		}
		
		// @TODO do we want to index parent uuid?
		
		// @TODO do we want to index target type?
		
		doc.addField(CREATION_TIME, shortcut.getCreationTime());
		doc.addField(MODIFICATION_TIME, shortcut.getModificationTime());
		
		return doc;
	}
	
	/**
	 * Creates index for document
	 * @param summary
	 * @param ownerUserName
	 * @param userReads
	 * @param userEdits
	 * @param grpReads
	 * @param grpEdits 
	 */
	public void createIndexForDocument(NetworkSummary summary, String ownerUserName, Collection<String> userReads,Collection<String> userEdits,
			Collection<String> grpReads, Collection<String> grpEdits) {
		SolrInputDocument doc = getIndexForDocument(summary, ownerUserName, userReads, userEdits,
			 grpReads,  grpEdits);
		commitDocument(doc);
		
	}
	
	public void commitDocument(SolrInputDocument document){
		var documents = new LinkedList<SolrInputDocument>();
		documents.add(document);
		try {
			_client.commit(CORE_NAME, documents);
		} catch(SolrServerException sse){
			logger.error("Unable to commit document: " + sse.getMessage(), sse);
		} catch(IOException io){
			logger.error("Unable to commit document: " + io.getMessage(), io);
		}
	}
	
	/**
	 * 
	 * @param folder 
	 */
	public void createIndexForDocument(NdexFolder folder){
		SolrInputDocument doc = getIndexForDocument(folder);
		var documents = new LinkedList<SolrInputDocument>();
		documents.add(doc);
		try {
			_client.commit(CORE_NAME, documents);
		} catch(SolrServerException sse){
			logger.error("Unable to commit document: " + sse.getMessage(), sse);
		} catch(IOException io){
			logger.error("Unable to commit document: " + io.getMessage(), io);
		}
	}
	
	/**
	 * 
	 * @param shortcut 
	 */
	public void createIndexForDocument(NdexShortcut shortcut){
		SolrInputDocument doc = getIndexForDocument(shortcut);
		var documents = new LinkedList<SolrInputDocument>();
		documents.add(doc);
		try {
			_client.commit(CORE_NAME, documents);
		} catch(SolrServerException sse){
			logger.error("Unable to commit document: " + sse.getMessage(), sse);
		} catch(IOException io){
			logger.error("Unable to commit document: " + io.getMessage(), io);
		}
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
	
	/**
	 * Deletes document from core/index
	 * @param uuid
	 * @throws SolrServerException
	 * @throws IOException 
	 */
	public void delete(final String uuid) throws SolrServerException, IOException {
		_client.delete(CORE_NAME, uuid, false);
	}
	
	public void commit () throws SolrServerException, IOException {
		_client.commit(CORE_NAME, null);
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
		_client.close();
	}
}
