package org.ndexbio.rest.mcp;

import org.ndexbio.model.object.User;

public record DownloadFileRequest(
    User   user,       // may be null for anonymous download of public/unlisted networks
    String networkId,
    String accessKey,  // nullable — for private networks with an access key
    long   createTime  // System.currentTimeMillis() at token creation
) {}
