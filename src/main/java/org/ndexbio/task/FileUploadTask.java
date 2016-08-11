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

import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.models.dao.postgresql.TaskDAO;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.Status;
import org.ndexbio.task.parsingengines.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Files;

/*
 * This class represents a NdexTask subclass that is responsible
 * for uploading a specified data file into a new NDEx network in
 * orientdb. A particular file parser is selected based on the file type.
 * Since this class is invoked based on a Task registered in the orientdb 
 * database, no user authentication is required.
 * 
 */

public class FileUploadTask extends NdexTask {

	private final String filename;
	private static final Logger logger = LoggerFactory
			.getLogger(FileUploadTask.class);
	
	public FileUploadTask(Task itask) throws IllegalArgumentException,
			SecurityException, NdexException {
		super(itask);
		this.filename = this.getTask().getResource();
		if (!(new File(this.filename).isFile())) {
			throw new NdexException("File " + this.filename + " does not exist");
		}
	}

	@Override
	public Task call() throws Exception {
		
		try {
			this.processFile();
			return this.getTask();
		} catch (InterruptedException e) {
			logger.info("FileUploadTask interupted");
			return null;
		}
	}

	protected String getFilename() {
		return this.filename;

	}

	private void processFile() throws Exception {
		logger.info("[start: Processing file='{}']", this.getFilename());
		Status taskStatus = Status.PROCESSING;
		this.startTask();
		File file = new File(this.getFilename());
		String fileExtension = com.google.common.io.Files
				.getFileExtension(this.getFilename()).toUpperCase().trim();
		logger.info("File extension = " + fileExtension);
		String networkName = Files.getNameWithoutExtension(this.getTask().getDescription());
		IParsingEngine parser = null;

		switch (fileExtension) {

		case ("CX")	:
			parser = new CXParser(file.getAbsolutePath(), getTask().getTaskOwnerId(),getTask().getDescription());
		    break;
		default:		
			String message = "The uploaded file type is not supported; must be SIF, XGMML, XBEL, CX, XLS or XLSX."; 
			logger.error(message);
			throw new NdexException (message);

		}
		parser.parseFile();
		taskStatus = Status.COMPLETED;
		long fileSize = file.length();
		file.delete(); // delete the file from the staging area
		logger.info("Network upload file: " + file.getName() +" deleted from staging area");
		try (TaskDAO dao = new TaskDAO ()) {
			this.getTask().getAttributes().put("networkUUID",parser.getUUIDOfUploadedNetwork().toString());
			dao.saveTaskAttributes(this.getTask().getExternalId(),this.getTask().getAttributes());
			dao.updateTaskStatus(this.getTask().getExternalId(),taskStatus );
			dao.commit();
			this.getTask().setStatus(taskStatus);
		}
        logger.info("[end: Network upload finished; UUID='{}' fileSize={}]", parser.getUUIDOfUploadedNetwork().toString(), fileSize);
	}

}
