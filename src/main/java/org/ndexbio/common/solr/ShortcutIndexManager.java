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
import org.ndexbio.model.object.NdexShortcut;
import org.ndexbio.model.object.network.VisibilityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ShortcutIndexManager extends NFSIndexManager<NdexShortcut> {

    protected static final String TARGET_UUID = "targetUuid";
    protected static final String TARGET_TYPE = "targetType";
    protected static final String IS_DANGLING = "isDangling";
    private static final Logger log = LoggerFactory.getLogger(ShortcutIndexManager.class);

    public ShortcutIndexManager(VisibilityType visibilityType){
        super(visibilityType);
    }

    @Override
    protected SolrInputDocument setupIndexDocument(NdexShortcut shortcut){
        doc = new SolrInputDocument();

        doc.addField(UUID, shortcut.getExternalId().toString());
        doc.addField(ENTITY_TYPE, FileType.SHORTCUT.toString());

        if (shortcut.getName() != null && !shortcut.getName().trim().isEmpty()){
            doc.addField(NAME, shortcut.getName());
        }
        if (shortcut.getOwner() != null && !shortcut.getOwner().isBlank()) {
            doc.addField(USER_ADMIN, shortcut.getOwner());
        }
        if (shortcut.getParent() != null) {
            doc.addField(PARENT_UUID, shortcut.getParent().toString());
        }

        if (shortcut.getTarget() != null) {
            doc.addField(TARGET_UUID, shortcut.getTarget().toString());
            doc.addField(IS_DANGLING, false);
        } else {
            doc.addField(IS_DANGLING, true);
        }

        if (shortcut.getTargetType() != null) {
            doc.addField(TARGET_TYPE, shortcut.getTargetType().toString());
        }
        doc.addField(CREATION_TIME, shortcut.getCreationTime());
        doc.addField(MODIFICATION_TIME, shortcut.getModificationTime());

        if (visibilityType.equals(VisibilityType.PRIVATE)){
            // @TODO add private information to document for indexing (need inherited permissions)
            // (already added) if (shortcut.getOwner() != null && !shortcut.getOwner().isBlank()) {
              //  doc.addField(OWNER_FIELD, shortcut.getOwner());
            //}
            doc.addField(VISIBILITY, VisibilityType.PRIVATE.toString());
        }
        return doc;
    }
    @Override
    protected String getQueryFields() {
        // Shortcuts are very simple - just name and owner
        return "uuid^20 name^10 owner^2";
    }

    /**
     * Find dangling shortcuts (where target has been deleted)
     */
    public SolrDocumentList findDanglingShortcuts(
            String userAccount,
            int limit,
            int offset) throws IOException, SolrServerException, NdexException {

        SolrQuery solrQuery = new SolrQuery();

        String permissionFilter = buildPermissionFilter(userAccount, null);
        String typeFilter = " AND (" + ENTITY_TYPE + ":SHORTCUT)";
        String danglingFilter = " AND (" + IS_DANGLING + ":true)";

        String resultFilter = "(" + permissionFilter + ")" + typeFilter + danglingFilter;
        configureQuery(solrQuery, "*:*", resultFilter, limit, offset);

        try {
            QueryResponse rsp = client.query(solrQuery, SolrRequest.METHOD.POST);
            return rsp.getResults();
        } catch (BaseHttpSolrClient.RemoteSolrException e) {
            throw convertException(e, coreName);
        }
    }
}

