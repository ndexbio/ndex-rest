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

import org.ndexbio.common.models.dao.postgresql.SingleNetworkDAO;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.Status;
import org.ndexbio.model.object.Task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AttachNamespacefilesTask extends NdexTask {

	private static final Logger logger = LoggerFactory
			.getLogger(AttachNamespacefilesTask.class);
	
	
	public AttachNamespacefilesTask(Task itask) throws NdexException {
		super(itask);
		
	}

	@Override
	public Task call() throws Exception  {
			logger.info("[start: attaching namespace files in network ='{}']", this.getTask().getResource());
			Status taskStatus = Status.PROCESSING;
			this.startTask();
			
			try (SingleNetworkDAO dao = new SingleNetworkDAO(this.getTask().getResource()); ) {
				dao.attachNamespaceFiles();
				dao.commit();
			}
			this.updateTaskStatus(taskStatus);
			logger.info("[end: attaching namespace files in network ='{}']", this.getTask().getResource());
	        return this.getTask();
	        
	}

}
