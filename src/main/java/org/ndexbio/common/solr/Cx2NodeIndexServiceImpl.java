package org.ndexbio.common.solr;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.ndexbio.common.persistence.CX2NetworkLoader;
import org.ndexbio.cx2.aspect.element.core.CxAttributeDeclaration;
import org.ndexbio.cx2.aspect.element.core.CxNode;
import org.ndexbio.cx2.aspect.element.core.DeclarationEntry;
import org.ndexbio.cxio.aspects.datamodels.ATTRIBUTE_DATA_TYPE;
import org.ndexbio.model.cx.FunctionTermElement;
import org.ndexbio.model.exceptions.NdexException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Builds node index entries from the CX2 aspect files under
 * {@code <ndexRoot>/data/<networkId>/aspects_cx2/}.
 *
 * <p>The ndex root is supplied at construction rather than read from global configuration so
 * that this reader can be exercised against any directory.
 */
public class Cx2NodeIndexServiceImpl implements Cx2NodeIndexService {

	private static final Logger logger = LoggerFactory.getLogger(Cx2NodeIndexServiceImpl.class);

	private final String ndexRoot;

	public Cx2NodeIndexServiceImpl(final String ndexRoot) {
		this.ndexRoot = ndexRoot;
	}

	@Override
	public Map<Long, NodeIndexEntry> loadNodeIndexEntries(String networkId)
			throws NdexException, IOException {

		Map<Long, NodeIndexEntry> result = new TreeMap<>();

		String pathPrefix = ndexRoot + "/data/" + networkId + "/"
				+ CX2NetworkLoader.cx2AspectDirName + "/";

		ObjectMapper om = new ObjectMapper();

		// Create the attribute name mapping table from the attribute declarations.
		//
		// canRead() is false both when the file is absent and when it (or its directory) cannot
		// be read by this process. Those are very different situations - a permission problem
		// used to be silently indistinguishable from an empty network, which let a reindex
		// running as the wrong user quietly produce empty indexes - so fail here and say which
		// one it was.
		File aspectDir = new File(pathPrefix);
		File declFile = new File(pathPrefix + CxAttributeDeclaration.ASPECT_NAME);
		if (!declFile.canRead()) {
			throw new NdexException(describeUnreadableDecl(aspectDir, declFile, networkId));
		}

		CxAttributeDeclaration[] declarations = om.readValue(declFile, CxAttributeDeclaration[].class);

		if (declarations.length == 0
				|| !declarations[0].getDeclarations().containsKey(CxNode.ASPECT_NAME)) {
			logger.warn("Network {} declares no {} aspect in {} - node index will be empty.",
					networkId, CxNode.ASPECT_NAME, declFile.getAbsolutePath());
			return result;
		}

		Map<String, DeclarationEntry> nodeAttributeDecls =
				declarations[0].getAttributesInAspect(CxNode.ASPECT_NAME);
		if (nodeAttributeDecls.size() == 0) {
			logger.warn("Network {} declares no node attributes in {} - node index will be empty.",
					networkId, declFile.getAbsolutePath());
			return result;
		}

		//key is lowercased attribute name that we need to index,
		//value is the actual attribute name in the node. This is for doing a case-insensitive lookup of attribute value.
		Map<String, Map.Entry<String, DeclarationEntry>> attributeNameMapping = new HashMap<>();
		for (Map.Entry<String, DeclarationEntry> entry : nodeAttributeDecls.entrySet()) {
			ATTRIBUTE_DATA_TYPE dType = entry.getValue().getDataType();
			String attrName = entry.getKey();

			if (dType == null || dType == ATTRIBUTE_DATA_TYPE.STRING
					|| dType == ATTRIBUTE_DATA_TYPE.LIST_OF_STRING) {
				if (attrName.equalsIgnoreCase(NodeIndexFields.ALIAS)) {
					attributeNameMapping.put(NodeIndexFields.ALIAS, entry);
				} else if (attrName.equalsIgnoreCase(NodeIndexFields.TYPE)) {
					attributeNameMapping.put(NodeIndexFields.TYPE, entry);
				} else if (attrName.equalsIgnoreCase(NodeIndexFields.MEMBER)) {
					attributeNameMapping.put(NodeIndexFields.MEMBER, entry);
				} else {
					attributeNameMapping.put(attrName, entry);
				}
			}
		}

		addNodeAspectEntries(pathPrefix, om, nodeAttributeDecls, attributeNameMapping, result);
		addFunctionTermEntries(pathPrefix, result);

		return result;
	}

	/**
	 * Explains why {@code declFile} could not be read. {@code File.canRead()} collapses
	 * "missing" and "no permission" into a single false, so probe both the file and its
	 * directory and name the OS user, which is what identifies a permission problem.
	 */
	private String describeUnreadableDecl(File aspectDir, File declFile, String networkId) {
		String user = System.getProperty("user.name");

		if (!aspectDir.exists()) {
			return "Cannot index network " + networkId + ": CX2 aspect directory is missing - "
					+ aspectDir.getAbsolutePath();
		}
		if (!aspectDir.canRead()) {
			return "Cannot index network " + networkId + ": CX2 aspect directory is not readable by user '"
					+ user + "' - " + aspectDir.getAbsolutePath();
		}
		if (declFile.exists()) {
			return "Cannot index network " + networkId + ": " + CxAttributeDeclaration.ASPECT_NAME
					+ " is present but not readable by user '" + user + "' - " + declFile.getAbsolutePath();
		}
		return "Cannot index network " + networkId + ": " + CxAttributeDeclaration.ASPECT_NAME
				+ " is missing - " + declFile.getAbsolutePath();
	}

