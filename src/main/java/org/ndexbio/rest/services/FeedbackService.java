package org.ndexbio.rest.services;

import javax.mail.MessagingException;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import org.ndexbio.rest.exceptions.NdexException;
import org.ndexbio.rest.helpers.Configuration;
import org.ndexbio.rest.helpers.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/feedback")
public class FeedbackService extends NdexService
{
    private static final Logger _logger = LoggerFactory.getLogger(FeedbackService.class);
    
    
    
    /**************************************************************************
    * Injects the HTTP request into the base class to be used by
    * getLoggedInUser(). 
    * 
    * @param httpRequest
    *            The HTTP request injected by RESTEasy's context.
    **************************************************************************/
    public FeedbackService(@Context HttpServletRequest httpRequest)
    {
        super(httpRequest);
    }
    
    
    
    /**************************************************************************
    * Emails feedback to Cytoscape Consortium.
    * 
    * @param feedbackType
    *            The type of feedback being given.
    * @param feedbackText
    *            The feedback.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws NdexException
    *            Failed to send the email. 
    **************************************************************************/
    @POST
    @Path("/{type}")
    @Produces("application/json")
    public void emailFeedback(@PathParam("type")final String feedbackType, final String feedbackText) throws IllegalArgumentException, NdexException
    {
        if (feedbackType == null || feedbackType.isEmpty())
            throw new IllegalArgumentException("Feedback type wasn't specified.");
        else if (feedbackText == null || feedbackText.isEmpty())
            throw new IllegalArgumentException("No feedback was supplied.");
        
        try
        {
            if (this.getLoggedInUser() != null)
            {
                Email.sendEmail(this.getLoggedInUser().getEmailAddress(),
                    Configuration.getInstance().getProperty("Feedback-Email"),
                    feedbackType,
                    feedbackText);
            }
            else
            {
                Email.sendEmail(Configuration.getInstance().getProperty("Feedback-Email"),
                    Configuration.getInstance().getProperty("Feedback-Email"),
                    feedbackType,
                    feedbackText);
            }
        }
        catch (MessagingException e)
        {
            _logger.error("Failed to send feedback email.", e);
            throw new NdexException("Sorry, we couldn't submit your feedback.");
        }
    }
}
