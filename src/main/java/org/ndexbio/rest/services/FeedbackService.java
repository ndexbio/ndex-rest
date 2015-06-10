/**
 *   Copyright (c) 2013, 2015
 *  	The Regents of the University of California
 *  	The Cytoscape Consortium
 *
 *   Permission to use, copy, modify, and distribute this software for any
 *   purpose with or without fee is hereby granted, provided that the above
 *   copyright notice and this permission notice appear in all copies.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 *   WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 *   MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 *   ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 *   WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 *   ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 *   OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */
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
    * @throws NdexException
    *            Failed to send the email. 
    **************************************************************************/
    @POST
    @Path("/{type}")
    @Produces("application/json")
    public void emailFeedback(@PathParam("type")final String feedbackType, final String feedbackText) throws NdexException
    {
    	logger.info(userNameForLog() + "[start: email feedback]");   
    	
        if (feedbackType == null || feedbackType.isEmpty()) {
        	logger.error(userNameForLog() + "[end: Feedback type wasn't specified. Throwing NdexException.]"); 
        	throw new NdexException("Feedback type wasn't specified.");
        }
        else if (feedbackText == null || feedbackText.isEmpty()) {
        	logger.error(userNameForLog() + "[end: No feedback was supplied. Throwing NdexException.]"); 
        	throw new NdexException("No feedback was supplied.");
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
