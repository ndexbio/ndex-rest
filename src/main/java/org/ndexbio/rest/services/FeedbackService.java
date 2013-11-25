package org.ndexbio.rest.services;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import org.ndexbio.rest.helpers.Email;

@Path("/feedback")
public class FeedbackService
{
    @POST
    @Path("/{type}")
    @Produces("application/json")
    public void emailFeedback(@PathParam("type")final String feedbackType, final String feedbackText) throws Exception
    {
        //TODO: Refactor this to get settings from a configuration file
        Email.sendEmail("feedback@ndexbio.org", "dexterpratt.bio@gmail.com", feedbackType, feedbackText);
    }
}
