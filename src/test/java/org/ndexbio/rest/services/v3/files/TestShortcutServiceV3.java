package org.ndexbio.rest.services.v3.files;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.mock.*;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.jboss.resteasy.spi.Dispatcher;
import org.ndexbio.common.models.dao.DAOFactory;
import org.ndexbio.common.models.dao.FolderDAO;
import org.ndexbio.common.models.dao.ShortcutDAO;
import org.ndexbio.model.exceptions.DuplicateObjectException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.FolderRequest;
import org.ndexbio.model.object.NdexObjectUpdateStatus;
import org.ndexbio.model.object.NdexShortcut;
import org.ndexbio.model.object.ShortcutRequest;
import org.ndexbio.model.object.User;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.exceptions.mappers.UnauthorizedOperationExceptionMapper;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.easymock.EasyMock.*;

public class TestShortcutServiceV3 {

    private Dispatcher dispatcher;
    private HttpServletRequest mockHttpServletRequest;
    private MockHttpResponse response;

    @Before
    public void setUp() {
        mockHttpServletRequest = createMock(HttpServletRequest.class);
        dispatcher = MockDispatcherFactory.createDispatcher();
        dispatcher.getRegistry().addSingletonResource(new TestShortcutServiceV3NoIndex(mockHttpServletRequest));
        dispatcher.getProviderFactory().registerProvider(UnauthorizedOperationExceptionMapper.class);
        response = new MockHttpResponse();
    }

    @Test
    public void testCreateShortcutSuccess() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID shortcutId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();

        User user = new User();
        user.setExternalId(userId);

        expect(mockHttpServletRequest.getAttribute("User")).andReturn(user).anyTimes();
        replay(mockHttpServletRequest);

        ShortcutRequest requestBody = new ShortcutRequest();
        requestBody.setName("My Shortcut");
        requestBody.setTarget(targetId);
        requestBody.setParent(parentId);
        requestBody.setTargetType(FileType.FOLDER);

        // Mock ShortcutDAO
        ShortcutDAO shortcutDAO = createMock(ShortcutDAO.class);
        NdexObjectUpdateStatus status = new NdexObjectUpdateStatus();
        status.setUuid(shortcutId);
        expect(shortcutDAO.createShortcut(anyObject(UUID.class), eq(userId), eq(parentId), eq("My Shortcut"), eq(targetId), eq(FileType.FOLDER))).andReturn(status);
        shortcutDAO.commit();
        expectLastCall();
        expect(shortcutDAO.getShortcutVisibility(anyObject(UUID.class))).andReturn(VisibilityType.PRIVATE);

        shortcutDAO.close();
        expectLastCall();
        replay(shortcutDAO);

        // Mock FolderDAO with isReadable = true
        FolderDAO folderDAO = createMock(FolderDAO.class);
        expect(folderDAO.isReadable(targetId, userId)).andReturn(true);
        folderDAO.close();
        expectLastCall();
        replay(folderDAO);

        // Mock DAOFactory
        DAOFactory daoFactory = createMock(DAOFactory.class);
        expect(daoFactory.getShortcutDAO()).andReturn(shortcutDAO);
        expect(daoFactory.getFolderDAO()).andReturn(folderDAO);
        replay(daoFactory);

        // Inject mocked DAOFactory
        Configuration.getInstance().setDAOFactory(daoFactory);

        ObjectMapper mapper = new ObjectMapper();
        byte[] json = mapper.writeValueAsBytes(requestBody);

        MockHttpRequest request = MockHttpRequest.post("/v3/files/shortcuts/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
        dispatcher.invoke(request, response);

        assertEquals(Status.CREATED.getStatusCode(), response.getStatus());
    }
    
    @Test
    public void testCreateShortcutWithNullRequest() throws Exception {
        expect(mockHttpServletRequest.getAttribute("User")).andReturn(null).anyTimes();
        replay(mockHttpServletRequest);

        MockHttpRequest request = MockHttpRequest.post("/v3/files/shortcuts/")
                .contentType(MediaType.APPLICATION_JSON);

        dispatcher.invoke(request, response);
        assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        assertTrue(new String(response.getOutput()).contains("No shortcut request provided"));
    }
    
