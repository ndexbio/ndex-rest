package org.ndexbio.common.importexport;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.ndexbio.rest.Configuration;

public class ExporterExecutor {
	
    private Logger _logger = Logger.getLogger(ExporterExecutor.class.getSimpleName());

	public static final String STDERR_EXT = ".stderr";
	
	public static final String PERIOD = ".";
	
	private String _errorMessage = null;
	
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
	 * @return -100 if userId is null, -200 if exception is caught 
	 *         otherwise exit code of command line process 0 for success
	 *         and any other number for failure 
	 */
	public int export(InputStream input, UUID taskId, UUID userId) {
		
		//clear error message
		_errorMessage = null;
        String pathPrefix = createOutputDirectory(userId);
        if (pathPrefix == null) {
        	return -100;
        }

		ProcessBuilder pb = new ProcessBuilder(impExp.getExporterCmd());
		pb.directory(new File(impExp.getDirectoryName()));
		try {
			Process p = pb.start();
			try (OutputStream out = p.getOutputStream()) {
				try (InputStream in = p.getInputStream() ) {
					try (InputStream err = p.getErrorStream()) {
						IOThreadHandler expHandler = new IOThreadHandler(in, pathPrefix+ File.separator +
								                                         taskId + ExporterExecutor.PERIOD +
							                                             impExp.getFileExtension());
						expHandler.start();
						IOThreadHandler errHandler = new IOThreadHandler(err, pathPrefix +
						 	                                             File.separator +
						 	                                             taskId +
						 	                                             ExporterExecutor.STDERR_EXT);
						errHandler.start();
						IOUtils.copy(input,out);
						input.close();
						out.close();
						p.waitFor();
						System.out.println("YYYYYY" + expHandler.getErrorMessage());
						System.out.println("XXXXXXX" + errHandler.getErrorMessage());
					}
				}		
			}
			return p.exitValue();
		} catch(IOException ioex) {
			this._errorMessage = "Caught IOException: " + ioex.getMessage();
		} catch(InterruptedException ie) {
			this._errorMessage = "Process was interrupted";
		}
		return -200;
	}
		
	/**
	 * Creates directory for user that is used to write export results to
	 * @param userID Id of user
	 * @return String set to file system path prefix
	 */
	protected String createOutputDirectory(UUID userId) {
		if (userId == null) {
			_errorMessage = "User UUID is null";
			return null;
		}
		String pathPrefix = getPathPrefix(userId);
		if (pathPrefix == null) {
			return null;
		}
		if (createDirectoryIfNeeded(pathPrefix) == false) {
			return null;
		}
        
        return pathPrefix;
	}
	
	/**
	 * If path exists, verifies directory otherwise code attempts
	 * to create it. 
	 * @param path File system path as string
	 * @return true if successful otherwise false and {@link #getErrorMessage()} 
	 *         set with information about failure.
	 */
	protected boolean createDirectoryIfNeeded(String path) {
		File archiveDir = new File(path);
		
		if (archiveDir.isDirectory() == true) {
			return true;
		}
		if (archiveDir.exists()) {
			_errorMessage = path + " exists, but is not a directory";
			return false;
		}
        try {
        	_logger.fine("Creating directory: " + archiveDir.getCanonicalPath());
        } catch(IOException ioex){
        	_logger.fine("Weird...Caught exception getting canonical path" +
        	             ioex.getMessage());
        }
        boolean result = archiveDir.mkdirs();
        _logger.fine("mkdirs() returned " + result);
        if (result == false) {
        	_errorMessage = "There was a problem creating the directory " + path;
        }
        return result;
	}
	
	/**
	 * Gets users path prefix
	 * @return Path as string or null if there was error in which case 
	 *         calling {@link #getErrorMessage()} may provide more insight into issue
	 */
	protected String getPathPrefix(UUID userId) {
		try {
			return Configuration.getInstance().getNdexRoot() +
					             File.separator + "workspace" +
					             File.separator + userId.toString();
		} catch(NullPointerException npe) {
			_errorMessage = "Caught NullPointerException trying to build users path prefix";
		}
		return null;
	}
	
	/**
	 * Returns last error message, if any from invocation
	 * of public methods for objects of this class
	 * @returns String containing error message or null if no error
	 */
	public String getErrorMessage() {
		return this._errorMessage;
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
