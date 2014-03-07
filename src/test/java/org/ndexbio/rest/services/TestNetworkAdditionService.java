package org.ndexbio.rest.services;

import java.util.Iterator;
import java.util.List;

import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.ndexbio.common.exceptions.NdexException;
import org.ndexbio.common.helpers.IdConverter;
import org.ndexbio.common.models.object.BaseTerm;
import org.ndexbio.common.models.object.Citation;
import org.ndexbio.common.models.object.Network;
import org.ndexbio.common.models.object.NetworkQueryParameters;

import com.orientechnologies.orient.core.id.ORID;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestNetworkAdditionService extends TestNdexService
{
    private static  NetworkService _networkService = new NetworkService(_mockRequest);

    @Test
    public void copyBELNetworkInBlocks() throws IllegalArgumentException, NdexException
    {
    	int edgesPerBlock = 100;
        //try
        //{
            final ORID sourceNetworkRid = getRid("BEL Framework Small Corpus Document");
            // Get the source network stats
            
            String sourceNetworkId = IdConverter.toJid(sourceNetworkRid);
            
            copyNetworkInBlocks(sourceNetworkId, edgesPerBlock);
            
            // Get the target network stats
            
            // Stats should be equal
        
        //}
        //catch (Exception e)
        //{
        //    Assert.fail(e.getMessage());
        //    e.printStackTrace();
        //}
    }
    
    private void copyNetworkInBlocks(String sourceNetworkId, int edgesPerBlock) throws IllegalArgumentException, NdexException{
    	Network currentSubnetwork = null;
    	
    	int skipBlocks = 0;
    	
    	// Get the first block of edges from the source network
    	System.out.println("Getting " + edgesPerBlock + " at offset " + skipBlocks);
    	currentSubnetwork = _networkService.getEdges(sourceNetworkId, skipBlocks, edgesPerBlock);
    	
    	currentSubnetwork.setName(currentSubnetwork.getName() + " - copy " + Math.random());
    	currentSubnetwork.setMembers(null);
    	
    	// Create the target network
    	System.out.println("Creating network with " + currentSubnetwork.getEdgeCount()  + " edges");
    	Network targetNetwork = _networkService.createNetwork(currentSubnetwork);

    	String targetNetworkId = targetNetwork.getId();
    	
 
    	// Loop getting subnetworks by edges until the returned subnetwork has no edges
    	do { 
    		skipBlocks++;
    		System.out.println("Getting " + edgesPerBlock + " at offset " + skipBlocks);
    		currentSubnetwork = _networkService.getEdges(sourceNetworkId, skipBlocks, edgesPerBlock);
    		// Add the subnetwork to the target
    		System.out.println("Adding " + currentSubnetwork.getEdgeCount()  + " edges to network " + targetNetworkId);
    		if (currentSubnetwork.getEdgeCount() > 0) 
    			_networkService.addNetwork(targetNetworkId, "JDEX_ID", currentSubnetwork);
    	} while (currentSubnetwork.getEdgeCount() > 0);
    			
    }
    /*
    @Test
    public void queryBELNetworkByCitations()
    {
        try
        {
            final ORID networkRid = getRid("BEL Framework Small Corpus Document");
            final Network network = _networkService.getEdgesByCitations(IdConverter.toJid(networkRid), 0, 100, new String[] { "C16R141" , "C16R144" });
            System.out.println("Subnework with " + network.getEdgeCount() + " edges and " + network.getNodeCount() + " nodes");
            System.out.println("Has " + network.getCitations().size() + " citations:");
            for (Citation citation : network.getCitations().values()){
            	System.out.println(citation.getIdentifier() + " " + citation.getTitle());
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
            	System.out.println(citation.getIdentifier() + " " + citation.getTitle());
            }         
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
    }
 */

}
