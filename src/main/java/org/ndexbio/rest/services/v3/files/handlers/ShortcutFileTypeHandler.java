package org.ndexbio.rest.services.v3.files.handlers;

import java.util.Map;
import java.util.UUID;

import org.ndexbio.common.models.dao.ShortcutDAO;
import org.ndexbio.common.util.NdexUUIDFactory;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.CopyRequest;
import org.ndexbio.model.object.NdexObjectUpdateStatus;
import org.ndexbio.model.object.NdexShortcut;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.ShortcutRequest;
import org.ndexbio.model.object.User;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.Configuration;

/**
 * Handles shortcut-specific operations.
 */
public class ShortcutFileTypeHandler extends AbstractFileTypeHandler {

    @Override
    public NdexObjectUpdateStatus copy(CopyRequest request, User user, String accessKey) throws Exception {
        UUID fromUUID = request.getFileId();
        UUID userId = user.getExternalId();
        UUID toPath = request.getTargetId();

        try (ShortcutDAO dao = Configuration.getInstance().getDAOFactory().getShortcutDAO()) {
            NdexShortcut sourceShortcut = dao.getShortcut(fromUUID, userId);
            ShortcutRequest shortcutRequest = new ShortcutRequest();
            shortcutRequest.setName("Copy of " + sourceShortcut.getName());
            shortcutRequest.setTarget(sourceShortcut.getTarget());
            shortcutRequest.setParent(toPath);
            shortcutRequest.setTargetType(sourceShortcut.getTargetType());

            UUID newShortcutUUID = NdexUUIDFactory.INSTANCE.createNewNDExUUID();
            NdexObjectUpdateStatus status = dao.createShortcut(
                newShortcutUUID,
                userId,
                toPath,
                shortcutRequest.getName(),
                shortcutRequest.getTarget(),
                shortcutRequest.getTargetType()
            );
            dao.commit();
            return status;
        }
    }

    @Override
    public String updateSharingMember(UUID fileId, UUID currentUserId, UUID memberId, Permissions permission) throws Exception {
        throw new NdexException("Unsupported sharing type: SHORTCUT");
    }

    @Override
    public Map<String, String> listMembers(UUID fileId, UUID currentUserId) throws Exception {
        throw new NdexException("Unsupported file type: SHORTCUT");
    }

    @Override
    public String enableAccessKey(UUID fileId, UUID userId) throws Exception {
        throw new NdexException("Sharing shortcut is not supported. Please share the folder or network the shortcut points to instead.");
    }

    @Override
    public void disableAccessKey(UUID fileId, UUID userId) throws Exception {
        throw new NdexException("Shortcuts are not sharable. Unshare is not supported.");
    }

    @Override
    public void setVisibility(UUID fileId, UUID userId, VisibilityType visibility) throws Exception {
        try (ShortcutDAO dao = Configuration.getInstance().getDAOFactory().getShortcutDAO()) {
            if (!dao.isShortcutOwner(fileId, userId)) {
                throw new NdexException("Not the owner of shortcut " + fileId);
            }
            dao.setShortcutVisibility(fileId, visibility);
            dao.commit();
        }
    }
}
