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

import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.Callable;

import org.ndexbio.common.models.dao.postgresql.TaskDAO;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.TaskType;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import org.ndexbio.model.object.Status;

public abstract class NdexTask implements Callable<Task> {
	
	protected  Task task;
	
	public NdexTask(Task itask)  {
		this.task = itask;
	}

	protected Task getTask() { return this.task;}
	
	private final void startTask() throws IllegalArgumentException, 
		ObjectNotFoundException, SecurityException, NdexException, SQLException, JsonParseException, JsonMappingException, IOException{
		try (TaskDAO dao = new TaskDAO()) {
			dao.updateTaskStatus(task.getExternalId(), Status.PROCESSING);
			dao.commit();
		}
	}
	
    /**
     * Function should update the message field of task before throw exception.
     */
	@Override
	public Task call() throws Exception {
		startTask();
		Task t= call_aux();	
		
		try (TaskDAO dao = new TaskDAO()) {
			dao.updateTaskStatus(task.getExternalId(), Status.COMPLETED);
			dao.commit();
		}
		return t;
	}
	
	protected abstract Task call_aux() throws Exception;
	
	
	public static NdexTask createUserTask(Task t) throws NdexException {
		switch (t.getTaskType()) {
		case EXPORT_NETWORK_TO_FILE:
			return new NetworkExportTask(t);
		default:
			throw new NdexException ("Unknow user task: " + t.getExternalId() + " - " + t.getTaskType());		
		}
		
	}
	
}
