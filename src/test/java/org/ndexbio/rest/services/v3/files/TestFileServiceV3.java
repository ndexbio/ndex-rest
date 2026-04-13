package org.ndexbio.rest.services.v3.files;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response.Status;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import org.easymock.EasyMock;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.TestConfigHelper;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import org.jboss.resteasy.mock.*;
import static org.junit.Assert.assertEquals;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Before;
import org.jboss.resteasy.spi.Dispatcher;
import org.ndexbio.common.models.dao.DAOFactory;
import org.ndexbio.common.models.dao.FileDAO;
import org.ndexbio.common.models.dao.TrashDAO;
import org.ndexbio.common.models.dao.FolderDAO;
import org.ndexbio.model.errorcodes.NDExError;
import org.ndexbio.model.object.FileCount;
import org.ndexbio.model.object.FileItemSummary;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.TrashRestoreRequest;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.exceptions.mappers.UnauthorizedOperationExceptionMapper;
import jakarta.ws.rs.core.MediaType;
import org.ndexbio.model.object.CopyRequest;
import org.ndexbio.model.object.SharingMemberRequest;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.SharingSimpleRequest;
import org.ndexbio.model.object.TransferOwnershipRequest;
import org.ndexbio.common.models.dao.NetworkDAO;
import org.ndexbio.common.models.dao.ShortcutDAO;
import org.ndexbio.model.object.NdexObjectUpdateStatus;

/**
 *
 * @author churas
 */
public class TestFileServiceV3 {
	
    private Dispatcher dispatcher;
	private HttpServletRequest mockHttpServletRequest;
	private MockHttpResponse response;
	
    @BeforeClass
    public static void initConfiguration() throws Exception {
        TestConfigHelper.initIfNeeded();
    }

	@Before
	public void before(){
		mockHttpServletRequest = createMock(HttpServletRequest.class);
		dispatcher = MockDispatcherFactory.createDispatcher();
		//register the class of the endpoint you want to test
        dispatcher.getRegistry().addSingletonResource(new TestFileServiceV3NoIndex(mockHttpServletRequest));
			
		//if test causes an exception to be thrown be sure to 
		//register the mapper for that exception
		dispatcher.getProviderFactory().registerProvider(UnauthorizedOperationExceptionMapper.class);
		
		// create mock response
		response = new MockHttpResponse();

	}
    //@Rule
    //public TemporaryFolder _folder = new TemporaryFolder();
	
