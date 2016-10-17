package org.ndexbio.task;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.GZIPOutputStream;

import org.ndexbio.model.object.Task;
import org.ndexbio.rest.Configuration;

public class NetworkExportTask extends NdexTask {
	
	//private Task task;
	
	public NetworkExportTask(Task t) {
		super(t);
	}
	
	@Override
	public Task call_aux() throws Exception {
		
		   System.out.println("Creating the GZIP output stream.");
		   
		   String outFileName = Configuration.getInstance().getNdexRoot() + "/exported-networks/"
				   + task.getExternalId() + "." +
				   task.getAttribute("downloadFileExtension").toString().toLowerCase() + ".gz";
           try (GZIPOutputStream out = new GZIPOutputStream(new FileOutputStream(outFileName)) ) {
        	
        	   try (FileInputStream in = new FileInputStream(Configuration.getInstance().getNdexRoot() +
        			   "/data/" + task.getResource() + "/network.cx")) {
               
        		   byte[] buf = new byte[8192];
        		   int len;
        		   while((len = in.read(buf)) > 0) {
        			   out.write(buf, 0, len);
        		   }
        	   	}
        	   
           }       

		return getTask();
	}

}
