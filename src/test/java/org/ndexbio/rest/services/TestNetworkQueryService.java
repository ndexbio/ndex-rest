package org.ndexbio.rest.services;

import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.ndexbio.common.exceptions.NdexException;
import org.ndexbio.common.helpers.IdConverter;
import org.ndexbio.common.models.object.NetworkQueryParameters;

import com.orientechnologies.orient.core.id.ORID;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestNetworkQueryService extends TestNdexService
{
    private static final NetworkService _networkService = new NetworkService(_mockRequest);
    
    
    @Test
    public void queryNetwork()
    {
        try
        {
            final ORID networkRid = getRid("Glucocorticoid_receptor_regulatory_network");
            
            final NetworkQueryParameters queryParameters = new NetworkQueryParameters();
            queryParameters.getStartingTermStrings().add("P04150");
            queryParameters.setRepresentationCriterion("STRICT");
            queryParameters.setSearchType("BOTH");
            queryParameters.setSearchDepth(1);
            
     //       Network result = _networkService.queryNetwork(IdConverter.toJid(networkRid), queryParameters);
     //       System.out.println("Network has " + result.getEdgeCount() + " edges");
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Test
    public void queryNetwork2()
    {
        try
        {
            final ORID networkRid = getRid("Glucocorticoid_receptor_regulatory_network");
            
            final NetworkQueryParameters queryParameters = new NetworkQueryParameters();
            queryParameters.getStartingTermStrings().add("P04150");
            queryParameters.setRepresentationCriterion("STRICT");
            queryParameters.setSearchType("BOTH");
            queryParameters.setSearchDepth(1);
            
            // queryNetwork2 has been removed and replaced by the augmented version of queryNetwork
            // Network result = _networkService.queryNetwork2(IdConverter.toJid(networkRid), queryParameters);

     //       Network result = _networkService.queryNetwork(IdConverter.toJid(networkRid), queryParameters);
     //       System.out.println("Network has " + result.getEdgeCount() + " edges");
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
    }

    @Test
    public void queryNetwork2Alias()
    {
        try
        {
            final ORID networkRid = getRid("Glucocorticoid_receptor_regulatory_network");
            
            final NetworkQueryParameters queryParameters = new NetworkQueryParameters();
            queryParameters.getStartingTermStrings().add("NR3C1");
            queryParameters.setRepresentationCriterion("PERMISSIVE");
            queryParameters.setSearchType("BOTH");
            queryParameters.setSearchDepth(1);
            
          //Network result = _networkService.queryNetwork2(IdConverter.toJid(networkRid), queryParameters);
          //  Network result = _networkService.queryNetwork(IdConverter.toJid(networkRid), queryParameters);
            
          //  System.out.println("Network has " + result.getEdgeCount() + " edges");
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Test
    public void queryNetwork2BEL()
    {
        try
        {
            final ORID networkRid = getRid("BEL Framework Large Corpus Document");
            
            final NetworkQueryParameters queryParameters = new NetworkQueryParameters();
            queryParameters.getStartingTermStrings().add("NR3C1");
            queryParameters.setRepresentationCriterion("PERMISSIVE");
            queryParameters.setSearchType("BOTH");
            queryParameters.setSearchDepth(1);
            
            //Network result = _networkService.queryNetwork2(IdConverter.toJid(networkRid), queryParameters);
   //         Network result = _networkService.queryNetwork(IdConverter.toJid(networkRid), queryParameters);
   //         System.out.println("Network has " + result.getEdgeCount() + " edges");
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void queryNetworkInvalid() throws IllegalArgumentException, NdexException
    {
  //      _networkService.queryNetwork("", null);
    }

}
