package org.ndexbio.task;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.solr.client.solrj.SolrServerException;
import org.ndexbio.common.models.dao.FolderDAO;
import org.ndexbio.common.models.dao.ShortcutDAO;
import org.ndexbio.common.models.dao.postgresql.PostgresNetworkDAO;
import org.ndexbio.common.persistence.CX2NetworkLoader;
import org.ndexbio.common.solr.*;
import org.ndexbio.cx2.aspect.element.core.CxAttributeDeclaration;
import org.ndexbio.cx2.aspect.element.core.CxNetworkAttribute;
import org.ndexbio.cx2.aspect.element.core.CxNode;
import org.ndexbio.cx2.aspect.element.core.DeclarationEntry;
import org.ndexbio.cxio.aspects.datamodels.ATTRIBUTE_DATA_TYPE;
import org.ndexbio.cxio.aspects.datamodels.NetworkAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NodeAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NodesElement;
import org.ndexbio.cxio.core.AspectIterator;
import org.ndexbio.model.cx.FunctionTermElement;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.*;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class SolrTaskRebuildFileIdx extends NdexSystemTask {

	private static final Logger log = LoggerFactory.getLogger(SolrTaskRebuildFileIdx.class);

	private static final TaskType taskType = TaskType.SYS_SOLR_REBUILD_NETWORK_INDEX; //todo may need new

	private final UUID fileId;
	private final UUID userId;

	private final VisibilityType visibilityType;
	private final FileType fileType;
	private final boolean createOnly;
	private final String username;
	private final boolean ignoreCxFiles;
	private final SolrObjectFactory solrObjectFactory;

	public SolrTaskRebuildFileIdx(UUID fileId, UUID userId,String username,
								  VisibilityType visibilityType,
								  FileType fileType, boolean createOnly) {
		this(fileId, userId, username, visibilityType,fileType, createOnly, false );
	}
	public SolrTaskRebuildFileIdx(UUID fileId, UUID userId,String username,
								  VisibilityType visibilityType,
								  FileType fileType, boolean createOnly, boolean ignoreCxFiles) {
		super();
		this.fileId = fileId;
		this.userId = userId;
		this.visibilityType = visibilityType;
		this.fileType = fileType;
		this.createOnly = createOnly;
		this.username = username;
		this.ignoreCxFiles = ignoreCxFiles;
		this.solrObjectFactory = Configuration.getInstance().getSolrObjectFactory();
	}

	@Override
	public void run() throws Exception {
		try {
			checkParameters();
			String id = fileId.toString();
			log.info("Rebuilding {} index for file type: {} ID: {} for User {}. Clearing previous: {}", visibilityType,
					fileType, id, userId, createOnly);
			if (fileType.equals(FileType.FOLDER)){
				rebuildFolderIndex(id);
			} else if (fileType.equals(FileType.SHORTCUT)) {
				rebuildShortcutIndex(id);
			} else if (fileType.equals(FileType.NETWORK)) {
				rebuildNetworkIndex(id);
			}
			else {
				throw new IllegalArgumentException("Invalid file type: " + fileType);
			}
		}
		catch (Exception e){
			log.info("Failed to create index for file {}! Reason: {} {}.", fileId, e.getMessage(), e.toString());
			throw e;
		}
		log.info("Rebuilt {} index for file type: {} ID: {} for User {}.", visibilityType,
				fileType, fileId, userId);


	}

	private void rebuildFolderIndex(String id) throws Exception {
		try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
			if (!createOnly){
				try (FolderIndexManager folderIdxManager = solrObjectFactory.getFolderIndexManager()) {
					folderIdxManager.delete(id, visibilityType);
				}

			}
			String accessKey = dao.getFolderAccessKey(fileId);
			NdexFolder folder = dao.getFolder(fileId, userId, accessKey );
			checkRecordExists(folder);
			folder.setOwner(username);
			try(FolderIndexManager globalIdx = solrObjectFactory.getFolderIndexManager()) {
				Map<String, String> folderPermissions = dao.getFolderPermissions(fileId);
 				globalIdx.createIndex(folder,
						visibilityType,
						getFolderUserReads(folderPermissions),
						getFolderUserWrites(folderPermissions));
			}

		}

    }
	private void rebuildShortcutIndex(String id) throws Exception {
		try (ShortcutDAO dao = Configuration.getInstance().getDAOFactory().getShortcutDAO()) {
			if (!createOnly){
				try (ShortcutIndexManager shortcutIndexManager = Configuration.getInstance()
						.getSolrObjectFactory().getShortcutIndexManager()) {
					shortcutIndexManager.delete(id, visibilityType);
				}
			}
			NdexShortcut shortcut = dao.getShortcut(fileId, userId);
			checkRecordExists(shortcut);
			shortcut.setOwner(username);
			try(ShortcutIndexManager globalIdx = solrObjectFactory.getShortcutIndexManager()) {
				globalIdx.createIndex(shortcut, visibilityType,null, null);
			}
		}
	}

	private void rebuildNetworkIndex(String id) throws Exception {

		try (PostgresNetworkDAO dao = new PostgresNetworkDAO()) {

			NetworkSummary summary = dao.getNetworkSummaryById(fileId);
			VisibilityType visibilityType = dao.getNetworkVisibility(fileId);
			SolrIndexScope idxScope;

			checkRecordExists(summary);
			if (summary.getNodeCount() >= SingleNetworkSolrIdxManager.AUTOCREATE_THRESHHOLD)
				idxScope = SolrIndexScope.both;
			else
				idxScope = SolrIndexScope.global;

			// drop the old ones.
			if (!createOnly) {
				try (GlobalNetworkIndexManager globalIdx = solrObjectFactory.getGlobalNetworkIndexManager()) {
					globalIdx.delete(id, visibilityType);
				}
				if (idxScope != SolrIndexScope.global)
					try (SingleNetworkSolrIdxManager idx2 = new SingleNetworkSolrIdxManager(id)) {
						idx2.dropIndex();
					}
			}

			if (idxScope != SolrIndexScope.global) {
				long t1 = Calendar.getInstance().getTimeInMillis();
				try (SingleNetworkSolrIdxManager idx2 = new SingleNetworkSolrIdxManager(id)) {
					idx2.createIndexFromCx2(null);
				}
				long t = Calendar.getInstance().getTimeInMillis() - t1;
                log.info("Takes {} secs to create index", t / 1000);
			}

            try (GlobalNetworkIndexManager globalIdx = solrObjectFactory.getGlobalNetworkIndexManager()) {
				// build the solr document obj
                List<Map<Permissions, Collection<String>>> permissionTable = dao
                        .getAllMembershipsOnNetwork(fileId);
                Map<Permissions, Collection<String>> userMemberships = permissionTable.get(0);
                globalIdx.prepareIndexDocument(summary, visibilityType,
                        userMemberships.get(Permissions.READ), userMemberships.get(Permissions.WRITE));

                String pathPrefix = Configuration.getInstance().getNdexRoot() + "/data/";
				String cx2AspectPath = pathPrefix + id + "/" + CX2NetworkLoader.cx2AspectDirName + "/";
				File attrFile = new File(cx2AspectPath + CxNetworkAttribute.ASPECT_NAME);
				File functionAspectFile = new File(cx2AspectPath + FunctionTermElement.ASPECT_NAME);

				// Always index network attributes (META + ALL behavior)
				if (attrFile.exists() && !ignoreCxFiles) {

					File declFile = new File(cx2AspectPath + CxAttributeDeclaration.ASPECT_NAME);
					ObjectMapper om = new ObjectMapper();

					CxAttributeDeclaration[] declarations = om.readValue(declFile, CxAttributeDeclaration[].class);

					CxNetworkAttribute[] attrs = om.readValue(attrFile, CxNetworkAttribute[].class);
					attrs[0].extendToFullNode(declarations[0].getAttributesInAspect(CxNetworkAttribute.ASPECT_NAME));

					List<String> indexWarnings = globalIdx.addCX2NetworkAttrToIndex(attrs[0]);
					if (!indexWarnings.isEmpty())
						for (String warning : indexWarnings)
							System.err.println("Warning: " + warning);
				}
				else {
					try (AspectIterator<NetworkAttributesElement> it = new AspectIterator<>(id,
							NetworkAttributesElement.ASPECT_NAME, NetworkAttributesElement.class, pathPrefix)) {
						while (it.hasNext()) {
							NetworkAttributesElement e = it.next();

							List<String> indexWarnings = globalIdx.addCXNetworkAttrToIndex(e);
							if (!indexWarnings.isEmpty())
								for (String warning : indexWarnings)
									System.err.println("Warning: " + warning);
						}
					}
				}

				// Always index node attributes and nodes (ALL behavior)
                if (functionAspectFile.exists() && !ignoreCxFiles) {
                    ObjectMapper om = new ObjectMapper();

					try (FileInputStream inputStream = new FileInputStream(cx2AspectPath + FunctionTermElement.ASPECT_NAME)) {

						Iterator<FunctionTermElement> it = om.readerFor(FunctionTermElement.class).readValues(inputStream);

						while (it.hasNext()) {
							FunctionTermElement fun = it.next();
							globalIdx.addFunctionTermToIndex(fun);
						}
					}

                    processCx2Nodes(cx2AspectPath, om, globalIdx);

                } else {
                    try (AspectIterator<FunctionTermElement> it = new AspectIterator<>(fileId.toString(),
                            FunctionTermElement.ASPECT_NAME, FunctionTermElement.class, pathPrefix)) {
                        while (it.hasNext()) {
                            FunctionTermElement fun = it.next();
                            globalIdx.addFunctionTermToIndex(fun);
                        }
                    }

                    try (AspectIterator<NodeAttributesElement> it = new AspectIterator<>(fileId.toString(),
                            NodeAttributesElement.ASPECT_NAME, NodeAttributesElement.class, pathPrefix)) {
                        while (it.hasNext()) {
                            NodeAttributesElement e = it.next();
                            globalIdx.addCXNodeAttrToIndex(e);
                        }
                    }

                    try (AspectIterator<NodesElement> it = new AspectIterator<>(fileId.toString(), NodesElement.ASPECT_NAME,
                            NodesElement.class, pathPrefix)) {
                        while (it.hasNext()) {
                            NodesElement e = it.next();
                            globalIdx.addCXNodeToIndex(e);
                        }
                    }
                }

                globalIdx.commit(visibilityType);
            }

            try {
				dao.setFlag(this.fileId, "iscomplete", true);
				dao.commit();
			} catch (SQLException e) {
				throw new NdexException("DB error when setting iscomplete flag: " + e.getMessage(), e);
			}

		} catch (SQLException | IOException | NdexException | SolrServerException e1) {
			e1.printStackTrace();
			try (PostgresNetworkDAO dao = new PostgresNetworkDAO()) {
				dao.setErrorMessage(fileId, "Failed to create Index on network."
						+ " Cause: " + e1.getMessage());
				dao.commit();
			}
			throw e1;
		}

	}

	private static void processCx2Nodes(String cx2AspectPath, ObjectMapper om, GlobalNetworkIndexManager globalIdx) throws JsonParseException, JsonMappingException, IOException {
		File declFile = new File(cx2AspectPath + CxAttributeDeclaration.ASPECT_NAME);
		if (!declFile.exists())
			return;

		CxAttributeDeclaration[] declarations = om.readValue(declFile,
				CxAttributeDeclaration[].class);

		if ( declarations.length == 0 || ! declarations[0].getDeclarations().containsKey(CxNode.ASPECT_NAME))
			return ;

		Map<String, DeclarationEntry> nodeAttributeDecls = declarations[0].getAttributesInAspect(CxNode.ASPECT_NAME);
		if ( nodeAttributeDecls.size() == 0 )
			return ;

		Map<String, Map.Entry<String,DeclarationEntry>> attributeNameMapping = new HashMap<> ();
		for ( Map.Entry<String,DeclarationEntry> entry: nodeAttributeDecls.entrySet()) {
			String attrName = entry.getKey();
			if (attrName.equals(CxNode.NAME)) {
				if ( entry.getValue().getDataType() == null ||
						entry.getValue().getDataType() == ATTRIBUTE_DATA_TYPE.STRING)
					attributeNameMapping.put (CxNode.NAME, entry);
			} else if (attrName.equals(CxNode.REPRESENTS) ) {
				if ( entry.getValue().getDataType() == null ||
						entry.getValue().getDataType() == ATTRIBUTE_DATA_TYPE.STRING)
					attributeNameMapping.put (CxNode.REPRESENTS, entry);
			} else if ( attrName.equalsIgnoreCase(SingleNetworkSolrIdxManager.ALIAS) ) {
				if ( entry.getValue().getDataType() == ATTRIBUTE_DATA_TYPE.LIST_OF_STRING) {
					attributeNameMapping.put (SingleNetworkSolrIdxManager.ALIAS, entry);
				}
			} else if ( attrName.equalsIgnoreCase(SingleNetworkSolrIdxManager.TYPE)) {
				if ( entry.getValue().getDataType() == null ||
						entry.getValue().getDataType() == ATTRIBUTE_DATA_TYPE.STRING)
					attributeNameMapping.put (SingleNetworkSolrIdxManager.TYPE, entry);
			} else if ( attrName.equalsIgnoreCase(SingleNetworkSolrIdxManager.MEMBER)) {
				if ( entry.getValue().getDataType() == null ||
						entry.getValue().getDataType() == ATTRIBUTE_DATA_TYPE.STRING)
					attributeNameMapping.put (SingleNetworkSolrIdxManager.MEMBER, entry);
			}

		}

		File nodeAspectFile = new File (cx2AspectPath + CxNode.ASPECT_NAME);
		if ( nodeAspectFile.exists()) {
			//go through node aspect
			try (FileInputStream inputStream = new FileInputStream(cx2AspectPath + "nodes")) {

				Iterator<CxNode> it = om.readerFor(CxNode.class).readValues(inputStream);

				while (it.hasNext()) {
					CxNode node = it.next();
					node.extendToFullNode(nodeAttributeDecls);

					globalIdx.addCX2NodeToIndex(node, attributeNameMapping);
				}
			}
		}

	}




	@Override
	public Task createTask() {
		Task t = super.createTask();
		t.setResource(fileId.toString());
		return t;
	}

	@Override
	public TaskType getTaskType() {
		return taskType;
	}

	public static Set<String> getFolderUserReads(Map<String, String> folderPermissions){
		return folderPermissions.entrySet().stream()
				.filter(entry -> entry.getValue().equals(Permissions.READ.toString()))
				.map(Map.Entry::getKey)
				.collect(Collectors.toSet());
	}
	public static Set<String> getFolderUserWrites(Map<String, String> folderPermissions){
		return folderPermissions.entrySet().stream()
				.filter(entry -> entry.getValue().equals(Permissions.WRITE.toString()))
				.map(Map.Entry::getKey)
				.collect(Collectors.toSet());
	}
	private void checkRecordExists(Object r) throws NdexException {
		if (r == null){
			throw new NdexException("No " + fileType + " record found with id " + fileId);
		}
	}
	private void checkParameters() throws NdexException {
		List<String> nulls = new ArrayList<>();
		if (fileId == null) nulls.add("file ID");
		if (fileType == null) nulls.add("file type");
		if (visibilityType == null) nulls.add("visibility type");
		if (username == null) nulls.add("username.");
		if (userId == null) nulls.add("file ID.");
		if (fileId == null) nulls.add("userId.");
		if (!nulls.isEmpty()){
			throw new IllegalArgumentException("Could not create index record due to null fields: " + String.join(",", nulls));
		}
	}


}
