package org.ndexbio.rest.services;

import javax.servlet.http.HttpServletRequest;
import org.easymock.EasyMock;
import org.junit.Test;

public class TestNetworkService
{
    private static final HttpServletRequest _mockRequest = EasyMock.createMock(HttpServletRequest.class);
    private static final NetworkService _networkService = new NetworkService(_mockRequest);

    
    
    @Test
    public void autoSuggest()
    {
    }

    @Test
    public void autoSuggestInvalid()
    {
    }

    @Test
    public void createNetwork()
    {
    }

    @Test
    public void createNetworkInvalid()
    {
    }

    @Test
    public void deleteNetwork()
    {
    }

    @Test
    public void deleteNetworkInvalid()
    {
    }

    @Test
    public void findNetworks()
    {
    }

    @Test
    public void findNetworksInvalid()
    {
    }

    @Test
    public void getNetwork()
    {
    }

    @Test
    public void getNetworkInvalid()
    {
    }

    @Test
    public void getEdges()
    {
    }

    @Test
    public void getEdgesInvalid()
    {
    }

    @Test
    public void queryNetwork()
    {
    }

    @Test
    public void queryNetworkInvalid()
    {
    }

    @Test
    public void updateNetwork()
    {
    }

    @Test
    public void updateNetworkInvalid()
    {
    }
}
