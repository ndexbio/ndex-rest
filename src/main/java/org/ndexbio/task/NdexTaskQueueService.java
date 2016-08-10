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

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.ndexbio.model.object.Task;

import com.google.common.base.Preconditions;
import com.google.common.collect.Queues;

/*
 * a singleton implemented as an enum to make the task queue avilable to
 * multiple threads within the application
 * Access is limited to classes within the same package
 */

enum NdexTaskQueueService {
	INSTANCE;
	
	private final ConcurrentLinkedQueue<Task> taskQueue =
			Queues.newConcurrentLinkedQueue();
	
	void addCollection(Collection<Task> iTasks){
		Preconditions.checkArgument(null != iTasks,
				"a collection if ITasks is required");
		this.taskQueue.addAll(iTasks);
	}
	
	/*
	 * encapsulate direct access to queue
	 */
	Task getNextTask() {
		return this.taskQueue.poll();
	}
	
	boolean isTaskQueueEmpty() {
		return this.taskQueue.isEmpty();
	}

	int getTaskQueueSize() {
		return this.taskQueue.size();
	}
}
