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
import java.io.OutputStreamWriter;
import java.util.UUID;

import org.ndexbio.common.exporter.SIFNetworkExporter;
import org.ndexbio.common.models.dao.postgresql.NetworkDocDAO;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.Status;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.network.Network;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SIFExporterTask extends NdexTask {

	private static final String NETWORK_EXPORT_PATH = "/opt/ndex/exported-networks/";
	private static final String XGMML_FILE_EXTENSION = ".sif";

	
	private static final Logger logger = LoggerFactory
			.getLogger(SIFExporterTask.class);
	
	private Status taskStatus;

	public SIFExporterTask(Task itask) throws NdexException {
		super(itask);
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
	
	private void exportNetwork() throws Exception{

			this.taskStatus = Status.PROCESSING;
			this.startTask();
			String exportFilename = this.resolveFilename(NETWORK_EXPORT_PATH, XGMML_FILE_EXTENSION);

			Network network = null;
			try (NetworkDocDAO dao = new NetworkDocDAO()) {
				network = dao.getNetworkById(UUID.fromString(getTask().getResource()));
			
			}
		
			if ( network == null) throw new NdexException("Failed to get network from db.");
		
			try (FileOutputStream out = new FileOutputStream (exportFilename)) {

				OutputStreamWriter writer = new OutputStreamWriter(out);
				SIFNetworkExporter exporter = new SIFNetworkExporter (network);	
				exporter.exportNetwork( writer );
				this.taskStatus = Status.COMPLETED;
				this.updateTaskStatus(this.taskStatus);
				writer.close();
			} 
	}
	
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
