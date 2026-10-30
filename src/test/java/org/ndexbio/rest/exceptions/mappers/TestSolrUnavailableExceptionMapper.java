package org.ndexbio.rest.exceptions.mappers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.ConnectException;

import jakarta.ws.rs.core.Response;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.ndexbio.common.solr.SolrUnavailableException;

@RunWith(JUnit4.class)
public class TestSolrUnavailableExceptionMapper {

	@Test
	public void mapsToServiceUnavailableWithTheNdexErrorBody() {
		Response response = new SolrUnavailableExceptionMapper().toResponse(
				new SolrUnavailableException(new ConnectException("Connection refused")));

		assertEquals(503, response.getStatus());
		assertEquals("application/json", response.getMediaType().toString());
		assertTrue(response.getEntity().toString().contains(SolrUnavailableException.MESSAGE));
	}
}
