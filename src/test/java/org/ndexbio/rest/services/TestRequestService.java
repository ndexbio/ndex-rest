package org.ndexbio.rest.services;

import javax.servlet.http.HttpServletRequest;
import org.easymock.EasyMock;
import org.junit.Test;

public class TestRequestService
{
    private static final HttpServletRequest _mockRequest = EasyMock.createMock(HttpServletRequest.class);
    private static final RequestService _requestService = new RequestService(_mockRequest);

    
    
    @Test
    public void createRequest()
    {
    }

    @Test
    public void createRequestInvalid()
    {
    }

    @Test
    public void deleteRequest()
    {
    }

    @Test
    public void deleteRequestInvalid()
    {
    }

    @Test
    public void getRequest()
    {
    }

    @Test
    public void getRequestInvalid()
    {
    }

    @Test
    public void updateRequest()
    {
    }

    @Test
    public void updateRequestInvalid()
    {
    }
}
