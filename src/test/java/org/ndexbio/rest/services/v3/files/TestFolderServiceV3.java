package org.ndexbio.rest.services.v3.files;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response.Status;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.TestConfigHelper;
import org.jboss.resteasy.mock.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Before;
import org.jboss.resteasy.spi.Dispatcher;
import org.ndexbio.common.models.dao.DAOFactory;
import org.ndexbio.common.models.dao.FolderDAO;
import org.ndexbio.model.object.FileCount;
import org.ndexbio.model.object.FileItemSummary;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.FolderRequest;
import org.ndexbio.model.object.NdexObjectUpdateStatus;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.exceptions.mappers.BadRequestExceptionMapper;
import org.ndexbio.rest.exceptions.mappers.UnauthorizedOperationExceptionMapper;
import jakarta.ws.rs.core.MediaType;

import static org.easymock.EasyMock.*;

public class TestFolderServiceV3 {

    private Dispatcher dispatcher;
    private HttpServletRequest mockHttpServletRequest;
    private MockHttpResponse response;

    @BeforeClass
    public static void initConfiguration() throws Exception {
        TestConfigHelper.initIfNeeded();
    }

    @Before
    public void before() {
        mockHttpServletRequest = createMock(HttpServletRequest.class);
        dispatcher = MockDispatcherFactory.createDispatcher();
        dispatcher.getRegistry().addSingletonResource(new TestFolderServiceV3NoIndex(mockHttpServletRequest));
        dispatcher.getProviderFactory().registerProvider(UnauthorizedOperationExceptionMapper.class);
        dispatcher.getProviderFactory().registerProvider(BadRequestExceptionMapper.class);
        response = new MockHttpResponse();
    }
    
    /**
     * Issue #163 removed GET /v3/files/folders. POST "/" still exists on this resource, so the bare
     * path must answer 405 -- and critically must NOT fall through to @Path("/{folderid}") with an
     * empty folderid, which would reach UUID.fromString("") and surface as a 500.
     */
    @Test
    public void testBareFolderListGetNoLongerRouted() throws Exception {
        expect(mockHttpServletRequest.getAttribute("User")).andReturn(null).anyTimes();
        replay(mockHttpServletRequest);

        for (String path : new String[] { "/v3/files/folders/", "/v3/files/folders" }) {
            MockHttpResponse resp = new MockHttpResponse();
            dispatcher.invoke(MockHttpRequest.get(path), resp);

            assertEquals("GET " + path + " must be 405, not 200 (endpoint gone) and not 500 "
                    + "(empty-segment fallthrough into getFolder)",
                    Status.METHOD_NOT_ALLOWED.getStatusCode(), resp.getStatus());
            assertFalse("GET " + path + " must not return a folder body",
                    new String(resp.getOutput()).contains("externalId"));
        }
    }

