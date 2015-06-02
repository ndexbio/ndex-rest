package org.ndexbio.rest.exceptions.mappers;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;

import org.ndexbio.model.exceptions.ObjectNotFoundException;

public class ObjectNotFoundExceptionMapper implements ExceptionMapper<ObjectNotFoundException>
{
    @Override
    public Response toResponse(ObjectNotFoundException exception)
    {
        return Response
            .status(Status.NOT_FOUND)
            .entity(exception.getNdexExceptionInJason())
            .type("application/json")
            .build();
    }
}
