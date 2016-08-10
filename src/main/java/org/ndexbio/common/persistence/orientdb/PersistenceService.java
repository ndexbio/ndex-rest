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
package org.ndexbio.common.persistence.orientdb;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.solr.client.solrj.SolrServerException;
import org.ndexbio.common.NdexClasses;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.models.dao.postgresql.BasicNetworkDAO;
import org.ndexbio.common.models.dao.postgresql.Helper;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.models.dao.postgresql.OrientDBIterableSingleLink;
import org.ndexbio.common.models.object.network.RawNamespace;
import org.ndexbio.common.solr.SingleNetworkSolrIdxManager;
import org.ndexbio.common.util.TermUtilities;
import org.ndexbio.model.cx.FunctionTermElement;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.object.NdexPropertyValuePair;
import org.ndexbio.model.object.ProvenanceEntity;
import org.ndexbio.model.object.network.Namespace;
import org.ndexbio.model.object.network.NetworkSourceFormat;
import org.ndexbio.model.object.network.NetworkSummary;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.tinkerpop.blueprints.impls.orient.OrientGraph;
import com.tinkerpop.blueprints.impls.orient.OrientVertex;

public abstract class PersistenceService extends BasicNetworkDAO {

	//TODO: turn this into configuration property
	private static final long CACHE_SIZE = 100000L;

	protected NdexDatabase database;

	protected LoadingCache<Long, ODocument>  elementIdCache;
    
	// prefix to namespace mapping
	private Map<String, Namespace> prefixMap;
	private Map<RawNamespace, Namespace> namespaceMap;
    private Map<String, Namespace> URINamespaceMap;

	private Map<String, Long> baseTermStrMap;

	protected OrientVertex networkVertex;

	protected NetworkDAO  networkDAO;

	protected ODocument networkDoc;

	protected NetworkSummary network;
	
    protected Logger logger ;

    protected OrientGraph graph;
//	protected ODatabaseDocumentTx  localConnection;  //all DML will be in this connection, in one transaction.
	
	 // prefix to URI mapping
	 private static final Map<String, String> defaultNSMap;

	 // URI to prefix mapping
	 private static final Map<String, String> reverseNSMap;

