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
package org.ndexbio.common.cx;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

import org.cxio.aspects.datamodels.EdgeAttributesElement;
import org.cxio.aspects.datamodels.EdgesElement;
import org.cxio.aspects.datamodels.NetworkAttributesElement;
import org.cxio.aspects.datamodels.NodeAttributesElement;
import org.cxio.aspects.datamodels.NodesElement;
import org.cxio.metadata.MetaDataCollection;
import org.cxio.metadata.MetaDataElement;
import org.ndexbio.model.cx.BELNamespaceElement;
import org.ndexbio.model.cx.CitationElement;
import org.ndexbio.model.cx.EdgeCitationLinksElement;
import org.ndexbio.model.cx.EdgeSupportLinksElement;
import org.ndexbio.model.cx.FunctionTermElement;
import org.ndexbio.model.cx.NamespacesElement;
import org.ndexbio.model.cx.NdexNetworkStatus;
import org.ndexbio.model.cx.NodeCitationLinksElement;
import org.ndexbio.model.cx.NodeSupportLinksElement;
import org.ndexbio.model.cx.Provenance;
import org.ndexbio.model.cx.ReifiedEdgeElement;
import org.ndexbio.model.cx.SupportElement;
import org.ndexbio.model.exceptions.NdexException;

public class CXMetaDataManager {

	private static CXMetaDataManager INSTANCE = null;
	
	// table stores ndex supported aspect and its version.
	private Map<String, String> NdexSupportedAspects;

	static protected final long consistencyGroupId = 1;
	
	
/*	public static final String[] NdexSupportedAspects = {NodesElement.ASPECT_NAME,EdgesElement.ASPECT_NAME,NetworkAttributesElement.ASPECT_NAME,
			NodeAttributesElement.ASPECT_NAME, EdgeAttributesElement.ASPECT_NAME, CitationElement.ASPECT_NAME, SupportElement.ASPECT_NAME,
			EdgeCitationLinksElement.ASPECT_NAME, EdgeSupportLinksElement.ASPECT_NAME, NodeCitationLinksElement.ASPECT_NAME,
			NodeSupportLinksElement.ASPECT_NAME, FunctionTermElement.ASPECT_NAME, NamespacesElement.ASPECT_NAME, NdexNetworkStatus.ASPECT_NAME,
			Provenance.ASPECT_NAME,ReifiedEdgeElement.ASPECT_NAME};	 */
	
	public CXMetaDataManager() { 
		NdexSupportedAspects = new TreeMap<>();
	    	  
	    NdexSupportedAspects.put(NodesElement.ASPECT_NAME,"1.0");
	    	  NdexSupportedAspects.put(EdgesElement.ASPECT_NAME,"1.0");
	    	  NdexSupportedAspects.put(NetworkAttributesElement.ASPECT_NAME,"1.0");
	    	  NdexSupportedAspects.put(NodeAttributesElement.ASPECT_NAME,"1.0");
	    	  NdexSupportedAspects.put(EdgeAttributesElement.ASPECT_NAME, "1.0");
	    	  NdexSupportedAspects.put(CitationElement.ASPECT_NAME, "1.0");
	    	  NdexSupportedAspects.put(SupportElement.ASPECT_NAME, "1.0");
	    	  NdexSupportedAspects.put(EdgeCitationLinksElement.ASPECT_NAME,"1.0"); 
	    	  NdexSupportedAspects.put(EdgeSupportLinksElement.ASPECT_NAME,"1.0");
	    	  NdexSupportedAspects.put(NodeCitationLinksElement.ASPECT_NAME,"1.0");
	    	  NdexSupportedAspects.put(	NodeSupportLinksElement.ASPECT_NAME,"1.0");
	    	  NdexSupportedAspects.put( FunctionTermElement.ASPECT_NAME,"1.0");
	    	  NdexSupportedAspects.put( NamespacesElement.ASPECT_NAME, "1.0");
	    	  NdexSupportedAspects.put(NdexNetworkStatus.ASPECT_NAME,"1.0");
	    	  NdexSupportedAspects.put(Provenance.ASPECT_NAME,"1.0");
	    	  NdexSupportedAspects.put(ReifiedEdgeElement.ASPECT_NAME,"1.0");	  
	    	  NdexSupportedAspects.put(BELNamespaceElement.ASPECT_NAME, "1.0;");
	}

	public static synchronized CXMetaDataManager getInstance() {
	      if(INSTANCE == null) {
	    	  INSTANCE = new CXMetaDataManager() ;
	      }
	      return INSTANCE;
	}

	public MetaDataCollection createCXMataDataTemplate() {
		MetaDataCollection md= new MetaDataCollection();

		for ( Map.Entry<String, String> e: NdexSupportedAspects.entrySet()) {
			MetaDataElement metadataElement = new MetaDataElement();
			metadataElement .setName(e.getKey());
			metadataElement .setVersion(e.getValue());
			metadataElement .setConsistencyGroup(consistencyGroupId);
	        md.add(metadataElement );

		}   
		md.setElementCount(NdexNetworkStatus.ASPECT_NAME, 1l);
        return md;
	}

	public MetaDataCollection createCXMataDataTemplateForAspects(Collection<String> aspects) throws NdexException {
		MetaDataCollection md= new MetaDataCollection();

		for (String aspectName : aspects) {
			String aspectVersion = NdexSupportedAspects.get(aspectName);
			if ( aspectVersion == null)
				throw new NdexException ("Aspect " + aspectName + " is not an NDEx supported aspect.");
			MetaDataElement metadataElement = new MetaDataElement();
			metadataElement .setName(aspectName);
			metadataElement .setVersion(aspectVersion);
			metadataElement .setConsistencyGroup(consistencyGroupId);
	        if ( aspectName .equals(NdexNetworkStatus.ASPECT_NAME))
	        	metadataElement.setElementCount(1L);
	        md.add(metadataElement );
	        	
		}    
        return md;
	}
	
	public boolean isNdexSupportedAspect(String aspect) {
		return NdexSupportedAspects.containsKey(aspect);
	}
	
}