	@Test
    public void testFileCountThrowsException() throws Exception {

        try {
            //File tempDir = _folder.newFolder();
            expect(mockHttpServletRequest.getAttribute("User")).andReturn(null);
            replay(mockHttpServletRequest);

			//Create a mock request
            MockHttpRequest request = MockHttpRequest.get("/v3/files/count");
            
			// invoke the endpoint
			dispatcher.invoke(request, response);
			
			// check the status of response
            assertEquals(Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
			
			// in this case we expected an exception so we are 
			// mapping it back to NDExError object which is what
			// the exceptionmapper generates
            ObjectMapper mapper = new ObjectMapper();
            NDExError er = mapper.readValue(response.getOutput(),
                    NDExError.class);
			
			//Here we are verifying the correct message was set in the exception
            assertEquals("You must be signed in to see your file counts.", er.getMessage());
			
        } finally {
            //_folder.delete();
        }
    }
	
	@Test
    public void testFileCountSuccess() throws Exception {

        try {
			UUID userID = UUID.randomUUID();
			User fakeUser = new User();
			fakeUser.setExternalId(userID);
            //File tempDir = _folder.newFolder();
            expect(mockHttpServletRequest.getAttribute("User")).andReturn(fakeUser);
            replay(mockHttpServletRequest);
			
			/** 
			 * 
			 *
			 * sets up a mock FileDAO and DAOFactory
			 * 
			 */
			FileCount fileCount = new FileCount();
			fileCount.setFolder(1);
			fileCount.setNetwork(2);
			fileCount.setShortcut(3);
			FileDAO mockFileDAO = createMock(FileDAO.class);
			expect(mockFileDAO.getOwnedFileCounts(userID)).andReturn(fileCount);
			mockFileDAO.close();
			EasyMock.expectLastCall();
			replay(mockFileDAO);
			DAOFactory mockDAOFactory = createMock(DAOFactory.class);
			expect(mockDAOFactory.getFileDAO()).andReturn(mockFileDAO);
			replay(mockDAOFactory);
			Configuration.getInstance().setDAOFactory(mockDAOFactory);
			
			//Create a mock request
            MockHttpRequest request = MockHttpRequest.get("/v3/files/count");
            
			// invoke the endpoint
			dispatcher.invoke(request, response);
			
			// check the status of response
            assertEquals(Status.OK.getStatusCode(), response.getStatus());
			
			// in this case we expected an exception so we are 
			// mapping it back to NDExError object which is what
			// the exceptionmapper generates
            ObjectMapper mapper = new ObjectMapper();
            FileCount respFileCount = mapper.readValue(response.getOutput(),
                    FileCount.class);
			assertEquals(respFileCount.getFolder(), 1);
			assertEquals(respFileCount.getNetwork(), 2);
			assertEquals(respFileCount.getShortcut(), 3);
			
        } finally {
            //_folder.delete();
        }
    }
	
	@Test
	public void testListTrashUnauthorized() throws Exception {
	    expect(mockHttpServletRequest.getAttribute("User")).andReturn(null);
	    replay(mockHttpServletRequest);

	    MockHttpRequest request = MockHttpRequest.get("/v3/files/trash");
	    dispatcher.invoke(request, response);

	    assertEquals(Status.UNAUTHORIZED.getStatusCode(), response.getStatus());

	    ObjectMapper mapper = new ObjectMapper();
	    NDExError er = mapper.readValue(response.getOutput(), NDExError.class);
	    assertEquals("You must be logged in to view your trash.", er.getMessage());
	}

	
	@Test
	public void testListTrashSuccess() throws Exception {
	    UUID userID = UUID.randomUUID();
	    User fakeUser = new User();
	    fakeUser.setExternalId(userID);

	    expect(mockHttpServletRequest.getAttribute("User")).andReturn(fakeUser);
	    replay(mockHttpServletRequest);

	    // Prepare mock data
	    List<FileItemSummary> trashedItems = new ArrayList<>();
	    trashedItems.add(new FileItemSummary(UUID.randomUUID(), FileType.FOLDER, "Test Folder"));
	    trashedItems.add(new FileItemSummary(UUID.randomUUID(), FileType.NETWORK, "Test Network"));

	    // Mock DAO and DAOFactory
	    TrashDAO mockTrashDAO = createMock(TrashDAO.class);
	    expect(mockTrashDAO.listTrashedItemsOfUser(userID)).andReturn(trashedItems);
	    mockTrashDAO.close();
	    EasyMock.expectLastCall();
	    replay(mockTrashDAO);

	    DAOFactory mockDAOFactory = createMock(DAOFactory.class);
	    expect(mockDAOFactory.getTrashDAO()).andReturn(mockTrashDAO);
	    replay(mockDAOFactory);

	    Configuration.getInstance().setDAOFactory(mockDAOFactory);
	    
	    // HTTP request
	    MockHttpRequest request = MockHttpRequest.get("/v3/files/trash");
	    dispatcher.invoke(request, response);

	    // Assert response
	    assertEquals(Status.OK.getStatusCode(), response.getStatus());

	    ObjectMapper mapper = new ObjectMapper();
	    FileItemSummary[] result = mapper.readValue(response.getOutput(), FileItemSummary[].class);

	    assertEquals(2, result.length);
	    assertEquals(FileType.FOLDER, result[0].getType());
	    assertEquals("Test Folder", result[0].getName());
	}
	
	@Test
	public void testRestoreItemsFromTrashUnauthorized() throws Exception {
	    expect(mockHttpServletRequest.getAttribute("User")).andReturn(null);
	    replay(mockHttpServletRequest);

	    // Empty restore request payload
	    TrashRestoreRequest requestPayload = new TrashRestoreRequest();
	    ObjectMapper mapper = new ObjectMapper();
	    byte[] json = mapper.writeValueAsBytes(requestPayload);

	    MockHttpRequest request = MockHttpRequest
	        .post("/v3/files/trash/restore")
	        .content(json)
	        .contentType(MediaType.APPLICATION_JSON);

	    dispatcher.invoke(request, response);

	    assertEquals(Status.UNAUTHORIZED.getStatusCode(), response.getStatus());

	    NDExError er = mapper.readValue(response.getOutput(), NDExError.class);
	    assertEquals("You must be logged in to restore items from trash.", er.getMessage());
	}
	
	@Test
	public void testRestoreItemsFromTrashSuccess() throws Exception {
	    UUID userID = UUID.randomUUID();
	    User fakeUser = new User();
	    fakeUser.setExternalId(userID);
		UUID fileID = UUID.randomUUID();

	    expect(mockHttpServletRequest.getAttribute("User")).andReturn(fakeUser).anyTimes();
	    replay(mockHttpServletRequest);

	    // Prepare restore request
	    TrashRestoreRequest requestPayload = new TrashRestoreRequest();
	    List<UUID> networks = new ArrayList<>();
	    networks.add(fileID);
	    requestPayload.setNetworks(networks);

	    ObjectMapper mapper = new ObjectMapper();
	    byte[] json = mapper.writeValueAsBytes(requestPayload);

	    // Use anyObject matcher to avoid object identity issues
	    TrashDAO mockTrashDAO = createMock(TrashDAO.class);
		NetworkDAO mockNetworkDAO = createMock(NetworkDAO.class);

		mockTrashDAO.restoreTrashedItems(EasyMock.eq(userID), EasyMock.anyObject(TrashRestoreRequest.class));
	    EasyMock.expectLastCall().once();
	    mockTrashDAO.commit();
	    EasyMock.expectLastCall().once();

	    mockTrashDAO.close();
	    EasyMock.expectLastCall().once();

	    DAOFactory mockDAOFactory = createMock(DAOFactory.class);
	    expect(mockDAOFactory.getTrashDAO()).andReturn(mockTrashDAO);
		//expect(mockDAOFactory.getFolderDAO()).andReturn(mockFolderDAO);
		//expect(mockDAOFactory.getShortcutDAO()).andReturn(mockShortcutDAO);
		expect(mockDAOFactory.getNetworkDAO()).andReturn(mockNetworkDAO);
		expect(mockNetworkDAO.getNetworkVisibility(fileID)).andReturn(VisibilityType.PRIVATE).anyTimes();
		mockNetworkDAO.close();
		EasyMock.expectLastCall().anyTimes();

		replay(mockDAOFactory, mockNetworkDAO, mockTrashDAO);

	    Configuration.getInstance().setDAOFactory(mockDAOFactory);

	    MockHttpRequest request = MockHttpRequest.post("/v3/files/trash/restore")
	            .content(json)
	            .contentType(MediaType.APPLICATION_JSON);

	    dispatcher.invoke(request, response);
		//System.out.println("Response body: " + new String(response.getOutput()));
	    assertEquals(Status.NO_CONTENT.getStatusCode(), response.getStatus());
	}

	@Test
	public void testPermanentlyDeleteTrashedItemUnauthorized() throws Exception {
	    expect(mockHttpServletRequest.getAttribute("User")).andReturn(null);
	    replay(mockHttpServletRequest);

	    MockHttpRequest request = MockHttpRequest.delete("/v3/files/trash/" + UUID.randomUUID());
	    dispatcher.invoke(request, response);

	    assertEquals(Status.UNAUTHORIZED.getStatusCode(), response.getStatus());

	    ObjectMapper mapper = new ObjectMapper();
	    NDExError er = mapper.readValue(response.getOutput(), NDExError.class);
	    assertEquals("You must be logged in to restore items from trash.", er.getMessage());
	}

	@Test
	public void testPermanentlyDeleteTrashedItemNotFound() throws Exception {
	    UUID userID = UUID.randomUUID();
	    User fakeUser = new User();
	    fakeUser.setExternalId(userID);

	    expect(mockHttpServletRequest.getAttribute("User")).andReturn(fakeUser);
	    replay(mockHttpServletRequest);

	    UUID itemId = UUID.randomUUID();
	    
	    // Mock DAO to return null for item type (indicating item not found)
	    TrashDAO mockTrashDAO = createMock(TrashDAO.class);
	    expect(mockTrashDAO.getTrashedItemType(itemId)).andReturn(null);
	    mockTrashDAO.close();
	    EasyMock.expectLastCall();
	    replay(mockTrashDAO);

	    DAOFactory mockDAOFactory = createMock(DAOFactory.class);
	    expect(mockDAOFactory.getTrashDAO()).andReturn(mockTrashDAO);
	    replay(mockDAOFactory);

	    Configuration.getInstance().setDAOFactory(mockDAOFactory);

	    MockHttpRequest request = MockHttpRequest.delete("/v3/files/trash/" + itemId);
	    dispatcher.invoke(request, response);

	    assertEquals(Status.NOT_FOUND.getStatusCode(), response.getStatus());
	}

	@Test
	public void testPermanentlyDeleteTrashedItemSuccess() throws Exception {
	    UUID userID = UUID.randomUUID();
	    User fakeUser = new User();
	    fakeUser.setExternalId(userID);

	    expect(mockHttpServletRequest.getAttribute("User")).andReturn(fakeUser);
	    replay(mockHttpServletRequest);

	    UUID itemId = UUID.randomUUID();
	    
	    // Mock DAO to return a valid item type and handle deletion
	    TrashDAO mockTrashDAO = createMock(TrashDAO.class);
	    expect(mockTrashDAO.getTrashedItemType(itemId)).andReturn(FileType.NETWORK);
	    mockTrashDAO.permanentlyDeleteTrashedItem(itemId, FileType.NETWORK);
	    EasyMock.expectLastCall().once();
	    mockTrashDAO.commit();
	    EasyMock.expectLastCall().once();
	    mockTrashDAO.close();
	    EasyMock.expectLastCall();
	    replay(mockTrashDAO);

	    DAOFactory mockDAOFactory = createMock(DAOFactory.class);
	    expect(mockDAOFactory.getTrashDAO()).andReturn(mockTrashDAO);
	    replay(mockDAOFactory);

	    Configuration.getInstance().setDAOFactory(mockDAOFactory);

	    MockHttpRequest request = MockHttpRequest.delete("/v3/files/trash/" + itemId);
	    dispatcher.invoke(request, response);

	    assertEquals(Status.NO_CONTENT.getStatusCode(), response.getStatus());
	}

	@Test
	public void testClearTrashUnauthorized() throws Exception {
	    expect(mockHttpServletRequest.getAttribute("User")).andReturn(null);
	    replay(mockHttpServletRequest);

	    MockHttpRequest request = MockHttpRequest.delete("/v3/files/trash");
	    dispatcher.invoke(request, response);

	    assertEquals(Status.UNAUTHORIZED.getStatusCode(), response.getStatus());

	    ObjectMapper mapper = new ObjectMapper();
	    NDExError er = mapper.readValue(response.getOutput(), NDExError.class);
	    assertEquals("You must be logged in to clear your trash.", er.getMessage());
	}

	@Test
	public void testClearTrashSuccess() throws Exception {
	    UUID userID = UUID.randomUUID();
	    User fakeUser = new User();
	    fakeUser.setExternalId(userID);

	    expect(mockHttpServletRequest.getAttribute("User")).andReturn(fakeUser);
	    replay(mockHttpServletRequest);

	    // Mock DAO to handle deletion
	    TrashDAO mockTrashDAO = createMock(TrashDAO.class);
	    mockTrashDAO.permanentlyDeleteAllTrashedItemsOfUser(userID);
	    EasyMock.expectLastCall().once();
	    mockTrashDAO.commit();
	    EasyMock.expectLastCall().once();
	    mockTrashDAO.close();
	    EasyMock.expectLastCall();
	    replay(mockTrashDAO);

	    DAOFactory mockDAOFactory = createMock(DAOFactory.class);
	    expect(mockDAOFactory.getTrashDAO()).andReturn(mockTrashDAO);
	    replay(mockDAOFactory);

	    Configuration.getInstance().setDAOFactory(mockDAOFactory);

	    MockHttpRequest request = MockHttpRequest.delete("/v3/files/trash");
	    dispatcher.invoke(request, response);

	    assertEquals(Status.NO_CONTENT.getStatusCode(), response.getStatus());
	}

	@Test
	public void testCopyFileUnauthorized() throws Exception {
	    expect(mockHttpServletRequest.getAttribute("User")).andReturn(null);
	    replay(mockHttpServletRequest);

	    CopyRequest request = new CopyRequest();
	    request.setFileId(UUID.randomUUID());
	    request.setType(FileType.NETWORK);

	    ObjectMapper mapper = new ObjectMapper();
	    byte[] json = mapper.writeValueAsBytes(request);

	    MockHttpRequest httpRequest = MockHttpRequest.post("/v3/files/copy")
	        .content(json)
	        .contentType(MediaType.APPLICATION_JSON);

	    dispatcher.invoke(httpRequest, response);

	    assertEquals(Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
	}

	@Test
	public void testShareMembersUnauthorized() throws Exception {
	    expect(mockHttpServletRequest.getAttribute("User")).andReturn(null);
	    replay(mockHttpServletRequest);

	    SharingMemberRequest request = new SharingMemberRequest();
	    Map<UUID, FileType> files = new HashMap<>();
	    files.put(UUID.randomUUID(), FileType.NETWORK);
	    request.setFiles(files);

	    Map<UUID, Permissions> members = new HashMap<>();
	    members.put(UUID.randomUUID(), Permissions.READ);
	    request.setMembers(members);

	    ObjectMapper mapper = new ObjectMapper();
	    byte[] json = mapper.writeValueAsBytes(request);

	    MockHttpRequest httpRequest = MockHttpRequest.post("/v3/files/sharing/members")
	        .content(json)
	        .contentType(MediaType.APPLICATION_JSON);

	    dispatcher.invoke(httpRequest, response);

	    assertEquals(Status.UNAUTHORIZED.getStatusCode(), response.getStatus());

	    NDExError er = mapper.readValue(response.getOutput(), NDExError.class);
	    assertEquals("You must be logged in to add members.", er.getMessage());
	}

	@Test
	public void testListMembersUnauthorized() throws Exception {
	    expect(mockHttpServletRequest.getAttribute("User")).andReturn(null);
	    replay(mockHttpServletRequest);

	    Map<UUID, FileType> files = new HashMap<>();
	    files.put(UUID.randomUUID(), FileType.NETWORK);

	    ObjectMapper mapper = new ObjectMapper();
	    byte[] json = mapper.writeValueAsBytes(files);

	    MockHttpRequest httpRequest = MockHttpRequest.post("/v3/files/sharing/members/list")
	        .content(json)
	        .contentType(MediaType.APPLICATION_JSON);

	    dispatcher.invoke(httpRequest, response);

	    assertEquals(Status.UNAUTHORIZED.getStatusCode(), response.getStatus());

	    NDExError er = mapper.readValue(response.getOutput(), NDExError.class);
	    assertEquals("You must be logged in to view file permissions.", er.getMessage());
	}

	@Test
	public void testListMembersInvalidFileType() throws Exception {
	    UUID userID = UUID.randomUUID();
	    User fakeUser = new User();
	    fakeUser.setExternalId(userID);

	    expect(mockHttpServletRequest.getAttribute("User")).andReturn(fakeUser);
	    replay(mockHttpServletRequest);

	    Map<UUID, FileType> files = new HashMap<>();
	    files.put(UUID.randomUUID(), FileType.SHORTCUT); // Shortcuts are not supported

	    ObjectMapper mapper = new ObjectMapper();
	    byte[] json = mapper.writeValueAsBytes(files);

	    MockHttpRequest httpRequest = MockHttpRequest.post("/v3/files/sharing/members/list")
	        .content(json)
	        .contentType(MediaType.APPLICATION_JSON);

	    dispatcher.invoke(httpRequest, response);

	    assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
	}

	@Test
	public void testShareObjectUnauthorized() throws Exception {
	    expect(mockHttpServletRequest.getAttribute("User")).andReturn(null);
	    replay(mockHttpServletRequest);

	    SharingSimpleRequest request = new SharingSimpleRequest();
	    Map<UUID, FileType> files = new HashMap<>();
	    files.put(UUID.randomUUID(), FileType.NETWORK);
	    request.setFiles(files);

	    ObjectMapper mapper = new ObjectMapper();
	    byte[] json = mapper.writeValueAsBytes(request);

	    MockHttpRequest httpRequest = MockHttpRequest.post("/v3/files/sharing/share")
	        .content(json)
	        .contentType(MediaType.APPLICATION_JSON);

	    dispatcher.invoke(httpRequest, response);

	    assertEquals(Status.UNAUTHORIZED.getStatusCode(), response.getStatus());

	    NDExError er = mapper.readValue(response.getOutput(), NDExError.class);
	    assertEquals("You must be logged in to share.", er.getMessage());
	}

	@Test
	public void testShareObjectInvalidFileType() throws Exception {
	    UUID userID = UUID.randomUUID();
	    User fakeUser = new User();
	    fakeUser.setExternalId(userID);

	    expect(mockHttpServletRequest.getAttribute("User")).andReturn(fakeUser);
	    replay(mockHttpServletRequest);

	    SharingSimpleRequest request = new SharingSimpleRequest();
	    Map<UUID, FileType> files = new HashMap<>();
	    files.put(UUID.randomUUID(), FileType.SHORTCUT); // Shortcuts are not supported
	    request.setFiles(files);

	    ObjectMapper mapper = new ObjectMapper();
	    byte[] json = mapper.writeValueAsBytes(request);

	    MockHttpRequest httpRequest = MockHttpRequest.post("/v3/files/sharing/share")
	        .content(json)
	        .contentType(MediaType.APPLICATION_JSON);

	    dispatcher.invoke(httpRequest, response);

	    assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());

	}

	@Test
	public void testShareObjectFolderSuccess() throws Exception {
	    UUID userID = UUID.randomUUID();
	    User fakeUser = new User();
	    fakeUser.setExternalId(userID);

	    expect(mockHttpServletRequest.getAttribute("User")).andReturn(fakeUser);
	    replay(mockHttpServletRequest);

	    UUID folderId = UUID.randomUUID();

	    SharingSimpleRequest request = new SharingSimpleRequest();
	    Map<UUID, FileType> files = new HashMap<>();
	    files.put(folderId, FileType.FOLDER);
	    request.setFiles(files);

	    ObjectMapper mapper = new ObjectMapper();
	    byte[] json = mapper.writeValueAsBytes(request);

	    // Mock FolderDAO
	    FolderDAO mockFolderDAO = createMock(FolderDAO.class);
	    expect(mockFolderDAO.isFolderOwner(folderId, userID)).andReturn(true);
	    expect(mockFolderDAO.enableFolderAccessKey(folderId)).andReturn("test-access-key");
	    mockFolderDAO.commit();
	    EasyMock.expectLastCall();
	    mockFolderDAO.close();
	    EasyMock.expectLastCall();
	    replay(mockFolderDAO);

	    // Mock DAOFactory
	    DAOFactory mockDAOFactory = createMock(DAOFactory.class);
	    expect(mockDAOFactory.getFolderDAO()).andReturn(mockFolderDAO);
	    replay(mockDAOFactory);

	    Configuration.getInstance().setDAOFactory(mockDAOFactory);

	    MockHttpRequest httpRequest = MockHttpRequest.post("/v3/files/sharing/share")
	        .content(json)
	        .contentType(MediaType.APPLICATION_JSON);

	    dispatcher.invoke(httpRequest, response);

	    assertEquals(Status.OK.getStatusCode(), response.getStatus());

	    Map<String, String> result = mapper.readValue(response.getOutput(), Map.class);
	    assertEquals("test-access-key", result.get(folderId.toString()));
	}

	@Test
	public void testShareObjectNotOwner() throws Exception {
	    UUID userID = UUID.randomUUID();
	    User fakeUser = new User();
	    fakeUser.setExternalId(userID);

	    expect(mockHttpServletRequest.getAttribute("User")).andReturn(fakeUser);
	    replay(mockHttpServletRequest);

	    UUID folderId = UUID.randomUUID();

	    SharingSimpleRequest request = new SharingSimpleRequest();
	    Map<UUID, FileType> files = new HashMap<>();
	    files.put(folderId, FileType.FOLDER);
	    request.setFiles(files);

	    ObjectMapper mapper = new ObjectMapper();
	    byte[] json = mapper.writeValueAsBytes(request);

	    // Mock FolderDAO
	    FolderDAO mockFolderDAO = createMock(FolderDAO.class);
	    expect(mockFolderDAO.isFolderOwner(folderId, userID)).andReturn(false);
	    mockFolderDAO.close();
	    EasyMock.expectLastCall();
	    replay(mockFolderDAO);

	    // Mock DAOFactory
	    DAOFactory mockDAOFactory = createMock(DAOFactory.class);
	    expect(mockDAOFactory.getFolderDAO()).andReturn(mockFolderDAO);
	    replay(mockDAOFactory);

	    Configuration.getInstance().setDAOFactory(mockDAOFactory);

	    MockHttpRequest httpRequest = MockHttpRequest.post("/v3/files/sharing/share")
	        .content(json)
	        .contentType(MediaType.APPLICATION_JSON);

	    dispatcher.invoke(httpRequest, response);

	    assertEquals(Status.UNAUTHORIZED.getStatusCode(), response.getStatus());

	    NDExError er = mapper.readValue(response.getOutput(), NDExError.class);
	    assertEquals("You are not the owner of folder " + folderId, er.getMessage());
	}

	@Test
	public void testUnshareObjectUnauthorized() throws Exception {
	    expect(mockHttpServletRequest.getAttribute("User")).andReturn(null);
	    replay(mockHttpServletRequest);

	    SharingSimpleRequest request = new SharingSimpleRequest();
	    Map<UUID, FileType> files = new HashMap<>();
	    files.put(UUID.randomUUID(), FileType.NETWORK);
	    request.setFiles(files);

	    ObjectMapper mapper = new ObjectMapper();
	    byte[] json = mapper.writeValueAsBytes(request);

	    MockHttpRequest httpRequest = MockHttpRequest.post("/v3/files/sharing/unshare")
	        .content(json)
	        .contentType(MediaType.APPLICATION_JSON);

	    dispatcher.invoke(httpRequest, response);

	    assertEquals(Status.UNAUTHORIZED.getStatusCode(), response.getStatus());

	    NDExError er = mapper.readValue(response.getOutput(), NDExError.class);
	    assertEquals("You must be logged in to unshare.", er.getMessage());
	}

	@Test
	public void testUnshareObjectInvalidFileType() throws Exception {
	    UUID userID = UUID.randomUUID();
	    User fakeUser = new User();
	    fakeUser.setExternalId(userID);

	    expect(mockHttpServletRequest.getAttribute("User")).andReturn(fakeUser);
	    replay(mockHttpServletRequest);

	    SharingSimpleRequest request = new SharingSimpleRequest();
	    Map<UUID, FileType> files = new HashMap<>();
	    files.put(UUID.randomUUID(), FileType.SHORTCUT); // Shortcuts are not supported
	    request.setFiles(files);

	    ObjectMapper mapper = new ObjectMapper();
	    byte[] json = mapper.writeValueAsBytes(request);

	    MockHttpRequest httpRequest = MockHttpRequest.post("/v3/files/sharing/unshare")
	        .content(json)
	        .contentType(MediaType.APPLICATION_JSON);

	    dispatcher.invoke(httpRequest, response);

	    assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
	}

	@Test
	public void testUnshareObjectFolderSuccess() throws Exception {
	    UUID userID = UUID.randomUUID();
	    User fakeUser = new User();
	    fakeUser.setExternalId(userID);

	    expect(mockHttpServletRequest.getAttribute("User")).andReturn(fakeUser);
	    replay(mockHttpServletRequest);

	    UUID folderId = UUID.randomUUID();

	    SharingSimpleRequest request = new SharingSimpleRequest();
	    Map<UUID, FileType> files = new HashMap<>();
	    files.put(folderId, FileType.FOLDER);
	    request.setFiles(files);

	    ObjectMapper mapper = new ObjectMapper();
	    byte[] json = mapper.writeValueAsBytes(request);

	    // Mock FolderDAO
	    FolderDAO mockFolderDAO = createMock(FolderDAO.class);
	    expect(mockFolderDAO.isFolderOwner(folderId, userID)).andReturn(true);
	    mockFolderDAO.disableFolderAccessKey(folderId);
	    EasyMock.expectLastCall();
	    mockFolderDAO.commit();
	    EasyMock.expectLastCall();
	    mockFolderDAO.close();
	    EasyMock.expectLastCall();
	    replay(mockFolderDAO);

	    // Mock DAOFactory
	    DAOFactory mockDAOFactory = createMock(DAOFactory.class);
	    expect(mockDAOFactory.getFolderDAO()).andReturn(mockFolderDAO);
	    replay(mockDAOFactory);

	    Configuration.getInstance().setDAOFactory(mockDAOFactory);

	    MockHttpRequest httpRequest = MockHttpRequest.post("/v3/files/sharing/unshare")
	        .content(json)
	        .contentType(MediaType.APPLICATION_JSON);

	    dispatcher.invoke(httpRequest, response);

	    assertEquals(Status.NO_CONTENT.getStatusCode(), response.getStatus());
	}

	@Test
	public void testUnshareObjectNotOwner() throws Exception {
	    UUID userID = UUID.randomUUID();
	    User fakeUser = new User();
	    fakeUser.setExternalId(userID);

	    expect(mockHttpServletRequest.getAttribute("User")).andReturn(fakeUser);
	    replay(mockHttpServletRequest);

	    UUID folderId = UUID.randomUUID();

	    SharingSimpleRequest request = new SharingSimpleRequest();
	    Map<UUID, FileType> files = new HashMap<>();
	    files.put(folderId, FileType.FOLDER);
	    request.setFiles(files);

	    ObjectMapper mapper = new ObjectMapper();
	    byte[] json = mapper.writeValueAsBytes(request);

	    // Mock FolderDAO
	    FolderDAO mockFolderDAO = createMock(FolderDAO.class);
	    expect(mockFolderDAO.isFolderOwner(folderId, userID)).andReturn(false);
	    mockFolderDAO.close();
	    EasyMock.expectLastCall();
	    replay(mockFolderDAO);

	    // Mock DAOFactory
	    DAOFactory mockDAOFactory = createMock(DAOFactory.class);
	    expect(mockDAOFactory.getFolderDAO()).andReturn(mockFolderDAO);
	    replay(mockDAOFactory);

	    Configuration.getInstance().setDAOFactory(mockDAOFactory);

	    MockHttpRequest httpRequest = MockHttpRequest.post("/v3/files/sharing/unshare")
	        .content(json)
	        .contentType(MediaType.APPLICATION_JSON);

	    dispatcher.invoke(httpRequest, response);

	    assertEquals(Status.UNAUTHORIZED.getStatusCode(), response.getStatus());

	    NDExError er = mapper.readValue(response.getOutput(), NDExError.class);
	    assertEquals("You are not the owner of folder " + folderId, er.getMessage());
	}

	@Test
	public void testTransferNetworksOwnershipUnauthorized() throws Exception {
	    expect(mockHttpServletRequest.getAttribute("User")).andReturn(null);
	    replay(mockHttpServletRequest);

	    TransferOwnershipRequest request = new TransferOwnershipRequest();
	    request.setNetworks(new ArrayList<>());
	    request.setNewOwner(UUID.randomUUID());

	    ObjectMapper mapper = new ObjectMapper();
	    byte[] json = mapper.writeValueAsBytes(request);

	    MockHttpRequest httpRequest = MockHttpRequest.post("/v3/files/sharing/transfer")
	        .content(json)
	        .contentType(MediaType.APPLICATION_JSON);

	    dispatcher.invoke(httpRequest, response);

	    assertEquals(Status.UNAUTHORIZED.getStatusCode(), response.getStatus());

	    NDExError er = mapper.readValue(response.getOutput(), NDExError.class);
	    assertEquals("You must be logged in to transfer objects.", er.getMessage());
	}

	@Test
	public void testTransferNetworksOwnershipNoNetworks() throws Exception {
	    UUID userID = UUID.randomUUID();
	    User fakeUser = new User();
	    fakeUser.setExternalId(userID);

	    expect(mockHttpServletRequest.getAttribute("User")).andReturn(fakeUser);
	    replay(mockHttpServletRequest);

	    TransferOwnershipRequest request = new TransferOwnershipRequest();
	    request.setNetworks(new ArrayList<>()); // Empty list
	    request.setNewOwner(UUID.randomUUID());

	    ObjectMapper mapper = new ObjectMapper();
	    byte[] json = mapper.writeValueAsBytes(request);

	    MockHttpRequest httpRequest = MockHttpRequest.post("/v3/files/sharing/transfer")
	        .content(json)
	        .contentType(MediaType.APPLICATION_JSON);

	    dispatcher.invoke(httpRequest, response);

	    assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
	}

	@Test
	public void testTransferNetworksOwnershipNoNewOwner() throws Exception {
	    UUID userID = UUID.randomUUID();
	    User fakeUser = new User();
	    fakeUser.setExternalId(userID);

	    expect(mockHttpServletRequest.getAttribute("User")).andReturn(fakeUser);
	    replay(mockHttpServletRequest);

	    TransferOwnershipRequest request = new TransferOwnershipRequest();
	    List<UUID> networks = new ArrayList<>();
	    networks.add(UUID.randomUUID());
	    request.setNetworks(networks);
	    request.setNewOwner(null); // No new owner specified

	    ObjectMapper mapper = new ObjectMapper();
	    byte[] json = mapper.writeValueAsBytes(request);

	    MockHttpRequest httpRequest = MockHttpRequest.post("/v3/files/sharing/transfer")
	        .content(json)
	        .contentType(MediaType.APPLICATION_JSON);

	    dispatcher.invoke(httpRequest, response);

	    assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
	}

	@Test
	public void testTransferNetworksOwnershipSuccess() throws Exception {
	    UUID userID = UUID.randomUUID();
	    User fakeUser = new User();
	    fakeUser.setExternalId(userID);

	    expect(mockHttpServletRequest.getAttribute("User")).andReturn(fakeUser).anyTimes();
	    replay(mockHttpServletRequest);

	    UUID networkId = UUID.randomUUID();
	    UUID newOwnerId = UUID.randomUUID();
	    UUID parentId = UUID.randomUUID();
	    String networkName = "Test Network";
		User newFakeUser = new User();
		newFakeUser.setExternalId(newOwnerId);

	    TransferOwnershipRequest request = new TransferOwnershipRequest();
	    List<UUID> networks = new ArrayList<>();
	    networks.add(networkId);
	    request.setNetworks(networks);
	    request.setNewOwner(newOwnerId);

	    ObjectMapper mapper = new ObjectMapper();
	    byte[] json = mapper.writeValueAsBytes(request);

	    // Mock NetworkDAO
	    NetworkDAO mockNetworkDAO = createMock(NetworkDAO.class);
	    expect(mockNetworkDAO.isAdmin(networkId, userID)).andReturn(true);
	    expect(mockNetworkDAO.getNetworkFolder(networkId)).andReturn(parentId);
	    expect(mockNetworkDAO.getNetworkName(networkId)).andReturn(networkName);
	    expect(mockNetworkDAO.grantPrivilegeToUser(networkId, newOwnerId, Permissions.ADMIN)).andReturn(1);
	    mockNetworkDAO.setNetworkFolder(networkId, null);
	    EasyMock.expectLastCall();
	    mockNetworkDAO.commit();
	    EasyMock.expectLastCall();
		expect(mockNetworkDAO.getNetworkVisibility(networkId)).andReturn(VisibilityType.PRIVATE);
	    mockNetworkDAO.close();
	    EasyMock.expectLastCall();
	    replay(mockNetworkDAO);

	    // Mock ShortcutDAO
	    ShortcutDAO mockShortcutDAO = createMock(ShortcutDAO.class);
	    expect(mockShortcutDAO.createShortcut(
	        EasyMock.anyObject(UUID.class),
	        EasyMock.eq(userID),
	        EasyMock.eq(parentId),
	        EasyMock.eq(networkName),
	        EasyMock.eq(networkId),
	        EasyMock.eq(FileType.NETWORK)
	    )).andReturn(new NdexObjectUpdateStatus());
	    mockShortcutDAO.commit();
	    EasyMock.expectLastCall();
		expect(mockShortcutDAO.getShortcutVisibility(EasyMock.anyObject(UUID.class))).andReturn(VisibilityType.PRIVATE);
	    mockShortcutDAO.close();
	    EasyMock.expectLastCall();
	    replay(mockShortcutDAO);
		UserDAO mockUserDAO = createMock(UserDAO.class);
		expect(mockUserDAO.getUserById(newOwnerId, false, false)).andReturn(newFakeUser);
		mockUserDAO.close();
		EasyMock.expectLastCall();
		replay(mockUserDAO);

	    // Mock DAOFactory
	    DAOFactory mockDAOFactory = createMock(DAOFactory.class);
	    expect(mockDAOFactory.getNetworkDAO()).andReturn(mockNetworkDAO);
	    expect(mockDAOFactory.getShortcutDAO()).andReturn(mockShortcutDAO);
		expect(mockDAOFactory.getUserDAO()).andReturn(mockUserDAO);
	    replay(mockDAOFactory);

	    Configuration.getInstance().setDAOFactory(mockDAOFactory);

	    MockHttpRequest httpRequest = MockHttpRequest.post("/v3/files/sharing/transfer")
	        .content(json)
	        .contentType(MediaType.APPLICATION_JSON);

	    dispatcher.invoke(httpRequest, response);
		System.out.println("Response body: " + new String(response.getOutput()));
	    assertEquals(Status.NO_CONTENT.getStatusCode(), response.getStatus());
	}

	@Test
	public void testListSharedObjectsUnauthorized() throws Exception {
	    expect(mockHttpServletRequest.getAttribute("User")).andReturn(null);
	    replay(mockHttpServletRequest);

	    MockHttpRequest request = MockHttpRequest.get("/v3/files/sharing/list");
	    dispatcher.invoke(request, response);

	    assertEquals(Status.UNAUTHORIZED.getStatusCode(), response.getStatus());

	    ObjectMapper mapper = new ObjectMapper();
	    NDExError er = mapper.readValue(response.getOutput(), NDExError.class);
	    assertEquals("You must be logged in.", er.getMessage());
	}

	private static class TestFileServiceV3NoIndex extends FileServiceV3 {
		public TestFileServiceV3NoIndex(HttpServletRequest request) {
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