	 static {
	        Map<String, String> aMap = new TreeMap<>();
	        
	        aMap.put("ndex", 	"http://www.ndexbio.org/");
	        
	        // fore the repetitive prefix entries, the first record will be used as the default namespace in 
	        // the reverse lookup.

			aMap.put("BINDINGDB", 	"http://identifiers.org/bindingDB/");
			aMap.put("bindingDB", 	"http://identifiers.org/bindingDB/");
	        
			aMap.put("BIOCYC", 	"http://identifiers.org/biocyc/");
			
			aMap.put("biogrid", 	"http://identifiers.org/biogrid/");
			aMap.put("BIOGRID", 	"http://identifiers.org/biogrid/");

			aMap.put("BIOMODELS DATABASE", 	"http://www.ebi.ac.uk/biomodels-main/");
			
			aMap.put("CAS",			"http://identifiers.org/cas/");

			aMap.put("CAZY",		"http://identifiers.org/cazy/");

			aMap.put("CHEBI",		"http://identifiers.org/chebi/");
			aMap.put("ChEBI",		"http://identifiers.org/chebi/");
			
			aMap.put("CHEMBL",		"http://identifiers.org/chembl.compound/");
			
			aMap.put("DRUGBANK",	"http://www.drugbank.ca/drugs/");
			aMap.put("DrugBank",	"http://www.drugbank.ca/drugs/");
			
			aMap.put("ENA",			"http://identifiers.org/ena.embl/");
			
			aMap.put("Ensembl", 	"http://identifiers.org/ensembl/");
			aMap.put("ENSEMBL", 	"http://identifiers.org/ensembl/");
			aMap.put("ensembl", 	"http://identifiers.org/ensembl/");

			aMap.put("GENATLAS",		"http://identifiers.org/genatlas/");
			aMap.put("genatlas",		"http://identifiers.org/genatlas/");

			aMap.put("GENECARDS",		"http://identifiers.org/genecards/");
			
			aMap.put("GENE ONTOLOGY",		"http://identifiers.org/go/");
			aMap.put("Gene Ontology",		"http://identifiers.org/go/");

			aMap.put("GENPEPT",		"http://www.ncbi.nlm.nih.gov/protein/");
			aMap.put("GENBANK PROTEIN DATABASE",		"http://www.ncbi.nlm.nih.gov/protein/");
			
			aMap.put("GLYCOMEDB",	"http://identifiers.org/glycomedb/");
			
			aMap.put("HGNC",		"http://identifiers.org/hgnc/");
			aMap.put("hgnc",		"http://identifiers.org/hgnc/");

			aMap.put("HGNC Symbol",	"http://identifiers.org/hgnc.symbol/");
			aMap.put("HGNC SYMBOL",	"http://identifiers.org/hgnc.symbol/");

			aMap.put("hprd",		"http://identifiers.org/hprd/");
			aMap.put("HPRD",		"http://identifiers.org/hprd/");
			
			
			aMap.put("InChIKey",	"http://identifiers.org/inchikey/");

			aMap.put("intact",		"http://identifiers.org/intact/");
			aMap.put("INTACT",		"http://identifiers.org/intact/");
			
			aMap.put("interpro",	"http://identifiers.org/interpro/");
			aMap.put("InterPro",	"http://identifiers.org/interpro/");
			aMap.put("INTERPRO",	"http://identifiers.org/interpro/");

			aMap.put("KEGG COMPOUND",	"http://identifiers.org/kegg.compound/");
			aMap.put("KEGG Compound",	"http://identifiers.org/kegg.compound/");
			aMap.put("KEGG DRUG",		"http://identifiers.org/kegg.drug/");
			aMap.put("KEGG GENES",	"http://identifiers.org/kegg.genes/");
			aMap.put("KEGG GLYCAN",		"http://identifiers.org/kegg.glycan/");
			aMap.put("KEGG ORTHOLOGY",	"http://identifiers.org/kegg.orthology/");
			aMap.put("KEGG PATHWAY",	"http://identifiers.org/kegg.pathway/");
			
			aMap.put("KEGG REACTION",	"http://identifiers.org/kegg.reaction/");
			
			aMap.put("LIPIDBANK",	"http://identifiers.org/lipidbank/");
			
			aMap.put("MOLECULAR INTERACTIONS ONTOLOGY",		"http://identifiers.org/psimi/");

			aMap.put("MOLECULAR MODELING DATABASE",		"http://identifiers.org/mmdb/");
			
			aMap.put("NCBI Gene","http://identifiers.org/ncbigene/");
			aMap.put("NCBI GENE","http://identifiers.org/ncbigene/");
			
			aMap.put("PROSITE","http://identifiers.org/prosite/");
			
			aMap.put("Pubmed",	"http://www.ncbi.nlm.nih.gov/pubmed/");
			aMap.put("Reactome",	"http://identifiers.org/reactome/");
			aMap.put("reactome",	"http://identifiers.org/reactome/");
			aMap.put("REACTOME",	"http://identifiers.org/reactome/");
			
			aMap.put("RefSeq",	"http://identifiers.org/refseq/");
			aMap.put("REFSEQ",	"http://identifiers.org/refseq/");
			aMap.put("refseq",	"http://identifiers.org/refseq/");
			
			aMap.put("pubchem-substance","http://identifiers.org/pubchem.substance/");
			
			aMap.put("pubchem",				"http://identifiers.org/pubchem.compound/");
			aMap.put("PUBCHEM-COMPOUND",	"http://identifiers.org/pubchem.compound/");

			aMap.put("omim",		"http://identifiers.org/omim/");
			aMap.put("OMIM",		"http://identifiers.org/omim/");

			aMap.put("PROTEIN DATA BANK", "http://identifiers.org/pdb/");
			aMap.put("Protein Data Bank", "http://identifiers.org/pdb/");
			
			aMap.put("Protein Modification Ontology", "http://identifiers.org/psimod/");
			aMap.put("PROTEIN MODIFICATION ONTOLOGY", "http://identifiers.org/psimod/");
			
			aMap.put("Panther Family", 		"http://identifiers.org/panther.family/");
			aMap.put("PANTHER Family", 		"http://identifiers.org/panther.family/");
			aMap.put("PANTHER FAMILY", 		"http://identifiers.org/panther.family/");
			aMap.put("Panther", 			"http://identifiers.org/panther.family/");
			aMap.put("PANTHER", 			"http://identifiers.org/panther.family/");

			aMap.put("Pfam", 		"http://identifiers.org/pfam/");
			aMap.put("PFAM", 		"http://identifiers.org/pfam/");
			
			aMap.put("PHARMGKB DRUG", 		"http://identifiers.org/pharmgkb.drug/");
			
			aMap.put("RAT GENOME DATABASE", 		"http://identifiers.org/rgd/");
			
			aMap.put("Smart", 		"http://identifiers.org/smart/");
			aMap.put("SMART", 		"http://identifiers.org/smart/");
			
			aMap.put("Taxonomy", 		"http://identifiers.org/taxonomy/");
			aMap.put("TAXONOMY", 		"http://identifiers.org/taxonomy/");
			
			aMap.put("UniProt", 			"http://identifiers.org/uniprot/");
			aMap.put("UNIPROT", 			"http://identifiers.org/uniprot/");
			aMap.put("UniProt Isoform",		"http://identifiers.org/uniprot.isoform/");
			aMap.put("UNIPROT ISOFORM",		"http://identifiers.org/uniprot.isoform/");
			
			aMap.put("UniProt Knowledgebase",		"http://identifiers.org/uniprot/");
			aMap.put("UNIPROT KNOWLEDGEBASE",		"http://identifiers.org/uniprot/");
			
			aMap.put("WIKIPEDIA",		"http://identifiers.org/wikipedia.en/");
			aMap.put("Wikipedia",		"http://identifiers.org/wikipedia.en/");
			
			aMap.put("nucleotide genbank identifier",	"http://www.ncbi.nlm.nih.gov/nuccore/");
			aMap.put("NUCLEOTIDE GENBANK IDENTIFIER",	"http://www.ncbi.nlm.nih.gov/nuccore/");
			aMap.put("GENBANK GENE DATABASE",			"http://www.ncbi.nlm.nih.gov/nuccore/");
			
			defaultNSMap = Collections.unmodifiableMap(aMap);

			// construct the reverse map.
			Map<String, String> bMap = new TreeMap<>();
			for ( Map.Entry<String, String> e: aMap.entrySet()) {
				if (!bMap.containsKey(e.getValue())) {
					bMap.put(e.getValue(), e.getKey());
				}
			}
			
			reverseNSMap = Collections.unmodifiableMap(bMap);

	    }

