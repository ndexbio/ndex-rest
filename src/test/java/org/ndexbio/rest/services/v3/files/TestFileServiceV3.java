package org.ndexbio.rest.services.v3.files;


import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response.Status;
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
import org.ndexbio.model.errorcodes.NDExError;
import org.ndexbio.model.object.FileCount;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.exceptions.mappers.UnauthorizedOperationExceptionMapper;

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
}
