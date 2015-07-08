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

		logger.info("[start: Creating {} request for {}]", newRequest.getType(), newRequest.getDestinationName());
		
		this.openDatabase();
		
		Request request = null;
		try {
			request = dao.createRequest(newRequest, this.getLoggedInUser());
			dao.commit();
			return request;
		} finally {
			this.closeDatabase();
			logger.info("[end: Request {} created]", 
					(request != null) ? request.getExternalId() : "null");
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

		logger.info("[start: Deleting request {}]", requestId);

    	this.openDatabase();
		
		try {
			dao.deleteRequest(UUID.fromString(requestId), this.getLoggedInUser());
			dao.commit();
		} finally {
			this.closeDatabase();
			logger.info("[end: Request {} deleted]", requestId);
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
 
		logger.info("[start: Getting request {}]", requestId);
		
    	this.openDatabase();
		
		try {
			final Request request = dao.getRequest(UUID.fromString(requestId), this.getLoggedInUser());
			return request;
		} finally {
			this.closeDatabase();
			logger.info("[end: Got request {}]", requestId);
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
  
		logger.info("[start: Updating request {}]", requestId);
    	
    	this.openDatabase();
		
		try {
			dao.updateRequest(UUID.fromString(requestId), updatedRequest, this.getLoggedInUser());
			dao.commit();
		} finally {
			this.closeDatabase();
			logger.info("[end: Updated request {}]", requestId);
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