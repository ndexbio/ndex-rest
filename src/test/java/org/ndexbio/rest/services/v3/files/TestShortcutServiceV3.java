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
import org.ndexbio.common.models.dao.ShortcutDAO;
import org.ndexbio.model.object.FolderRequest;
import org.ndexbio.model.object.NdexObjectUpdateStatus;
import org.ndexbio.model.object.Shortcut;
import org.ndexbio.model.object.ShortcutRequest;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.exceptions.mappers.UnauthorizedOperationExceptionMapper;

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
        dispatcher.getRegistry().addSingletonResource(new ShortcutServiceV3(mockHttpServletRequest));
        dispatcher.getProviderFactory().registerProvider(UnauthorizedOperationExceptionMapper.class);
        response = new MockHttpResponse();
    }

    @Test
    public void testCreateShortcutSuccess() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID shortcutId = UUID.randomUUID();
        User user = new User();
        user.setExternalId(userId);

        expect(mockHttpServletRequest.getAttribute("User")).andReturn(user);
        replay(mockHttpServletRequest);

        ShortcutRequest requestBody = new ShortcutRequest();
        requestBody.setName("My Shortcut");
        requestBody.setTarget(UUID.randomUUID());
        requestBody.setParent(UUID.randomUUID());

        ShortcutDAO shortcutDAO = createMock(ShortcutDAO.class);
        NdexObjectUpdateStatus status = new NdexObjectUpdateStatus();
        status.setUuid(shortcutId);
        expect(shortcutDAO.createShortcut(anyObject(UUID.class), eq(userId), eq(requestBody.getParent()), eq("My Shortcut"), eq(requestBody.getTarget()))).andReturn(status);
        shortcutDAO.commit();
        expectLastCall();
        shortcutDAO.close();
        expectLastCall();
        replay(shortcutDAO);

        DAOFactory daoFactory = createMock(DAOFactory.class);
        expect(daoFactory.getShortcutDAO()).andReturn(shortcutDAO);
        replay(daoFactory);

        Configuration.getInstance().setDAOFactory(daoFactory);

        ObjectMapper mapper = new ObjectMapper();
        byte[] json = mapper.writeValueAsBytes(requestBody);

        MockHttpRequest request = MockHttpRequest.post("/v3/files/shortcuts/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
        dispatcher.invoke(request, response);

        assertEquals(Status.CREATED.getStatusCode(), response.getStatus());
    }
    
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
    public void testGetShortcutSuccess() throws Exception {
        UUID shortcutId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setExternalId(userId);

        expect(mockHttpServletRequest.getAttribute("User")).andReturn(user).times(1);
        replay(mockHttpServletRequest);

        Shortcut mockShortcut = new Shortcut();
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
        Shortcut result = mapper.readValue(response.getOutput(), Shortcut.class);
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
        shortcutDAO.deleteShortcut(shortcutId);
        expectLastCall();
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
        shortcutDAO.updateShortcut(shortcutId, "New Name", null, userId);
        expectLastCall();
        shortcutDAO.commit();
        expectLastCall();
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

        List<Shortcut> mockShortcuts = new ArrayList<>();
        Shortcut s = new Shortcut();
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
        Shortcut[] result = mapper.readValue(response.getOutput(), Shortcut[].class);
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


}
