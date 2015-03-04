package org.ndexbio.rest.exceptions.mappers;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;

import org.ndexbio.model.exceptions.UnautherizedOperationException;

public class UnautherizedOperationExceptionMapper implements ExceptionMapper<UnautherizedOperationException>
{
    @Override
    public Response toResponse(UnautherizedOperationException exception)
    {
        return Response
            .status(Status.UNAUTHORIZED)
            .entity(exception.getMessage())
            .build();
    }
}
