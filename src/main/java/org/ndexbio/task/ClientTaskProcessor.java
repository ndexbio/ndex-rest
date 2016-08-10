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
package org.ndexbio.task;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.models.dao.postgresql.TaskDAO;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.Status;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.network.FileFormat;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.MDC;



public class ClientTaskProcessor extends NdexTaskProcessor {

	static Logger logger = LoggerFactory.getLogger(ClientTaskProcessor.class);
	
	public ClientTaskProcessor () {
		super();
	}
	
	@Override
	public void run() {
		while ( !shutdown) {
			Task task = null;
			try {
				task = NdexServerQueue.INSTANCE.takeNextUserTask();
				if ( task == NdexServerQueue.endOfQueue) {
					logger.info("End of queue signal received. Shutdown processor.");
					return;
				}
			} catch (InterruptedException e) {
				logger.info("takeNextUserTask Interrupted.");
				return;
			}
			
			try {		        
		        MDC.put("RequestsUniqueId", (String)task.getAttribute("RequestsUniqueId") );
				logger.info("[start: starting task]");
				
				NdexTask t = getNdexTask(task);
				saveTaskStatus(task.getExternalId().toString(), Status.PROCESSING, null,null);
				Task taskObj = t.call();
				saveTaskStatus(task.getExternalId().toString(), Status.COMPLETED, taskObj.getMessage(),null);

				logger.info("[end: task completed]");

			} catch (Exception e) {
				logger.error("Error occured when executing task " + task.getExternalId());
				e.printStackTrace();
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				e.printStackTrace(pw);     
				try {
					saveTaskStatus(task.getExternalId().toString(), Status.FAILED, e.getMessage(), sw.toString() );

				} catch (NdexException e1) {
					logger.error("Error occured when saving task " + e1);
				}
				
			} 
		}
	}
	
	private static NdexTask getNdexTask(Task task) throws NdexException{
		
		try {
			switch ( task.getTaskType()) { 
				case PROCESS_UPLOADED_NETWORK: 
					return new FileUploadTask(task, NdexDatabase.getInstance());
				case DOWNLOAD_NAMESPACE_FILES:
					return new AttachNamespacefilesTask(task);
				case EXPORT_NETWORK_TO_FILE: 
					
					if ( task.getFormat() == FileFormat.XBEL)
						return new XbelExporterTask(task);
					else if ( task.getFormat() == FileFormat.XGMML) {
						return new XGMMLExporterTask(task);
					} if ( task.getFormat() == FileFormat.BIOPAX) {
						return new BioPAXExporterTask(task);
					} if ( task.getFormat() == FileFormat.SIF) {
						return new SIFExporterTask(task);
					} if ( task.getFormat() == FileFormat.CX)  {
						return new CXExporterTask(task);
					}
				
					throw new NdexException ("Only XBEL, XGMML, SIF, CX, and BIOPAX exporters are implemented.");
				case CREATE_NETWORK_CACHE: 
					return new AddNetworkToCacheTask(task);
				case DELETE_NETWORK_CACHE:
					return new RemoveNetworkFromCacheTask(task);
				default:
					throw new NdexException("Task type: " +task.getTaskType() +" is not supported");
			}		
		} catch (IllegalArgumentException | SecurityException | NdexException e) {
			e.printStackTrace();
			throw new NdexException ("Error occurred when creating task. " + e.getMessage());
		} 
	}


	private static  void saveTaskStatus (String taskID, Status status, String message, String stackTrace) throws NdexException {
		try (TaskDAO dao = new TaskDAO (NdexDatabase.getInstance().getAConnection());) {
			dao.saveTaskStatus(taskID, status, message,stackTrace);
			dao.commit();
		}
	}
	
	
}
