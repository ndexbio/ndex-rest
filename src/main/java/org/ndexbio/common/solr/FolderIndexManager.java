package org.ndexbio.common.solr;

import org.apache.solr.common.SolrInputDocument;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.NdexFolder;
import org.ndexbio.model.object.network.VisibilityType;

public class FolderIndexManager extends NFSIndexManager<NdexFolder> {


    public FolderIndexManager(VisibilityType visibilityType){
        super(visibilityType);
    }

    @Override
    protected SolrInputDocument setupIndexDocument(NdexFolder folder) {
        doc = new SolrInputDocument();
        doc.addField(UUID, folder.getExternalId().toString());
        doc.addField(ENTITY_TYPE, FileType.FOLDER.toString());

        if (folder.getName() != null && folder.getName().length()>1){
            doc.addField(NAME, folder.getName());
        }
        if (folder.getDescription() != null && folder.getDescription().length()>1){
            doc.addField(DESC, folder.getDescription());
        }
        // @TODO do we want to index parent uuid?
        if (folder.getOwner() != null && !folder.getOwner().isBlank()) {
            doc.addField(USER_ADMIN, folder.getOwner());
        }
        doc.addField(CREATION_TIME, folder.getCreationTime());
        doc.addField(MODIFICATION_TIME, folder.getModificationTime());

        if (visibilityType.equals(VisibilityType.PRIVATE)){
            if (folder.getOwner() != null && !folder.getOwner().isBlank()) {
                doc.addField(OWNER_FIELD, folder.getOwner());
            }
            doc.addField(VISIBILITY, VisibilityType.PRIVATE.toString());
        }

        return doc;
    }

}
