package org.ndexbio.rest.services;

import java.util.Collection;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.ndexbio.rest.exceptions.DuplicateObjectException;
import org.ndexbio.rest.exceptions.NdexException;
import org.ndexbio.rest.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.helpers.RidConverter;
import org.ndexbio.rest.models.Network;
import org.ndexbio.rest.models.NetworkQueryParameters;
import org.ndexbio.rest.models.SearchParameters;
import com.orientechnologies.orient.core.id.ORID;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestNetworkService extends TestNdexService
{
    private static final NetworkService _networkService = new NetworkService(_mockRequest);
    
    
    
    
    @Test
    public void autoSuggest()
    {
        try
        {
            final ORID testNetworkRid = getRid("REACTOME TEST");
            final Collection<String> suggestions = _networkService.autoSuggestTerms(RidConverter.convertToJid(testNetworkRid), "RBL");
            Assert.assertNotNull(suggestions);
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void autoSuggestInvalidNetwork() throws IllegalArgumentException, NdexException
    {
        _networkService.autoSuggestTerms("", "RBL");
    }

    @Test(expected = IllegalArgumentException.class)
    public void autoSuggestInvalidPartialTerm() throws IllegalArgumentException, NdexException
    {
        final ORID testNetworkRid = getRid("REACTOME TEST");
        _networkService.autoSuggestTerms(RidConverter.convertToJid(testNetworkRid), "");
    }

    @Test
    public void createNetwork()
    {
        Assert.assertTrue(createNewNetwork());
    }

    @Test(expected = DuplicateObjectException.class)
    public void createNetworkDuplicate() throws IllegalArgumentException, DuplicateObjectException, NdexException
    {
        Assert.assertTrue(createNewNetwork());
        
        final Network newNetwork = new Network();
        newNetwork.setCopyright("2013 Cytoscape Consortium");
        newNetwork.setDescription("This is a test network.");
        newNetwork.setFormat("JDEX");
        newNetwork.setTitle("Test Network");
        
        _networkService.createNetwork(newNetwork);
    }

    @Test(expected = IllegalArgumentException.class)
    public void createNetworkInvalid() throws IllegalArgumentException, DuplicateObjectException, NdexException
    {
        _networkService.createNetwork(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void createNetworkInvalidTitle() throws IllegalArgumentException, DuplicateObjectException, NdexException
    {
        final Network newNetwork = new Network();
        newNetwork.setCopyright("2013 Cytoscape Consortium");
        newNetwork.setDescription("This is a test network.");
        newNetwork.setFormat("JDEX");
        
        _networkService.createNetwork(newNetwork);
    }

    @Test
    public void deleteNetwork()
    {
        Assert.assertTrue(createNewNetwork());

        final ORID testNetworkRid = getRid("Test Network");
        Assert.assertTrue(deleteTargetNetwork(RidConverter.convertToJid(testNetworkRid)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void deleteNetworkInvalid() throws IllegalArgumentException, ObjectNotFoundException, SecurityException, NdexException
    {
        _networkService.deleteNetwork(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void deleteNetworkNonexistant() throws IllegalArgumentException, ObjectNotFoundException, SecurityException, NdexException
    {
        _networkService.deleteNetwork("C999R999");
    }

    @Test
    public void findNetworks()
    {
        final SearchParameters searchParameters = new SearchParameters();
        searchParameters.setSearchString("reactome");
        searchParameters.setSkip(0);
        searchParameters.setTop(25);
        
        try
        {
            _networkService.findNetworks(searchParameters);
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void findNetworksInvalid() throws IllegalArgumentException, NdexException
    {
        _networkService.findNetworks(null);
    }

    @Test
    public void getNetwork()
    {
        try
        {
            final ORID networkRid = getRid("NCI_NATURE:FoxO family signaling");
            final Network testNetwork = _networkService.getNetwork(RidConverter.convertToJid(networkRid));
            Assert.assertNotNull(testNetwork);
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void getNetworkInvalid() throws IllegalArgumentException, NdexException
    {
        _networkService.getNetwork("");
    }

    @Test
    public void getEdges()
    {
        try
        {
            final ORID networkRid = getRid("NCI_NATURE:FoxO family signaling");
            _networkService.getEdges(RidConverter.convertToJid(networkRid), 0, 25);
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void getEdgesInvalid() throws IllegalArgumentException, NdexException
    {
        _networkService.getEdges("", 0, 25);
    }

    @Test
    public void queryNetwork()
    {
        try
        {
            final ORID networkRid = getRid("NCI_NATURE:FoxO family signaling");
            
            final NetworkQueryParameters queryParameters = new NetworkQueryParameters();
            queryParameters.getStartingTermStrings().add("RBL_HUMAN");
            
            _networkService.queryNetwork(RidConverter.convertToJid(networkRid), queryParameters);
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
        _networkService.queryNetwork("", null);
    }

    @Test
    public void updateNetwork()
    {
        try
        {
            Assert.assertTrue(createNewNetwork());

            //Refresh the user or the system won't know they have access to
            //update the network
            this.resetLoggedInUser();
            this.setLoggedInUser();

            final ORID testNetworkRid = getRid("Test Network");
            final Network testNetwork = _networkService.getNetwork(RidConverter.convertToJid(testNetworkRid));

            testNetwork.setTitle("Updated Test Network");
            _networkService.updateNetwork(testNetwork);
            Assert.assertEquals(_networkService.getNetwork(testNetwork.getId()).getTitle(), testNetwork.getTitle());

            Assert.assertTrue(deleteTargetNetwork(testNetwork.getId()));
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void updateNetworkInvalid() throws IllegalArgumentException, ObjectNotFoundException, SecurityException, NdexException
    {
        _networkService.updateNetwork(null);
    }
    
    
    
    private boolean createNewNetwork()
    {
        final Network newNetwork = new Network();
        newNetwork.setCopyright("2013 Cytoscape Consortium");
        newNetwork.setDescription("This is a test network.");
        newNetwork.setFormat("JDEX");
        newNetwork.setTitle("Test Network");
        
        try
        {
            final Network createdNetwork = _networkService.createNetwork(newNetwork);
            Assert.assertNotNull(createdNetwork);
            
            return true;
        }
        catch (DuplicateObjectException doe)
        {
            return true;
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
        
        return false;
    }
    
    private boolean deleteTargetNetwork(String networkId)
    {
        try
        {
            _networkService.deleteNetwork(networkId);
            Assert.assertNull(_networkService.getNetwork(networkId));
            
            return true;
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
        
        return false;
    }
}
