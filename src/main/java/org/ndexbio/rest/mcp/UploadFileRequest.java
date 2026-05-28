package org.ndexbio.rest.mcp;

import org.ndexbio.model.object.User;

public record UploadFileRequest(
    User   user,
    String networkId,      // null = create, non-null = update
    String visibility,     // nullable; ignored on update path
    String extraNodeIndex, // nullable; ignored on update path
    String folderId,       // nullable; ignored on update path
    long   createTime      // System.currentTimeMillis() at token creation
) {}
