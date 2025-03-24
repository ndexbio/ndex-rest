package org.ndexbio.common.models.dao.postgresql;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Logger;
import org.ndexbio.common.models.dao.FileDAO;
import org.ndexbio.common.models.dao.postgresql.NdexDBDAO;
import org.ndexbio.common.models.dao.postgresql.PostgresShortcutDAO;

import org.ndexbio.model.object.FileCount;

public class PostgresFileDAO extends NdexDBDAO implements FileDAO {
	
	private static Logger logger = Logger.getLogger(PostgresFileDAO.class.getName());

	public PostgresFileDAO() throws SQLException {
		super();
	}
	
	@Override
	public FileCount getOwnedFileCounts(UUID ownerId) throws SQLException {
	    FileCount fc = new FileCount();

	    // Count networks
	    String sqlNet = "SELECT COUNT(*) FROM network WHERE owneruuid=? AND is_deleted=false";
	    try (PreparedStatement pst = db.prepareStatement(sqlNet)) {
	        pst.setObject(1, ownerId);
	        try (ResultSet rs = pst.executeQuery()) {
	            if (rs.next()) {
	                fc.setNetwork(rs.getLong(1));
	            }
	        }
	    }

	    // Count folders
	    String sqlFolder = "SELECT COUNT(*) FROM folder WHERE owneruuid=? AND is_deleted=false";
	    try (PreparedStatement pst = db.prepareStatement(sqlFolder)) {
	        pst.setObject(1, ownerId);
	        try (ResultSet rs = pst.executeQuery()) {
	            if (rs.next()) {
	                fc.setFolder(rs.getLong(1));
	            }
	        }
	    }

	    // Count shortcuts
	    String sqlShortcut = "SELECT COUNT(*) FROM shortcut WHERE owneruuid=? AND is_deleted=false";
	    try (PreparedStatement pst = db.prepareStatement(sqlShortcut)) {
	        pst.setObject(1, ownerId);
	        try (ResultSet rs = pst.executeQuery()) {
	            if (rs.next()) {
	                fc.setShortcut(rs.getLong(1));
	            }
	        }
	    }
	    return fc;
	}


}
