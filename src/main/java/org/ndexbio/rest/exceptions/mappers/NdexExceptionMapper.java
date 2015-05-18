package org.ndexbio.rest.exceptions.mappers;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;

import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.rest.services.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NdexExceptionMapper implements ExceptionMapper<NdexException>
{
	
	static Logger logger = LoggerFactory.getLogger(NdexExceptionMapper.class);

	
    @Override
    public Response toResponse(NdexException exception)
    {
    	logger.error("SERVER ERROR", exception);
        return Response
            .status(Status.INTERNAL_SERVER_ERROR)
            .entity(exception.getNdexExceptionInJason())
            .build();
    }
}
