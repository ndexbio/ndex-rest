package org.ndexbio.rest.exceptions.mappers;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.ExceptionMapper;

import org.ndexbio.common.solr.SolrUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Answers 503 when Solr is unreachable or timed out, so clients back off and retry instead of
 * treating it as a server fault. More specific than {@link NdexExceptionMapper}, so it wins for
 * this subclass.
 */
public class SolrUnavailableExceptionMapper implements ExceptionMapper<SolrUnavailableException> {
	static Logger logger = LoggerFactory.getLogger(SolrUnavailableExceptionMapper.class);

    @Override
    public Response toResponse(SolrUnavailableException exception)
    {
    	MDC.put("error", exception.getMessage());
    	logger.error("SERVER ERROR:", exception);
        return Response
            .status(Status.SERVICE_UNAVAILABLE)
            .entity(exception.getNdexExceptionInJason())
            .type("application/json")
            .build();
    }
}
