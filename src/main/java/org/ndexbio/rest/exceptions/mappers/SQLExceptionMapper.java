package org.ndexbio.rest.exceptions.mappers;

import java.sql.SQLException;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SQLExceptionMapper implements ExceptionMapper<SQLException> {
	static Logger logger = LoggerFactory.getLogger(SQLExceptionMapper.class);

    @Override
    public Response toResponse(SQLException exception)
    {
    	logger.error("SERVER ERROR:", exception);
    	exception.printStackTrace();
        return Response
            .status(Status.INTERNAL_SERVER_ERROR)
            .entity(exception.toString())
            .type("application/json")
            .build();
    }
}
