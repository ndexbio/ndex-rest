/**
 * Copyright (c) 2013, 2015, The Regents of the University of California, The Cytoscape Consortium
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
package org.ndexbio.rest.helpers;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.ndexbio.model.object.network.Citation;
import org.ndexbio.model.object.network.Network;



public class ObjectModelTools {
	
	   public static void summarizeNetwork(Network network){
		   System.out.println("_________________________________");
		   System.out.println("Summarizing Object Model Network:");
	        System.out.println("Subnetwork with edgeCount = " + network.getEdgeCount() + " and nodeCount = " + network.getNodeCount());
	        System.out.println("- " + network.getNamespaces().size() + " namespaces");
	        System.out.println("- " + network.getBaseTerms().size() + " terms");
	        System.out.println("- " + network.getCitations().size() + " citations");
	        System.out.println("- " + network.getSupports().size() + " supports");
	       // checkForNullJdexIds(network.getSupports());
	        System.out.println("- " + network.getNodes().size() + " nodes");
	        System.out.println("- " + network.getEdges().size() + " edges");
	        for (Citation citation : network.getCitations().values()){
	        	System.out.println("citation: " + citation.getProperties().toString());
	        //	System.out.println("has supports: " + citation.getSupports().size());
	        	//for (String supportId : citation.getSupports()){
	        	//	System.out.println("- " + supportId );
	        	//}
	        }
	        System.out.println(network.getBaseTerms().size() + " Terms:");
	      //  summarizeTerms(network.getBaseTermIds(), network);
	        
	        
	        
	        Set<String> termIdsFromNodesAndEdges = new HashSet<>();
	        for (Long entryId : network.getNodes().keySet()){
	        	
	        	//System.out.println("Node " + nodeId);
//	        	String termId = node.getRepresents();

	//        	getAllTermIds(termId, network, termIdsFromNodesAndEdges);      	
	        }
	 /*       for (Edge edge : network.getEdges().values()){
	        	termIdsFromNodesAndEdges.add(edge.getP());
	        } */
	        System.out.println(termIdsFromNodesAndEdges.size() + " Terms from Nodes:");
	//        summarizeTerms(termIdsFromNodesAndEdges, network);
	        
	        System.out.println("_________________________________");
	    }
	    
/*	    public static void getAllTermIds(String termId, Network network, Set<String>termIds){
	    	termIds.add(termId);
	    	Term term = network.getTerms().get(termId);
	    	if (null == term){
	    		System.out.println("  Missing term " + termId );
	    	} else if ("Function".equals(term.getTermType())){
	    		FunctionTerm ft = (FunctionTerm)term;
	    		termIds.add(ft.getTermFunction());
	    		//System.out.println("  Function term " + termId + " function = " + ft.getTermFunction());
	    		for (String parameterId : ft.getParameters().values()){
	    			getAllTermIds(parameterId, network, termIds);
	    		}
	    	}
	    }
	*/    
	 /*   public static void summarizeTerms(Collection<String> termIds, Network network){
	        int baseTermCount = 0;
	        int functionTermCount = 0;
	        int reifiedEdgeTermCount = 0;
	        int nullTermCount = 0;
	        for (String termId : termIds){
	        	Term term = network.getTerms().get(termId);
	        	if (null == term){
	        		nullTermCount++;
	        	} else {
	        	if ("ReifiedEdge".equals(term.getTermType())){
	        		reifiedEdgeTermCount++;
	        	} else if ("Function".equals(term.getTermType())){
	        		functionTermCount++;
	        	} else {
	        		baseTermCount++;
	        	}
	        	}
	        }
	        System.out.println("   baseTerms: " + baseTermCount);
	        System.out.println("   functionTerms: " + functionTermCount);
	        System.out.println("   reifiedEdgeTerms: " + reifiedEdgeTermCount);
	        System.out.println("   missing terms: " + nullTermCount);	
	    }
*/
	/*	private static void checkForNullJdexIds(Map<String, Support> objectMap) {
			int nullKeyCount = 0;
			int nullValueCount = 0;
			int nullSupportJdexIdCount = 0;
			for (Entry<String, Support> entry : objectMap.entrySet()){
				//System.out.println("   " + entry.getKey() + " " + entry.getValue().getText().substring(0,20));
				if (entry.getKey() == null) nullKeyCount++;
				if (entry.getValue() == null) nullValueCount++;
				if (entry.getValue() != null && entry.getValue().getJdexId() == null) nullSupportJdexIdCount++;
			}
			if (nullKeyCount > 0 || nullValueCount > 0){
				System.out.println("null jdexIds: " + nullKeyCount + ", null objects: " + nullValueCount + ", objects with null jdex: " + nullSupportJdexIdCount);
			}
			
		} */

}
