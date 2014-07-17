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
	        System.out.println("- " + network.getBaseTermIds().size() + " terms");
	        System.out.println("- " + network.getCitations().size() + " citations");
	        System.out.println("- " + network.getSupports().size() + " supports");
	       // checkForNullJdexIds(network.getSupports());
	        System.out.println("- " + network.getNodes().size() + " nodes");
	        System.out.println("- " + network.getEdges().size() + " edges");
	        for (Citation citation : network.getCitations().values()){
	        	System.out.println("citation: " + citation.getProperties().toString());
	        	System.out.println("has supports: " + citation.getSupports().size());
	        	//for (String supportId : citation.getSupports()){
	        	//	System.out.println("- " + supportId );
	        	//}
	        }
	        System.out.println(network.getBaseTermIds().size() + " Terms:");
	      //  summarizeTerms(network.getBaseTermIds(), network);
	        
	        
	        
	        Set<String> termIdsFromNodesAndEdges = new HashSet<String>();
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
