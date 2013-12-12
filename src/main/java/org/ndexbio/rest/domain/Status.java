package org.ndexbio.rest.domain;

public enum Status
{
    QUEUED,
    PROCESSING,
    COMPLETED,
    COMPLETED_WITH_WARNINGS,
    COMPLETED_WITH_ERRORS,
    FAILED
}
