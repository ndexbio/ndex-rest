package org.ndexbio.common.solr;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.ndexbio.cx2.aspect.element.core.CxNode;
import org.ndexbio.cx2.aspect.element.core.DeclarationEntry;
import org.ndexbio.cxio.aspects.datamodels.ATTRIBUTE_DATA_TYPE;
import org.ndexbio.model.cx.FunctionTermElement;
import org.ndexbio.model.tools.TermUtilities;

/**
 * CX2 node attribute names that participate in node indexing, plus the shared
 * accessors that pull indexable terms out of a {@link CxNode} using a network's
 * attribute declarations.
 *
 * <p>These were previously static members of {@link SingleNetworkSolrIdxManager}. They
 * are consumed both by the per-network node index and by the network documents of the
 * file index ({@link GlobalNetworkIndexManager}), so they live here to avoid the two managers
 * depending on each other.
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
					result.addAll(getIndexableString(v));
				}
			} else if (t == ATTRIBUTE_DATA_TYPE.LIST_OF_STRING) {
				List<String> vl = (List<String>) node.getAttributes().get(actualAttrName);
				if (vl != null) {
					for (String v : vl) {
						result.addAll(getIndexableString(v));
					}
				}
			}
		}

		return result;
	}

	/**
	 * Terms a function term contributes to an index: its name and every argument, recursively.
	 */
	static List<String> getIndexableStringsFromFunctionTerm(FunctionTermElement e)  {

		List<String> terms = getIndexableString(e.getFunctionName());
		for (Object arg: e.getArgs()) {
			if (arg instanceof String ) {
				terms.addAll(getIndexableString((String)arg));
			} else if ( arg instanceof FunctionTermElement ){
				terms.addAll(getIndexableStringsFromFunctionTerm((FunctionTermElement)arg));
			}
		}

		return terms;
	}

	/**
	 * Splits a term into the strings worth indexing: the identifier of a URI, both the qualified
	 * name and the identifier of a prefixed name, or the term itself.
	 */
	static List<String> getIndexableString(String termString) {

		// case 1 : termString is a URI
		// example: http://identifiers.org/uniprot/P19838
		// treat the last element in the URI as the identifier and the rest as
		// prefix string. Just to help the future indexing.
		//
		List<String> result = new ArrayList<>(2) ;
		String identifier = null;
		if ( termString.length() > 10 && (termString.substring(0, 7).equalsIgnoreCase("http://") ||
				termString.substring(0, 8).equalsIgnoreCase("https://") )&&
				(!termString.endsWith("/"))) {
			try {
				URI termStringURI = new URI(termString);
				identifier = termStringURI.getFragment();

				if ( identifier == null ) {
					String path = termStringURI.getPath();
					if (path != null && path.indexOf("/") != -1) {
						int pos = termString.lastIndexOf('/');
						identifier = termString.substring(pos + 1);
					} else
						return result; // the string is a URL in the format that we don't want to index it in Solr.
				}
				result.add(identifier);
				return result;

			} catch (URISyntaxException e) {
				// ignore and move on to next case
			}
		}

		String[] termStringComponents = TermUtilities.getNdexQName(termString);
		if (termStringComponents != null && termStringComponents.length == 2) {
			// case 2: termString is of the form (NamespacePrefix:)*Identifier
			//     if ( !termStringComponents[0].contains(" "))
			result.add(termString);
			result.add(termStringComponents[1]);
			return  result;
		}

		// case 3: termString cannot be parsed, use it as the identifier.
		// so leave the prefix as null and return the string
		result.add(termString);
		return result;

	}
}
