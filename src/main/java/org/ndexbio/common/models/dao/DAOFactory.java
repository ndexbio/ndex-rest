
package org.ndexbio.common.models.dao;

import org.ndexbio.common.models.dao.postgresql.UserDAO;

import java.sql.SQLException;

/**
 *
 * @author churas
 */
public interface DAOFactory {
	
	/**
	 * Gets File Data Access Object
	 * @return 
	 */
	FileDAO getFileDAO() throws SQLException;
	
	FolderDAO getFolderDAO() throws SQLException;
	
	ShortcutDAO getShortcutDAO() throws SQLException;

	TrashDAO getTrashDAO() throws SQLException;

	NetworkDAO getNetworkDAO() throws SQLException;
	UserDAO getUserDAO() throws SQLException;

}
