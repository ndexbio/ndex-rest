package org.ndexbio.rest.services.v3.files;


import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response.Status;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import org.jboss.resteasy.mock.*;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.jboss.resteasy.spi.Dispatcher;
import org.ndexbio.model.errorcodes.NDExError;
import org.ndexbio.rest.exceptions.mappers.UnauthorizedOperationExceptionMapper;

/**
 *
 * @author churas
 */
public class TestFileServiceV3 {
	
    
    //@Rule
    //public TemporaryFolder _folder = new TemporaryFolder();
	
	@Test
    public void testFileCountThrowsException() throws Exception {

        try {
            //File tempDir = _folder.newFolder();
			HttpServletRequest mockHttpServletRequest = createMock(HttpServletRequest.class);
            expect(mockHttpServletRequest.getAttribute("User")).andReturn(null);
            replay(mockHttpServletRequest);
			
			// create a mock dispatcher
            Dispatcher dispatcher = MockDispatcherFactory.createDispatcher();
			
			//register the class of the endpoint you want to test
            dispatcher.getRegistry().addSingletonResource(new FileServiceV3(mockHttpServletRequest));
			
			//if test causes an exception to be thrown be sure to 
			//register the mapper for that exception
			dispatcher.getProviderFactory().registerProvider(UnauthorizedOperationExceptionMapper.class);

			//Create a mock request
            MockHttpRequest request = MockHttpRequest.get("/v3/files/count");

			// Create a mock response
            MockHttpResponse response = new MockHttpResponse();
            

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
}