    @Test
    public void testCreateShortcutWithEmptyName() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setExternalId(userId);
        expect(mockHttpServletRequest.getAttribute("User")).andReturn(user);
        replay(mockHttpServletRequest);

        ShortcutRequest requestBody = new ShortcutRequest();
        requestBody.setName("");
        requestBody.setTarget(UUID.randomUUID());
        requestBody.setParent(UUID.randomUUID());

        ObjectMapper mapper = new ObjectMapper();
        byte[] json = mapper.writeValueAsBytes(requestBody);

        MockHttpRequest request = MockHttpRequest.post("/v3/files/shortcuts/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);

        dispatcher.invoke(request, response);
        assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        assertTrue(new String(response.getOutput()).contains("Shortcut name cannot be empty"));
    }
    
    @Test
    public void testCreateShortcutWithNullTarget() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setExternalId(userId);
        expect(mockHttpServletRequest.getAttribute("User")).andReturn(user);
        replay(mockHttpServletRequest);

        ShortcutRequest requestBody = new ShortcutRequest();
        requestBody.setName("Test Shortcut");
        requestBody.setTarget(null);
        requestBody.setParent(UUID.randomUUID());
        requestBody.setTargetType(FileType.FOLDER);

        ObjectMapper mapper = new ObjectMapper();
        byte[] json = mapper.writeValueAsBytes(requestBody);

        MockHttpRequest request = MockHttpRequest.post("/v3/files/shortcuts/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);

        dispatcher.invoke(request, response);
        assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        assertTrue(new String(response.getOutput()).contains("Shortcut 'target' cannot be null"));
    }
    
    @Test
    public void testCreateShortcutWithNullTargetType() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setExternalId(userId);
        expect(mockHttpServletRequest.getAttribute("User")).andReturn(user);
        replay(mockHttpServletRequest);

        ShortcutRequest requestBody = new ShortcutRequest();
        requestBody.setName("Test Shortcut");
        requestBody.setTarget(UUID.randomUUID());
        requestBody.setParent(UUID.randomUUID());
        requestBody.setTargetType(null);

        ObjectMapper mapper = new ObjectMapper();
        byte[] json = mapper.writeValueAsBytes(requestBody);

        MockHttpRequest request = MockHttpRequest.post("/v3/files/shortcuts/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);

        dispatcher.invoke(request, response);
        assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        assertTrue(new String(response.getOutput()).contains("Shortcut 'target_type' cannot be null"));
    }
    
    @Test
    public void testCreateShortcutWithInaccessibleFolder() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        User user = new User();
        user.setExternalId(userId);

        expect(mockHttpServletRequest.getAttribute("User")).andReturn(user);
        replay(mockHttpServletRequest);

        ShortcutRequest requestBody = new ShortcutRequest();
        requestBody.setName("Test Shortcut");
        requestBody.setTarget(targetId);
        requestBody.setParent(UUID.randomUUID());
        requestBody.setTargetType(FileType.FOLDER);

        FolderDAO folderDAO = createMock(FolderDAO.class);
        expect(folderDAO.isReadable(targetId, userId)).andReturn(false);
        folderDAO.close();
        expectLastCall();
        replay(folderDAO);

        DAOFactory daoFactory = createMock(DAOFactory.class);
        expect(daoFactory.getFolderDAO()).andReturn(folderDAO);
        replay(daoFactory);

        Configuration.getInstance().setDAOFactory(daoFactory);

        ObjectMapper mapper = new ObjectMapper();
        byte[] json = mapper.writeValueAsBytes(requestBody);

        MockHttpRequest request = MockHttpRequest.post("/v3/files/shortcuts/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);

        dispatcher.invoke(request, response);
        assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        assertTrue(new String(response.getOutput()).contains("Target folder does not exist or is not accessible"));
    }

