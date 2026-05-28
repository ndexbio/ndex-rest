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
        response = new MockHttpResponse();
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
    public void testListMyFoldersSuccess() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setExternalId(userId);

        expect(mockHttpServletRequest.getAttribute("User")).andReturn(user);
        replay(mockHttpServletRequest);

        List<org.ndexbio.model.object.NdexFolder> folderList = new ArrayList<>();
        org.ndexbio.model.object.NdexFolder folder = new org.ndexbio.model.object.NdexFolder();
        folder.setName("My Folder");
        folderList.add(folder);

        FolderDAO mockFolderDAO = createMock(FolderDAO.class);
        expect(mockFolderDAO.listFoldersOfUser(userId, 100)).andReturn(folderList);
        mockFolderDAO.close();
        expectLastCall();
        replay(mockFolderDAO);

        DAOFactory mockFactory = createMock(DAOFactory.class);
        expect(mockFactory.getFolderDAO()).andReturn(mockFolderDAO);
        replay(mockFactory);

        Configuration.getInstance().setDAOFactory(mockFactory);

        MockHttpRequest request = MockHttpRequest.get("/v3/files/folders/");
        dispatcher.invoke(request, response);

        assertEquals(Status.OK.getStatusCode(), response.getStatus());
    }
    
    @Test
    public void testListMyFoldersUnauthorized() throws Exception {
        expect(mockHttpServletRequest.getAttribute("User")).andReturn(null).anyTimes();
        replay(mockHttpServletRequest);

        MockHttpRequest request = MockHttpRequest.get("/v3/files/folders/");
        dispatcher.invoke(request, response);

        assertEquals(Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
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
        expect(folderDAO.isReadable(folderId, userId)).andReturn(true);
        expect(folderDAO.accessKeyIsValid(folderId, null)).andReturn(false);
        expect(folderDAO.getFolderChildCounts(folderId)).andReturn(count);
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
        expect(folderDAO.isReadable(folderId, userId)).andReturn(true);
        expect(folderDAO.accessKeyIsValid(folderId, null)).andReturn(false);
        expect(folderDAO.listItemsInFolder(folderId, false, null)).andReturn(items);
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
