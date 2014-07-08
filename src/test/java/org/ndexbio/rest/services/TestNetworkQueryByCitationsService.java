package org.ndexbio.rest.services;

import java.util.List;

import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.ndexbio.common.helpers.IdConverter;
import org.ndexbio.rest.helpers.ObjectModelTools;

import com.orientechnologies.orient.core.id.ORID;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestNetworkQueryByCitationsService extends TestNdexService
{
    private static final NetworkService _networkService = new NetworkService(_mockRequest);
    
    
    @Test
    public void queryBELNetworkByCitations()
    {
    	System.out.println("_________________________________");
   /*     try
        {
            final ORID networkRid = getRid("BEL Framework Small Corpus Document");
            final List<Citation> citations = _networkService.getCitations(IdConverter.toJid(networkRid), 0, 5);
            int citationIndex = 1;
            System.out.println("fetched " + citations.size() + " citations from " + networkRid);
            System.out.println("using " + citations.get(citationIndex).getIdentifier() + " " + citations.get(citationIndex).getTitle());
            for (Citation citation : citations){	
            	Network network = _networkService.getEdgesByCitations(IdConverter.toJid(networkRid), 0, 100, new String[] { citation.getId()});
                ObjectModelTools.summarizeNetwork(network);
            }
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
     */   
    }
    
    @Test
    public void queryBELNetworkToFindCitations()
    {
  /*      try
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
        } */
    }
    
 
 

}
