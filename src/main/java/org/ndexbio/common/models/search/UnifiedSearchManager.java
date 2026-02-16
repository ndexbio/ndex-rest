package org.ndexbio.common.models.search;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.common.solr.NFSIndexManager;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.*;
import org.ndexbio.model.object.network.VisibilityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class UnifiedSearchManager implements AutoCloseable {
    protected final Logger logger = LoggerFactory.getLogger(this.getClass().getSimpleName());

    // Use one of the existing managers just for its search infrastructure
    // A "dummy" NFSIndexManager that only does search
    private final SearchOnlyManager delegate;

    public UnifiedSearchManager(VisibilityType visibilityType) {
        this.delegate = new SearchOnlyManager(visibilityType);
    }

    public FileSearchResult searchFiles(SimpleFileQuery query, VisibilityType visibilityType, int skipBlocks, int blockSize) throws NdexException, SolrServerException, IOException {
        User user;
        try (UserDAO dao = new UserDAO()) {
            user = dao.getUserByAccountName(query.getAccountName(), false, false);
        }
        catch (Exception e){
            throw new RuntimeException(e);
        }

        List<FileItemSummary> fileItemSummaryList = delegate.search(query.getSearchString(),user.getExternalId().toString(),blockSize, skipBlocks, null,
                query.getPermission())
                 .stream()
                 .map(this::mapSolrDocumentToSummary)
                .collect(Collectors.toList());
         return new FileSearchResult(fileItemSummaryList.size(), (long) skipBlocks *blockSize, fileItemSummaryList);


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
        SearchOnlyManager(VisibilityType visibilityType) {
            super(visibilityType);
        }

        @Override
        protected SolrInputDocument setupIndexDocument(Void inputData) {
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