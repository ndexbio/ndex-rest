package org.ndexbio.common.solr;

import java.util.Collection;

import org.apache.solr.common.SolrInputDocument;
import org.ndexbio.cx2.aspect.element.core.CxNode;

final class NodeIndexDocumentBuilder {

	private static final String FIELD_ID = "id";
	private static final String FIELD_NODE_NAME = "nodeName";
	private static final String FIELD_ALIAS = "alias";
	private static final String FIELD_TEXT = "text";
	static final int DEFAULT_BATCH_SIZE = 2000;

	private NodeIndexDocumentBuilder() {
		// utility class
	}

	static SolrInputDocument buildNodeDocument(Long id, String name, Collection<String> represents,
			Collection<String> aliases, Collection<String> textValues) {

		if (id == null) {
			throw new IllegalArgumentException("Node id cannot be null");
		}

		SolrInputDocument doc = new SolrInputDocument();
		doc.addField(FIELD_ID, id);

		addTrimmedField(doc, FIELD_NODE_NAME, name);
		addTrimmedFields(doc, CxNode.REPRESENTS, represents);
		addTrimmedFields(doc, FIELD_ALIAS, aliases);
		addTrimmedFields(doc, FIELD_TEXT, textValues);

		return doc;
	}

	private static void addTrimmedField(SolrInputDocument doc, String fieldName, String value) {
		String normalized = trimToNull(value);
		if (normalized != null) {
			doc.addField(fieldName, normalized);
		}
	}

	private static void addTrimmedFields(SolrInputDocument doc, String fieldName, Collection<String> values) {
		if (values == null || values.isEmpty()) {
			return;
		}
		for (String value : values) {
			addTrimmedField(doc, fieldName, value);
		}
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
