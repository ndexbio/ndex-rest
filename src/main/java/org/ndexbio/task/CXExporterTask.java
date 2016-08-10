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

import java.io.File;
import java.io.FileOutputStream;

import org.ndexbio.common.models.dao.postgresql.CXNetworkExporter;
import org.ndexbio.common.models.dao.postgresql.SingleNetworkDAO;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.Status;
import org.ndexbio.model.object.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CXExporterTask extends NdexTask {

	private static final String NETWORK_EXPORT_PATH = BioPAXExporterTask.NETWORK_EXPORT_PATH;
	private static final String FILE_EXTENSION = ".cx";

	private Status taskStatus;
	
	
	private static final Logger logger = LoggerFactory
			.getLogger(CXExporterTask.class);
	
	public CXExporterTask(Task itask) throws NdexException {
		super(itask);
		// TODO Auto-generated constructor stub
	}


	@Override
	public Task call() throws Exception {
		try {
			this.exportNetwork();
			return this.getTask();
		} catch (InterruptedException e) {
			logger.info(this.getClass().getName() +" interupted");
			return null;
		} 
	}
	
	/*
	 * private method to invoke the xbel network exporter
	 */
	private void exportNetwork() throws Exception{
		this.taskStatus = Status.PROCESSING;
		this.startTask();
		String exportFilename = this.resolveFilename(NETWORK_EXPORT_PATH, FILE_EXTENSION);

		FileOutputStream out = new FileOutputStream (exportFilename);
		try {
			
			try (CXNetworkExporter snetworkDAO = new CXNetworkExporter(getTask().getResource())) {
				snetworkDAO.writeNetworkInCX(out, true);
			}
			
			this.taskStatus = Status.COMPLETED;
			this.updateTaskStatus(this.taskStatus);
		} finally { 
		    out.close();
		}
	}
	
	/*
	 * private method to resolve the filename for the exported file
	 * Current convention is to use a fixed based directory under /opt/ndex
	 * add a subdriectory based on the username and use the network name plus the
	 * xbel extension as a filename
	 */
	private String resolveFilename(String path, String extension) {
		// create the directory if not exists
		if (! new File(path).exists()) {
			new File(path).mkdir();
		}
		
		StringBuilder sb = new StringBuilder(path);
		sb.append(File.separator);
		sb.append(this.getTask().getExternalId());
		sb.append(extension);
		return sb.toString();		
	}
	


}
