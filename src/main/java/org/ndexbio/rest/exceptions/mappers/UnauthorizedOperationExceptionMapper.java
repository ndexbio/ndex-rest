package org.ndexbio.rest.exceptions.mappers;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;

import org.ndexbio.model.exceptions.UnauthorizedOperationException;

public class UnauthorizedOperationExceptionMapper implements ExceptionMapper<UnauthorizedOperationException>
{
    @Override
    public Response toResponse(UnauthorizedOperationException exception)
    {
        return Response
            .status(Status.UNAUTHORIZED)
            .entity(exception.getMessage())
            .build();
    }
}
