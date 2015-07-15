package org.ndexbio.rest.exceptions.mappers;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.ndexbio.model.exceptions.ForbiddenOperationException;

@Provider
public class ForbiddenOperationExceptionMapper implements ExceptionMapper<ForbiddenOperationException>
{	
    @Override
    public Response toResponse(ForbiddenOperationException exception)
    {
        return Response
            .status(Status.FORBIDDEN)
            .entity(exception.getNdexExceptionInJason())
            .type("application/json")
            .build();
    }
}
