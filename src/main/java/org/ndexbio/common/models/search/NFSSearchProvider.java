package org.ndexbio.common.models.search;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.ndexbio.common.solr.NFSIndexManager;
import org.ndexbio.common.solr.SolrClientWrapper;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.*;
import org.ndexbio.model.object.network.VisibilityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
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

        SolrDocumentList documents;
        if (query.getType() != null) {
            documents = delegate.searchByType(query.getSearchString(), userAccessorId,
                    visibilityType,
                    blockSize, skipBlocks, ownedBy,
                    query.getPermission(), query.getType().toString());
        } else {
            documents = delegate.search(query.getSearchString(), userAccessorId,
                    visibilityType, blockSize, skipBlocks, ownedBy,
                    query.getPermission());
        }

        List<FileItemSummary> fileItemSummaryList = documents
                .stream()
                .map(this::mapSolrDocumentToSummary)
                .collect(Collectors.toList());

        return new FileSearchResult(fileItemSummaryList.size(), (long) skipBlocks * blockSize, fileItemSummaryList);
    }


    private FileItemSummary mapSolrDocumentToSummary(SolrDocument solrDocument){
        String uuid = (String)solrDocument.get(NFSIndexManager.UUID);

        String entityType = (String)solrDocument.get(NFSIndexManager.ENTITY_TYPE);

        FileType fileType = FileType.valueOf(entityType);

        String name = (String)solrDocument.getOrDefault(NFSIndexManager.NAME, "unknown");
        return new FileItemSummary(UUID.fromString(uuid), fileType, name);
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