package org.ndexbio.common.models.dao.postgresql;

import java.sql.SQLException;
import org.ndexbio.common.models.dao.DAOFactory;
import org.ndexbio.common.models.dao.FileDAO;
import org.ndexbio.common.models.dao.FolderDAO;
import org.ndexbio.common.models.dao.ShortcutDAO;
/**
 *
 * @author churas
 */
public class PostgresDAOFactory implements DAOFactory {

	@Override
	public FileDAO getFileDAO() throws SQLException {
		return new PostgresFileDAO();
	}

	@Override
	public FolderDAO getFolderDAO() throws SQLException {
		return new PostgresFolderDAO();
	}

	@Override
	public ShortcutDAO getShortcutDAO() throws SQLException {
		return new PostgresShortcutDAO();
	}

}
