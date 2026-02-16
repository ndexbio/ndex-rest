package org.ndexbio.common.solr;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.NdexFolder;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.network.VisibilityType;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class FolderIndexManager extends NFSIndexManager<NdexFolder> {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(FolderIndexManager.class);

    public FolderIndexManager(SolrClientWrapper solrClientWrapper){
        super(solrClientWrapper);
    }

    @Override
    protected SolrInputDocument setupIndexDocument(NdexFolder folder, VisibilityType visibilityType) {
        doc = new SolrInputDocument();
        doc.addField(UUID, folder.getExternalId().toString());
        doc.addField(ENTITY_TYPE, FileType.FOLDER.toString());

        if (folder.getName() != null && !folder.getName().trim().isEmpty()){
            doc.addField(NAME, folder.getName());
        }
        if (folder.getDescription() != null && !folder.getDescription().trim().isEmpty()){
            doc.addField(DESC, folder.getDescription());
        }
        if (folder.getParent() != null) {
            doc.addField(PARENT_UUID, folder.getParent().toString());
        }

        if (folder.getOwner() != null && !folder.getOwner().isBlank()) {
            doc.addField(USER_ADMIN, folder.getOwner());
        }

        doc.addField(CREATION_TIME, folder.getCreationTime());
        doc.addField(MODIFICATION_TIME, folder.getModificationTime());

        return doc;
    }

    @Override
    protected String getQueryFields() {
        // Folders are simpler - mainly name and description
        return "uuid^20 name^10 description^5 owner^2";
    }

    /**
     * Search for folders in a specific parent folder
     */
    public SolrDocumentList searchInFolder(
            String searchTerms,
            String userAccount,
            int limit,
            int offset,
            String parentFolderId,
            Permissions permission, VisibilityType visibilityType) throws IOException, SolrServerException, NdexException {

        SolrQuery solrQuery = new SolrQuery();
        String coreName = getCoreNameFromVisibility(visibilityType);

        String permissionFilter = buildPermissionFilter(userAccount, visibilityType,permission);
        String typeFilter = " AND (" + ENTITY_TYPE + ":FOLDER)";
        String parentFilter = "";

        if (parentFolderId != null) {
            parentFilter = " AND (" + PARENT_UUID + ":\"" + parentFolderId + "\")";
        }

        String resultFilter = "(" + permissionFilter + ")" + typeFilter + parentFilter;

        configureQuery(solrQuery, searchTerms, resultFilter, limit, offset);

        try {
            QueryResponse rsp = solrClientWrapper.query(coreName, solrQuery);
            return rsp.getResults();
        } catch (BaseHttpSolrClient.RemoteSolrException e) {
            throw convertException(e, coreName);
        }
    }
}