    @Test
    public void testGetShortcutSuccess() throws Exception {
        UUID shortcutId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setExternalId(userId);

        expect(mockHttpServletRequest.getAttribute("User")).andReturn(user).times(1);
        replay(mockHttpServletRequest);

        NdexShortcut mockShortcut = new NdexShortcut();
        mockShortcut.setName("Test Shortcut");
        mockShortcut.setExternalId(shortcutId);

        ShortcutDAO shortcutDAO = createMock(ShortcutDAO.class);
        expect(shortcutDAO.isReadable(shortcutId, userId)).andReturn(true);
        expect(shortcutDAO.getShortcut(shortcutId, userId)).andReturn(mockShortcut);
        shortcutDAO.close();
        expectLastCall();
        replay(shortcutDAO);

        DAOFactory daoFactory = createMock(DAOFactory.class);
        expect(daoFactory.getShortcutDAO()).andReturn(shortcutDAO);
        replay(daoFactory);

        Configuration.getInstance().setDAOFactory(daoFactory);

        MockHttpRequest request = MockHttpRequest.get("/v3/files/shortcuts/" + shortcutId);
        dispatcher.invoke(request, response);

        assertEquals(Status.OK.getStatusCode(), response.getStatus());

        ObjectMapper mapper = new ObjectMapper();
        NdexShortcut result = mapper.readValue(response.getOutput(), NdexShortcut.class);
        assertEquals("Test Shortcut", result.getName());
        assertEquals(shortcutId, result.getExternalId());
    }

