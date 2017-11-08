package org.ndexbio.rest.exceptions.mappers;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;

import org.ndexbio.model.exceptions.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class BadRequestExceptionMapper implements ExceptionMapper<BadRequestException> {
	static Logger logger = LoggerFactory.getLogger(BadRequestExceptionMapper.class);

    @Override
    public Response toResponse(BadRequestException exception)
    {
    	MDC.put("error", exception.getMessage());
    	logger.error("SERVER ERROR:", exception);
        return Response
            .status(Status.BAD_REQUEST)
            .entity(exception.getNdexExceptionInJason())
            .type("application/json")
            .build();
    }
}
