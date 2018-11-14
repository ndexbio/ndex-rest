package org.ndexbio.common.importexport;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.Configuration;

public class ExporterExecutor {
	
    private Logger _logger = Logger.getLogger(ExporterExecutor.class.getSimpleName());

	public static final String STDERR_EXT = ".stderr";
	
	private ImporterExporterEntry impExp;
	
	public ExporterExecutor(ImporterExporterEntry impExpEntry) {
		impExp = impExpEntry;
	}

	/**
	 * Runs external command line process that is fed CX format network via standard in,
	 * writing standard output from command line process to a file which is assumed to
	 * be results of exporter
	 * 
	 * @param input CX format network as an InputStream
	 * @param taskId Id of task
	 * @param userId Id of user
	 * @return exit code of command line process which is 0 for success otherwise failure
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws SQLException
	 * @throws ObjectNotFoundException
	 * @throws NdexException
	 */
	public int export(InputStream input, UUID taskId, UUID userId) throws IOException, InterruptedException, SQLException, ObjectNotFoundException, NdexException {
		
        String pathPrefix = this.createOutputDirectory(userId);
		ProcessBuilder pb = new ProcessBuilder(impExp.getExporterCmd());
		pb.directory(new File(impExp.getDirectoryName()));
		Process p = pb.start();
		try (OutputStream out = p.getOutputStream()) {
			try (InputStream in = p.getInputStream() ) {
				try (InputStream err = p.getErrorStream()) {
					IOThreadHandler expHandler = new IOThreadHandler(in, pathPrefix+ "/"+ taskId + "." + impExp.getFileExtension());
					expHandler.start();
					IOThreadHandler errHandler = new IOThreadHandler(err, pathPrefix +
							File.separator + taskId + ExporterExecutor.STDERR_EXT);
					errHandler.start();
					IOUtils.copy(input,out);
					input.close();
					out.close();
					p.waitFor();
				}
			}			
		}
		return p.exitValue();
	}
		
	/**
	 * Creates directory for user that is used to write export results to
	 * @param userID Id of user
	 * @throws IOException if there is a problem creating the directory
	 */
	protected String createOutputDirectory(UUID userId) throws IOException {
		String pathPrefix = Configuration.getInstance().getNdexRoot() + "/workspace/" + userId.toString();
        File archiveDir = new File(pathPrefix);
        if (!archiveDir.exists())
        	_logger.fine("Creating directory: " + archiveDir.getCanonicalPath());
        	archiveDir.mkdirs();
        return pathPrefix;
	}
	
	private static class IOThreadHandler extends Thread {
		
		private InputStream inputStream;
		private File targetFile ;
		private String errorMessage;
		
		IOThreadHandler(InputStream inputStream, String outFilePath) {
		
			  this.inputStream = inputStream;
			  targetFile = new File (outFilePath);
			  errorMessage = null;
		}
		
		public String getErrorMessage() { return errorMessage;} 
		
		@Override
		public void run() {
				
			try {
				  
				  java.nio.file.Files.copy(
					      inputStream, 
					      targetFile.toPath(), 
					      StandardCopyOption.REPLACE_EXISTING);		
		    } catch (IOException e) {
		    	errorMessage = e.getMessage();
		    }
		}
	}
}
