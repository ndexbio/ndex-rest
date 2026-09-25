package org.ndexbio.rest.services;

import java.lang.reflect.Method;

import org.junit.Assert;
import org.junit.Test;
import org.ndexbio.model.object.network.NetworkSummary;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

/**
 * What {@code PUT /v2/network/{networkid}/summary} promises its callers.
 *
 * <p>The operation replaces name, description, version, visibility and properties as a set, which is
 * the part a caller gets wrong: a body carrying one field silently clears the rest, and one without a
 * visibility is refused outright. Neither is discoverable from the signature, so the documented
 * contract is the only place a client learns it — and an undocumented rejection reads as a server
 * fault rather than a request to fix.</p>
 */
public class TestNetworkServiceV2Doc {

	private static Method updateNetworkSummary() throws Exception {
		return NetworkServiceV2.class.getMethod("updateNetworkSummary", String.class, NetworkSummary.class);
	}

	@Test
	public void theSummaryUpdateDocumentsThatItReplacesEveryFieldAtOnce() throws Exception {
		Operation op = updateNetworkSummary().getAnnotation(Operation.class);
		Assert.assertNotNull("updateNetworkSummary must carry an @Operation doc", op);
		String desc = op.description().toLowerCase();

		Assert.assertTrue("the doc should say the fields are replaced together: " + desc,
				desc.contains("together") || desc.contains("all five"));
		Assert.assertTrue("the doc should name the endpoint that changes a subset: " + desc,
				desc.contains("profile"));
	}

	@Test
	public void theSummaryUpdateDocumentsItsRejectionOfAnIncompletePayload() throws Exception {
		ApiResponses responses = updateNetworkSummary().getAnnotation(ApiResponses.class);
		Assert.assertNotNull("updateNetworkSummary must document its responses", responses);

		ApiResponse badRequest = null;
		for (ApiResponse r : responses.value()) {
			if ("400".equals(r.responseCode())) {
				badRequest = r;
			}
		}
		Assert.assertNotNull("a payload without a visibility is refused, so 400 must be documented",
				badRequest);
		Assert.assertTrue("the 400 should say what makes the payload incomplete: " + badRequest.description(),
				badRequest.description().toLowerCase().contains("visibility"));
	}
}
