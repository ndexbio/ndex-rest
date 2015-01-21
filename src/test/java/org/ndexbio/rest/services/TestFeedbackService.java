package org.ndexbio.rest.services;

import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.rest.services.FeedbackService;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestFeedbackService extends TestNdexService
{
    private static final FeedbackService _feedbackService = new FeedbackService(_mockRequest);

    
  /*  
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
    } */
}
