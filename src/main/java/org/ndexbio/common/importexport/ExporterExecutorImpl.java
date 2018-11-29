package org.ndexbio.common.importexport;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;

public class ExporterExecutorImpl {
	
    private Logger _logger = Logger.getLogger(ExporterExecutorImpl.class.getSimpleName());

	public static final String STDERR_EXT = ".stderr";
	
	public static final String PERIOD = ".";
	
	private String _errorMessage = null;
	
	private ImporterExporterEntry impExp;
	private String _ndexRootPath;
	private long _defaultTimeOut;
	
	/**
	 * Constructor
	 * @param impExpEntry Exporter to run
	 * @param ndexRootPath Ndex root filesystem path as string
	 * @param defaultTimeOut Time in seconds exporter should be allowed
	 *                       to run. If set to 0 then this object will wait forever.
	 */
	public ExporterExecutorImpl(ImporterExporterEntry impExpEntry,
			                final String ndexRootPath,
			                long defaultTimeOut) {
		impExp = impExpEntry;
		_ndexRootPath = ndexRootPath;
		_defaultTimeOut = defaultTimeOut;
	}

	/**
	 * Runs external command line process that is fed CX format network via standard in,
	 * writing standard output from command line process to a file which is assumed to
	 * be results of exporter
	 * 
	 * @param input CX format network as an InputStream
	 * @param taskId Id of task
	 * @param userId Id of user
	 * @return -100 if userId is null, -200 if exception is caught, 
	 *         -300 if timeout exceeded
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
						IOThreadHandler expHandler = new IOThreadHandler(in, getStandardOutFilePath(pathPrefix,
								                                         taskId.toString()));
						expHandler.start();
						IOThreadHandler errHandler = new IOThreadHandler(err, pathPrefix +
						 	                                             File.separator +
						 	                                             taskId +
						 	                                             ExporterExecutorImpl.STDERR_EXT);
						errHandler.start();
						
						//Copy cx data in input stream to process (out stream)
						IOUtils.copy(input,out);
						
						// even though these are both closed with try calls later
						// they must be closed otherwise the IOThreadHandlers just
						// wait
						input.close();
						out.close();
						
						//wait for IOThreadHandlers to complete
						if (_defaultTimeOut == 0){
							_logger.info("Default timeout set to 0, will wait forever");
							expHandler.join();
							errHandler.join();
							p.waitFor();
						} else {
							_logger.fine("Timeout set to " + _defaultTimeOut + " seconds");
							expHandler.join(_defaultTimeOut*1000);
							errHandler.join(_defaultTimeOut*1000);
							p.waitFor(_defaultTimeOut, TimeUnit.SECONDS);
						}
					}
				}
			}
			// check if _defaultTimeOut is greater then zero and process
			// is alive which means it timed out, so lets kill it.
			if (_defaultTimeOut > 0 && p.isAlive() == true) {
				_logger.info("Timelimit for process exceeded. Killing");
				p.destroyForcibly();
				p.waitFor();
				return -300;
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
	 * Generates path to standard out file for exporter by prepending
	 * the pathPrefix with taskId and appending the file extension of 
	 * the internal {@link #impExp} ImporterExporter object.
	 * @param pathPrefix Directory where the standard out file should be written
	 * @param taskId id of task as string
	 * @return String containing full path to standard out file.
	 */
	protected String getStandardOutFilePath(final String pathPrefix, 
			                                final String taskId) {
		String periodstr = ExporterExecutorImpl.PERIOD;
		if (impExp.getFileExtension().startsWith(ExporterExecutorImpl.PERIOD)) {
			periodstr = "";
		}
		return pathPrefix+ File.separator + taskId + periodstr + impExp.getFileExtension();
	}
	
	/**
	 * Generates path to standard error file for exporter by prepending
	 * the pathPrefix with taskId and appending ExporterExecutor.STDERR_EXT
	 * @param pathPrefix Directory where the standard out file should be written
	 * @param taskId id of task as string
	 * @return String containing full path to standard out file.
	 */
	protected String getStandardErrorFilePath(final String pathPrefix, 
			                                final String taskId) {
		return pathPrefix+ File.separator + taskId + ExporterExecutorImpl.STDERR_EXT;
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
			return _ndexRootPath + File.separator + "workspace" + File.separator +
				   userId.toString();
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
		
		private Logger _logger = Logger.getLogger(IOThreadHandler.class.getSimpleName());
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
				_logger.fine("About to write data to" + targetFile.getCanonicalPath());
				try (FileOutputStream fos = new FileOutputStream(targetFile.getCanonicalFile())) {
					long bytesWritten = IOUtils.copyLarge(inputStream, fos);
					_logger.fine("Wrote " + bytesWritten + " bytes to " + targetFile.getCanonicalPath());
					fos.flush();
				}
			} catch (IOException e) {
				_logger.warning("Caught IOException (" + e.getMessage() +
						        ") writing data to file: " + targetFile.getPath());
		    	errorMessage = e.getMessage();
		    }
		}
	}
}