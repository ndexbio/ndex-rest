package org.ndexbio.rest.services;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;

import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.NdexStatus;
import org.ndexbio.task.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;

@Path("/admin")
public class AdminService extends NdexService {
	static Logger logger = LoggerFactory.getLogger(AdminService.class);
	
	static final String networkPostEdgeLimit = "NETWORK_POST_EDGE_LIMIT";
	static final String defaultPostEdgeLimit = "200000";

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
		
		logger.info(userNameForLog() + "[start: Getting status]");
		
		try (ODatabaseDocumentTx db =NdexDatabase.getInstance().getAConnection()){
			
			NdexStatus status = new NdexStatus();
			status.setNetworkCount(AdminService.getClassCount(db,"network"));
			status.setUserCount(AdminService.getClassCount(db,"user"));
			status.setGroupCount(AdminService.getClassCount(db,"group")); 

			Map<String,String> props = status.getProperties();
			
			String edgeLimit = Configuration.getInstance().getProperty(networkPostEdgeLimit);
			if ( edgeLimit != null ) {
				try {
					int i = Integer.parseInt(edgeLimit);
					props.put("ServerPostEdgeLimit", Integer.toString(i));
				} catch( NumberFormatException e) {
					logger.error(userNameForLog () + "[Invalid value in server property " + networkPostEdgeLimit + "]");
					props.put("ServerPostEdgeLimit", defaultPostEdgeLimit);
				}
			} else {
				props.put("ServerPostEdgeLimit", defaultPostEdgeLimit);
			}
		    
			props.put("ServerResultLimit", "10000");
			status.setProperties(props);
			logger.info(userNameForLog() + "[end: Got status]");
			return status;
		} 
	}

	private static Integer getClassCount(ODatabaseDocumentTx db, String className) {

		final List<ODocument> classCountResult = db.query(new OSQLSynchQuery<ODocument>(
						"SELECT COUNT(*) as count FROM " + className + " where not isDeleted"));

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
