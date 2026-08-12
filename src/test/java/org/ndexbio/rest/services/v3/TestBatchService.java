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
import org.junit.BeforeClass;
import org.junit.Test;
import org.ndexbio.common.models.dao.DAOFactory;
import org.ndexbio.common.models.dao.FolderDAO;
import org.ndexbio.common.models.dao.NetworkDAO;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.MoveNetworksRequest;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.User;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.TestConfigHelper;
import org.ndexbio.rest.exceptions.mappers.BadRequestExceptionMapper;
import org.ndexbio.rest.exceptions.mappers.NdexExceptionMapper;
import org.ndexbio.rest.exceptions.mappers.UnauthorizedOperationExceptionMapper;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.UUID;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Authorization on {@code POST /v3/batch/networks/move} (issue #165).
 *
 * <p>Owning a network authorizes taking it <em>out</em> of where it is; it says nothing about writing it
 * <em>into</em> the destination. Before this guard the target folder was not checked at all, so a network
 * could be moved into any folder by UUID. Ownership or effective WRITE on the target is now required —
 * "effective" so a user whose write comes from an ancestor folder is allowed through.</p>
 *
 * <p>Each rejection also asserts nothing was moved: the network DAO is a strict mock with no
 * expectations, so any attempt to reparent fails the test.</p>
 */
public class TestBatchService {

    private static final UUID TARGET_FOLDER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID NETWORK = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private Dispatcher dispatcher;
    private HttpServletRequest mockHttpServletRequest;
    private MockHttpResponse response;

    @BeforeClass
    public static void initConfiguration() throws Exception {
        TestConfigHelper.initIfNeeded();
    }

    @Before
    public void setup() {
        mockHttpServletRequest = createMock(HttpServletRequest.class);
        dispatcher = MockDispatcherFactory.createDispatcher();
        dispatcher.getRegistry().addSingletonResource(new BatchServiceNoIndex(mockHttpServletRequest));
        dispatcher.getProviderFactory().registerProvider(UnauthorizedOperationExceptionMapper.class);
        dispatcher.getProviderFactory().registerProvider(NdexExceptionMapper.class);
        dispatcher.getProviderFactory().registerProvider(BadRequestExceptionMapper.class);
        response = new MockHttpResponse();
    }

    /**
     * Drives the move endpoint with a mocked authorization outcome for the target folder.
     *
     * @param isOwner        what isFolderOwner reports for the target
     * @param effective      what getEffectivePermission reports (consulted only when not owner)
     * @param expectTheMove  true when the request should get far enough to reparent the network
     */
    private void invokeMove(Boolean isOwner, Permissions effective, boolean expectTheMove) throws Exception {
        UUID userId = UUID.randomUUID();
        User fakeUser = new User();
        fakeUser.setExternalId(userId);
        fakeUser.setUserName("tester");
        expect(mockHttpServletRequest.getAttribute("User")).andReturn(fakeUser).anyTimes();
        replay(mockHttpServletRequest);

        FolderDAO mockFolderDAO = createMock(FolderDAO.class);
        expect(mockFolderDAO.isFolderOwner(TARGET_FOLDER, userId)).andReturn(isOwner);
        if (!Boolean.TRUE.equals(isOwner))
            expect(mockFolderDAO.getEffectivePermission(TARGET_FOLDER, userId)).andReturn(effective);
        mockFolderDAO.close();
        expectLastCall();
        replay(mockFolderDAO);

        NetworkDAO mockNetworkDAO = createMock(NetworkDAO.class);
        if (expectTheMove) {
            expect(mockNetworkDAO.isAdmin(NETWORK, userId)).andReturn(true);
            mockNetworkDAO.setNetworkFolder(NETWORK, TARGET_FOLDER);
            expectLastCall();
            expect(mockNetworkDAO.getNetworkVisibility(NETWORK)).andReturn(VisibilityType.PRIVATE);
            mockNetworkDAO.commit();
            expectLastCall();
            mockNetworkDAO.close();
            expectLastCall();
        }
        replay(mockNetworkDAO);   // strict: no reparent may happen on a rejected request

        DAOFactory mockDAOFactory = createMock(DAOFactory.class);
        expect(mockDAOFactory.getFolderDAO()).andReturn(mockFolderDAO);
        expect(mockDAOFactory.getNetworkDAO()).andReturn(mockNetworkDAO).anyTimes();
        replay(mockDAOFactory);
        Configuration.getInstance().setDAOFactory(mockDAOFactory);

        MoveNetworksRequest request = new MoveNetworksRequest();
        request.setTargetFolder(TARGET_FOLDER);
        request.setNetworks(Arrays.asList(NETWORK));

        MockHttpRequest httpRequest = MockHttpRequest.post("/v3/batch/networks/move")
                .content(new ObjectMapper().writeValueAsBytes(request))
                .contentType(MediaType.APPLICATION_JSON);

        dispatcher.invoke(httpRequest, response);

        verify(mockNetworkDAO, mockFolderDAO);
    }

    /** Read-only on the destination must not permit moving networks into it. */
    @Test
    public void testMove_readGranteeOnTarget_rejectedAndMovesNothing() throws Exception {
        invokeMove(false, Permissions.READ, false);
        assertEquals(Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    }

    /** No relationship with the destination at all — the original hole. */
    @Test
    public void testMove_noPermissionOnTarget_rejectedAndMovesNothing() throws Exception {
        invokeMove(false, null, false);
        assertEquals(Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    }

    /** Effective WRITE on the destination is sufficient, including when inherited. */
    @Test
    public void testMove_writeGranteeOnTarget_permittedAndMovesTheNetwork() throws Exception {
        invokeMove(false, Permissions.WRITE, true);
        assertNotEquals("effective WRITE on the target must be permitted",
                Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    }

    /** The destination's owner is permitted without a permission lookup. */
    @Test
    public void testMove_ownerOfTarget_permittedAndMovesTheNetwork() throws Exception {
        invokeMove(true, null, true);
        assertNotEquals("the target folder's owner must be permitted",
                Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    }

    /**
     * Reindexing is a side effect of a successful move; stubbed so tests need no Solr. The terminal
     * seven-argument overload is the one stubbed, since the shorter overloads delegate to it.
     */
    private static class BatchServiceNoIndex extends BatchService {
        BatchServiceNoIndex(HttpServletRequest request) {
            super(request);
        }

        @Override
        protected void createFileIndex(UUID fileId, UUID userId, String username, VisibilityType visibility,
                                       FileType fileType, boolean createOnly, boolean ignoreCxFiles) {
            // no-op for testing
        }
    }
}
