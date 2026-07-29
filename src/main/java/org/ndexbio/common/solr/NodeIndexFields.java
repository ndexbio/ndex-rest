package org.ndexbio.common.solr;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.ndexbio.cx2.aspect.element.core.CxNode;
import org.ndexbio.cx2.aspect.element.core.DeclarationEntry;
import org.ndexbio.cxio.aspects.datamodels.ATTRIBUTE_DATA_TYPE;

/**
 * CX2 node attribute names that participate in node indexing, plus the shared
 * accessors that pull indexable terms out of a {@link CxNode} using a network's
 * attribute declarations.
 *
 * <p>These were previously static members of {@link SingleNetworkSolrIdxManager}. They
 * are consumed both by the per-network node index and by the global network index, so
 * they live here to avoid the two managers depending on each other.
 */
public final class NodeIndexFields {

	public static final String TYPE = "type";
	public static final String COMPLEX = "complex";
	public static final String PROTEINFAMILY = "proteinfamily";
	public static final String MEMBER = "member";
	public static final String ALIAS = "alias";

	private NodeIndexFields() {
		// constants and shared accessors only
	}

	/**
	 * Returns the single string value of {@code attrName} on {@code node}, or {@code null}
	 * when the attribute is not declared, not a string, or absent from the node.
	 *
	 * @param attrName             lower-cased attribute name to look up
	 * @param node                 node to read, already expanded to a full node
	 * @param attributeNameMapping lower-cased attribute name to its actual name and declaration
	 */
	static String getSingleIndexableTermFromNode(String attrName, CxNode node,
			Map<String, Map.Entry<String, DeclarationEntry>> attributeNameMapping) {

		Map.Entry<String, DeclarationEntry> entry = attributeNameMapping.get(attrName);

		if (entry != null) {
			String actualAttrName = entry.getKey();
			ATTRIBUTE_DATA_TYPE t = entry.getValue().getDataType();
			if (t == null || t == ATTRIBUTE_DATA_TYPE.STRING) {
				return (String) node.getAttributes().get(actualAttrName);
			}
		}

		return null;
	}

	/**
	 * Returns every indexable term for {@code attrName} on {@code node}, splitting each raw
	 * value into its indexable parts. Handles both string and list-of-string attributes;
	 * returns an empty list when the attribute is not declared or absent.
	 *
	 * @param attrName             lower-cased attribute name to look up
	 * @param node                 node to read, already expanded to a full node
	 * @param attributeNameMapping lower-cased attribute name to its actual name and declaration
	 */
	@SuppressWarnings("unchecked")
	static List<String> getSplitableTerms(String attrName, CxNode node,
			Map<String, Map.Entry<String, DeclarationEntry>> attributeNameMapping) {

		List<String> result = new ArrayList<>();

		Map.Entry<String, DeclarationEntry> entry = attributeNameMapping.get(attrName);

		if (entry != null) {
			String actualAttrName = entry.getKey();
			ATTRIBUTE_DATA_TYPE t = entry.getValue().getDataType();

			if (t == null || t == ATTRIBUTE_DATA_TYPE.STRING) {
				String v = (String) node.getAttributes().get(actualAttrName);
				if (v != null) {
					result.addAll(NetworkGlobalIndexManager.getIndexableString(v));
				}
			} else if (t == ATTRIBUTE_DATA_TYPE.LIST_OF_STRING) {
				List<String> vl = (List<String>) node.getAttributes().get(actualAttrName);
				if (vl != null) {
					for (String v : vl) {
						result.addAll(NetworkGlobalIndexManager.getIndexableString(v));
					}
				}
			}
		}

		return result;
	}
}
