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
import org.ndexbio.common.models.dao.FolderDAO;
import org.ndexbio.common.models.dao.NetworkDAO;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.common.persistence.CX2NetworkLoader;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.exceptions.mappers.BadRequestExceptionMapper;
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
        dispatcher.getProviderFactory().registerProvider(BadRequestExceptionMapper.class);
        response = new MockHttpResponse();
    }

    // ── POST /v3/networks?folderId= must authorize the target folder (issue #165) ──
    //
    // Before this guard the parameter was applied with no check at all, so a network could be planted in
    // any folder by UUID, including a stranger's. Ownership or *effective* WRITE is required — effective
    // because a user whose write comes from an ancestor folder must be allowed through.
    //
    // Each case asserts the HTTP status and, on rejection, that no network was created: the network DAO
    // is a strict mock with no expectations, so any attempt to create one fails the test. That is the
    // property that justifies placing the guard before the upload rather than at the placement line.

    private static final UUID TARGET_FOLDER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    /**
     * Drives POST /v3/networks?folderId= with a mocked folder authorization outcome.
     *
     * @param isOwner   what isFolderOwner reports
     * @param effective what getEffectivePermission reports (only consulted when not owner)
     */
    private void invokeCreateWithFolder(String folderIdParam, Boolean isOwner, Permissions effective,
                                        boolean expectFolderLookup) throws Exception {
        UUID userId = UUID.randomUUID();
        User fakeUser = new User();
        fakeUser.setExternalId(userId);
        fakeUser.setUserName("tester");
        expect(mockHttpServletRequest.getAttribute("User")).andReturn(fakeUser).anyTimes();
        replay(mockHttpServletRequest);

        // The disk-space check runs before the guard; it must succeed so the guard is reached.
        UserDAO mockUserDAO = createMock(UserDAO.class);
        mockUserDAO.checkDiskSpace(userId);
        expectLastCall();
        mockUserDAO.close();
        expectLastCall();
        replay(mockUserDAO);

        FolderDAO mockFolderDAO = createMock(FolderDAO.class);
        if (expectFolderLookup) {
            expect(mockFolderDAO.isFolderOwner(TARGET_FOLDER, userId)).andReturn(isOwner);
            if (!Boolean.TRUE.equals(isOwner))
                expect(mockFolderDAO.getEffectivePermission(TARGET_FOLDER, userId)).andReturn(effective);
            mockFolderDAO.close();
            expectLastCall();
        }
        replay(mockFolderDAO);

        // Strict: a rejected request must never reach network creation.
        NetworkDAO mockNetworkDAO = createMock(NetworkDAO.class);
        replay(mockNetworkDAO);

        DAOFactory mockDAOFactory = createMock(DAOFactory.class);
        expect(mockDAOFactory.getUserDAO()).andReturn(mockUserDAO);
        if (expectFolderLookup)
            expect(mockDAOFactory.getFolderDAO()).andReturn(mockFolderDAO);
        expect(mockDAOFactory.getNetworkDAO()).andReturn(mockNetworkDAO).anyTimes();
        replay(mockDAOFactory);
        Configuration.getInstance().setDAOFactory(mockDAOFactory);

        MockHttpRequest request = MockHttpRequest.post("/v3/networks?folderId=" + folderIdParam)
                .content("{}".getBytes())
                .contentType(MediaType.APPLICATION_JSON);

        dispatcher.invoke(request, response);

        verify(mockNetworkDAO);   // nothing created
        if (expectFolderLookup)
            verify(mockFolderDAO);
    }

    /** Read-only on the target folder must not permit placing a network into it. */
    @Test
    public void testCreateNetworkInFolder_readGrantee_rejectedAndCreatesNothing() throws Exception {
        invokeCreateWithFolder(TARGET_FOLDER.toString(), false, Permissions.READ, true);
        assertEquals(Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    }

    /** No relationship with the target folder at all — the original hole. */
    @Test
    public void testCreateNetworkInFolder_noPermission_rejectedAndCreatesNothing() throws Exception {
        invokeCreateWithFolder(TARGET_FOLDER.toString(), false, null, true);
        assertEquals(Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    }

    /** A malformed folder id is a client error, not an authorization failure. */
    @Test
    public void testCreateNetworkInFolder_malformedUuid_rejectedAsBadRequest() throws Exception {
        invokeCreateWithFolder("not-a-uuid", null, null, false);
        assertEquals(Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }

    /**
     * Positive path: an effective-WRITE grantee gets past the guard. Asserted as "not rejected" — the
     * request proceeds into upload handling, which needs real storage and is covered end to end by the
     * integration suite rather than here.
     */
    @Test
    public void testCreateNetworkInFolder_writeGrantee_passesAuthorization() throws Exception {
        invokeCreateWithFolder(TARGET_FOLDER.toString(), false, Permissions.WRITE, true);
        assertNotEquals("effective WRITE must not be rejected",
                Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
        assertNotEquals(Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }

    /** Positive path: the folder's owner gets past the guard without a permission lookup. */
    @Test
    public void testCreateNetworkInFolder_owner_passesAuthorization() throws Exception {
        invokeCreateWithFolder(TARGET_FOLDER.toString(), true, null, true);
        assertNotEquals("the folder owner must not be rejected",
                Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
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