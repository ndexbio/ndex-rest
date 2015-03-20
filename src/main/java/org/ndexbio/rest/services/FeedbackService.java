package org.ndexbio.rest.services;

import javax.mail.MessagingException;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;

import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.rest.helpers.Email;
import org.ndexbio.task.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/feedback")
public class FeedbackService extends NdexService
{
	static Logger logger = LoggerFactory.getLogger(FeedbackService.class);
    
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
    	logger.info(userNameForLog() + "[start: email feedback]");   
    	
        if (feedbackType == null || feedbackType.isEmpty()) {
        	logger.error(userNameForLog() + "[end: Feedback type wasn't specified. Throwing IllegalArgumentException.]"); 
        	throw new IllegalArgumentException("Feedback type wasn't specified.");
        }
        else if (feedbackText == null || feedbackText.isEmpty()) {
        	logger.error(userNameForLog() + "[end: No feedback was supplied. Throwing IllegalArgumentException.]"); 
        	throw new IllegalArgumentException("No feedback was supplied.");
        }
        try
        {
            if (this.getLoggedInUser() != null)
            {
                Email.sendEmail(this.getLoggedInUser().getEmailAddress(),
                    Configuration.getInstance().getProperty("Feedback-Email"),
                    feedbackType,
                    feedbackText);
                logger.info(userNameForLog() + "[end: email feedback sent for logged user.]"); 
            }
            else
            {
                Email.sendEmail(Configuration.getInstance().getProperty("Feedback-Email"),
                    Configuration.getInstance().getProperty("Feedback-Email"),
                    feedbackType,
                    feedbackText);
                logger.info(userNameForLog() + "[end: email feedback sent for non-logged user.]");
            }
        }
        catch (MessagingException e)
        {
            //_logger.error("Failed to send feedback email.", e);

			logger.error(userNameForLog() + "[end: Failed to send feedback email. Exception caught:]", e);            
            throw new NdexException("Sorry, we couldn't submit your feedback.");
        }
    }
}
