package org.ndexbio.common.solr.v3;

import org.apache.solr.common.SolrInputDocument;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.NdexShortcut;
import org.ndexbio.model.object.ShortcutRequest;
import org.ndexbio.model.object.network.VisibilityType;

public class ShortcutIndexManager extends NFSIndexManager<NdexShortcut> {


    public ShortcutIndexManager(VisibilityType visibilityType){
        super(visibilityType);
    }

    @Override
    protected SolrInputDocument setupIndexDocument(NdexShortcut shortcut){
        doc = new SolrInputDocument();

        doc.addField(UUID, shortcut.getExternalId().toString());
        doc.addField(ENTITY_TYPE, FileType.SHORTCUT.toString());

        if (shortcut.getName() != null && shortcut.getName().length()>1){
            doc.addField(NAME, shortcut.getName());
        }
        if (shortcut.getOwner() != null && !shortcut.getOwner().isBlank()) {
            doc.addField(USER_ADMIN, shortcut.getOwner());
        }
        // @TODO do we want to index parent uuid?

        // @TODO do we want to index target type?
        doc.addField(CREATION_TIME, shortcut.getCreationTime());
        doc.addField(MODIFICATION_TIME, shortcut.getModificationTime());

        if (visibilityType.equals(VisibilityType.PRIVATE)){
            // @TODO add private information to document for indexing
            if (shortcut.getOwner() != null && !shortcut.getOwner().isBlank()) {
                doc.addField(OWNER_FIELD, shortcut.getOwner());
            }
            doc.addField(VISIBILITY, VisibilityType.PRIVATE.toString());
        }
        return doc;
    }



}
