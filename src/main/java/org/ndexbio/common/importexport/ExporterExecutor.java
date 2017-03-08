package org.ndexbio.common.importexport;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.ndexbio.common.models.dao.postgresql.TaskDAO;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.object.Status;
import org.ndexbio.rest.Configuration;

public class ExporterExecutor {
	
	private ImporterExporterEntry impExp;
	
	public ExporterExecutor(ImporterExporterEntry impExpEntry) {
		impExp = impExpEntry;
	}

	public void export(InputStream input, UUID taskId, UUID userId) throws IOException, InterruptedException, SQLException, ObjectNotFoundException, NdexException {
		
		//prepare the diretory
		String pathPrefix = Configuration.getInstance().getNdexRoot() + "/workspace/" + userId.toString();
        File archiveDir = new File(pathPrefix);
        if (!archiveDir.exists())
        	archiveDir.mkdirs();
        
	
		ProcessBuilder pb = new ProcessBuilder(impExp.getExporterCmd());
		pb.directory(new File(impExp.getDirectoryName()));
		Process p = pb.start();
		try (OutputStream out = p.getOutputStream()) {
			try (InputStream in = p.getInputStream() ) {
				IOThreadHandler expHandler = new IOThreadHandler(in, pathPrefix+ "/"+ taskId + "." + impExp.getFileExtension());
				expHandler.start();
		
				IOUtils.copy(input,out);
				input.close();
				out.close();
				p.waitFor();
			}			
		}
		int exitCode = p.exitValue();
		
		// update task
		try (TaskDAO dao = new TaskDAO()) {
			if (exitCode == 0 )
				dao.updateTaskStatus(taskId, Status.COMPLETED);
			else 
				dao.updateTaskStatus(taskId, Status.FAILED, "", "");
			dao.commit();
		}
		
	}
		
	
	private static class IOThreadHandler extends Thread {
		
		private InputStream inputStream;
		private File targetFile ;
		
		IOThreadHandler(InputStream inputStream, String outFilePath) throws FileNotFoundException {
		
			  this.inputStream = inputStream;
			  targetFile = new File (outFilePath);
		}
		
		@Override
		public void run() {
				
			try {
				  
				  java.nio.file.Files.copy(
					      inputStream, 
					      targetFile.toPath(), 
					      StandardCopyOption.REPLACE_EXISTING);
					 
				//	    IOUtils.closeQuietly(inputStream);
		
		    } catch (IOException e) {
		    	
		    }
		}
	}
}
