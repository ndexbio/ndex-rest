/**
 * Copyright (c) 2013, 2015, The Regents of the University of California, The Cytoscape Consortium
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
package org.ndexbio.rest.services;

import java.util.List;
import java.util.Map;

import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;

import org.eclipse.jetty.server.Server;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.NdexStatus;
import org.ndexbio.rest.server.StandaloneServer;
import org.ndexbio.task.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;

@Path("/admin")
public class AdminService extends NdexService {
	private static Logger logger = LoggerFactory.getLogger(AdminService.class);
	
//	static final String defaultPostEdgeLimit = "800000";
	
	private static final String postElementLimitProp = "ServerPostElementLimit";

	public AdminService(@Context HttpServletRequest httpRequest)
    {
        super(httpRequest);
	}

	/**************************************************************************
	 * 
	 * Gets status for the service.
	 * @throws NdexException 
	 **************************************************************************/

	@GET
	@PermitAll
	@NdexOpenFunction
	@Path("/status")
	@Produces("application/json")
	public NdexStatus getStatus() throws NdexException	{

		logger.info("[start: Getting status]");
		
		try (ODatabaseDocumentTx db =NdexDatabase.getInstance().getAConnection()){
			
			NdexStatus status = new NdexStatus();
			status.setNetworkCount(AdminService.getClassCount(db,"network"));
			status.setUserCount(AdminService.getClassCount(db,"user"));
			status.setGroupCount(AdminService.getClassCount(db,"group")); 

			Map<String,String> props = status.getProperties();
			
			String edgeLimit = Configuration.getInstance().getProperty(Configuration.networkPostEdgeLimit);
			if ( edgeLimit != null ) {
				try {
					int i = Integer.parseInt(edgeLimit);
					props.put(postElementLimitProp, Integer.toString(i));
				} catch( NumberFormatException e) {
					logger.error("[Invalid value in server property {}]", Configuration.networkPostEdgeLimit);
			//		props.put("ServerPostEdgeLimit", "-1");  //defaultPostEdgeLimit);
				}
			} /* else {
				props.put(postElementLimitProp, "-1"); // defaultPostEdgeLimit);
			} */
		    
			props.put("ServerResultLimit", "10000");
			status.setProperties(props);
			logger.info("[end: Got status]");
			return status;
		} 
	}

	/*
	 * Shut down the server.  Currently it only works for Jetty.  We need it for our performance benchmarking.
	 * 
	 * In future we have to 
	 *    1) add support for Tomcat
	 *    2) only allow privileged users to shut down Tomcat.
	 */
	@GET
	@PermitAll
	@NdexOpenFunction
	@Path("/shutdown")
	@Produces("application/json")
	public void shutDown()	{
		logger.info("[start: shutdown server]");
		
		Server jettyServer = StandaloneServer.getJettyServer();
		if (null != jettyServer) {			
			stopJettyServer(jettyServer);			
			// the following log entry will not log since the server will be down; but let's still have it
			logger.info("[end: shutdown server]");			
			System.exit(0);
		}
	}
	
	private static void stopJettyServer(Server server) {
		if (null != server) {
	        try {
	    		NdexDatabase.close();
	        	server.stop();
	        } catch (Exception e) {
	    	    ;
	        }
		}
		return;
	}

	
	private static Integer getClassCount(ODatabaseDocumentTx db, String className) {

		final List<ODocument> classCountResult = db.query(new OSQLSynchQuery<ODocument>(
						"SELECT COUNT(*) as count FROM " + className + " where isDeleted = false"));

		final Long count = classCountResult.get(0).field("count");

		Integer classCount = count != null ? count.intValue() : null;

		return classCount;

	} 
	
/*	
	@GET
	@Path("/processqueue")
	@Produces("application/json")
	public void processTasks() throws NdexException	{
		if ( !isSystemUser())
			throw new NdexException ("Only Sysetm users are allowed to call task runner from API.");
	        	NdexDatabase db = null;
	     		try {
	     			LoggerFactory.getLogger(AdminService.class).info("Task processor started.") ;
	     		    db = NdexDatabase.getInstance();
	     			NdexQueuedTaskProcessor processor = new NdexQueuedTaskProcessor(
	     				db );
					processor.processAll();
				} catch (NdexException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					LoggerFactory.getLogger(AdminService.class).error("Failed to process queued task.  " + e.getMessage()) ;
				} finally {
					LoggerFactory.getLogger(AdminService.class).info("Task processor finished. Db connection closed.") ;
				}
	}
*/	
/*
	@GET
	@Path("/backupdb")
	@Produces("application/json")
	public void backupDB() throws NdexException	{
		if ( !isSystemUser())
			throw new NdexException ("Only Sysetm users are allowed to backup database from API.");
		Thread t = new Thread(new Runnable() {
	         @Override
			public void run()
	         {
	        	 ODatabaseDocumentTx db = null;
	        	 try {
			
	        		 String ndexRoot = Configuration.getInstance().getNdexRoot();
	        		 SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
	        		 String strDate = sdf.format(Calendar.getInstance().getTime());
			
	        		 db = NdexDatabase.getInstance().getAConnection();
	        		 String exportFile = ndexRoot + "/dbbackups/db_"+ strDate + ".export";

	        		 logger.info("Backing up database to " + exportFile);
	        		 
	        		 try{
	        			  OCommandOutputListener listener = new OCommandOutputListener() {
	        			    @Override
	        			    public void onMessage(String iText) {
	        			      System.out.print(iText);
	        			      logger.info(iText);
	        			    }
	        			  };

	        			  ODatabaseExport export = new ODatabaseExport(db, exportFile, listener);
	        			  export.setIncludeIndexDefinitions(false);
	        			  export.exportDatabase();
	        			  export.close();
	        			} catch (IOException e) {
							e.printStackTrace();
							logger.error("IO exception when backing up database. " + e.getMessage());
						}  finally {
	        			  db.close();
	        			} 
	        		 logger.info("Database back up fininished succefully.");

	        	 } catch (NdexException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					logger.error("Failed to backup database.  " + e.getMessage()) ;
	        	 } finally {
	        		 if ( db!=null) db.close();

	        	 }
	         }
		});
		t.start();
	}
*/
	
/*	private boolean isSystemUser() throws NdexException {
	  return getLoggedInUser().getAccountName().equals(Configuration.getInstance().getSystmUserName()) ;
	}
*/
}
