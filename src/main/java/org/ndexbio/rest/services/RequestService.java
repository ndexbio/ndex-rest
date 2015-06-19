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

import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.models.dao.orientdb.RequestDAO;
import org.ndexbio.model.exceptions.DuplicateObjectException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.object.Request;
import org.ndexbio.rest.annotations.ApiDoc;
import org.slf4j.LoggerFactory;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;

import org.slf4j.Logger;

@Path("/request")
public class RequestService extends NdexService
{
	private RequestDAO dao;
	private ODatabaseDocumentTx  localConnection; 

	static Logger logger = LoggerFactory.getLogger(RequestService.class);
    
    /**************************************************************************
    * Injects the HTTP request into the base class to be used by
    * getLoggedInUser(). 
    * 
    * @param httpRequest
    *            The HTTP request injected by RESTEasy's context.
    **************************************************************************/
    public RequestService(@Context HttpServletRequest httpRequest) {
        super(httpRequest);
    }
    
    /**************************************************************************
    * Creates a request. 
    * 
    * @return The newly created request.
    **************************************************************************/
    @POST
    @Produces("application/json")
	@ApiDoc("Create a new request based on a request JSON structure. Returns the JSON structure including the assigned database id.")
    public Request createRequest(final Request newRequest) 
    		throws IllegalArgumentException, DuplicateObjectException, NdexException {
       
    	//logInfo ( logger, "Creating " + newRequest.getType() + " request for " + newRequest.getDestinationName());
    	
		logger.info(userNameForLog() + "[start: Creating " + newRequest.getType() + " request for " + newRequest.getDestinationName() + "]");
		this.openDatabase();
		
		try {
			Request request = dao.createRequest(newRequest, this.getLoggedInUser());
			dao.commit();
			//logInfo ( logger, "Request " + request.getExternalId() +" created.");
			logger.info(userNameForLog(), "[end: Request " + request.getExternalId() +" created.]");
			return request;
		} finally {
			this.closeDatabase();
		}
    	
    }

    /**************************************************************************
    * Deletes a request.
    * 
    * 
    **************************************************************************/
    @DELETE
    @Path("/{requestId}")
    @Produces("application/json")
	@ApiDoc("Deletes the request specified by requestId. Errors if requestId not specified or if request not found.")
    public void deleteRequest(@PathParam("requestId")final String requestId) 
    		throws IllegalArgumentException, ObjectNotFoundException, NdexException {
        
		logger.info(userNameForLog() + "[start: Deleting request " + requestId + "]");
		
    	//logInfo ( logger, "Deleting request " + requestId );
    	this.openDatabase();
		
		try {
			dao.deleteRequest(UUID.fromString(requestId), this.getLoggedInUser());
			dao.commit();
			//logInfo ( logger, "Request " + requestId + " deleted");
			logger.info(userNameForLog(), "[end: Request " + requestId + " deleted.]");
		} finally {
			this.closeDatabase();

		}
    	
    }

    /**************************************************************************
    * Gets a request by ID.
    * 
    * @param requestId
    *           The request ID.
    * @throws IllegalArgumentException
    *           Bad input.
    * @throws NdexException
    *           Failed to query the database.
    * @return The request.
    **************************************************************************/
    @GET
    @Path("/{requestId}")
    @Produces("application/json")
	@ApiDoc("Returns the request JSON structure for the request specified by requestId. Errors if requestId not specified or if request not found.")
    public Request getRequest(@PathParam("requestId")final String requestId) 
    		throws IllegalArgumentException, NdexException {
       
    	//logInfo ( logger, "Getting request " + requestId );
    	logger.info(userNameForLog() + "[start: Getting request " + requestId + "]");
 
    	this.openDatabase();
		
		try {
			final Request request = dao.getRequest(UUID.fromString(requestId), this.getLoggedInUser());
			//logInfo ( logger, "Request object for id " + requestId + " returned.");
			logger.info(userNameForLog() + "[end: Got request " + requestId + "]");
			return request;
		} finally {
			this.closeDatabase();

		}
    }

    /**************************************************************************
    * Updates a request.
    * 
    **************************************************************************/
    @POST
    @Path("/{requestId}")
    @Produces("application/json")
	@ApiDoc("Updates a request corresponding to the POSTed request JSON structure. " +
			"The request JSON must specify the request id. " +
			"Errors if requestId is not specified or if request is not found." +
			"If the response field of the request is updated such that the request is accepted, then the action associated with the request is performed.")
    public void updateRequest(@PathParam("requestId")final String requestId, final Request updatedRequest)
    		throws IllegalArgumentException, NdexException {
    	
    	//logInfo( logger, "Updating request " + requestId);
   
    	logger.info(userNameForLog() + "[start: Updating request " + requestId + "]");
    	
    	this.openDatabase();
		
		try {
			dao.updateRequest(UUID.fromString(requestId), updatedRequest, this.getLoggedInUser());
			dao.commit();
			//logInfo ( logger, "Request " + requestId + " updated.");
			logger.info(userNameForLog() + "[end: Updated request " + requestId + "]");			
		} finally {
			this.closeDatabase();

		}
    }
    
  
    
    private void openDatabase() throws NdexException {
    	localConnection = NdexDatabase.getInstance().getAConnection();
		dao = new RequestDAO(localConnection);
	}
	private void closeDatabase() {
		dao.close();
	}
    
    
}