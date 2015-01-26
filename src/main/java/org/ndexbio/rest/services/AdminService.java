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

import org.ndexbio.common.access.NdexAOrientDBConnectionPool;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.NdexStatus;
import org.ndexbio.task.Configuration;
import org.ndexbio.task.NdexQueuedTaskProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.orientechnologies.orient.core.command.OCommandOutputListener;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.db.tool.ODatabaseExport;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;

@Path("/admin")
public class AdminService extends NdexService {
	private static final Logger logger = LoggerFactory.getLogger(AdminService.class);

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
	@Path("/status")
	@Produces("application/json")
	public NdexStatus getStatus() throws NdexException	{

		ODatabaseDocumentTx db = null;
		try {
			
			db = NdexDatabase.getInstance().getAConnection();

			NdexStatus status = new NdexStatus();
			status.setNetworkCount(this.getClassCount(db,"network"));
			status.setUserCount(this.getClassCount(db,"user"));
			status.setGroupCount(this.getClassCount(db,"group")); 
		    
			Map<String,String> props = status.getProperties();
			props.put("ServerResultLimit", "10000");
			status.setProperties(props);
			return status;
		} finally {
			if ( db!=null) db.close();

		}

	}

	private Integer getClassCount(ODatabaseDocumentTx db, String className) {

		final List<ODocument> classCountResult = db.query(new OSQLSynchQuery<ODocument>(
						"SELECT COUNT(*) as count FROM " + className));

		final Long count = classCountResult.get(0).field("count");

		Integer classCount = count != null ? count.intValue() : null;

		return classCount;

	} 
	
	
	@GET
	@Path("/processqueue")
	@Produces("application/json")
	public void processTasks() throws NdexException	{
		if ( !isSystemUser())
			throw new NdexException ("Only Sysetm users are allowed to call task runner from API.");
/*		Thread t = new Thread(new Runnable() {
	         @Override
			public void run()
	         {*/
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
					if ( db != null) db.close();
					LoggerFactory.getLogger(AdminService.class).info("Task processor finished. Db connection closed.") ;
				}
	       /*  }
		});
		t.start(); */
	}
	
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
	        			  export.exportDatabase();
	        			  export.close();
	        			} catch (IOException e) {
							e.printStackTrace();
							logger.error("IO exception when backing up database. " + e.getMessage());
						} finally {
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

	
	private boolean isSystemUser() throws NdexException {
	  return getLoggedInUser().getAccountName().equals(Configuration.getInstance().getSystmUserName()) ;
	}

}
