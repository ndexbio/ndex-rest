package org.ndexbio.rest.exceptions.mappers;

import java.sql.SQLException;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.ExceptionMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class SQLExceptionMapper implements ExceptionMapper<SQLException> {
	static Logger logger = LoggerFactory.getLogger(SQLExceptionMapper.class);

    @Override
    public Response toResponse(SQLException exception)
    {
    	logger.error("SERVER ERROR:", exception);
    	MDC.put("error", exception.getMessage());
    	exception.printStackTrace();
        return Response
            .status(Status.INTERNAL_SERVER_ERROR)
            .entity(exception.toString())
            .type("application/json")
            .build();
    }
}
