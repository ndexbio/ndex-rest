package org.ndexbio.task;

import org.apache.solr.client.solrj.SolrServerException;
import org.ndexbio.common.models.dao.FolderDAO;
import org.ndexbio.common.models.dao.ShortcutDAO;
import org.ndexbio.common.models.dao.postgresql.PostgresFolderDAO;
import org.ndexbio.common.models.dao.postgresql.PostgresNetworkDAO;
import org.ndexbio.common.solr.FolderIndexManager;
import org.ndexbio.common.solr.NetworkGlobalIndexManager;
import org.ndexbio.common.solr.ShortcutIndexManager;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.*;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.Configuration;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class SolrTaskRebuildFileIdx extends NdexSystemTask {

//private static Logger logger = Logger.getLogger(CXNetworkLoadingTask.class.getName());

	private static final TaskType taskType = TaskType.SYS_LOAD_CX2_NETWORK; //todo may need new

	private final UUID fileId;
	private final UUID userId;

	private final VisibilityType visibilityType;
	private final FileType fileType;
	private final boolean createOnly;

	public SolrTaskRebuildFileIdx(UUID fileId, UUID userId, VisibilityType visibilityType,
								  FileType fileType, boolean createOnly) {
		super();
		this.fileId = fileId;
		this.userId = userId;
		this.visibilityType = visibilityType;
		this.fileType = fileType;
		this.createOnly = createOnly;
	}

	@Override
	public void run() throws NdexException, SolrServerException, IOException, SQLException {
		String id = fileId.toString();
		if (fileType.equals(FileType.FOLDER)){
			rebuildFolderIndex(id);
		} else if (fileType.equals(FileType.SHORTCUT)) {
			rebuildShortcutIndex(id);
		} else if (fileType.equals(FileType.NETWORK)) {
			throw new UnsupportedOperationException("Use SolrTaskRebuildNetworkIdx task for network rebuilding");
		}
		else {
			throw new IllegalArgumentException("Invalid file type: " + fileType);
		}


	}

	private void rebuildFolderIndex(String id) throws NdexException, SolrServerException, IOException, SQLException {
		try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
			if (!createOnly){
				try (FolderIndexManager folderIdxManager = new FolderIndexManager(visibilityType)) {
					folderIdxManager.delete(id);
				}

			}
			String accessKey = dao.getFolderAccessKey(fileId); //todo temp, not sure if right
			NdexFolder folder = dao.getFolder(fileId, userId, accessKey );
			try(FolderIndexManager globalIdx = new FolderIndexManager(visibilityType)) {
				Map<String, String> folderPermissions = dao.getFolderPermissions(fileId);
 				globalIdx.createIndex(folder,
						getFolderUserReads(folderPermissions),
						getFolderUserWrites(folderPermissions));
			}

		} catch (Exception e) {
            throw new RuntimeException(e);
        }


    }
	private void rebuildShortcutIndex(String id) throws NdexException, SolrServerException, IOException {
		try (ShortcutDAO dao = Configuration.getInstance().getDAOFactory().getShortcutDAO()) {
			NdexShortcut shortcut = dao.getShortcut(fileId, userId);
			try(ShortcutIndexManager globalIdx = new ShortcutIndexManager(visibilityType)) {
				// todo get user reads and edits
				globalIdx.createIndex(shortcut, null, null);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}


	@Override
	public Task createTask() {
		Task t = super.createTask();
		t.setResource(fileId.toString());
		return t;
	}

	@Override
	public TaskType getTaskType() {
		return taskType;
	}

	public static Set<String> getFolderUserReads(Map<String, String> folderPermissions){
		return folderPermissions.entrySet().stream()
				.filter(entry -> entry.getValue().equals(Permissions.READ.toString()))
				.map(Map.Entry::getKey)
				.collect(Collectors.toSet());
	}
	public static Set<String> getFolderUserWrites(Map<String, String> folderPermissions){
		return folderPermissions.entrySet().stream()
				.filter(entry -> entry.getValue().equals(Permissions.WRITE.toString()))
				.map(Map.Entry::getKey)
				.collect(Collectors.toSet());
	}


}
