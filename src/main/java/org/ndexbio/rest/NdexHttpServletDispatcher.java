/**
 *   Copyright (c) 2013, 2015
 *  	The Regents of the University of California
 *  	The Cytoscape Consortium
 *
 *   Permission to use, copy, modify, and distribute this software for any
 *   purpose with or without fee is hereby granted, provided that the above
 *   copyright notice and this permission notice appear in all copies.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 *   WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 *   MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 *   ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 *   WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 *   ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 *   OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */
package org.ndexbio.rest;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Timer;
import java.util.logging.Logger;

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
	
    private static Logger logger = Logger.getLogger(NdexHttpServletDispatcher.class.getSimpleName());
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static final int defaultPoolSize = 50;
	private OServer orientDBServer;
	private Thread  systemTaskProcessorThread;
	private Thread  clientTaskProcessorThread;
	private SystemTaskProcessor systemTaskProcessor;
	private ClientTaskProcessor clientTaskProcessor;
	
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
    	
			logger.info("Db created for " + NdexDatabase.getURIPrefix());
    	
			try (UserDocDAO dao = new UserDocDAO(db.getAConnection())) {
    	
				String sysUserEmail = configuration.getProperty("NdexSystemUserEmail");
				DatabaseInitializer.createUserIfnotExist(dao, configuration.getSystmUserName(),
					(sysUserEmail == null? "support@ndexbio.org" : sysUserEmail), 
    				configuration.getSystemUserPassword());
			}
			
			// find tasks that needs to be processed in system queue
			populateSystemQueue();
			populateUserQueue();

			systemTaskProcessor = new SystemTaskProcessor();
			clientTaskProcessor = new ClientTaskProcessor();
			systemTaskProcessorThread = new Thread(systemTaskProcessor);
			systemTaskProcessorThread.start();
			logger.info("System task executor started.");
			clientTaskProcessorThread = new Thread(clientTaskProcessor);
			clientTaskProcessorThread.start();
			logger.info("Client task executor started.");

			// setup the automatic backup
			 Timer timer = new Timer();
			 timer.scheduleAtFixedRate(new DatabaseBackupTask(), 
					 DatabaseBackupTask.getTomorrowBackupTime(), 
					 DatabaseBackupTask.fONCE_PER_DAY);
			
		} catch (NdexException e) {
			e.printStackTrace();
			throw new javax.servlet.ServletException(e.getMessage());
		}
    	
	}
	
	
	@Override
	public void destroy() {
		
		logger.info("Shutting down ndex rest server.");
        try {
        	
        	//signal the task queues and wait for them to finish.
        	clientTaskProcessor.shutdown();
        	systemTaskProcessor.shutdown();

        	NdexServerQueue.INSTANCE.shutdown();
        	
        	logger.info("Waiting task processors to stop.");
        	
        	systemTaskProcessorThread.join();
        	logger.info("System task processor stopped.");
        	clientTaskProcessorThread.join();
        	
        	logger.info("Client task processors stopped. Closing database");
        	
        	NdexDatabase.close();
        	Orient.instance().shutdown();
		    orientDBServer.shutdown();			
		    logger.info ("Database has been closed.");
        } catch (Exception ee) {
            ee.printStackTrace();
            logger.info("Error occured when shutting down Orient db.");
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
			logger.info (records.size() + " deleted network found for system task queue.");
		}
	}

	
	private static void populateUserQueue() throws NdexException {
		try ( TaskDocDAO taskDAO = new TaskDocDAO(NdexDatabase.getInstance().getAConnection())) {
			Collection<Task> list =taskDAO.getUnfinishedTasks(); 
			for ( Task t : list) {
				NdexServerQueue.INSTANCE.addUserTask(t);
			}
			logger.info (list.size() + " unfinished user tasks found for user task queue.");
		} 
	}

}
