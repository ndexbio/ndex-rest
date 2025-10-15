package org.ndexbio.common.solr;

import java.util.Collection;

import org.apache.solr.common.SolrInputDocument;
import org.ndexbio.cx2.aspect.element.core.CxNode;

final class NodeIndexDocumentBuilder {

	private static final String FIELD_ID = "id";
	private static final String FIELD_NODE_NAME = "nodeName";
	private static final String FIELD_ALIAS = "alias";
	private static final String FIELD_TEXT = "text";

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

		if (name != null && name.length() > 0) {
			doc.addField(FIELD_NODE_NAME, name);
		}
		if (represents != null && !represents.isEmpty()) {
			for (String rterm : represents) {
				doc.addField(CxNode.REPRESENTS, rterm.trim());
			}
		}
		if (aliases != null && !aliases.isEmpty()) {
			for (String aTerm : aliases) {
				doc.addField(FIELD_ALIAS, aTerm.trim());
			}
		}
		if (textValues != null) {
			for (String txt : textValues) {
				doc.addField(FIELD_TEXT, txt.trim());
			}
		}
		return doc;
	}
}
