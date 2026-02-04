package org.ndexbio.common.solr.v3;

import org.apache.solr.common.SolrInputDocument;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.object.network.VisibilityType;

import java.util.*;

public class GlobalNetworkIndexManager extends NFSIndexManager<NetworkSummary> {

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
    protected SolrInputDocument setupIndexDocument(NetworkSummary inputData) {
        return null;
    }


    @Override
    public void postCommit(){
        nodeMembers = new TreeMap<>();
    }

}
