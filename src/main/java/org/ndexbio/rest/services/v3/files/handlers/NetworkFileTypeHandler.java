package org.ndexbio.rest.services.v3.files.handlers;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Timestamp;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.ndexbio.common.cx.CX2NetworkFileGenerator;
import org.ndexbio.common.cx.CXNetworkFileGenerator;
import org.ndexbio.common.models.dao.FolderDAO;
import org.ndexbio.common.models.dao.NetworkDAO;
import org.ndexbio.common.models.dao.postgresql.PostgresNetworkDAO;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.common.persistence.CX2NetworkLoader;
import org.ndexbio.common.persistence.CXNetworkLoader;
import org.ndexbio.common.util.NdexUUIDFactory;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.CopyRequest;
import org.ndexbio.model.object.NdexObjectUpdateStatus;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.User;
import org.ndexbio.model.object.network.NetworkIndexLevel;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.services.NetworkServiceV2;
import org.ndexbio.task.NdexServerQueue;
import org.ndexbio.task.SolrIndexScope;
import org.ndexbio.task.SolrTaskRebuildNetworkIdx;

/**
 * Handles network-specific operations.
 */
public class NetworkFileTypeHandler extends AbstractFileTypeHandler {

    @Override
    public NdexObjectUpdateStatus copy(CopyRequest request, User user, String accessKey) throws Exception {
        UUID userId = user.getExternalId();
        UUID srcNetUUID = request.getFileId();
        UUID targetId = request.getTargetId();

        try (UserDAO dao = new UserDAO()) {
            dao.checkDiskSpace(userId);
        }

        try (PostgresNetworkDAO dao = new PostgresNetworkDAO()) {
            if (!dao.isReadable(srcNetUUID, userId)) {
                if (!dao.accessKeyIsValid(srcNetUUID, accessKey)) {
                    throw new UnauthorizedOperationException("User doesn't have read access to this network.");
                }
            }

            if (!dao.networkIsValid(srcNetUUID)) {
                throw new NdexException("Invalid networks can not be copied.");
            }
        }

        if (targetId != null) {
            try (FolderDAO folderDao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
                if (!folderDao.isFolderOwner(targetId, userId)) {
                    throw new UnauthorizedOperationException("User doesn't have access to the target folder.");
                }
            }
        }

        UUID uuid = NdexUUIDFactory.INSTANCE.createNewNDExUUID();
        String uuidStr = uuid.toString();
        java.nio.file.Path tgt = Paths.get(Configuration.getInstance().getNdexRoot() + "/data/" + uuidStr);

        Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxrwxr-x");
        FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(perms);
        Files.createDirectory(tgt, attr);

        copyNetworkFiles(srcNetUUID, uuidStr, attr);
        createNetworkEntryAndFiles(uuid, user, srcNetUUID, targetId);

        NdexServerQueue.INSTANCE.addSystemTask(
            new SolrTaskRebuildNetworkIdx(uuid, SolrIndexScope.individual, true, null, NetworkIndexLevel.NONE, false)
        );

        NdexObjectUpdateStatus status = new NdexObjectUpdateStatus();
        status.setUuid(uuid);
        status.setModificationTime(new Timestamp(System.currentTimeMillis()));
        return status;
    }

    @Override
    public String updateSharingMember(UUID fileId, UUID currentUserId, UUID memberId, Permissions permission) throws Exception {
        try (NetworkDAO dao = Configuration.getInstance().getDAOFactory().getNetworkDAO()) {
            if (!dao.isAdmin(fileId, currentUserId)) {
                throw new UnauthorizedOperationException("You are not an administrator of network " + fileId);
            }

            if (permission == null) {
                dao.revokeUserPrivilege(fileId, memberId);
                return "network permissions removed";
            }

            dao.grantPrivilegeToUser(fileId, memberId, permission);
            return "network permission granted";
        }
    }

    @Override
    public Map<String, String> listMembers(UUID fileId, UUID currentUserId) throws Exception {
        try (NetworkDAO dao = Configuration.getInstance().getDAOFactory().getNetworkDAO()) {
            if (!dao.isAdmin(fileId, currentUserId)) {
                throw new UnauthorizedOperationException("You are not an administrator of network " + fileId);
            }
            return dao.getNetworkUserPermissions(fileId, null, -1, -1);
        }
    }

    @Override
    public String enableAccessKey(UUID fileId, UUID userId) throws Exception {
        try (NetworkDAO dao = Configuration.getInstance().getDAOFactory().getNetworkDAO()) {
            if (!dao.isAdmin(fileId, userId)) {
                throw new UnauthorizedOperationException("You are not an administrator of network " + fileId);
            }
            String accessKey = dao.enableNetworkAccessKey(fileId);
            dao.commit();
            return accessKey;
        }
    }