    @Test
    public void testGetShortcutUnauthorized() throws Exception {
        UUID shortcutId = UUID.randomUUID();

        expect(mockHttpServletRequest.getAttribute("User")).andReturn(null).times(2);
        replay(mockHttpServletRequest);

        ShortcutDAO shortcutDAO = createMock(ShortcutDAO.class);
        expect(shortcutDAO.isReadable(shortcutId, null)).andReturn(false);
        shortcutDAO.close();
        expectLastCall();
        replay(shortcutDAO);

        DAOFactory daoFactory = createMock(DAOFactory.class);
        expect(daoFactory.getShortcutDAO()).andReturn(shortcutDAO);
        replay(daoFactory);

        Configuration.getInstance().setDAOFactory(daoFactory);

        MockHttpRequest request = MockHttpRequest.get("/v3/files/shortcuts/" + shortcutId);
        dispatcher.invoke(request, response);

        assertEquals(Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    }
    
    @Test
    public void testDeleteShortcutSuccess() throws Exception {
        UUID shortcutId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setExternalId(userId);

        expect(mockHttpServletRequest.getAttribute("User")).andReturn(user).times(1);
        replay(mockHttpServletRequest);

        ShortcutDAO shortcutDAO = createMock(ShortcutDAO.class);
        expect(shortcutDAO.isShortcutOwner(shortcutId, userId)).andReturn(true);
        shortcutDAO.deleteShortcut(shortcutId, false);
        expectLastCall();
        expect(shortcutDAO.getShortcutVisibility(anyObject(UUID.class))).andReturn(VisibilityType.PRIVATE);
        shortcutDAO.commit();
        expectLastCall();
        shortcutDAO.close();
        expectLastCall();
        replay(shortcutDAO);

        DAOFactory daoFactory = createMock(DAOFactory.class);
        expect(daoFactory.getShortcutDAO()).andReturn(shortcutDAO);
        replay(daoFactory);

        Configuration.getInstance().setDAOFactory(daoFactory);

        MockHttpRequest request = MockHttpRequest.delete("/v3/files/shortcuts/" + shortcutId);
        dispatcher.invoke(request, response);
        assertEquals(Status.NO_CONTENT.getStatusCode(), response.getStatus()); // DELETE returns 204
    }
    
    @Test
    public void testDeleteShortcutUnauthorized() throws Exception {
        UUID shortcutId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setExternalId(userId);
        expect(mockHttpServletRequest.getAttribute("User")).andReturn(user);
        replay(mockHttpServletRequest);

        ShortcutDAO shortcutDAO = createMock(ShortcutDAO.class);
        expect(shortcutDAO.isShortcutOwner(shortcutId, userId)).andReturn(false);
        shortcutDAO.close();
        expectLastCall();
        replay(shortcutDAO);

        DAOFactory daoFactory = createMock(DAOFactory.class);
        expect(daoFactory.getShortcutDAO()).andReturn(shortcutDAO);
        replay(daoFactory);

        Configuration.getInstance().setDAOFactory(daoFactory);

        MockHttpRequest request = MockHttpRequest.delete("/v3/files/shortcuts/" + shortcutId);
        dispatcher.invoke(request, response);

        assertEquals(Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
        assertTrue(new String(response.getOutput()).contains("not the owner"));
    }

    @Test
    public void testUpdateShortcutSuccess() throws Exception {
        UUID shortcutId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setExternalId(userId);

        expect(mockHttpServletRequest.getAttribute("User")).andReturn(user).times(2);
        replay(mockHttpServletRequest);

        ShortcutDAO shortcutDAO = createMock(ShortcutDAO.class);
        expect(shortcutDAO.isShortcutOwner(shortcutId, userId)).andReturn(true);
        shortcutDAO.updateShortcut(shortcutId, "New Name", null);
        expectLastCall();
        shortcutDAO.commit();
        expectLastCall();
        expect(shortcutDAO.getShortcutVisibility(anyObject(UUID.class))).andReturn(VisibilityType.PRIVATE);
        shortcutDAO.close();
        expectLastCall();
        replay(shortcutDAO);

        DAOFactory daoFactory = createMock(DAOFactory.class);
        expect(daoFactory.getShortcutDAO()).andReturn(shortcutDAO);
        replay(daoFactory);

        Configuration.getInstance().setDAOFactory(daoFactory);
        
        FolderRequest requestBody = new FolderRequest();
        requestBody.setName("New Name");
        
        ObjectMapper mapper = new ObjectMapper();
        byte[] json = mapper.writeValueAsBytes(requestBody);

        MockHttpRequest request = MockHttpRequest.put("/v3/files/shortcuts/" + shortcutId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);

        dispatcher.invoke(request, response);
        assertEquals(Status.NO_CONTENT.getStatusCode(), response.getStatus());
    }
    
    @Test
    public void testUpdateShortcutUnauthorized() throws Exception {
        UUID shortcutId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setExternalId(userId);
        expect(mockHttpServletRequest.getAttribute("User")).andReturn(user).times(2);
        replay(mockHttpServletRequest);

        ShortcutDAO shortcutDAO = createMock(ShortcutDAO.class);
        expect(shortcutDAO.isShortcutOwner(shortcutId, userId)).andReturn(false);
        shortcutDAO.close();
        expectLastCall();
        replay(shortcutDAO);

        DAOFactory daoFactory = createMock(DAOFactory.class);
        expect(daoFactory.getShortcutDAO()).andReturn(shortcutDAO);
        replay(daoFactory);

        Configuration.getInstance().setDAOFactory(daoFactory);

        MockHttpRequest request = MockHttpRequest.put("/v3/files/shortcuts/" + shortcutId + "?name=NewName")
                .contentType(MediaType.APPLICATION_JSON);

        dispatcher.invoke(request, response);
        assertEquals(Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
        assertTrue(new String(response.getOutput()).contains("not the owner"));
    }

    @Test
    public void testListMyShortcutsSuccess() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setExternalId(userId);

        expect(mockHttpServletRequest.getAttribute("User")).andReturn(user).times(1);
        replay(mockHttpServletRequest);

        List<NdexShortcut> mockShortcuts = new ArrayList<>();
        NdexShortcut s = new NdexShortcut();
        s.setName("Test Shortcut");
        s.setExternalId(UUID.randomUUID());
        mockShortcuts.add(s);

        ShortcutDAO shortcutDAO = createMock(ShortcutDAO.class);
        expect(shortcutDAO.listShortcutsOfUser(userId, 100)).andReturn(mockShortcuts);
        shortcutDAO.close();
        expectLastCall();
        replay(shortcutDAO);

        DAOFactory daoFactory = createMock(DAOFactory.class);
        expect(daoFactory.getShortcutDAO()).andReturn(shortcutDAO);
        replay(daoFactory);

        Configuration.getInstance().setDAOFactory(daoFactory);

        MockHttpRequest request = MockHttpRequest.get("/v3/files/shortcuts/");
        dispatcher.invoke(request, response);
        assertEquals(Status.OK.getStatusCode(), response.getStatus());

        ObjectMapper mapper = new ObjectMapper();
        NdexShortcut[] result = mapper.readValue(response.getOutput(), NdexShortcut[].class);
        assertEquals(1, result.length);
        assertEquals("Test Shortcut", result[0].getName());
    }

    @Test
    public void testListMyShortcutsUnauthorized() throws Exception {
        expect(mockHttpServletRequest.getAttribute("User")).andReturn(null).anyTimes();
        replay(mockHttpServletRequest);

        ShortcutDAO shortcutDAO = createMock(ShortcutDAO.class);
        replay(shortcutDAO);

        DAOFactory daoFactory = createMock(DAOFactory.class);
        expect(daoFactory.getShortcutDAO()).andReturn(shortcutDAO).anyTimes();
        replay(daoFactory);

        Configuration.getInstance().setDAOFactory(daoFactory);

        MockHttpRequest request = MockHttpRequest.get("/v3/files/shortcuts/");
        dispatcher.invoke(request, response);

        assertEquals(Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    }

    @Test
    public void testDeleteShortcutWithPermanentFlag() throws Exception {
        UUID shortcutId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setExternalId(userId);

        expect(mockHttpServletRequest.getAttribute("User")).andReturn(user);
        replay(mockHttpServletRequest);

        ShortcutDAO shortcutDAO = createMock(ShortcutDAO.class);
        expect(shortcutDAO.isShortcutOwner(shortcutId, userId)).andReturn(true);
        shortcutDAO.deleteShortcut(shortcutId, true);
        expectLastCall();
        expect(shortcutDAO.getShortcutVisibility(anyObject(UUID.class))).andReturn(VisibilityType.PRIVATE);
        shortcutDAO.commit();
        expectLastCall();
        shortcutDAO.close();
        expectLastCall();
        replay(shortcutDAO);

        DAOFactory daoFactory = createMock(DAOFactory.class);
        expect(daoFactory.getShortcutDAO()).andReturn(shortcutDAO);
        replay(daoFactory);

        Configuration.getInstance().setDAOFactory(daoFactory);

        MockHttpRequest request = MockHttpRequest.delete("/v3/files/shortcuts/" + shortcutId + "?permanent=true");
        dispatcher.invoke(request, response);
        assertEquals(Status.NO_CONTENT.getStatusCode(), response.getStatus());
    }

    @Test
    public void testUpdateShortcutWithNullRequest() throws Exception {
        UUID shortcutId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setExternalId(userId);

        expect(mockHttpServletRequest.getAttribute("User")).andReturn(user);
        replay(mockHttpServletRequest);

        MockHttpRequest request = MockHttpRequest.put("/v3/files/shortcuts/" + shortcutId)
                .contentType(MediaType.APPLICATION_JSON);

        dispatcher.invoke(request, response);
        assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    }

    @Test
    public void testUpdateShortcutWithInvalidParent() throws Exception {
        UUID shortcutId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        User user = new User();
        user.setExternalId(userId);

        expect(mockHttpServletRequest.getAttribute("User")).andReturn(user);
        replay(mockHttpServletRequest);

        ShortcutDAO shortcutDAO = createMock(ShortcutDAO.class);
        expect(shortcutDAO.isShortcutOwner(shortcutId, userId)).andReturn(true);
        shortcutDAO.updateShortcut(shortcutId, "Test Shortcut", parentId);
        expectLastCall().andThrow(new NdexException("Invalid parent folder"));
        shortcutDAO.close();
        expectLastCall();
        replay(shortcutDAO);

        DAOFactory daoFactory = createMock(DAOFactory.class);
        expect(daoFactory.getShortcutDAO()).andReturn(shortcutDAO);
        replay(daoFactory);

        Configuration.getInstance().setDAOFactory(daoFactory);

        ShortcutRequest requestBody = new ShortcutRequest();
        requestBody.setName("Test Shortcut");
        requestBody.setParent(parentId);

        ObjectMapper mapper = new ObjectMapper();
        byte[] json = mapper.writeValueAsBytes(requestBody);

        MockHttpRequest request = MockHttpRequest.put("/v3/files/shortcuts/" + shortcutId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);

        dispatcher.invoke(request, response);
        assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        assertTrue(new String(response.getOutput()).contains("Invalid parent folder"));
    }
    private static class TestShortcutServiceV3NoIndex extends ShortcutServiceV3 {
        public TestShortcutServiceV3NoIndex(HttpServletRequest request) {
            super(request);
        }

        @Override
        protected void indexShortcut(UUID folderUUID,User user,
                                   VisibilityType visibilityType, boolean createOnly) {
            // no-op for testing
        }
        @Override
        protected void deleteShortcutIndex(UUID folderUUID,
                                         VisibilityType visibilityType) throws SQLException, NdexException, IOException {

        }
    }

}
