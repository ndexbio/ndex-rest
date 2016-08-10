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
package org.ndexbio.common.models.dao.postgresql;


import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.cxio.metadata.MetaDataCollection;
import org.ndexbio.common.NdexClasses;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.model.cx.BELNamespaceElement;

import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.object.network.Namespace;
import org.ndexbio.model.object.network.NetworkSourceFormat;
import org.ndexbio.task.parsingengines.XbelParser;

import com.orientechnologies.common.concur.ONeedRetryException;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.impls.orient.OrientGraph;
import com.tinkerpop.blueprints.impls.orient.OrientVertex;

public class SingleNetworkDAO extends BasicNetworkDAO {
		
	public static final String CXsrcFormatAttrName="ndex:sourceFormat";
	protected ODocument networkDoc;
	
	protected OrientVertex networkVertex;
	
    protected OrientGraph graph;
    protected String uuid;
    
    
	public SingleNetworkDAO ( String UUID) throws NdexException {
		super();
		uuid = UUID;
		networkDoc = getRecordByUUIDStr(UUID);
		
		graph =  new OrientGraph(localConnection,false);
		graph.setAutoScaleEdgeType(true);
		graph.setEdgeContainerEmbedded2TreeThreshold(40);
		graph.setUseLightweightEdges(true);
		networkVertex = graph.getVertex(networkDoc);
		
	}

	
	protected long getVertexCount(String edgeName) {
		return networkVertex.countEdges(Direction.OUT,edgeName);
	}
	
	
    protected Iterable<ODocument> getNetworkElements(String elementEdgeString) {	
    	
    	return getNetworkElements(networkDoc, elementEdgeString);	     
    }
	
	public Iterator<Namespace> getNamespaces() {
		return new NamespaceIterator(getNetworkElements(NdexClasses.Network_E_Namespace));
	}
		
	/*
	public Iterable<CitationElement>  getCXCitations () {
		return new CXCitationCollection(getNetworkElements(NdexClasses.Network_E_Citations),db);
	}
	
	*/



    protected String getBaseTermStringById(long id) throws ObjectNotFoundException {
    	ODocument doc = getBasetermDocById(id);
    	return  getBaseTermStringFromDoc(doc);
    	
    }
    
    protected String getBaseTermStringFromDoc(ODocument doc) throws ObjectNotFoundException {
	    String name = doc.field(NdexClasses.BTerm_P_name);
    	
	    String prefix = doc.field(NdexClasses.BTerm_P_prefix);
	    if (prefix !=null)
	    	name = prefix + name;
	    
    	Long nsId = doc.field(NdexClasses.BTerm_NS_ID); 
    	if ( nsId == null || nsId.longValue() <= 0) 
    		return name;
    	
    	ODocument nsdoc = getNamespaceDocById(nsId);
        prefix = nsdoc.field(NdexClasses.ns_P_prefix)	;
    	return prefix + ":"+ name;
    	
    //	return nsdoc.field(NdexClasses.ns_P_uri) + name;
    }
    
	/**
	 * This function check if the given network contains all the give aspects. 
	 * @param aspectNames
	 * @return the aspect list that are not found in this network. if all aspects are found in the given network,
	 * an empty set will be returned.
	 *
	 */
	public Set<String> findMissingAspect ( Collection<String> aspectNames) {
		MetaDataCollection md = networkDoc.field(NdexClasses.Network_P_metadata);
		TreeSet<String> result = new TreeSet<>();
		if ( md !=null) {
			for (String aspect: aspectNames) {
				if ( md.getMetaDataElement(aspect) == null) 
					result.add(aspect);
			}
			return result;
		}
		
		for ( String aspect : aspectNames) {
			if (Arrays.binarySearch(NdexDatabase.NdexSupportedAspects, aspect) ==-1)
				result.add(aspect);
		}
		return result;
	}
	
	
   public NetworkSourceFormat getSourceFormat()    {
	   return NetworkSourceFormat.valueOf((String)networkDoc.field(NdexClasses.Network_P_source_format));
   }

   /**
 * @throws NdexException 
 * @throws IOException 
 * @throws MalformedURLException 
    *  
    * @param task This function will update the status in the passed in task argument. populate the message attribute in it 
    * if error occurs.
    * @return the status of this task. complete, error etc.
 * @throws  
    */
   public void attachNamespaceFiles() throws NdexException  {
	   Map<String,String> namespaceFileMap = new TreeMap<>();
		   
	    // clear perviou archives if they exist.
	   for ( ODocument rec : getNetworkElements(BELNamespaceElement.ASPECT_NAME) ) {
			   cleanupElement(rec);
	   }
		   
	   for (Iterator<Namespace> i = getNamespaces() ; i.hasNext(); ) {
			   Namespace ns = i.next();
			   
			if ( ! ns.getPrefix().equals(XbelParser.belPrefix) && 
				! ns.getPrefix().equals("TextLocation") ) { // we ignore the bel and TextLocation prefix we put in from the loader
			     try {   
			    	 URL link = new URL(ns.getUri());
			    	 InputStream in = new BufferedInputStream(link.openStream());
			    	 String inputStreamString = new Scanner(in,"UTF-8").useDelimiter("\\A").next();
			    	 namespaceFileMap.put(ns.getPrefix(),inputStreamString)	;
			    	 in.close();
			     } catch ( IOException e) {
			    	 throw new NdexException ("IOException received when downloading file " + ns.getUri() + ": " + e.getMessage());
			     }
		   }
	   }
		   
	   for ( Map.Entry<String, String > entry : namespaceFileMap.entrySet()) {
			   ODocument doc = new ODocument ( NdexClasses.OpaqueElement)
					   .fields(NdexClasses.BELPrefix, entry.getKey(),
							   NdexClasses.BELNamespaceFileContent, entry.getValue()).save();
			   networkVertex.addEdge(BELNamespaceElement.ASPECT_NAME, graph.getVertex(doc));
	   }
	 
   }
   
   public String getNamespaceFile ( String prefix) throws ObjectNotFoundException {
	   for ( ODocument rec : getNetworkElements(BELNamespaceElement.ASPECT_NAME) ) {
		   if ( rec.field(NdexClasses.BELPrefix).equals(prefix)){
			   return rec.field(NdexClasses.BELNamespaceFileContent);
		   }
	   }
	   throw new ObjectNotFoundException("Namespace file of " + prefix + " not found in this network.");
   }

   public void commit () {
	   this.graph.commit();
   }
   
	private void cleanupElement(ODocument doc) {
		doc.reload();

		for	(int retry = 0;	retry <	NdexDatabase.maxRetries;	++retry)	{
			try	{
				graph.removeVertex(graph.getVertex(doc));
				break;
			} catch(ONeedRetryException	e)	{
//				logger.warning("Retry: "+ e.getMessage());
				doc.reload();
			}
		}
	}

}
