package org.ndexbio.common.solr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.ndexbio.model.cx.FunctionTermElement;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit tests for the term-splitting helpers shared by the node index and the file index.
 */
@RunWith(JUnit4.class)
public class TestNodeIndexFields {

	@Test
	public void aUriContributesItsLastPathSegment() {
		assertEquals(List.of("P19838"),
				NodeIndexFields.getIndexableString("http://identifiers.org/uniprot/P19838"));
	}

	@Test
	public void aUriContributesItsFragmentWhenItHasOne() {
		assertEquals(List.of("TP53"),
				NodeIndexFields.getIndexableString("https://example.org/genes#TP53"));
	}

	@Test
	public void aUriWithoutAPathContributesNothing() {
		assertTrue(NodeIndexFields.getIndexableString("http://example.org").isEmpty());
	}

	@Test
	public void aPrefixedNameContributesItselfAndItsIdentifier() {
		assertEquals(List.of("hgnc:TP53", "TP53"), NodeIndexFields.getIndexableString("hgnc:TP53"));
	}

	@Test
	public void aPlainTermContributesItself() {
		assertEquals(List.of("TP53"), NodeIndexFields.getIndexableString("TP53"));
	}

	@Test
	public void aFunctionTermContributesItsNameAndEveryArgumentRecursively() throws Exception {
		FunctionTermElement term = new ObjectMapper().readValue(
				"{\"po\":1,\"f\":\"complex\",\"args\":[\"hgnc:TP53\",{\"f\":\"p\",\"args\":[\"MDM2\"]}]}",
				FunctionTermElement.class);

		assertEquals(List.of("complex", "hgnc:TP53", "TP53", "p", "MDM2"),
				NodeIndexFields.getIndexableStringsFromFunctionTerm(term));
	}
}