    @Override
    public void disableAccessKey(UUID fileId, UUID userId) throws Exception {
        try (NetworkDAO dao = Configuration.getInstance().getDAOFactory().getNetworkDAO()) {
            if (!dao.isAdmin(fileId, userId)) {
                throw new UnauthorizedOperationException("You are not an administrator of network " + fileId);
            }
            dao.disableNetworkAccessKey(fileId);
            dao.commit();
        }
    }

    @Override
    public void setVisibility(UUID fileId, UUID userId, VisibilityType visibility) throws Exception {
        try (PostgresNetworkDAO dao = new PostgresNetworkDAO()) {
            if (!dao.isAdmin(fileId, userId)) {
                throw new NdexException("Not the owner of network " + fileId);
            }
            dao.updateNetworkVisibility(fileId, visibility, true);
            dao.commit();
        }
    }

    private void copyNetworkFiles(UUID srcNetUUID, String tgtUUID, FileAttribute<Set<PosixFilePermission>> attr) throws Exception {
        String srcPathPrefix = Configuration.getInstance().getNdexRoot() + "/data/" + srcNetUUID + "/";
        String tgtPathPrefix = Configuration.getInstance().getNdexRoot() + "/data/" + tgtUUID + "/";

        File srcAspectDir = new File(srcPathPrefix + CXNetworkLoader.CX1AspectDir);
        if (srcAspectDir.exists()) {
            File tgtAspectDir = new File(tgtPathPrefix + CXNetworkLoader.CX1AspectDir);
            FileUtils.copyDirectory(srcAspectDir, tgtAspectDir);
        }

        String tgtCX2AspectPathPrefix = tgtPathPrefix + CX2NetworkLoader.cx2AspectDirName;
        String srcCX2AspectPathPrefix = srcPathPrefix + CX2NetworkLoader.cx2AspectDirName;
        File srcCX2AspectDir = new File(srcCX2AspectPathPrefix);
        Files.createDirectories(Paths.get(tgtCX2AspectPathPrefix), attr);

        String[] aspectFiles = srcCX2AspectDir.list();
        if (aspectFiles != null) {
            for (String fname : aspectFiles) {
                java.nio.file.Path src = Paths.get(srcPathPrefix + CX2NetworkLoader.cx2AspectDirName, fname);
                java.nio.file.Path link = Paths.get(tgtCX2AspectPathPrefix, fname);

                if (Files.isSymbolicLink(src)) {
                    java.nio.file.Path target = Paths.get(tgtPathPrefix + CXNetworkLoader.CX1AspectDir, fname);
                    Files.createSymbolicLink(link, target);
                } else {
                    Files.copy(Paths.get(srcCX2AspectPathPrefix, fname), Paths.get(tgtCX2AspectPathPrefix, fname));
                }
            }
        }

        java.nio.file.Path srcSample = Paths.get(Configuration.getInstance().getNdexRoot() + "/data/" + srcNetUUID + "/sample.cx");
        if (Files.exists(srcSample, LinkOption.NOFOLLOW_LINKS)) {
            java.nio.file.Path tgtSample = Paths.get(Configuration.getInstance().getNdexRoot() + "/data/" + tgtUUID + "/sample.cx");
            Files.copy(srcSample, tgtSample);
        }
    }

    private void createNetworkEntryAndFiles(UUID uuid, User user, UUID srcNetUUID, UUID targetId) throws Exception {
        String cxFileName = Configuration.getInstance().getNdexRoot() + "/data/" + srcNetUUID + "/" + NetworkServiceV2.cx1NetworkFileName;
        long fileSize = new File(cxFileName).length();

        try (PostgresNetworkDAO dao = new PostgresNetworkDAO()) {
            dao.CreateCloneNetworkEntry(uuid, user.getExternalId(), user.getUserName(), fileSize, srcNetUUID);

            if (targetId != null) {
                dao.setNetworkFolder(uuid, targetId);
            }

            CXNetworkFileGenerator g = new CXNetworkFileGenerator(uuid, dao);
            g.reCreateCXFile();

            CX2NetworkFileGenerator g2 = new CX2NetworkFileGenerator(uuid, dao);
            String tmpFilePath = g2.createCX2File();
            Files.move(
                Paths.get(tmpFilePath),
                Paths.get(Configuration.getInstance().getNdexRoot() + "/data/" + uuid + "/" + CX2NetworkLoader.cx2NetworkFileName),
                StandardCopyOption.ATOMIC_MOVE
            );

            dao.setFlag(uuid, "iscomplete", true);
            dao.commit();
        }
    }
}
