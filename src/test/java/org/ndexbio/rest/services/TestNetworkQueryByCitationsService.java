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
public class TestNetworkQueryByCitationsService extends TestNdexService
{
    private static final NetworkService _networkService = new NetworkService(_mockRequest);
    
    
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
 

}
