package org.ndexbio.rest.services;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import org.ndexbio.rest.exceptions.ValidationException;
import org.ndexbio.rest.helpers.Configuration;
import org.ndexbio.rest.helpers.Email;

@Path("/feedback")
public class FeedbackService extends NdexService
{
    /**************************************************************************
    * Injects the HTTP request into the base class to be used by
    * getLoggedInUser(). 
    * 
    * @param httpRequest The HTTP request injected by RESTEasy's context.
    **************************************************************************/
    public FeedbackService(@Context HttpServletRequest httpRequest)
    {
        super(httpRequest);
    }
    
    
    
    /**************************************************************************
    * Emails feedback to Cytoscape Consortium. 
    **************************************************************************/
    @POST
    @Path("/{type}")
    @Produces("application/json")
    public void emailFeedback(@PathParam("type")final String feedbackType, final String feedbackText) throws Exception
    {
        if (feedbackType == null || feedbackType.isEmpty())
            throw new ValidationException("Feedback type wasn't specified.");
        else if (feedbackText == null || feedbackText.isEmpty())
            throw new ValidationException("No feedback was supplied.");
        
        Email.sendEmail(this.getLoggedInUser().getEmailAddress(),
            Configuration.getInstance().getProperty("Feedback-Email"),
            feedbackType,
            feedbackText);
    }
}
