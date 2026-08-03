package org.ndexbio.common.solr;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

import org.apache.solr.client.solrj.request.ConfigSetAdminRequest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Wiring tests for {@link SolrObjectFactoryImpl}.
 *
 * <p>The factory is the single place production code obtains a
 * {@link SingleNetworkSolrIdxManager}, so it is where the manager's Solr client and CX2 aspect
 * reader get supplied.
 */
@RunWith(JUnit4.class)
public class TestSolrObjectFactoryImpl {

	private static final String SOLR_URL = "http://localhost:8983/solr";
	private static final String NDEX_ROOT = "/opt/ndex";
	private static final String NETWORK_ID = "ec25aeeb-fb51-11ef-b81d-005056ae3c32";

	@Test
	public void buildsAFullyWiredNodeIndexManager() {
		SolrObjectFactoryImpl factory = new SolrObjectFactoryImpl(SOLR_URL, NDEX_ROOT);

		try (SingleNetworkSolrIdxManager mgr = factory.getSingleNetworkSolrIdxManager(NETWORK_ID)) {
			assertNotNull(mgr);
		}
	}

	@Test
	public void eachManagerGetsItsOwnClientSoClosingOneDoesNotCloseAnother() {
		SolrObjectFactoryImpl factory = new SolrObjectFactoryImpl(SOLR_URL, NDEX_ROOT);

		SingleNetworkSolrIdxManager first = factory.getSingleNetworkSolrIdxManager(NETWORK_ID);
		SingleNetworkSolrIdxManager second = factory.getSingleNetworkSolrIdxManager(NETWORK_ID);

		assertNotSame("callers close managers independently, so they must not share a client",
				first, second);

		first.close();
		second.close();
	}

	@Test
	public void exposesAConfigSetCreateRequest() {
		SolrObjectFactoryImpl factory = new SolrObjectFactoryImpl(SOLR_URL, NDEX_ROOT);

		ConfigSetAdminRequest.Create request = factory.getConfigSetAdminRequestCreate();

		assertNotNull("needed so the extra-fields index path can be exercised in tests", request);
	}
}
