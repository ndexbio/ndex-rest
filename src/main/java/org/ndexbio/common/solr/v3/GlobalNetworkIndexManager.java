package org.ndexbio.common.solr.v3;

import org.apache.solr.common.SolrInputDocument;
import org.ndexbio.common.util.Util;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.object.network.VisibilityType;

import java.util.*;

public class GlobalNetworkIndexManager extends NFSIndexManager<NetworkSummaryWrapper> {
    private static final String USER_READ= "userRead";
    private static final String USER_EDIT = "userEdit";

    // user required indexing fields. hardcoded for now. Will turn them into configurable list in 1.4.
    public static final Set<String> otherAttributes =
            new HashSet<>(Arrays.asList("objectCategory", "organism",
                    "platform",
                    "graphPropertiesHash",
                    "networkType",
                    "disease",
                    "tissue",
                    "rightsHolder",
                    "author",
                    "createdAt",
                    "methods",
                    "subnetworkType","subnetworkFilter","graphHash","rights", "labels"));

    // holds the mapping between node ID and member attributes.
    // members will be added to the represent field as additional lists.
    private Map<Long, Set<String>> nodeMembers;

    public GlobalNetworkIndexManager(VisibilityType visibilityType){
        super(visibilityType);
        nodeMembers = new TreeMap<>();
    }

    @Override
    protected SolrInputDocument setupIndexDocument(NetworkSummaryWrapper summaryWrapper) {
        NetworkSummary summary = summaryWrapper.getNetworkSummary();
        doc.addField(UUID,  summary.getExternalId().toString() );
        doc.addField(EDGE_COUNT, summary.getEdgeCount());
        doc.addField(NODE_COUNT, summary.getNodeCount());
        doc.addField(VISIBILITY, summary.getVisibility().toString());

        if ( summary.getName() !=null && summary.getName().length()>1) {
            doc.addField(NAME, summary.getName());
        }

        if (summary.getDescription() !=null && summary.getDescription().length()>1) {
            doc.addField(DESC, summary.getDescription());
        }

        if ( summary.getVersion() !=null && summary.getVersion().length()>1) {
            doc.addField(VERSION, summary.getVersion());
        }

        doc.addField(CREATION_TIME, summary.getCreationTime());
        doc.addField(MODIFICATION_TIME, summary.getModificationTime());

        doc.addField(NDEX_SCORE, Util.getNdexScoreFromSummary(summary));

        // network summary already has owner field?
        doc.addField(USER_ADMIN, summaryWrapper.getOwnerUserName());
        //doc.setDocumentBoost(documentBoost);;
        if( summaryWrapper.getUserReads() != null) {
            for(String userName : summaryWrapper.getUserReads()) {
                doc.addField(USER_READ, userName);
            }
        }

        if ( summaryWrapper.getUserEdits() !=null) {
            for ( String userName: summaryWrapper.getUserEdits()) {
                doc.addField(USER_EDIT, userName);
            }
        }

        if (visibilityType.equals(VisibilityType.PRIVATE)){
            if( summaryWrapper.getUserReads() != null) {
                addKeyWithValues(doc, USER_READ, summaryWrapper.getUserReads());
            }
            if ( summaryWrapper.getUserEdits() !=null) {
                addKeyWithValues(doc, USER_EDIT, summaryWrapper.getUserEdits());
            }


            if (summaryWrapper.getOwnerUserName() != null && !summaryWrapper.getOwnerUserName().isBlank()) {
                doc.addField(OWNER_FIELD, summaryWrapper.getOwnerUserName());
            }

        }
        return doc;
    }


    @Override
    public void postCommit(){
        nodeMembers = new TreeMap<>();
    }


    private void addStringOrListgObj(Object e, String solrFieldName, List<String>  warnings ) {
        if (e instanceof String) {
            doc.addField(solrFieldName, e);
        } else if (e instanceof List<?>) {
            for ( Object value : ((List<?>)e)) {
                if ( value instanceof String)
                    doc.addField(solrFieldName, value);
                else {
                    warnings.add("Network attribute " + solrFieldName +  " is not indexed because its data type is not 'string' or 'list_of_string'.");
                    break;
                }
            }
        } else
            warnings.add("Network attribute " + solrFieldName + " is not indexed because its data type is not 'string' or 'list_of_string'.");
    }


}
