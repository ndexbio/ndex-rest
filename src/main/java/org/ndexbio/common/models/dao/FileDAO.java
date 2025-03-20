package org.ndexbio.common.models.dao;

import java.sql.SQLException;
import java.util.UUID;
import org.ndexbio.model.object.FileCount;

/**
 *
 * @author churas
 */
public interface FileDAO extends AutoCloseable {

	FileCount getOwnedFileCounts(UUID ownerId) throws SQLException;
	
}
