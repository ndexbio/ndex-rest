package org.ndexbio.task;

import java.io.FileInputStream;
import java.util.Map;

import org.ndexbio.common.importexport.ExporterExecutor;
import org.ndexbio.common.importexport.ImporterExporterEntry;
import org.ndexbio.model.object.Task;
import org.ndexbio.rest.Configuration;

public class NetworkExportTask extends NdexTask {
	
	//private Task task;
	
	public NetworkExportTask(Task t) {
		super(t);
	}
	
	
	@Override
	public Task call_aux() throws Exception {
		Map<String, Object> attrs = task.getAttributes();
		String converterName = (String)attrs.get("name");
		
		ImporterExporterEntry entry = Configuration.getInstance().getImpExpEntry(converterName);
		
		ExporterExecutor executor = new ExporterExecutor (entry);
		
		try (FileInputStream input = new FileInputStream (Configuration.getInstance().getNdexRoot() + "/data/"+task.getResource() + "/network.cx")) {
		
			executor.export(input, task.getExternalId(), task.getTaskOwnerId());
	
		}
		return getTask();
		
		/*	   System.out.println("Creating the GZIP output stream.");
		   
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
      */
	}

}
