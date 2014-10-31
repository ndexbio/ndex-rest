package org.ndexbio.rest;

import java.io.File;

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
import com.orientechnologies.orient.server.OServer;
import com.orientechnologies.orient.server.OServerMain;

public class NdexHttpServletDispatcher extends HttpServletDispatcher {
	
	private static final int defaultPoolSize = 50;
	private OServer orientDBServer;
	
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

			try {
				String configFile = configuration.getNdexRoot() + "/conf/orientdb-server-config.xml";
				File cf = new File( configFile);
				orientDBServer = OServerMain.create();
				orientDBServer.startup(cf);
				orientDBServer.activate();
			} catch (Exception e1) {
				e1.printStackTrace();
				throw new javax.servlet.ServletException("Failed to start up OrientDB server: " + e1.getMessage());
			}
			
			String poolSize = configuration.getProperty(NdexServerProperties.NDEX_DBCONNECTION_POOL_SIZE);
			Integer size = null;
			try {
				if ( poolSize != null ) {
					size = Integer.valueOf(poolSize);
				}
			} catch (NumberFormatException e) {
				size = defaultPoolSize;
			}
			
			// check if the db exists, if not create it.
			try ( ODatabaseDocumentTx odb = new ODatabaseDocumentTx(configuration.getDBURL())) {
				if ( !odb.exists() ) 
					odb.create();
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
		    orientDBServer.shutdown();			
        	System.out.println ("Database has been closed.");
        } catch (Exception ee) {
            ee.printStackTrace();
            System.out.println("Error occured when shutting down Orient db.");
        }
        
		super.destroy();
	}
	
}
