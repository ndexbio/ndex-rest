package org.ndexbio.common.solr;

import org.ndexbio.model.object.network.NetworkSummary;

import java.util.Collection;

public class NetworkSummaryWrapper {

    private final NetworkSummary networkSummary;
    private final String ownerUserName;
    private final Collection<String> userReads;
    private final Collection<String> userEdits;

    public NetworkSummaryWrapper(NetworkSummary networkSummary, String ownerUserName,
                                 Collection<String> userReads, Collection<String> userEdits) {
        this.networkSummary = networkSummary;
        this.ownerUserName = ownerUserName;
        this.userReads = userReads;
        this.userEdits = userEdits;
    }

    public NetworkSummary getNetworkSummary() {
        return networkSummary;
    }

    public String getOwnerUserName() {
        return ownerUserName;
    }

    public Collection<String> getUserReads() {
        return userReads;
    }

    public Collection<String> getUserEdits() {
        return userEdits;
    }
}
