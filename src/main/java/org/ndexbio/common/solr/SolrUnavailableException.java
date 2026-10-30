package org.ndexbio.common.solr;

import org.ndexbio.model.exceptions.NdexException;

/**
 * Solr could not be reached, or did not answer within the client's timeouts.
 *
 * <p>Distinct from other Solr failures so callers can tell "the search service is down, retry
 * later" apart from a bad query or a server-side error. It is mapped to HTTP 503.</p>
 */
public class SolrUnavailableException extends NdexException {

	private static final long serialVersionUID = 1L;

	public static final String MESSAGE = "Search service unavailable, retry later";

	public SolrUnavailableException(Throwable cause) {
		super(MESSAGE, cause);
	}
}
