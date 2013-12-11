package org.ndexbio.rest.exceptions.mappers;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import org.ndexbio.rest.exceptions.ValidationException;

public class ValidationExceptionMapper implements ExceptionMapper<ValidationException>
{
    @Override
    public Response toResponse(ValidationException exception)
    {
        return Response
            .status(Status.NOT_ACCEPTABLE)
            .entity(exception.getMessage())
            .build();
    }
}
