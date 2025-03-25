
package org.ndexbio.common.models.dao;

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
	
}
