/**
 * Copyright (c) 2013, 2016, The Regents of the University of California, The Cytoscape Consortium
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

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;

import org.ndexbio.common.models.dao.postgresql.RequestDAO;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.Request;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

@Path("/v2/request")
public class RequestServiceV2 extends NdexService
{
	//static Logger logger = LoggerFactory.getLogger(RequestServiceV2.class);
    
    /**************************************************************************
    * Injects the HTTP request into the base class to be used by
    * getLoggedInUser(). 
    * 
    * @param httpRequest
    *            The HTTP request injected by RESTEasy's context.
    **************************************************************************/
    public RequestServiceV2(@Context HttpServletRequest httpRequest) {
        super(httpRequest);
    }
    
    /**************************************************************************
    * Creates a request. 
    * 
    * @return The newly created request.
     * @throws SQLException 
     * @throws IOException 
     * @throws JsonMappingException 
     * @throws JsonParseException 
    **************************************************************************/
/*    @POST
    @Produces("application/json")
	@ApiDoc("Create a new request based on a request JSON structure. Returns the JSON structure including the assigned UUID of this request."
			+ "CreationDate, modificationDate, and sourceName fields will be ignored in the input object. A user can only create request for "
			+ "himself or the group that he is a member of.")
    public Request createRequest(final Request newRequest) 
    		throws IllegalArgumentException, DuplicateObjectException, NdexException, SQLException, JsonParseException, JsonMappingException, IOException {

		logger.info("[start: Creating request for {}]", newRequest.getDestinationName());
		
		if ( newRequest.getRequestType() == null)
			throw new NdexException ("RequestType is missing in the posted request object.");
				
		try (RequestDAO dao = new RequestDAO ()){			
			Request request = dao.createRequest(newRequest, this.getLoggedInUser());
			dao.commit();
			return request;
		} finally {
			logger.info("[end: Request created]");
		}
    	
    } */

    /**************************************************************************
    * Deletes a request.
     * @throws SQLException 
    * 
    * 
    **************************************************************************/
/*    @DELETE
    @Path("/{requestid}")
    @Produces("application/json")
	@ApiDoc("Deletes the request specified by requestId. Errors if requestId not specified or if request not found.")
    public void deleteRequest(@PathParam("requestid")final String requestId) 
    		throws IllegalArgumentException, ObjectNotFoundException, NdexException, SQLException {

		logger.info("[start: Deleting request {}]", requestId);
		
		try (RequestDAO dao = new RequestDAO()) {
			
			dao.deleteRequest(UUID.fromString(requestId), this.getLoggedInUserId());
			dao.commit();
		} finally {
			logger.info("[end: Request deleted]");
		}
    	
    } */

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
     * @throws SQLException 
     * @throws IOException 
     * @throws JsonMappingException 
     * @throws JsonParseException 
    **************************************************************************/
    @GET
    @Path("/{requestid}")
    @Produces("application/json")
    public Request getRequest(@PathParam("requestid")final String requestId) 
    		throws IllegalArgumentException, NdexException, SQLException, JsonParseException, JsonMappingException, IOException {
 				
		try (RequestDAO dao = new RequestDAO()){
			final Request request = dao.getRequest(UUID.fromString(requestId), this.getLoggedInUser());
			return request;
		} 
    }

    /**************************************************************************
    * Updates a request.
     * @throws SQLException 
     * @throws JsonProcessingException 
    * 
    **************************************************************************/
 /*   @PUT
    @Path("/{requestid}")
    @Produces("application/json")
	@ApiDoc("Updates a request corresponding to the POSTed request JSON structure. " +
			"The request JSON must specify the request id. " +
			"Errors if requestId is not specified or if request is not found." +
			"If the response field of the request is updated such that the request is accepted, then the action associated with the request is performed.")
    public void updateRequest(@PathParam("requestid")final String requestId, final Request updatedRequest)
    		throws IllegalArgumentException, NdexException, SQLException, JsonProcessingException {
  
		logger.info("[start: Updating request {}]", requestId);
		
		try (RequestDAO dao = new RequestDAO()) {
			dao.updateRequest(UUID.fromString(requestId), updatedRequest, this.getLoggedInUser());
			dao.commit();
		} finally {
			logger.info("[end: Updated request {}]", requestId);
		}
    } */

    
    /**************************************************************************
    * Updates a request.
     * @throws SQLException 
     * @throws JsonProcessingException 
    * 
    **************************************************************************/
    @PUT
    @Path("/{requestid}/properties")
    @Produces("application/json")
	
    public void updateRequestProperties(@PathParam("requestid")final String requestId, final Map<String,Object> properties)
    		throws IllegalArgumentException, NdexException, SQLException, JsonProcessingException {
  
		try (RequestDAO dao = new RequestDAO()) {
			dao.updateRequestProperties(UUID.fromString(requestId), properties, this.getLoggedInUser());
			dao.commit();
		}
	}

}