	private void addNodeAspectEntries(String pathPrefix, ObjectMapper om,
			Map<String, DeclarationEntry> nodeAttributeDecls,
			Map<String, Map.Entry<String, DeclarationEntry>> attributeNameMapping,
			Map<Long, NodeIndexEntry> result) throws IOException {

		try (FileInputStream inputStream = new FileInputStream(pathPrefix + CxNode.ASPECT_NAME)) {

			Iterator<CxNode> it = om.readerFor(CxNode.class).readValues(inputStream);

			while (it.hasNext()) {
				CxNode node = it.next();

				node.extendToFullNode(nodeAttributeDecls);

				NodeIndexEntry e = null;
				String name = NodeIndexFields.getSingleIndexableTermFromNode(CxNode.NAME, node,
						attributeNameMapping);
				if (name != null) {
					e = new NodeIndexEntry(node.getId(), name);
				}
				List<String> represents = NodeIndexFields.getSplitableTerms(CxNode.REPRESENTS, node,
						attributeNameMapping);
				if (represents.size() > 0) {
					if (e == null)
						e = new NodeIndexEntry(node.getId(), null);
					e.setRepresents(represents);
				}

				// process alias
				List<String> aliases = NodeIndexFields.getSplitableTerms(NodeIndexFields.ALIAS, node,
						attributeNameMapping);
				if (aliases.size() > 0) {
					if (e == null)
						e = new NodeIndexEntry(node.getId(), null);
					e.setAliases(aliases);
				}

				//process members
				boolean proteinMembersFound = false;
				Map.Entry<String, DeclarationEntry> typeAttrName =
						attributeNameMapping.get(NodeIndexFields.TYPE);
				if (typeAttrName != null) {
					ATTRIBUTE_DATA_TYPE t = typeAttrName.getValue().getDataType();
					if (t == null || t == ATTRIBUTE_DATA_TYPE.STRING) {
						String nodeType = (String) node.getAttributes().get(typeAttrName.getKey());
						if (nodeType != null && (nodeType.equalsIgnoreCase(NodeIndexFields.PROTEINFAMILY)
								|| nodeType.equalsIgnoreCase(NodeIndexFields.COMPLEX))) {
							List<String> memberGenes = NodeIndexFields.getSplitableTerms(
									NodeIndexFields.MEMBER, node, attributeNameMapping);
							if (e == null)
								e = new NodeIndexEntry(node.getId(), null);
							e.getRepresents().addAll(memberGenes);
							proteinMembersFound = true;
						}
					}
				}

				e = addRemainingAttributes(node, attributeNameMapping, proteinMembersFound, e);

				if (e != null)
					result.put(node.getId(), e);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private NodeIndexEntry addRemainingAttributes(CxNode node,
			Map<String, Map.Entry<String, DeclarationEntry>> attributeNameMapping,
			boolean proteinMembersFound, NodeIndexEntry entry) {

		NodeIndexEntry e = entry;

		for (Map.Entry<String, Map.Entry<String, DeclarationEntry>> attrDecl
				: attributeNameMapping.entrySet()) {
			String attrName = attrDecl.getKey();
			if (attrName.equals(CxNode.NAME) || attrName.equals(CxNode.REPRESENTS)
					|| attrName.equals(NodeIndexFields.ALIAS))
				continue;
			if (proteinMembersFound && attrName.equals(NodeIndexFields.MEMBER))
				continue;

			// process the value
			Map.Entry<String, DeclarationEntry> decl = attrDecl.getValue();
			Object attrValue = node.getAttributes().get(decl.getKey());
			ATTRIBUTE_DATA_TYPE dType = decl.getValue().getDataType();
			if (dType == null || dType == ATTRIBUTE_DATA_TYPE.STRING) {
				if (attrValue != null) {
					String s = (String) attrValue;
					if (s.length() > 1) {
						if (e == null)
							e = new NodeIndexEntry(node.getId(), null);
						e.addText(s);
					}
				}
			} else { // list of strings
				List<String> ls = (List<String>) attrValue;
				if (ls != null) {
					for (String str : ls) {
						if (str != null && str.length() > 1) {
							if (e == null)
								e = new NodeIndexEntry(node.getId(), null);
							e.addText(str);
						}
					}
				}
			}
		}

		return e;
	}

	private void addFunctionTermEntries(String pathPrefix, Map<Long, NodeIndexEntry> result)
			throws IOException {

		java.nio.file.Path functionTermAspect = Paths.get(pathPrefix + FunctionTermElement.ASPECT_NAME);

		if (!Files.exists(functionTermAspect)) {
			return;
		}

		try (FileInputStream inputStream =
				new FileInputStream(pathPrefix + FunctionTermElement.ASPECT_NAME)) {

			Iterator<FunctionTermElement> it =
					new ObjectMapper().readerFor(FunctionTermElement.class).readValues(inputStream);

			while (it.hasNext()) {
				FunctionTermElement functionTerm = it.next();
				List<String> terms =
						NetworkGlobalIndexManager.getIndexableStringsFromFunctionTerm(functionTerm);
				if (terms.size() > 0) {
					NodeIndexEntry e = result.get(functionTerm.getNodeID());
					if (e == null) { // need to add a new entry
						e = new NodeIndexEntry(functionTerm.getNodeID(), null);
						result.put(functionTerm.getNodeID(), e);
					}
					e.setRepresents(terms);
				}
			}
		}
	}
}
