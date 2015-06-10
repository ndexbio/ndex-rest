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
