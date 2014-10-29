package org.ndexbio.rest;

import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.ndexbio.common.NdexServerProperties;
import org.ndexbio.common.access.NdexAOrientDBConnectionPool;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.exceptions.NdexException;
import org.ndexbio.common.models.dao.orientdb.UserDAO;
import org.ndexbio.task.Configuration;
import org.ndexbio.task.utility.DatabaseInitializer;

import com.orientechnologies.orient.core.Orient;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;

public class NdexHttpServletDispatcher extends HttpServletDispatcher {
	
	private static final int defaultPoolSize = 50;
	
	public NdexHttpServletDispatcher() {
		super();
	}

	@Override
	public void init(javax.servlet.ServletConfig servletConfig)
	          throws javax.servlet.ServletException {
		super.init(servletConfig);
		
		Configuration configuration = null;
		try {
			configuration = Configuration.getInstance();
			
			String poolSize = configuration.getProperty(NdexServerProperties.NDEX_DBCONNECTION_POOL_SIZE);
			Integer size = null;
			try {
				size = Integer.valueOf(poolSize);
			} catch (NumberFormatException e) {
				size = defaultPoolSize;
			}
			
			//and initialize the db connections
			NdexAOrientDBConnectionPool.createOrientDBConnectionPool(
    			configuration.getDBURL(),
    			configuration.getDBUser(),
    			configuration.getDBPasswd(), size.intValue());
    	
			NdexDatabase db = new NdexDatabase (configuration.getHostURI());
    	
			System.out.println("Db created for " + NdexDatabase.getURIPrefix());
    	
			ODatabaseDocumentTx conn = db.getAConnection();
			UserDAO dao = new UserDAO(conn);
    	
			DatabaseInitializer.createUserIfnotExist(dao, configuration.getSystmUserName(), "support@ndexbio.org", 
    				configuration.getSystemUserPassword());
			conn.commit();
			conn.close();
			conn = null;
			db.close();	
			db = null;
		} catch (NdexException e) {
			e.printStackTrace();
			throw new javax.servlet.ServletException(e.getMessage());
		}
    	
	}
	
	
	@Override
	public void destroy() {
		
        System.out.println("Database clean up started");
        try {
        	NdexAOrientDBConnectionPool.close();
        	Orient.instance().shutdown();
        	System.out.println ("Database has been closed.");
        } catch (Exception ee) {
            ee.printStackTrace();
        }
        
		super.destroy();
	}
}