    public PersistenceService(NdexDatabase db) throws NdexException {
		this.database = db;
		this.localConnection = this.database.getAConnection();
		this.graph = new OrientGraph(this.localConnection,false);
		graph.setAutoScaleEdgeType(true);
		graph.setEdgeContainerEmbedded2TreeThreshold(40);
		graph.setUseLightweightEdges(true);
		
		this.networkDAO = new NetworkDAO(localConnection);

		this.baseTermStrMap = new TreeMap <>();
		prefixMap = new HashMap<>();
		this.namespaceMap   = new TreeMap <>();
		URINamespaceMap = new HashMap<>();

		this.elementIdCache = CacheBuilder
				.newBuilder().maximumSize(CACHE_SIZE*5)
				.expireAfterAccess(240L, TimeUnit.MINUTES)
				.build(new CacheLoader<Long, ODocument>() {
				   @Override
				   public ODocument load(Long key) throws NdexException, ExecutionException {
					   ODocument o = networkDAO.getDocumentByElementId(key);
                       if ( o == null )
                    	   throw new NdexException ("Document is not found for element id: " + key);
					   return o;
				   }
			    });

    }
	 
	 protected static ODocument createSimplePropertyDoc(String key, String value) {
			ODocument pDoc = new ODocument(NdexClasses.SimpleProperty)
				.fields(NdexClasses.SimpleProp_P_name,key,
						NdexClasses.SimpleProp_P_value, value)
			   .save();
			return pDoc;
		}

	 public void commit () {
			this.localConnection.commit();
		}
	
