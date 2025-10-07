package org.ndexbio.rest.services.v3.files.handlers;

import java.util.Map;
import java.util.UUID;

import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.CopyRequest;
import org.ndexbio.model.object.NdexObjectUpdateStatus;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.User;
import org.ndexbio.model.object.network.VisibilityType;

/**
 * Base handler for file-type specific behaviour. Concrete subclasses override the
 * operations they support and throw {@link NdexException} for unsupported actions.
 */
public abstract class AbstractFileTypeHandler {

    public NdexObjectUpdateStatus copy(CopyRequest request, User user, String accessKey) throws Exception {
        throw unsupported("copy");
    }

    public String updateSharingMember(UUID fileId, UUID currentUserId, UUID memberId, Permissions permission) throws Exception {
        throw unsupported("update member permissions");
    }

    public Map<String, String> listMembers(UUID fileId, UUID currentUserId) throws Exception {
        throw unsupported("list members");
    }

    public String enableAccessKey(UUID fileId, UUID userId) throws Exception {
        throw unsupported("enable access key");
    }

    public void disableAccessKey(UUID fileId, UUID userId) throws Exception {
        throw unsupported("disable access key");
    }

    public void setVisibility(UUID fileId, UUID userId, VisibilityType visibility) throws Exception {
        throw unsupported("set visibility");
    }

    protected NdexException unsupported(String action) {
        return new NdexException("Unsupported operation: " + action);
    }
}
