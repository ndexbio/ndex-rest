package org.ndexbio.rest.services.v3.files;


import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import org.apache.zookeeper.proto.ErrorResponse;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import org.jboss.resteasy.mock.*;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.jboss.resteasy.spi.Dispatcher;

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
			
            Dispatcher dispatcher = MockDispatcherFactory.createDispatcher();
            dispatcher.getRegistry().addSingletonResource(new FileServiceV3(mockHttpServletRequest));

            MockHttpRequest request = MockHttpRequest.get("/v3/files/count");

            MockHttpResponse response = new MockHttpResponse();
            
      
            dispatcher.invoke(request, response);
            assertEquals(500, response.getStatus());
            //ObjectMapper mapper = new ObjectMapper();
            //ErrorResponse er = mapper.readValue(response.getOutput(),
             //       ErrorResponse.class);
			String errStr = new String(response.getOutput(), StandardCharsets.UTF_8);
            assertEquals("You must be signed in to see your file counts.", errStr);
			//assertEquals("hi", response.getOutput().toString());
            //assertEquals(ErrorCode.NDEx_Unauthorized_Operation_Exception, er.getErr());
        } finally {
            //_folder.delete();
        }
    }
}