    @Test
    public void testCreateFolderRequestNull() throws Exception {
        expect(mockHttpServletRequest.getAttribute("User")).andReturn(null).anyTimes();
        replay(mockHttpServletRequest);

        MockHttpRequest request = MockHttpRequest.post("/v3/files/folders/")
                .contentType(MediaType.APPLICATION_JSON);
        dispatcher.invoke(request, response);

        assertEquals(Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }

    @Test
    public void testCreateFolderSuccess() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setExternalId(userId);

        expect(mockHttpServletRequest.getAttribute("User")).andReturn(user).anyTimes();
        replay(mockHttpServletRequest);

        UUID folderId = UUID.randomUUID();
        FolderRequest folderRequest = new FolderRequest();
        folderRequest.setName("Test Folder");

        NdexObjectUpdateStatus status = new NdexObjectUpdateStatus();
        status.setUuid(folderId);

        FolderDAO mockFolderDAO = createMock(FolderDAO.class);
        expect(mockFolderDAO.createFolder(isA(UUID.class), eq(userId), isNull(), eq("Test Folder"), isNull())).andReturn(status);
        mockFolderDAO.commit();
        expectLastCall();
        mockFolderDAO.close();
        expectLastCall();
        replay(mockFolderDAO);

        DAOFactory mockFactory = createMock(DAOFactory.class);
        expect(mockFactory.getFolderDAO()).andReturn(mockFolderDAO);
        replay(mockFactory);

        Configuration.getInstance().setDAOFactory(mockFactory);

        ObjectMapper mapper = new ObjectMapper();
        byte[] requestBytes = mapper.writeValueAsBytes(folderRequest);

        MockHttpRequest request = MockHttpRequest.post("/v3/files/folders/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBytes);

        dispatcher.invoke(request, response);

        assertEquals(Status.CREATED.getStatusCode(), response.getStatus());
    }
    
    @Test
    public void testGetFolderUnauthorized() throws Exception {
        UUID folderId = UUID.randomUUID();

        expect(mockHttpServletRequest.getAttribute("User")).andReturn(null).anyTimes();
        replay(mockHttpServletRequest);

        FolderDAO folderDAO = createMock(FolderDAO.class);
        expect(folderDAO.isReadable(folderId, null)).andReturn(false);
        expect(folderDAO.accessKeyIsValid(folderId, null)).andReturn(false);
        folderDAO.close();
        expectLastCall();
        replay(folderDAO);

        DAOFactory daoFactory = createMock(DAOFactory.class);
        expect(daoFactory.getFolderDAO()).andReturn(folderDAO);
        replay(daoFactory);

        Configuration.getInstance().setDAOFactory(daoFactory);

        MockHttpRequest request = MockHttpRequest.get("/v3/files/folders/" + folderId);
        dispatcher.invoke(request, response);
        
        assertEquals(Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    }

    
    @Test
    public void testGetFolderSuccess() throws Exception {
        UUID folderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setExternalId(userId);

        expect(mockHttpServletRequest.getAttribute("User")).andReturn(user).anyTimes();
        replay(mockHttpServletRequest);

        org.ndexbio.model.object.NdexFolder folder = new org.ndexbio.model.object.NdexFolder();
        folder.setName("Folder Success");
        folder.setExternalId(folderId);

        FolderDAO folderDAO = createMock(FolderDAO.class);
        expect(folderDAO.isReadable(folderId, userId)).andReturn(true);
        expect(folderDAO.accessKeyIsValid(folderId, null)).andReturn(false);
        expect(folderDAO.getFolder(folderId, userId, null)).andReturn(folder);
        folderDAO.close();
        expectLastCall();
        replay(folderDAO);

        DAOFactory daoFactory = createMock(DAOFactory.class);
        expect(daoFactory.getFolderDAO()).andReturn(folderDAO);
        replay(daoFactory);

        Configuration.getInstance().setDAOFactory(daoFactory);

        MockHttpRequest request = MockHttpRequest.get("/v3/files/folders/" + folderId);
        dispatcher.invoke(request, response);

        assertEquals(Status.OK.getStatusCode(), response.getStatus());

        ObjectMapper mapper = new ObjectMapper();
        org.ndexbio.model.object.NdexFolder result = mapper.readValue(response.getOutput(), org.ndexbio.model.object.NdexFolder.class);
        assertEquals("Folder Success", result.getName());
        assertEquals(folderId, result.getExternalId());
    }


    
    
    @Test
    public void testDeleteFolderUnauthorized() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        User user = new User();
        user.setExternalId(userId);

        expect(mockHttpServletRequest.getAttribute("User")).andReturn(user);
        replay(mockHttpServletRequest);

        FolderDAO folderDAO = createMock(FolderDAO.class);
        expect(folderDAO.isFolderOwner(folderId, userId)).andReturn(false);
        folderDAO.close();
        expectLastCall();
        replay(folderDAO);

        DAOFactory daoFactory = createMock(DAOFactory.class);
        expect(daoFactory.getFolderDAO()).andReturn(folderDAO);
        replay(daoFactory);

        Configuration.getInstance().setDAOFactory(daoFactory);

        MockHttpRequest request = MockHttpRequest.delete("/v3/files/folders/" + folderId);
        dispatcher.invoke(request, response);

        assertEquals(Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    }
    
    @Test
    public void testUpdateFolderUnauthorized() throws Exception {
        UUID folderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setExternalId(userId);

        expect(mockHttpServletRequest.getAttribute("User")).andReturn(user).times(2);
        replay(mockHttpServletRequest);

        FolderRequest requestBody = new FolderRequest();
        requestBody.setName("Unauthorized Folder Update");

        FolderDAO folderDAO = createMock(FolderDAO.class);
        expect(folderDAO.isFolderOwner(folderId, userId)).andReturn(false);
        folderDAO.close();
        expectLastCall();
        replay(folderDAO);

        DAOFactory daoFactory = createMock(DAOFactory.class);
        expect(daoFactory.getFolderDAO()).andReturn(folderDAO);
        replay(daoFactory);

        Configuration.getInstance().setDAOFactory(daoFactory);

        ObjectMapper mapper = new ObjectMapper();
        byte[] requestJson = mapper.writeValueAsBytes(requestBody);

        MockHttpRequest request = MockHttpRequest.put("/v3/files/folders/" + folderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson);

        dispatcher.invoke(request, response);

        assertEquals(Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    }

    
    @Test
    public void testUpdateFolderSuccess() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        User user = new User();
        user.setExternalId(userId);

        expect(mockHttpServletRequest.getAttribute("User")).andReturn(user).times(2);
        replay(mockHttpServletRequest);

        FolderRequest requestBody = new FolderRequest();
        requestBody.setName("Updated Folder Name");

        FolderDAO folderDAO = createMock(FolderDAO.class);
        expect(folderDAO.isFolderOwner(folderId, userId)).andReturn(true);
        folderDAO.updateFolder(eq(folderId), eq("Updated Folder Name"), isNull(), eq(userId), isNull());
        expect(folderDAO.getFolderVisibility(folderId)).andReturn(VisibilityType.PRIVATE);

        expectLastCall();
        folderDAO.commit();
        expectLastCall();
        folderDAO.close();
        expectLastCall();
        replay(folderDAO);

        DAOFactory daoFactory = createMock(DAOFactory.class);
        expect(daoFactory.getFolderDAO()).andReturn(folderDAO);
        replay(daoFactory);

        Configuration.getInstance().setDAOFactory(daoFactory);

        ObjectMapper mapper = new ObjectMapper();
        byte[] json = mapper.writeValueAsBytes(requestBody);

        MockHttpRequest request = MockHttpRequest.put("/v3/files/folders/" + folderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);

        dispatcher.invoke(request, response);
        assertEquals(Status.NO_CONTENT.getStatusCode(), response.getStatus());
    }
    
    @Test
    public void testGetFolderChildCountUnauthorized() throws Exception {
        UUID folderId = UUID.randomUUID();

        expect(mockHttpServletRequest.getAttribute("User")).andReturn(null).anyTimes();
        replay(mockHttpServletRequest);

        FolderDAO folderDAO = createMock(FolderDAO.class);
        expect(folderDAO.isReadable(folderId, null)).andReturn(false);
        expect(folderDAO.accessKeyIsValid(folderId, null)).andReturn(false);
        folderDAO.close();
        expectLastCall();
        replay(folderDAO);

        DAOFactory daoFactory = createMock(DAOFactory.class);
        expect(daoFactory.getFolderDAO()).andReturn(folderDAO);
        replay(daoFactory);

        Configuration.getInstance().setDAOFactory(daoFactory);

        dispatcher.getProviderFactory().registerProvider(UnauthorizedOperationExceptionMapper.class);
        dispatcher.getProviderFactory().registerProvider(BadRequestExceptionMapper.class);

        MockHttpRequest request = MockHttpRequest.get("/v3/files/folders/" + folderId + "/count");
        dispatcher.invoke(request, response);

        assertEquals(Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    }

    
    @Test
    public void testGetFolderChildCountSuccess() throws Exception {
        UUID folderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setExternalId(userId);

        expect(mockHttpServletRequest.getAttribute("User")).andReturn(user);
        replay(mockHttpServletRequest);

        FileCount count = new FileCount();
        count.setFolder(1);
        count.setNetwork(2);
        count.setShortcut(3);

        FolderDAO folderDAO = createMock(FolderDAO.class);
        expect(folderDAO.accessKeyIsValid(folderId, null)).andReturn(false);
        expect(folderDAO.isReadable(folderId, userId)).andReturn(true);
        // readable by the authenticated caller -> counts filtered to what they may see
        expect(folderDAO.getReadableFolderChildCounts(folderId, userId)).andReturn(count);
        folderDAO.close();
        expectLastCall();
        replay(folderDAO);

        DAOFactory daoFactory = createMock(DAOFactory.class);
        expect(daoFactory.getFolderDAO()).andReturn(folderDAO);
        replay(daoFactory);

        Configuration.getInstance().setDAOFactory(daoFactory);

        MockHttpRequest request = MockHttpRequest.get("/v3/files/folders/" + folderId + "/count");
        dispatcher.invoke(request, response);
        assertEquals(Status.OK.getStatusCode(), response.getStatus());
    }

    @Test
    public void testGetHomeChildCountUsesRootCounts() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setExternalId(userId);

        expect(mockHttpServletRequest.getAttribute("User")).andReturn(user).anyTimes();
        replay(mockHttpServletRequest);

        FileCount count = new FileCount();
        count.setFolder(4);
        count.setNetwork(5);
        count.setShortcut(6);

        FolderDAO folderDAO = createMock(FolderDAO.class);
        expect(folderDAO.getRootChildCountsOfUser(userId)).andReturn(count);
        folderDAO.close();
        expectLastCall();
        replay(folderDAO);

        DAOFactory daoFactory = createMock(DAOFactory.class);
        expect(daoFactory.getFolderDAO()).andReturn(folderDAO);
        replay(daoFactory);

        Configuration.getInstance().setDAOFactory(daoFactory);

        MockHttpRequest request = MockHttpRequest.get("/v3/files/folders/HoMe/count");
        dispatcher.invoke(request, response);

        assertEquals(Status.OK.getStatusCode(), response.getStatus());

        ObjectMapper mapper = new ObjectMapper();
        FileCount result = mapper.readValue(response.getOutput(), FileCount.class);
        assertEquals(count.getFolder(), result.getFolder());
        assertEquals(count.getNetwork(), result.getNetwork());
        assertEquals(count.getShortcut(), result.getShortcut());
    }

    @Test
    public void testGetHomeChildCountUnauthorizedDoesNotOpenDao() throws Exception {
        expect(mockHttpServletRequest.getAttribute("User")).andReturn(null).anyTimes();
        replay(mockHttpServletRequest);

        DAOFactory daoFactory = createMock(DAOFactory.class);
        replay(daoFactory);

        Configuration.getInstance().setDAOFactory(daoFactory);

        MockHttpRequest request = MockHttpRequest.get("/v3/files/folders/home/count");
        dispatcher.invoke(request, response);

        assertEquals(Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
        verify(daoFactory);
    }
    
    @Test
    public void testListItemsInFolderUnauthorized() throws Exception {
        UUID folderId = UUID.randomUUID();

        expect(mockHttpServletRequest.getAttribute("User")).andReturn(null).anyTimes();
        replay(mockHttpServletRequest);

        FolderDAO folderDAO = createMock(FolderDAO.class);
        expect(folderDAO.isReadable(folderId, null)).andReturn(false);
        expect(folderDAO.accessKeyIsValid(folderId, null)).andReturn(false);
        folderDAO.close();
        expectLastCall();
        replay(folderDAO);

        DAOFactory daoFactory = createMock(DAOFactory.class);
        expect(daoFactory.getFolderDAO()).andReturn(folderDAO);
        replay(daoFactory);

        Configuration.getInstance().setDAOFactory(daoFactory);

        dispatcher.getProviderFactory().registerProvider(UnauthorizedOperationExceptionMapper.class);
        dispatcher.getProviderFactory().registerProvider(BadRequestExceptionMapper.class);

        MockHttpRequest request = MockHttpRequest.get("/v3/files/folders/" + folderId + "/list");
        dispatcher.invoke(request, response);

        assertEquals(Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    }


    @Test
    public void testListItemsInFolderSuccess() throws Exception {
        UUID folderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setExternalId(userId);

        expect(mockHttpServletRequest.getAttribute("User")).andReturn(user).times(2);
        replay(mockHttpServletRequest);

        List<FileItemSummary> items = new ArrayList<>();
        items.add(new FileItemSummary(UUID.randomUUID(), FileType.NETWORK, "Net 1"));

        FolderDAO folderDAO = createMock(FolderDAO.class);
        expect(folderDAO.accessKeyIsValid(folderId, null)).andReturn(false);
        expect(folderDAO.isReadable(folderId, userId)).andReturn(true);
        // readable by the authenticated caller -> children filtered to what they may read
        expect(folderDAO.listReadableItemsInFolder(folderId, false, null, userId, 0, -1)).andReturn(items);
        folderDAO.close();
        expectLastCall();
        replay(folderDAO);

        DAOFactory daoFactory = createMock(DAOFactory.class);
        expect(daoFactory.getFolderDAO()).andReturn(folderDAO).times(2);
        replay(daoFactory);

        Configuration.getInstance().setDAOFactory(daoFactory);

        MockHttpRequest request = MockHttpRequest.get("/v3/files/folders/" + folderId + "/list");
        dispatcher.invoke(request, response);
        assertEquals(Status.OK.getStatusCode(), response.getStatus());

        ObjectMapper mapper = new ObjectMapper();
        FileItemSummary[] result = mapper.readValue(response.getOutput(), FileItemSummary[].class);
        assertEquals(1, result.length);
        assertEquals(FileType.NETWORK, result[0].getType());
    }

    @Test
    public void testListItemsInFolderViaAccessKeyUsesKeyFiltered() throws Exception {
        UUID folderId = UUID.randomUUID();

        // Anonymous caller presenting a valid folder access key.
        expect(mockHttpServletRequest.getAttribute("User")).andReturn(null).anyTimes();
        replay(mockHttpServletRequest);

        List<FileItemSummary> items = new ArrayList<>();
        items.add(new FileItemSummary(UUID.randomUUID(), FileType.NETWORK, "Net 1"));

        FolderDAO folderDAO = createMock(FolderDAO.class);
        // A valid access key -> key-filtered listing (folder/network children; shortcuts excluded).
        expect(folderDAO.accessKeyIsValid(folderId, "k")).andReturn(true).anyTimes();
        expect(folderDAO.listItemsInFolderKeyFiltered(folderId, false, null, 0, -1)).andReturn(items);
        folderDAO.close();
        expectLastCall().anyTimes();
        replay(folderDAO);

        DAOFactory daoFactory = createMock(DAOFactory.class);
        expect(daoFactory.getFolderDAO()).andReturn(folderDAO).anyTimes();
        replay(daoFactory);
        Configuration.getInstance().setDAOFactory(daoFactory);

        MockHttpRequest request = MockHttpRequest.get("/v3/files/folders/" + folderId + "/list?accesskey=k");
        dispatcher.invoke(request, response);
        assertEquals(Status.OK.getStatusCode(), response.getStatus());
    }

    @Test
    public void testGetFolderChildCountViaAccessKeyUsesKeyFiltered() throws Exception {
        UUID folderId = UUID.randomUUID();

        expect(mockHttpServletRequest.getAttribute("User")).andReturn(null).anyTimes();
        replay(mockHttpServletRequest);

        FileCount count = new FileCount();
        count.setNetwork(2);

        FolderDAO folderDAO = createMock(FolderDAO.class);
        // A valid access key -> key-filtered counts (shortcut count excluded).
        expect(folderDAO.accessKeyIsValid(folderId, "k")).andReturn(true).anyTimes();
        expect(folderDAO.getFolderChildCountsKeyFiltered(folderId)).andReturn(count);
        folderDAO.close();
        expectLastCall().anyTimes();
        replay(folderDAO);

        DAOFactory daoFactory = createMock(DAOFactory.class);
        expect(daoFactory.getFolderDAO()).andReturn(folderDAO).anyTimes();
        replay(daoFactory);
        Configuration.getInstance().setDAOFactory(daoFactory);

        MockHttpRequest request = MockHttpRequest.get("/v3/files/folders/" + folderId + "/count?accesskey=k");
        dispatcher.invoke(request, response);
        assertEquals(Status.OK.getStatusCode(), response.getStatus());
    }

    @Test
    public void testListItemsInHomeUsesListRootItems() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setExternalId(userId);

        expect(mockHttpServletRequest.getAttribute("User")).andReturn(user).anyTimes();
        replay(mockHttpServletRequest);

        List<FileItemSummary> items = new ArrayList<>();
        items.add(new FileItemSummary(UUID.randomUUID(), FileType.FOLDER, "Sub"));

        FolderDAO folderDAO = createMock(FolderDAO.class);
        expect(folderDAO.listRootItemsOfUser(userId, false, null, 0, -1)).andReturn(items);
        folderDAO.close();
        expectLastCall().anyTimes();
        replay(folderDAO);

        DAOFactory daoFactory = createMock(DAOFactory.class);
        expect(daoFactory.getFolderDAO()).andReturn(folderDAO).anyTimes();
        replay(daoFactory);
        Configuration.getInstance().setDAOFactory(daoFactory);

        MockHttpRequest request = MockHttpRequest.get("/v3/files/folders/home/list");
        dispatcher.invoke(request, response);
        assertEquals(Status.OK.getStatusCode(), response.getStatus());
    }

    /**
     * The page window has to reach the DAO, which is where it is applied. If the endpoint dropped it,
     * every one of these tests would still pass on the row set alone.
     */
    @Test
    public void testListItemsInFolderForwardsThePageWindowToTheDao() throws Exception {
        UUID folderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setExternalId(userId);

        expect(mockHttpServletRequest.getAttribute("User")).andReturn(user).anyTimes();
        replay(mockHttpServletRequest);

        List<FileItemSummary> items = new ArrayList<>();
        items.add(new FileItemSummary(UUID.randomUUID(), FileType.NETWORK, "Net 1"));

        FolderDAO folderDAO = createMock(FolderDAO.class);
        expect(folderDAO.accessKeyIsValid(folderId, null)).andReturn(false);
        expect(folderDAO.isReadable(folderId, userId)).andReturn(true);
        expect(folderDAO.listReadableItemsInFolder(folderId, false, null, userId, 10, 5)).andReturn(items);
        folderDAO.close();
        expectLastCall().anyTimes();
        replay(folderDAO);

        DAOFactory daoFactory = createMock(DAOFactory.class);
        expect(daoFactory.getFolderDAO()).andReturn(folderDAO).anyTimes();
        replay(daoFactory);
        Configuration.getInstance().setDAOFactory(daoFactory);

        MockHttpRequest request =
                MockHttpRequest.get("/v3/files/folders/" + folderId + "/list?start=10&size=5");
        dispatcher.invoke(request, response);

        assertEquals(Status.OK.getStatusCode(), response.getStatus());
        verify(folderDAO);
    }

    /**
     * Omitting the parameters must stay unbounded. Every existing client calls this endpoint bare, so a
     * default page size here would silently truncate all of them.
     */
    @Test
    public void testListItemsInFolderWithoutPagingParametersIsUnbounded() throws Exception {
        UUID folderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setExternalId(userId);

        expect(mockHttpServletRequest.getAttribute("User")).andReturn(user).anyTimes();
        replay(mockHttpServletRequest);

        FolderDAO folderDAO = createMock(FolderDAO.class);
        expect(folderDAO.accessKeyIsValid(folderId, null)).andReturn(false);
        expect(folderDAO.isReadable(folderId, userId)).andReturn(true);
        expect(folderDAO.listReadableItemsInFolder(folderId, false, null, userId, 0, -1))
                .andReturn(new ArrayList<>());
        folderDAO.close();
        expectLastCall().anyTimes();
        replay(folderDAO);

        DAOFactory daoFactory = createMock(DAOFactory.class);
        expect(daoFactory.getFolderDAO()).andReturn(folderDAO).anyTimes();
        replay(daoFactory);
        Configuration.getInstance().setDAOFactory(daoFactory);

        MockHttpRequest request = MockHttpRequest.get("/v3/files/folders/" + folderId + "/list");
        dispatcher.invoke(request, response);

        assertEquals(Status.OK.getStatusCode(), response.getStatus());
        verify(folderDAO);
    }

    /**
     * A negative offset is rejected rather than clamped, so a caller computing it arithmetically finds
     * out. Note this covers a numerically valid but out-of-range value; a non-numeric one is a
     * parameter-conversion failure and surfaces as 404.
     */
    @Test
    public void testListItemsInFolderRejectsANegativeStart() throws Exception {
        UUID folderId = UUID.randomUUID();
        expect(mockHttpServletRequest.getAttribute("User")).andReturn(null).anyTimes();
        replay(mockHttpServletRequest);

        MockHttpRequest request =
                MockHttpRequest.get("/v3/files/folders/" + folderId + "/list?start=-1");
        dispatcher.invoke(request, response);

        assertEquals(Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }

    private static class TestFolderServiceV3NoIndex extends FolderServiceV3 {
        public TestFolderServiceV3NoIndex(HttpServletRequest request) {
            super(request);
        }

        @Override
        protected void createFileIndex(UUID fileId, UUID userId, String username, VisibilityType visibility,
                                       FileType fileType, boolean createOnly, boolean ignoreCxFiles) {
            // no-op for testing
        }
        @Override
        protected void deleteFileIndex(UUID folderUUID,
                                       VisibilityType visibilityType) throws SQLException, NdexException, IOException {

        }
    }
}
