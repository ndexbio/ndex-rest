/**
 * Copyright (c) 2013, 2015, The Regents of the University of California, The Cytoscape Consortium
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
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
    	logger.info("[start: email feedback]");   
    	
        if (feedbackType == null || feedbackType.isEmpty()) {
        	logger.error("[end: Feedback type wasn't specified. Throwing NdexException.]");        	
        	throw new NdexException("Feedback type wasn't specified.");
        }
        else if (feedbackText == null || feedbackText.isEmpty()) {
        	logger.error("[end: No feedback was supplied. Throwing NdexException.]"); 
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
                logger.info("[end: email feedback sent for logged user]");  
            }
            else
            {
                Email.sendEmail(Configuration.getInstance().getProperty("Feedback-Email"),
                    Configuration.getInstance().getProperty("Feedback-Email"),
                    feedbackType,
                    feedbackText);
                logger.info("[end: email feedback sent for non-logged user]");
            }
        }
        catch (MessagingException e)
        {
			logger.error("[end: Failed to send feedback email. Exception caught:]{}", e);            
            throw new NdexException("Sorry, we couldn't submit your feedback.");
        }
    }
}
