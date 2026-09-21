package org.ndexbio.common.solr;

import org.apache.solr.common.SolrInputDocument;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.NdexFolder;
import org.ndexbio.model.object.network.VisibilityType;
import org.slf4j.LoggerFactory;


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
}
