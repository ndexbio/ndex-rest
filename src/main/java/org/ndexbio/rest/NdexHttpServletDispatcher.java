package org.ndexbio.rest;

import java.io.File;
import java.util.Collection;
import java.util.List;

import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.ndexbio.common.NdexClasses;
import org.ndexbio.common.NdexServerProperties;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.models.dao.orientdb.TaskDocDAO;
import org.ndexbio.common.models.dao.orientdb.UserDocDAO;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.TaskType;
import org.ndexbio.task.ClientTaskProcessor;
import org.ndexbio.task.Configuration;
import org.ndexbio.task.NdexServerQueue;
import org.ndexbio.task.SystemTaskProcessor;
import org.ndexbio.task.utility.DatabaseInitializer;

import com.orientechnologies.orient.core.Orient;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
import com.orientechnologies.orient.server.OServer;
import com.orientechnologies.orient.server.OServerMain;

public class NdexHttpServletDispatcher extends HttpServletDispatcher {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
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
				} else 
					size = defaultPoolSize;
			} catch (NumberFormatException e) {
				size = defaultPoolSize;
			}
			
			// check if the db exists, if not create it.
			try ( ODatabaseDocumentTx odb = new ODatabaseDocumentTx(configuration.getDBURL())) {
				if ( !odb.exists() ) 
					odb.create();
			}
			
			//and initialize the db connections
    	
			NdexDatabase db = NdexDatabase.createNdexDatabase(configuration.getHostURI(),
					configuration.getDBURL(),
	    			configuration.getDBUser(),
	    			configuration.getDBPasswd(), size.intValue());
    	
			System.out.println("Db created for " + NdexDatabase.getURIPrefix());
    	
			try (UserDocDAO dao = new UserDocDAO(db.getAConnection())) {
    	
				String sysUserEmail = configuration.getProperty("NdexSystemUserEmail");
				DatabaseInitializer.createUserIfnotExist(dao, configuration.getSystmUserName(),
					(sysUserEmail == null? "support@ndexbio.org" : sysUserEmail), 
    				configuration.getSystemUserPassword());
			}
			
			// find tasks that needs to be processed in system queue
			populateSystemQueue();
			populateUserQueue();
			
			System.out.print("Starting system task executor...");
			new Thread(new SystemTaskProcessor()).start();
			System.out.println("Done.");
			System.out.print("Starting client task executor...");
			new Thread(new ClientTaskProcessor()).start();
			System.out.println("Done.");

			
		} catch (NdexException e) {
			e.printStackTrace();
			throw new javax.servlet.ServletException(e.getMessage());
		}
    	
	}
	
	
	@Override
	public void destroy() {
		
        System.out.println("Database clean up started");
        try {
        	NdexDatabase.close();
        	Orient.instance().shutdown();
		    orientDBServer.shutdown();			
        	System.out.println ("Database has been closed.");
        } catch (Exception ee) {
            ee.printStackTrace();
            System.out.println("Error occured when shutting down Orient db.");
        }
        
		super.destroy();
	}
	
	
	private static void populateSystemQueue() throws NdexException {
		try ( ODatabaseDocumentTx odb = NdexDatabase.getInstance().getAConnection()) {
			OSQLSynchQuery<ODocument> query = new OSQLSynchQuery<>(
		  			"SELECT FROM network where isDeleted = true");
			List<ODocument> records = odb.command(query).execute();
			for ( ODocument doc : records ) {
				String networkId = doc.field(NdexClasses.ExternalObj_ID);
				Task t = new Task();
				t.setResource(networkId);
				t.setTaskType(TaskType.SYSTEM_DELETE_NETWORK);
				NdexServerQueue.INSTANCE.addSystemTask(t);
			}
			System.out.println (records.size() + " deleted network found, adding to system task queue.");
		} catch (InterruptedException e) {
		}
	}

	
	private static void populateUserQueue() throws NdexException {
		try ( TaskDocDAO taskDAO = new TaskDocDAO(NdexDatabase.getInstance().getAConnection())) {
			Collection<Task> list =taskDAO.getUnfinishedTasks(); 
			for ( Task t : list) {
				NdexServerQueue.INSTANCE.addUserTask(t);
			}
			System.out.println (list.size() + " unfinished user tasks found, adding to user task queue.");
		} catch (InterruptedException e) {
		}
	}

}
