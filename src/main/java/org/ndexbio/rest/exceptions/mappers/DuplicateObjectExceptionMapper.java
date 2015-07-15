package org.ndexbio.rest.exceptions.mappers;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.ndexbio.model.exceptions.DuplicateObjectException;

@Provider
public class DuplicateObjectExceptionMapper implements ExceptionMapper<DuplicateObjectException>
{
    @Override
    public Response toResponse(DuplicateObjectException exception)
    {
        return Response
                .status(Status.CONFLICT)
                .entity(exception.getNdexExceptionInJason())
                .type("application/json")
                .build();
    }
}