	private Namespace findOrCreateNamespace(RawNamespace key) throws NdexException {
		Namespace ns = namespaceMap.get(key);

		if ( ns != null ) {
	        // check if namespace definitions are consistent
			if (key.getPrefix() !=null && key.getURI() !=null && 
	          		 !ns.getUri().equals(key.getURI()))
	          	   throw new NdexException("Namespace conflict: prefix " 
	          		       + key.getPrefix() + " maps to  " + 
	          			   ns.getUri() + " and " + key.getURI());

	        return ns;
		}
		
		if ( key.getPrefix() !=null && key.getURI() == null )
			throw new NdexException ("Prefix " + key.getPrefix() + " is not defined." );
		
		// persist the Namespace in db.
		ns = new Namespace();
		ns.setPrefix(key.getPrefix());
		ns.setUri(key.getURI());
		ns.setId(database.getNextId(localConnection));
		

		ODocument nsDoc = new ODocument(NdexClasses.Namespace);
		nsDoc = nsDoc.field("prefix", key.getPrefix())
		  .field("uri", ns.getUri())
		  .field("id", ns.getId())
		  .save();
		
        
		OrientVertex nsV = graph.getVertex(nsDoc);
		networkVertex.addEdge(NdexClasses.Network_E_Namespace, nsV);
		
		if (ns.getPrefix() != null) 
			prefixMap.put(ns.getPrefix(), ns);
		
		if ( ns.getUri() != null) 
			URINamespaceMap.put(ns.getUri(), ns);
		
		elementIdCache.put(ns.getId(),nsDoc);
		namespaceMap.put(key, ns);
		return ns; 
		
	}

	
	
	 
	 protected Long createCitation(String title, String idType, String identifier, 
				List<String> contributors, 
				Collection<NdexPropertyValuePair> properties) {
			Long citationId = database.getNextId(localConnection);

			ODocument citationDoc = new ODocument(NdexClasses.Citation)
			  .fields(
					NdexClasses.Element_ID, citationId,
			        NdexClasses.Citation_P_title, title,
			        NdexClasses.Citation_p_idType, idType,
			        NdexClasses.Citation_P_identifier, identifier)
			        
			   .field( NdexClasses.Citation_P_contributors, contributors, OType.EMBEDDEDLIST);
			   
			if(properties!=null && properties.size()>0) {
				citationDoc.field(NdexClasses.ndexProperties, properties);
			}
			
			citationDoc.save();
	        
			OrientVertex citationV = graph.getVertex(citationDoc);
			networkVertex.addEdge(NdexClasses.Network_E_Citations, citationV);
//			this.addPropertiesToVertex(citationV, properties, presentationProperties);
			elementIdCache.put(citationId, citationV.getRecord());
			return citationId; 
		}

	 
	  protected Long createSupport(String literal, Long citationId, List<NdexPropertyValuePair> props) {
			
			Long supportId =database.getNextId(localConnection) ;

			ODocument supportDoc = new ODocument(NdexClasses.Support)
			   .fields(NdexClasses.Element_ID, supportId,
			           NdexClasses.Support_P_text, literal)	;
			  
			if ( citationId != null && citationId >= 0 ) {
				supportDoc.fields(NdexClasses.Citation,citationId);
			} 
			
			if ( props !=null && props.size()>0) {
				supportDoc.field(NdexClasses.ndexProperties, props);
			}
			supportDoc.save();

			OrientVertex supportV = graph.getVertex(supportDoc);

			networkVertex.addEdge(NdexClasses.Network_E_Supports, supportV);

			supportDoc = supportV.getRecord();
			elementIdCache.put(supportId, supportDoc);
			return supportId; 
			
		}

		protected Long createFunctionTerm(Long baseTermId, List<Long> termList) throws ExecutionException {
			
			Long functionTermId = database.getNextId(localConnection); 
			
		    ODocument fTerm = new ODocument(NdexClasses.FunctionTerm)
		       .fields(NdexClasses.Element_ID, functionTermId,
		    		   NdexClasses.BaseTerm, baseTermId)
		       .save();
		    
	        OrientVertex fTermV = graph.getVertex(fTerm);
	        
	        for (Long id : termList) {
	        	ODocument o = elementIdCache.get(id);
	        	fTermV.addEdge(NdexClasses.FunctionTerm_E_paramter, graph.getVertex(o));
	        }
		    
	        //add link to the network vertex
	        this.networkVertex.addEdge(NdexClasses.Network_E_FunctionTerms, fTermV);
	        
	        elementIdCache.put(functionTermId, fTerm);
	        return functionTermId;
		}

