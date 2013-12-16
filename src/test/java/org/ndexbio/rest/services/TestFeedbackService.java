package org.ndexbio.rest.services;

import javax.servlet.http.HttpServletRequest;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;
import org.ndexbio.rest.services.FeedbackService;
import org.ndexbio.rest.exceptions.NdexException;

public class TestFeedbackService
{
    private static final HttpServletRequest _mockRequest = EasyMock.createMock(HttpServletRequest.class);
    private static final FeedbackService _feedbackService = new FeedbackService(_mockRequest);

    
    
    @Test(timeout=5000)
    public void emailFeedback()
    {
        try
        {
            _feedbackService.emailFeedback("Question", "Does this test work?");
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void emailFeedbackInvalidType() throws IllegalArgumentException, NdexException
    {
        _feedbackService.emailFeedback(null, "This feedback has no type.");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void emailFeedbackInvalidFeedback() throws IllegalArgumentException, NdexException
    {
        _feedbackService.emailFeedback("Bug", "");
    }
}
