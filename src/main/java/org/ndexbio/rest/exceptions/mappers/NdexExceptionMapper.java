package org.ndexbio.rest.exceptions.mappers;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;

import org.ndexbio.model.exceptions.NdexException;

public class NdexExceptionMapper implements ExceptionMapper<NdexException>
{
    @Override
    public Response toResponse(NdexException exception)
    {
        return Response
            .status(Status.INTERNAL_SERVER_ERROR)
            .entity(exception.getNdexExceptionInJason())
            .build();
    }
}
