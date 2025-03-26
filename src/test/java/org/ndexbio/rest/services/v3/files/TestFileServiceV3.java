package org.ndexbio.rest.services.v3.files;


import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response.Status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.easymock.EasyMock;
import org.ndexbio.rest.Configuration;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import org.jboss.resteasy.mock.*;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.junit.Before;
import org.jboss.resteasy.spi.Dispatcher;
import org.ndexbio.common.models.dao.DAOFactory;
import org.ndexbio.common.models.dao.FileDAO;
import org.ndexbio.common.models.dao.TrashDAO;
import org.ndexbio.model.errorcodes.NDExError;
import org.ndexbio.model.object.FileCount;
import org.ndexbio.model.object.FileItemSummary;
import org.ndexbio.model.object.TrashRestoreRequest;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.exceptions.mappers.UnauthorizedOperationExceptionMapper;
import jakarta.ws.rs.core.MediaType;

/**
 *
 * @author churas
 */
public class TestFileServiceV3 {
	
    private Dispatcher dispatcher;
	private HttpServletRequest mockHttpServletRequest;
	private MockHttpResponse response;
	
	@Before
	public void before(){
		mockHttpServletRequest = createMock(HttpServletRequest.class);
		dispatcher = MockDispatcherFactory.createDispatcher();
		//register the class of the endpoint you want to test
        dispatcher.getRegistry().addSingletonResource(new FileServiceV3(mockHttpServletRequest));
			
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
	public void testListTrashSuccess() throws Exception {
	    UUID userID = UUID.randomUUID();
	    User fakeUser = new User();
	    fakeUser.setExternalId(userID);

	    expect(mockHttpServletRequest.getAttribute("User")).andReturn(fakeUser);
	    replay(mockHttpServletRequest);

	    // Prepare mock data
	    List<FileItemSummary> trashedItems = new ArrayList<>();
	    trashedItems.add(new FileItemSummary(UUID.randomUUID(), "folder", "Test Folder"));
	    trashedItems.add(new FileItemSummary(UUID.randomUUID(), "network", "Test Network"));

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
	    assertEquals("folder", result[0].getType());
	    assertEquals("Test Folder", result[0].getName());
	}
	
	@Test
	public void testRestoreItemsFromTrashSuccess() throws Exception {
	    UUID userID = UUID.randomUUID();
	    User fakeUser = new User();
	    fakeUser.setExternalId(userID);

	    expect(mockHttpServletRequest.getAttribute("User")).andReturn(fakeUser);
	    replay(mockHttpServletRequest);

	    // Prepare restore request
	    TrashRestoreRequest requestPayload = new TrashRestoreRequest();
	    List<UUID> networks = new ArrayList<>();
	    networks.add(UUID.randomUUID());
	    requestPayload.setNetworks(networks);

	    ObjectMapper mapper = new ObjectMapper();
	    byte[] json = mapper.writeValueAsBytes(requestPayload);

	    // Use anyObject matcher to avoid object identity issues
	    TrashDAO mockTrashDAO = createMock(TrashDAO.class);
	    mockTrashDAO.restoreTrashedItems(EasyMock.eq(userID), EasyMock.anyObject(TrashRestoreRequest.class));
	    EasyMock.expectLastCall().once();
	    mockTrashDAO.commit();
	    EasyMock.expectLastCall().once();
	    mockTrashDAO.close();
	    EasyMock.expectLastCall().once();
	    replay(mockTrashDAO);

	    DAOFactory mockDAOFactory = createMock(DAOFactory.class);
	    expect(mockDAOFactory.getTrashDAO()).andReturn(mockTrashDAO);
	    replay(mockDAOFactory);

	    Configuration.getInstance().setDAOFactory(mockDAOFactory);

	    MockHttpRequest request = MockHttpRequest.post("/v3/files/trash/restore")
	            .content(json)
	            .contentType(MediaType.APPLICATION_JSON);

	    dispatcher.invoke(request, response);

	    assertEquals(Status.NO_CONTENT.getStatusCode(), response.getStatus());
	}



}
