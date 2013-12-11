package org.ndexbio.rest.exceptions.mappers;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import org.ndexbio.rest.exceptions.DuplicateObjectException;

public class DuplicateObjectExceptionMapper implements ExceptionMapper<DuplicateObjectException>
{
    @Override
    public Response toResponse(DuplicateObjectException exception)
    {
        return Response
            .status(Status.CONFLICT)
            .entity(exception.getMessage())
            .build();
    }
}
