package org.ndexbio.common.models.search;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.ndexbio.common.models.dao.DAOFactory;
import org.ndexbio.common.models.dao.FolderDAO;
import org.ndexbio.common.models.dao.NetworkDAO;
import org.ndexbio.common.models.dao.ShortcutDAO;
import org.ndexbio.common.solr.NFSIndexManager;
import org.ndexbio.common.solr.SolrClientWrapper;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.*;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class NFSSearchProvider implements SearchProvider {
    protected final Logger logger = LoggerFactory.getLogger(this.getClass().getSimpleName());
    private final int maxDefaultResults;

    // Use one of the existing managers just for its search infrastructure
    // A "dummy" NFSIndexManager that only does search
    private final SearchOnlyManager delegate;

    public NFSSearchProvider(SolrClientWrapper solrClientWrapper, int defaultMaxSearchResultRows) {
        this.maxDefaultResults = defaultMaxSearchResultRows;
        this.delegate = new SearchOnlyManager(solrClientWrapper, maxDefaultResults);
    }

    public FileSearchResult searchFiles(SimpleFileQuery query, VisibilityType visibilityType,
                                        User accesser, int skipBlocks, int blockSize) throws NdexException {
        // accesser determines what they're allowed to see (permission filter)
        String userAccessorId = accesser != null ? accesser.getUserName() : null;
        // accountName is an optional owner filter
        String ownedBy = query.getAccountName();
        List<FileItemSummary> allSummaries;
        SolrDocumentList documents;
        try {
            if (query.getType() != null) {
                documents = delegate.searchByType(query.getSearchString(), userAccessorId,
                        visibilityType,
                        blockSize, skipBlocks, ownedBy,
                        query.getPermission(), query.getType().toString());
                allSummaries = getDocumentSummariesByEntityType(query.getType(), getUUIDsFromDocuments(documents));

            } else {
                documents = delegate.search(query.getSearchString(), userAccessorId,
                        visibilityType, blockSize, skipBlocks, ownedBy,
                        query.getPermission());
                List<UUID> sortedUUIDs = new ArrayList<>();
                Map<FileType, List<UUID>> documentsByType = new HashMap<>();

                for (SolrDocument document : documents) {
                    String entityTypeStr = (String) document.get(NFSIndexManager.ENTITY_TYPE);
                    FileType entityType = FileType.valueOf(entityTypeStr);
                    UUID id = getUUIDFromDocument(document);
                    sortedUUIDs.add(id);
                    if (!documentsByType.containsKey(entityType)) {
                        documentsByType.put(entityType, new ArrayList<>());
                    }
                    documentsByType.get(entityType).add(id);
                }
                Map<UUID, FileItemSummary> summariesById = new HashMap<>();
                for (Map.Entry<FileType, List<UUID>> entry : documentsByType.entrySet()) {
                    getDocumentSummariesByEntityType(entry.getKey(), entry.getValue())
                            .forEach(summary ->
                                    summariesById.put(summary.getUuid(), summary));
                }
                allSummaries = sortedUUIDs.stream()
                        .map(summariesById::get)
                        .toList();
            }

        } catch (Exception e) {
            throw new NdexException("Failed to fetch data about search results. " + e.getMessage(), e);
        }
        if (allSummaries == null) allSummaries = new ArrayList<>();

        return new FileSearchResult(allSummaries.size(), (long) skipBlocks * blockSize, allSummaries);
    }

    private List<FileItemSummary> getDocumentSummariesByEntityType(FileType fileType, List<UUID> ids) throws Exception {
        DAOFactory daoFactory = Configuration.getInstance().getDAOFactory();
        List<FileItemSummary> result;
        if (fileType.equals(FileType.SHORTCUT)){
            try (ShortcutDAO shortcutDAO = daoFactory.getShortcutDAO()){
                result = shortcutDAO.getShortcutsByIds(ids).stream()
                        .map(this::mapShortcutToSummary)
                        .toList();
            }
        } else if (fileType.equals(FileType.FOLDER)) {
            try (FolderDAO folderDAO = daoFactory.getFolderDAO()){
                result =  folderDAO.getFoldersByIds(ids).stream()
                        .map(this::mapFolderToSummary)
                        .toList();
            }
        }
        else if (fileType.equals(FileType.NETWORK)){
            try (NetworkDAO networkDAO = daoFactory.getNetworkDAO()){
                result =  networkDAO.getNetworkSummariesByIds(ids).stream()
                        .map(this::mapNetworkToSummary)
                        .toList();
            }
        }
        else {
            throw new NdexException("Invalid entity type found: " + fileType);
        }
        return result;
    }

    private FileItemSummary mapShortcutToSummary(NdexShortcut ndexShortcut) {
        FileItemSummary fis = new FileItemSummary();
        fis.setUuid(ndexShortcut.getExternalId());
        fis.setType(FileType.SHORTCUT);
        fis.setName(ndexShortcut.getName());
        fis.setModificationTime(ndexShortcut.getModificationTime());

        Map<String, Object> attr = new HashMap<>();
        attr.put("parent", ndexShortcut.getParent());
        attr.put("target", ndexShortcut.getTarget());
        attr.put("target_type", ndexShortcut.getTargetType() != null ? ndexShortcut.getTargetType().toString() : null);
        attr.put("creationTime", ndexShortcut.getCreationTime());
        fis.setAttributes(attr);

        fis.setOwner(ndexShortcut.getOwner());
        fis.setOwnerId(UUID.fromString(ndexShortcut.getOwner_id()));

        return fis;
    }
    private FileItemSummary mapFolderToSummary(NdexFolder ndexFolder) {
        FileItemSummary fis = new FileItemSummary();
        fis.setUuid(ndexFolder.getExternalId());
        fis.setType(FileType.FOLDER);
        fis.setName(ndexFolder.getName());
        fis.setModificationTime(ndexFolder.getModificationTime());

        Map<String, Object> attr = new HashMap<>();
        attr.put("description", ndexFolder.getDescription());
        attr.put("parent", ndexFolder.getParent());
        attr.put("creationTime", ndexFolder.getCreationTime());
        fis.setAttributes(attr);

        fis.setOwner(ndexFolder.getOwner());
        fis.setOwnerId(UUID.fromString(ndexFolder.getOwner_id()));

        return fis;
    }
    private FileItemSummary mapNetworkToSummary(NetworkSummary networkSummary) {
        FileItemSummary fis = new FileItemSummary();
        fis.setUuid(networkSummary.getExternalId());
        fis.setType(FileType.NETWORK);
        fis.setName(networkSummary.getName());
        fis.setModificationTime(networkSummary.getModificationTime());
        fis.setOwner(networkSummary.getOwner());
        fis.setOwnerId(networkSummary.getOwnerUUID());
        fis.setVisibility(networkSummary.getVisibility() != null ? networkSummary.getVisibility().toString() : null);
        fis.setEdges(networkSummary.getEdgeCount());
        fis.setIsReadOnly(networkSummary.getIsReadOnly());
        fis.setErrorMessage(networkSummary.getErrorMessage());
        fis.setWarnings(networkSummary.getWarnings());
        fis.setIsCompleted(networkSummary.isCompleted());
        fis.setIsValid(networkSummary.getIsValid());
        fis.setDoi(networkSummary.getDoi());

        Map<String, Object> attr = new HashMap<>();
        attr.put("description", networkSummary.getDescription());
        attr.put("version", networkSummary.getVersion());
        attr.put("nodeCount", networkSummary.getNodeCount());
        attr.put("creationTime", networkSummary.getCreationTime());
        attr.put("isShowcase", networkSummary.getIsShowcase());
        attr.put("isCertified", networkSummary.getIsCertified());
        attr.put("indexLevel", networkSummary.getIndexLevel() != null ? networkSummary.getIndexLevel().toString() : null);
        attr.put("hasLayout", networkSummary.getHasLayout());
        attr.put("hasSample", networkSummary.getHasSample());
        attr.put("cxFormat", networkSummary.getCxFormat());
        attr.put("cxFileSize", networkSummary.getCxFileSize());
        attr.put("cx2FileSize", networkSummary.getCx2FileSize());
        attr.put("folderId", networkSummary.getFolderId());
        attr.put("subnetworkIds", networkSummary.getSubnetworkIds());
        fis.setAttributes(attr);

        return fis;
    }
    private FileItemSummary mapSolrDocumentToSummary(SolrDocument solrDocument){
        String uuid = (String)solrDocument.get(NFSIndexManager.UUID);

        String entityType = (String)solrDocument.get(NFSIndexManager.ENTITY_TYPE);

        FileType fileType = FileType.valueOf(entityType);

        String name = (String)solrDocument.getOrDefault(NFSIndexManager.NAME, "unknown");

        return new FileItemSummary(UUID.fromString(uuid), fileType, name);
    }
    private List<UUID> getUUIDsFromDocuments(SolrDocumentList solrDocuments){
        return solrDocuments.stream()
                .map(this::getUUIDFromDocument)
                .toList();
    }
    private UUID getUUIDFromDocument(SolrDocument solrDocument){
        String uuid = (String)solrDocument.get(NFSIndexManager.UUID);
        return UUID.fromString(uuid);
    }

    private void addTargetTypeToShortcutSummaryItem(FileItemSummary fileItemSummary, ShortcutDAO shortcutDAO,
                                                    User accesser){
        NdexShortcut ndexShortcut;
        try {
            ndexShortcut = shortcutDAO.getShortcut(fileItemSummary.getUuid(), accesser.getExternalId());
        } catch (SQLException | ObjectNotFoundException | UnauthorizedOperationException | IOException e) {
            ndexShortcut = null;
        }
        Map<String, Object> attributes = new HashMap<>();
        if (ndexShortcut != null){
            attributes.put(NFSIndexManager.TARGET_TYPE, ndexShortcut.getTargetType());
        }
        else attributes.put(NFSIndexManager.TARGET_TYPE, "unknown");

        fileItemSummary.setAttributes(attributes);

    }

        @Override
    public void close() { delegate.close(); }

    private static final class SearchOnlyManager extends NFSIndexManager<Void> {
        SearchOnlyManager(SolrClientWrapper solrClientWrapper, int maxResults) {
            super(solrClientWrapper, maxResults);
        }

        @Override
        protected SolrInputDocument setupIndexDocument(Void inputData, VisibilityType visibilityType) {
            throw new UnsupportedOperationException("Unified search is read-only");
        }

        @Override
        protected String getQueryFields() {
            return "uuid^20 name^10 description^5 labels^6 owner^2 " +
                   "networkType^4 organism^3 disease^3 tissue^3 author^2 methods " +
                   "nodeName represents alias rights^0.6 rightsHolder^0.6";
        }

    }

}