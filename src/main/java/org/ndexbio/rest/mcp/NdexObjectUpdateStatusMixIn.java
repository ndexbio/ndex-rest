package org.ndexbio.rest.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Jackson MixIn for NdexObjectUpdateStatus.
 * Adds @JsonPropertyDescription to each field so victools can generate
 * a fully-documented output schema without modifying the external model class.
 */
public abstract class NdexObjectUpdateStatusMixIn {

    @JsonProperty("uuid")
    @JsonPropertyDescription("UUID of the network accepted for update.")
    public abstract java.util.UUID getUuid();

    @JsonProperty("modificationTime")
    @JsonPropertyDescription(
        "Server timestamp when the update was accepted, as Unix epoch milliseconds. " +
        "Poll get_network_summary with this networkId until the summary's isCompleted is true.")
    public abstract long getModificationTime();
}
