package org.ndexbio.task;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.solr.client.solrj.SolrServerException;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.persistence.CX2NetworkLoader;
import org.ndexbio.common.solr.NetworkGlobalIndexManager;
import org.ndexbio.common.solr.SingleNetworkSolrIdxManager;
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
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.TaskType;
import org.ndexbio.model.object.network.NetworkIndexLevel;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.rest.Configuration;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SolrTaskRebuildNetworkIdx extends NdexSystemTask {
	
	private UUID networkId;
	private SolrIndexScope idxScope;
	private boolean createOnly;
	private boolean fromCX2File;
	
    private static final TaskType taskType = TaskType.SYS_SOLR_REBUILD_NETWORK_INDEX;
    public static final String AttrScope = "scope";
    public static final String AttrCreateOnly ="createOnly";
    public static final String FORMCX2FILE = "fromCX2";
    private Set<String> indexedFields;
    private NetworkIndexLevel indexLevel;
    
	
	public SolrTaskRebuildNetworkIdx (UUID networkUUID, SolrIndexScope scope, boolean createOnly, 
			Set<String> indexedFields, NetworkIndexLevel level, boolean fromCX2File) {
		super();
		this.networkId = networkUUID;
		this.idxScope = scope;
		this.createOnly = createOnly;
		this.indexedFields =indexedFields;
		this.indexLevel = level;
		this.fromCX2File = fromCX2File;
	}
	
	@Override
	public void run() throws Exception { // throws NdexException, SolrServerException, IOException, SQLException {

		try (NetworkDAO dao = new NetworkDAO()) {

			NetworkSummary summary = dao.getNetworkSummaryById(networkId);
			if (summary == null)
				throw new NdexException("Network " + networkId + " not found in the server.");

			// drop the old ones.
			if (!createOnly) {
				if (idxScope != SolrIndexScope.individual) {
					try (NetworkGlobalIndexManager globalIdx = new NetworkGlobalIndexManager()) {
						globalIdx.deleteNetwork(networkId.toString());
					}
				}
				if (idxScope != SolrIndexScope.global)
					try (SingleNetworkSolrIdxManager idx2 = new SingleNetworkSolrIdxManager(networkId.toString())) {
						idx2.dropIndex();
					}
			}

			if (this.idxScope != SolrIndexScope.global) {
				long t1 = Calendar.getInstance().getTimeInMillis();
				try (SingleNetworkSolrIdxManager idx2 = new SingleNetworkSolrIdxManager(networkId.toString())) {
					if ( this.fromCX2File)
						idx2.createIndexFromCx2(indexedFields);
					else
						idx2.createIndex(indexedFields);
					idx2.close();
				}
				long t = Calendar.getInstance().getTimeInMillis() -t1;
				System.out.println("Takes " + t/1000 +" secs to create index");
			}

			if (this.idxScope != SolrIndexScope.individual && indexLevel != NetworkIndexLevel.NONE) {

				try (NetworkGlobalIndexManager globalIdx = new NetworkGlobalIndexManager()) {

					// build the solr document obj
					List<Map<Permissions, Collection<String>>> permissionTable = dao
							.getAllMembershipsOnNetwork(networkId);
					Map<Permissions, Collection<String>> userMemberships = permissionTable.get(0);
					Map<Permissions, Collection<String>> grpMemberships = permissionTable.get(1);
					globalIdx.createIndexDocFromSummary(summary, summary.getOwner(),
							userMemberships.get(Permissions.READ), userMemberships.get(Permissions.WRITE),
							grpMemberships.get(Permissions.READ), grpMemberships.get(Permissions.WRITE));

					String pathPrefix = Configuration.getInstance().getNdexRoot() + "/data/" ; 

					if (indexLevel == NetworkIndexLevel.META || indexLevel == NetworkIndexLevel.ALL) {
						if ( this.fromCX2File) {
							
							String cx2AspectPath = pathPrefix + networkId.toString() + "/" + CX2NetworkLoader.cx2AspectDirName + "/";
							File attrFile = new File (cx2AspectPath + CxNetworkAttribute.ASPECT_NAME);
							if (attrFile.exists()) {
							
								//create the attribute name mapping table from attribute declaration
								File declFile = new File(cx2AspectPath + CxAttributeDeclaration.ASPECT_NAME);
								ObjectMapper om = new ObjectMapper();
							
								CxAttributeDeclaration[] declarations = om.readValue(declFile, CxAttributeDeclaration[].class);
								
								CxNetworkAttribute[] attrs = om.readValue(attrFile,CxNetworkAttribute[].class);
								attrs[0].extendToFullNode(declarations[0].getAttributesInAspect(CxNetworkAttribute.ASPECT_NAME));
								
							
								List<String> indexWarnings = globalIdx.addCX2NetworkAttrToIndex(attrs[0]);
								if (!indexWarnings.isEmpty())
									for (String warning : indexWarnings)
										System.err.println("Warning: " + warning);
								
							}
						} else { 
							try (AspectIterator<NetworkAttributesElement> it = new AspectIterator<>(networkId.toString(),
								NetworkAttributesElement.ASPECT_NAME, NetworkAttributesElement.class,pathPrefix)) {
								while (it.hasNext()) {
									NetworkAttributesElement e = it.next();

									List<String> indexWarnings = globalIdx.addCXNetworkAttrToIndex(e);
									if (!indexWarnings.isEmpty())
										for (String warning : indexWarnings)
											System.err.println("Warning: " + warning);

								}
							}
						}
					}	
					
					// process node attribute aspect and add to solr doc
					if (indexLevel == NetworkIndexLevel.ALL) {
						if ( fromCX2File) {
							//TODO: build from CX2
							String cx2AspectPath = pathPrefix + networkId.toString() + "/" + CX2NetworkLoader.cx2AspectDirName + "/";
							ObjectMapper om = new ObjectMapper();
							
							File functionAspectFile = new File (cx2AspectPath + FunctionTermElement.ASPECT_NAME);
							if ( functionAspectFile.exists()) {
								try (FileInputStream inputStream = new FileInputStream(cx2AspectPath + FunctionTermElement.ASPECT_NAME)) {

									Iterator<FunctionTermElement> it = om.readerFor(FunctionTermElement.class).readValues(inputStream);

									while (it.hasNext()) {
										FunctionTermElement fun = it.next();

										globalIdx.addFunctionTermToIndex(fun);

									}
								}
							}
							
							//create the attribute name mapping table from attribute declaration
							processCx2Nodes(cx2AspectPath, om, globalIdx);
							
						} else { 
							try (AspectIterator<FunctionTermElement> it = new AspectIterator<>(networkId.toString(),
								FunctionTermElement.ASPECT_NAME, FunctionTermElement.class, pathPrefix)) {
								while (it.hasNext()) {
									FunctionTermElement fun = it.next();

									globalIdx.addFunctionTermToIndex(fun);

								}
							}

							try (AspectIterator<NodeAttributesElement> it = new AspectIterator<>(networkId.toString(),
									NodeAttributesElement.ASPECT_NAME, NodeAttributesElement.class, pathPrefix)) {
								while (it.hasNext()) {
									NodeAttributesElement e = it.next();
									globalIdx.addCXNodeAttrToIndex(e);
								}
							}

							try (AspectIterator<NodesElement> it = new AspectIterator<>(networkId.toString(), NodesElement.ASPECT_NAME,
									NodesElement.class,pathPrefix)) {
								while (it.hasNext()) {
									NodesElement e = it.next();
									globalIdx.addCXNodeToIndex(e);
								}
							}
						}	
					}
					
					globalIdx.commit();
				}
			}

			try {
				dao.setFlag(this.networkId, "iscomplete", true);
				dao.commit();
			} catch (SQLException e) {
				throw new NdexException("DB error when setting iscomplete flag: " + e.getMessage(), e);
			}

		} catch (SQLException | IOException | NdexException | SolrServerException e1) {
			e1.printStackTrace();
			try (NetworkDAO dao = new NetworkDAO()) {
				dao.setErrorMessage(networkId, "Failed to create Index on network. Index type: " + this.idxScope
						+ ". Cause: " + e1.getMessage());
				dao.commit();
			}
			throw e1;
		}

	}
	
	
	private static void processCx2Nodes(String cx2AspectPath, ObjectMapper om, NetworkGlobalIndexManager globalIdx) throws JsonParseException, JsonMappingException, IOException {
		File declFile = new File(cx2AspectPath + CxAttributeDeclaration.ASPECT_NAME);
		if (!declFile.exists())
			return;
		
		CxAttributeDeclaration[] declarations = om.readValue(declFile, 
				CxAttributeDeclaration[].class); 
		
		if ( declarations.length == 0 || ! declarations[0].getDeclarations().containsKey(CxNode.ASPECT_NAME))
			return ;
		
		Map<String,DeclarationEntry> nodeAttributeDecls = declarations[0].getAttributesInAspect(CxNode.ASPECT_NAME);
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
	
	/*
	private int getScoreFromSummary(NetworkSummary summary) {
		int score = 0;
		
		if ( summary.getName()!=null && !summary.getName().isEmpty())
			score +=10;
		if ( summary.getDescription() !=null && !summary.getDescription().isEmpty())
			score += 10;
		if ( summary.getVersion() !=null && !summary.getVersion().isEmpty()){
			score +=10;
		}
		
		score += Util.getNetworkScores(summary.getProperties(), false);
		
		return score;
	} */


	@Override
	public Task createTask() {
		Task t = super.createTask();
		t.setResource(networkId.toString());
		t.getAttributes().put(AttrScope, this.idxScope);
		t.getAttributes().put(AttrCreateOnly, Boolean.valueOf(this.createOnly));
		t.setAttribute("fields", indexedFields);
		t.setAttribute("indexLevel", this.indexLevel);
		t.setAttribute(FORMCX2FILE, this.fromCX2File);
		return t;
	}

	@Override
	public TaskType getTaskType() {
		return taskType;
	}
}
