package org.ndexbio.common.models.dao.postgresql;

import java.sql.SQLException;
import org.ndexbio.common.models.dao.DAOFactory;
import org.ndexbio.common.models.dao.FileDAO;
/**
 *
 * @author churas
 */
public class PostgresDAOFactory implements DAOFactory {

	@Override
	public FileDAO getFileDAO() throws SQLException {
		return new PostgresFileDAO();
	}

}
