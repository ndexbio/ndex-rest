package org.ndexbio.rest.services.v3.files.handlers;

import java.util.Map;
import java.util.UUID;

import org.ndexbio.common.models.dao.FolderDAO;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.CopyRequest;
import org.ndexbio.model.object.NdexObjectUpdateStatus;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.User;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.Configuration;

/**
 * Handles folder-specific operations.
 */
public class FolderFileTypeHandler extends AbstractFileTypeHandler {

    @Override
    public NdexObjectUpdateStatus copy(CopyRequest request, User user, String accessKey) throws Exception {
        throw new NdexException("Coping folder is not supported. Create shortcut instead");
    }

    @Override
    public String updateSharingMember(UUID fileId, UUID currentUserId, UUID memberId, Permissions permission) throws Exception {
        try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
            if (!dao.isFolderOwner(fileId, currentUserId)) {
                throw new UnauthorizedOperationException("Signed in user is not the owner of this folder.");
            }

            if (permission == null) {
                dao.removeFolderPermission(fileId, memberId);
                dao.commit();
                return "folder permissions removed";
            }

            dao.setFolderPermission(fileId, memberId, permission);
            dao.commit();
            return "folder permission granted";
        }
    }

    @Override
    public Map<String, String> listMembers(UUID fileId, UUID currentUserId) throws Exception {
        try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
            if (!dao.isFolderOwner(fileId, currentUserId)) {
                throw new UnauthorizedOperationException("Signed in user is not the owner of this folder.");
            }
            return dao.getFolderPermissions(fileId);
        }
    }

    @Override
    public String enableAccessKey(UUID fileId, UUID userId) throws Exception {
        try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
            if (!dao.isFolderOwner(fileId, userId)) {
                throw new UnauthorizedOperationException("You are not the owner of folder " + fileId);
            }
            String accessKey = dao.enableFolderAccessKey(fileId);
            dao.commit();
            return accessKey;
        }
    }

    @Override
    public void disableAccessKey(UUID fileId, UUID userId) throws Exception {
        try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
            if (!dao.isFolderOwner(fileId, userId)) {
                throw new UnauthorizedOperationException("You are not the owner of folder " + fileId);
            }
            dao.disableFolderAccessKey(fileId);
            dao.commit();
        }
    }

    @Override
    public void setVisibility(UUID fileId, UUID userId, VisibilityType visibility) throws Exception {
        try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
            if (!dao.isFolderOwner(fileId, userId)) {
                throw new UnauthorizedOperationException("Not the owner of folder " + fileId);
            }
            dao.setFolderVisibility(fileId, visibility);
            dao.commit();
        }
    }

    @Override
    public void validateShortcutTarget(UUID targetId, UUID userId) throws Exception {
        try (FolderDAO folderDao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
            if (!folderDao.isReadable(targetId, userId)) {
                throw new NdexException("Target folder does not exist or is not accessible.");
            }
        }
    }
}
