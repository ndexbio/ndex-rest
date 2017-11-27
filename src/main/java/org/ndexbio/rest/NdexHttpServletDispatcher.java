/**
 * Copyright (c) 2013, 2016, The Regents of the University of California, The Cytoscape Consortium
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */
package org.ndexbio.rest;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.logging.Logger;

import javax.servlet.ServletContext;
import javax.ws.rs.core.Application;

import org.apache.solr.client.solrj.SolrServerException;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.models.dao.postgresql.TaskDAO;
import org.ndexbio.common.solr.GroupIndexManager;
import org.ndexbio.common.solr.NetworkGlobalIndexManager;
import org.ndexbio.common.solr.UserIndexManager;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.Task;
import org.ndexbio.rest.filters.BasicAuthenticationFilter;
import org.ndexbio.security.GoogleOpenIDAuthenticator;
import org.ndexbio.task.ClientTaskProcessor;
import org.ndexbio.task.NdexServerQueue;
import org.ndexbio.task.NdexSystemTask;
import org.ndexbio.task.NdexTask;
import org.ndexbio.task.SystemTaskProcessor;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class NdexHttpServletDispatcher extends HttpServletDispatcher {
	
    private static Logger logger = Logger.getLogger(NdexHttpServletDispatcher.class.getSimpleName());
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static final int defaultPoolSize = 10;
	private Thread  systemTaskProcessorThread;
	private Thread  clientTaskProcessorThread;
	private SystemTaskProcessor systemTaskProcessor;
	private ClientTaskProcessor clientTaskProcessor;
	
	private static String ndexVersion = "";
	private static String buildNumber  = "";
	
	private final static String backupDB = "BACKUP_DATABASE";
	
	
	public NdexHttpServletDispatcher() {
		super();
	}

	
	
	
	@Override
	public void init(javax.servlet.ServletConfig servletConfig)
	          throws javax.servlet.ServletException {
		super.init(servletConfig);
			
		//Configuration configuration = null;
		try {
			Configuration configuration = Configuration.createInstance();
			
			String poolSize = configuration.getProperty("NdexDBConnectionPoolSize");
			int size = defaultPoolSize;
			try {
				if ( poolSize != null ) {
					size = Integer.parseInt(poolSize);
				} 
			} catch (NumberFormatException e) {
				logger.warning("NdexDBConnectionPoolSize doesn't have a numeric value in configuration. Using default setting " + defaultPoolSize + " instead.");
			}

			//and initialize the db connections
	    	
			NdexDatabase db = NdexDatabase.createNdexDatabase(
					configuration.getDBURL(),
	    			configuration.getDBUser(),
	    			configuration.getDBPasswd(), size);
    	
			logger.info("Db created for " + configuration.getDBURL());

			// create solr core for network indexes if needed.
			NetworkGlobalIndexManager mgr = new NetworkGlobalIndexManager();
			mgr.createCoreIfNotExists();
			UserIndexManager umgr = new UserIndexManager();
			umgr.createCoreIfNotExists();
			GroupIndexManager gmgr = new GroupIndexManager();
			gmgr.createCoreIfNotExists();
			
    	
		/*	try (UserDocDAO dao = new UserDocDAO(db.getAConnection())) {
    	
				String sysUserEmail = configuration.getProperty("NdexSystemUserEmail");
				Helper.createUserIfnotExist(dao, configuration.getSystmUserName(),
					(sysUserEmail == null? "support@ndexbio.org" : sysUserEmail), 
    				configuration.getSystemUserPassword());
			} */
			
			// find tasks that needs to be processed in system queue
			populateQueuedTasksFromDB();

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
			 String dbNeedsBackup = configuration.getProperty(backupDB);
			 if ( dbNeedsBackup ==null || dbNeedsBackup.trim().equalsIgnoreCase("true") ) {
				 timer.scheduleAtFixedRate(new DatabaseBackupTask(), 
					 DatabaseBackupTask.getTomorrowBackupTime(), 
					 DatabaseBackupTask.fONCE_PER_DAY);
			 }
			 timer.scheduleAtFixedRate(new EmailNotificationTask(), 
					 EmailNotificationTask.getTomorrowNotificationTime(), 
					 EmailNotificationTask.fONCE_PER_DAY);
			 
			 //just for testing comment out when done.
		/*	 EmailNotificationTask e = new EmailNotificationTask();
			 e.run();
			 if ( e.getError() != null) 
				 throw e.getError(); */
			
		} catch ( Exception e) {
			e.printStackTrace();
			throw new javax.servlet.ServletException("Failed to start Ndex server. Cause: " + e.getMessage(), e);
		}
		
		
		ServletContext application = getServletConfig().getServletContext();
		InputStream inputStream = application.getResourceAsStream("/META-INF/MANIFEST.MF");
		if ( inputStream !=null) {
		try {
			Manifest manifest = new Manifest(inputStream);
	/*		Map<String,Attributes> attr = manifest.getEntries();
		    System.out.println(" ------   total entries:" + attr.size() );
			for (String name: attr.keySet()) {
				System.out.println("Attr-name:"+name);
			} */
			Attributes aa = manifest.getMainAttributes();	
	/*		System.out.println( "---------- total main:" + aa.size());
			for (Map.Entry<Object, Object> e : aa.entrySet() ){
				System.out.println("key:"+e.getKey() + "="+e.getValue());
			} */
			String ver = aa.getValue("NDEx-Version");
			String bui = aa.getValue("NDEx-Build"); 
			System.out.println("NDEx-Version: " + ver + ",Build:" + bui);
			buildNumber= bui.substring(0, 5);
			ndexVersion = ver;
		} catch (IOException e) {
			System.out.println("failed to read MANIFEST.MF");
			e.printStackTrace();
		//	System.exit(1);
		}     
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
		    logger.info ("Ndex Database connections have been closed.");
        } catch (Exception ee) {
            ee.printStackTrace();
            logger.info("Error occured when closing Ndex database connections.");
        }
        
        // revoke the googleTokens 
        
      /*  GoogleOpenIDAuthenticator authenticator = BasicAuthenticationFilter.getGoogleOAuthAuthenticatior();
        try {
          authenticator.revokeAllTokens();
        } catch (Exception e)  {
        	logger.severe("Error occurred when revoking Google access tokens." + e.getMessage());
        } */
		super.destroy();
	}
	
	
/*	private static void populateSystemQueue() throws NdexException {
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
	} */

	
	private static void populateQueuedTasksFromDB() throws NdexException, SQLException, JsonParseException, JsonMappingException, IOException {
		try ( TaskDAO taskDAO = new TaskDAO()) {
			List<Task> list =taskDAO.getQueuedTasks(); 
			for ( Task t : list) {
				if ( t.getTaskOwnerId()!=null)
					NdexServerQueue.INSTANCE.addUserTask(NdexTask.createUserTask(t));
				else {
				   NdexSystemTask sysTask = NdexSystemTask.createSystemTask(t);
				   if (sysTask !=null) {
					   sysTask.setTaskId(t.getExternalId());
					   NdexServerQueue.INSTANCE.addSystemTaskToQueue(sysTask);
				   }	   
				}
			}
			logger.info (list.size() + " previously queued tasks were added to the queue.");
		}  
	}


	public static String getNdexVersion() {
		return ndexVersion;
	}


	public static String getBuildNumber() {
		return buildNumber;
	}

}
