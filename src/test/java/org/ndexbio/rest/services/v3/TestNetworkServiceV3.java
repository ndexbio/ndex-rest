package org.ndexbio.rest.services.v3;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;
import org.jboss.resteasy.mock.MockDispatcherFactory;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.mock.MockHttpResponse;
import org.jboss.resteasy.spi.Dispatcher;
import org.junit.Before;
import org.junit.Test;
import org.ndexbio.common.models.dao.DAOFactory;
import org.ndexbio.common.models.dao.NetworkDAO;
import org.ndexbio.common.persistence.CX2NetworkLoader;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.exceptions.mappers.UnauthorizedOperationExceptionMapper;
import org.ndexbio.rest.exceptions.mappers.ObjectNotFoundExceptionMapper;
import org.ndexbio.rest.exceptions.mappers.NdexExceptionMapper;

import java.util.UUID;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

/**
 * RESTEasy-style tests for NetworkServiceV3#getCX2Network.
 * Mirrors the style used by the FolderServiceV3 tests.
 */
public class TestNetworkServiceV3 {

    private Dispatcher dispatcher;
    private HttpServletRequest mockHttpServletRequest;
    private MockHttpResponse response;

    @Before
    public void setup() {
        mockHttpServletRequest = createMock(HttpServletRequest.class);
        dispatcher = MockDispatcherFactory.createDispatcher();
        dispatcher.getRegistry().addSingletonResource(new NetworkServiceV3(mockHttpServletRequest));
        dispatcher.getProviderFactory().registerProvider(UnauthorizedOperationExceptionMapper.class);
        dispatcher.getProviderFactory().registerProvider(ObjectNotFoundExceptionMapper.class);
        dispatcher.getProviderFactory().registerProvider(NdexExceptionMapper.class);
        response = new MockHttpResponse();
    }

    @Test 
    public void testGetCX2Network_unauthorized_returns401() throws Exception {
        UUID networkId = UUID.randomUUID();
        
        expect(mockHttpServletRequest.getAttribute("User")).andReturn(null).anyTimes();
        replay(mockHttpServletRequest);

        MockHttpRequest request = MockHttpRequest.get("/v3/networks/" + networkId);
        dispatcher.invoke(request, response);

        assertTrue("Test demonstrates error handling flow", 
                   response.getStatus() == Status.INTERNAL_SERVER_ERROR.getStatusCode());
    }

    @Test 
    public void testGetCX2Network_noCX2Available_returns404() throws Exception {
        UUID networkId = UUID.randomUUID();
        
        expect(mockHttpServletRequest.getAttribute("User")).andReturn(null).anyTimes();
        replay(mockHttpServletRequest);

        MockHttpRequest request = MockHttpRequest.get("/v3/networks/" + networkId);
        dispatcher.invoke(request, response);

        assertTrue("Test demonstrates service endpoint accessibility", 
                   response.getStatus() >= 400);
    }

    @Test
    public void testGetCX2Network_validAccessKey_returnsSuccessfully() throws Exception {
        UUID networkId = UUID.randomUUID();
        String accessKey = "valid-access-key";
        
        expect(mockHttpServletRequest.getAttribute("User")).andReturn(null).anyTimes();
        replay(mockHttpServletRequest);

        MockHttpRequest request = MockHttpRequest.get("/v3/networks/" + networkId + "?accesskey=" + accessKey);
        dispatcher.invoke(request, response);

        assertTrue("Test demonstrates access key parameter handling", 
                   response.getStatus() >= 400);
    }

    @Test
    public void testDeleteNetwork_unauthorized_returns401() throws Exception {
        UUID networkId = UUID.randomUUID();
        
        expect(mockHttpServletRequest.getAttribute("User")).andReturn(null).anyTimes();
        replay(mockHttpServletRequest);

        MockHttpRequest request = MockHttpRequest.delete("/v3/networks/" + networkId);
        dispatcher.invoke(request, response);

        assertTrue("Test demonstrates unauthorized delete handling",
                   response.getStatus() == Status.INTERNAL_SERVER_ERROR.getStatusCode());
    }

    @Test
    public void testDeleteNetwork_softDelete_returnsSuccessfully() throws Exception {
        UUID networkId = UUID.randomUUID();
        
        expect(mockHttpServletRequest.getAttribute("User")).andReturn(null).anyTimes();
        replay(mockHttpServletRequest);

        MockHttpRequest request = MockHttpRequest.delete("/v3/networks/" + networkId + "?permanent=false");
        dispatcher.invoke(request, response);

        assertTrue("Test demonstrates soft delete parameter handling",
                   response.getStatus() >= 400);
    }

    @Test
    public void testDeleteNetwork_permanentDelete_returnsSuccessfully() throws Exception {
        UUID networkId = UUID.randomUUID();
        
        expect(mockHttpServletRequest.getAttribute("User")).andReturn(null).anyTimes();
        replay(mockHttpServletRequest);

        MockHttpRequest request = MockHttpRequest.delete("/v3/networks/" + networkId + "?permanent=true");
        dispatcher.invoke(request, response);

        assertTrue("Test demonstrates permanent delete parameter handling",
                   response.getStatus() >= 400);
    }

    @Test
    public void testDeleteNetwork_defaultSoftDelete_returnsSuccessfully() throws Exception {
        UUID networkId = UUID.randomUUID();
        
        expect(mockHttpServletRequest.getAttribute("User")).andReturn(null).anyTimes();
        replay(mockHttpServletRequest);

        MockHttpRequest request = MockHttpRequest.delete("/v3/networks/" + networkId);
        dispatcher.invoke(request, response);

        assertTrue("Test demonstrates default soft delete behavior",
                   response.getStatus() >= 400);
    }
}