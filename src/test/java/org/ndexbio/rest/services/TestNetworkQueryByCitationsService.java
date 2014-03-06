package org.ndexbio.rest.services;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.ndexbio.common.exceptions.NdexException;
import org.ndexbio.common.helpers.IdConverter;
import org.ndexbio.common.models.object.BaseTerm;
import org.ndexbio.common.models.object.Citation;
import org.ndexbio.common.models.object.NdexObject;
import org.ndexbio.common.models.object.Network;
import org.ndexbio.common.models.object.NetworkQueryParameters;
import org.ndexbio.common.models.object.Support;

import com.orientechnologies.orient.core.id.ORID;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestNetworkQueryByCitationsService extends TestNdexService
{
    private static final NetworkService _networkService = new NetworkService(_mockRequest);
    
    
    @Test
    public void queryBELNetworkByCitations()
    {
    	System.out.println("_________________________________");
        try
        {
            final ORID networkRid = getRid("BEL Framework Small Corpus Document");
            final List<Citation> citations = _networkService.getCitations(IdConverter.toJid(networkRid), 0, 5);
            int citationIndex = 1;
            System.out.println("fetched " + citations.size() + " citations from " + networkRid);
            System.out.println("using " + citations.get(citationIndex).getIdentifier() + " " + citations.get(citationIndex).getTitle());
            for (Citation citation : citations){	
            	Network network = _networkService.getEdgesByCitations(IdConverter.toJid(networkRid), 0, 100, new String[] { citation.getId()});
                summarizeNetwork(network);
            }
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
        
    }
    
    @Test
    public void queryBELNetworkToFindCitations()
    {
        try
        {
            final ORID networkRid = getRid("BEL Framework Small Corpus Document");
            final List<Citation> citations = _networkService.getCitations(IdConverter.toJid(networkRid), 0, 100);
            System.out.println(networkRid + " has " + citations.size() + " citations:");
            for (Citation citation : citations){
            	
            	//System.out.println(citation.getIdentifier() + " " + citation.getTitle() + " (supports = " + citation.getSupports().size() + ")");
            }         
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void summarizeNetwork(Network network){
        System.out.println("Subnetwork with edgeCount = " + network.getEdgeCount() + " and nodeCount = " + network.getNodeCount());
        System.out.println("- " + network.getNamespaces().size() + " namespaces");
        System.out.println("- " + network.getTerms().size() + " terms");
        System.out.println("- " + network.getCitations().size() + " citations");
        System.out.println("- " + network.getSupports().size() + " supports");
        checkForNullJdexIds(network.getSupports());
        System.out.println("- " + network.getNodes().size() + " nodes");
        System.out.println("- " + network.getEdges().size() + " edges");
        for (Citation citation : network.getCitations().values()){
        	System.out.println("citiation: " + citation.getIdentifier() + " " + citation.getTitle());
        	System.out.println("has supports: " + citation.getSupports().size());
        	for (String supportId : citation.getSupports()){
        		System.out.println("- " + supportId );
        	}
        }
        System.out.println("_________________________________");
    }

	private void checkForNullJdexIds(Map<String, Support> objectMap) {
		int nullKeyCount = 0;
		int nullValueCount = 0;
		int nullSupportJdexIdCount = 0;
		for (Entry<String, Support> entry : objectMap.entrySet()){
			System.out.println("- " + entry.getKey() + " " + entry.getValue().getText().substring(0,20));
			if (entry.getKey() == null) nullKeyCount++;
			if (entry.getValue() == null) nullValueCount++;
			if (entry.getValue() != null && entry.getValue().getJdexId() == null) nullSupportJdexIdCount++;
		}
		if (nullKeyCount > 0 || nullValueCount > 0){
			System.out.println("null jdexIds: " + nullKeyCount + ", null objects: " + nullValueCount + ", objects with null jdex: " + nullSupportJdexIdCount);
		}
		
	}
 

}