		/**
		 * Find or create a base term from a string, and return its identifier.
		 * @param termString
		 * @return
		 * @throws NdexException
		 * @throws ExecutionException
		 */
		// just fore reference before the 1.3 is done.
/*		private Long getBaseTermId_old(String termStringRaw) throws NdexException, ExecutionException {
			
	        String termString = termStringRaw;		
			if ( termStringRaw.length() > 8 && termStringRaw.substring(0, 7).equalsIgnoreCase("http://") ) {
		  		  try {
					URI termStringURI = new URI(termStringRaw);
					String fragment = termStringURI.getFragment();
					
				    String uriPrefix;
				    if ( fragment == null ) {
						    String path = termStringURI.getPath();
						    if (path != null && path.indexOf("/") != -1) {
							   fragment = termStringRaw.substring(termStringRaw.lastIndexOf('/') + 1);
							   uriPrefix = termStringRaw.substring(0,
									   termStringRaw.lastIndexOf('/') + 1);
						    } else
						       throw new NdexException ("Unsupported URI format in term: " + termStringRaw);
				    } else {
						    uriPrefix = termStringURI.getScheme()+":"+termStringURI.getSchemeSpecificPart()+"#";
				    }
		                 
				    Namespace ns = this.URINamespaceMap.get(uriPrefix);
				    if ( ns != null ) {
				    	if (ns.getPrefix() != null ) 
				    		termString =  ns.getPrefix() + ":" + fragment;
				    } else { // check if it is a uri we know
				    	String prefix = reverseNSMap.get(uriPrefix);
				    	if ( prefix !=null ) {
				    		// create the namespace and the term.
				    		return createBaseTerm(termString);
				    	}
				    	
				    }
				  } catch (URISyntaxException e) {
					// ignore and move on to next case
				  }
			}		
			
			Long termId = this.baseTermStrMap.get(termString);
			if ( termId != null) {
				return termId;
			}
			
			//check its canonical form
			String[] termStringComponents = TermUtilities.getNdexQName(termString);
			if (termStringComponents != null && termStringComponents.length == 2) {
				String identifier = termStringComponents[1];
				String orgPrefix = termStringComponents[0];
				
				Namespace ns2 = this.prefixMap.get(orgPrefix);  // exists in prefix mapping & map with a different prefix.
				if ( ns2 !=null ) {
					if ( ! orgPrefix.equals(ns2.getPrefix())) {   
						termId = getBaseTermId(ns2.getPrefix() +":"+ identifier); // use the canonical form
				        this.baseTermStrMap.put(termString, termId);
				        return termId;
					} 
				} else {
				
					// consult the default ns mapping
					String newURI = defaultNSMap.get(orgPrefix);
					
				    if ( newURI !=null) {
				    	Namespace ns = this.URINamespaceMap.get(newURI);
				    	if (ns != null && ns.getPrefix() != null) {
				    		this.prefixMap.put(orgPrefix, ns); // add this prefix to mapping
				    		termId = getBaseTermId(ns.getPrefix() +":"+ identifier);
				    		this.baseTermStrMap.put(termString, termId);
				    		return termId;
				    	} 
				    } 
				}
			}	
			
		    return this.createBaseTerm(termString);	
		}
*/
		public Long getBaseTermId(String termStringRaw) throws NdexException, ExecutionException {
			Long termId = this.baseTermStrMap.get(termStringRaw);
			if ( termId !=null)
				return termId;

			return createBaseTerm(termStringRaw);
	  
		}
		
		
		public Long getBaseTermId (  String prefix, String localTerm) throws ExecutionException {
			Long termId = this.baseTermStrMap.get(prefix+":"+localTerm);
			if ( termId != null) {
				return termId;
			}
//		    return this.createBaseTerm(prefix,localTerm,null);	
			
			Long btId = null;
			Namespace namespace = this.prefixMap.get(prefix);
			if ( namespace !=null) {
			   btId = createBaseTerm(null, localTerm, namespace.getId());
			} else
				btId = createBaseTerm(prefix + ":" , localTerm, null);
	        this.baseTermStrMap.put(prefix+":"+localTerm, btId);
	        return btId;

		}
			
	
		protected Long createBaseTerm(String termString) throws NdexException {
				
				// case 1 : termString is a URI
				// example: http://identifiers.org/uniprot/P19838
				// treat the last element in the URI as the identifier and the rest as
				// prefix string. Just to help the future indexing.
				//
				String prefix = null;
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
							   prefix = termString.substring(0, pos + 1);
						    } else
						       throw new NdexException ("Unsupported URI format in term: " + termString);
					    } else {
						    prefix = termStringURI.getScheme()+":"+termStringURI.getSchemeSpecificPart()+"#";
					    }
		                 
					    Long btId = createBaseTerm(prefix,identifier,null);
					    baseTermStrMap.put(termString, btId);
					    return btId;
					  
				  } catch (URISyntaxException e) {
					// ignore and move on to next case
				  }
				}
				
				String[] termStringComponents = TermUtilities.getNdexQName(termString);
				if (termStringComponents != null && termStringComponents.length == 2) {
					// case 2: termString is of the form (NamespacePrefix:)*Identifier
					identifier = termStringComponents[1];
					prefix = termStringComponents[0];
					
					Long btId = null;
					Namespace namespace = this.prefixMap.get(prefix);
					if ( namespace !=null) {
					   btId = createBaseTerm(null, identifier, namespace.getId());
					} else
						btId = createBaseTerm(prefix + ":" , identifier, null);
			        this.baseTermStrMap.put(termString, btId);
			        return btId;
				} 
				
					// case 3: termString cannot be parsed, use it as the identifier.
					// so leave the prefix as null and create the baseterm				
				
				   // create baseTerm in db
				    Long id= createBaseTerm(null,termString,null);
		           this.baseTermStrMap.put(termString, id);
		           return id;
			
			}
				
		
		protected Long createBaseTerm (String prefix, String localName, Long nsId) {
			
			Long termId = database.getNextId(localConnection);
						
			ODocument btDoc = new ODocument(NdexClasses.BaseTerm)
					.fields(NdexClasses.BTerm_P_name, localName,
				  NdexClasses.Element_ID, termId,
		  		  NdexClasses.BTerm_P_prefix, prefix); 
			if ( nsId != null) {
				btDoc.field(NdexClasses.BTerm_NS_ID, nsId);
			} 
			
			btDoc.save();
			OrientVertex basetermV = graph.getVertex(btDoc);
			networkVertex.getRecord().reload();
	        networkVertex.addEdge(NdexClasses.Network_E_BaseTerms, basetermV);
	        elementIdCache.put(termId, basetermV.getRecord());
	        return termId;
		}
		
	/*	
		 protected Long createBaseTerm(String localTerm, long nsId) {
				Long termId = database.getNextId();

				ODocument btDoc = new ODocument(NdexClasses.BaseTerm)
						.fields(NdexClasses.BTerm_P_name, localTerm,
					  NdexClasses.Element_ID, termId); 
	
				if ( nsId > 0) {
					//Namespace namespace = this.prefixMap.get(prefix);
					btDoc.fields(NdexClasses.BTerm_NS_ID, nsId);
				} 
				  
				btDoc.save();
				OrientVertex basetermV = graph.getVertex(btDoc);
				networkVertex.getRecord().reload();
		        networkVertex.addEdge(NdexClasses.Network_E_BaseTerms, basetermV);
		        elementIdCache.put(termId, basetermV.getRecord());
				return termId;
		 }
*/

		/**
		 * Find or create a namespace object from database;
		 * @param rns
		 * @return
		 * @throws NdexException
		 */
		public Namespace getNamespace(RawNamespace rns) throws NdexException {
			if (rns.getPrefix() == null) {
				Namespace ns = URINamespaceMap.get(rns.getURI());
				if ( ns != null ) {
					return ns; 
				}
			}
			
			if (rns.getURI() == null) {
				Namespace ns = this.prefixMap.get(rns.getPrefix()); 
				if ( ns != null ) {
					return ns; 
				}
			}
			
			return findOrCreateNamespace(rns);
		}
		
		
	  public void setNetworkSourceFormat(NetworkSourceFormat fmt) {
		  this.networkDoc.field(NdexClasses.Network_P_source_format, fmt.toString()).save();
		  NdexPropertyValuePair p = new NdexPropertyValuePair (NdexClasses.Network_P_source_format, fmt.toString());
		  this.network.getProperties().add(p);
	  }

    //DW: Moved from NdexPersistenceService to here, PersistenceService
    public void setNetworkProvenance(ProvenanceEntity e) throws JsonProcessingException
    {

        ObjectMapper mapper = new ObjectMapper();
        String provenanceString = mapper.writeValueAsString(e);
        // store provenance string
        this.networkDoc = this.networkDoc.field(NdexClasses.Network_P_provenance, provenanceString)
                .save();
    }

    //DW: New method to read provenance
    public ProvenanceEntity getNetworkProvenance() throws IOException
    {
        // Make an object mapper
        ObjectMapper mapper = new ObjectMapper();
        // get the provenance string
        String provenanceString = this.networkDoc.field(NdexClasses.Network_P_provenance);
        // deserialize it to create a ProvenanceEntity object
        if (provenanceString != null && provenanceString.length() > 0){
            return mapper.readValue(provenanceString, ProvenanceEntity.class);
        }
        return new ProvenanceEntity();
    }

		
	  @Override
	public void close () {
          this.graph.shutdown();
	  }
	  
	  
	public NetworkSummary getCurrentNetwork() {
			return this.network;
	}
		
	
}
