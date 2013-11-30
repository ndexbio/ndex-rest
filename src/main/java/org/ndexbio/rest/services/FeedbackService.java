package org.ndexbio.rest.services;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import org.ndexbio.rest.exceptions.ValidationException;
import org.ndexbio.rest.helpers.Email;

@Path("/feedback")
public class FeedbackService extends NdexService
{
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
        
        //TODO: Refactor this to get settings from a configuration file
        Email.sendEmail("feedback@ndexbio.org", "dexterpratt.bio@gmail.com", feedbackType, feedbackText);
    }
}
