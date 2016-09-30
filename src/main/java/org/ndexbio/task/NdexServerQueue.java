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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import org.ndexbio.model.object.Task;

public enum NdexServerQueue {
	
	INSTANCE;
	
	private LinkedBlockingDeque<NdexSystemTask> systemTaskQueue;
	private LinkedBlockingDeque<NdexTask> userTaskQueue;
	
	public static final NdexTask endOfQueue = new NdexTask (null) { 
		@Override 
		public Task call() throws Exception {return null;}
		@Override 
		protected Task call_aux() {return null;} } ;
	
	public static final NdexSystemTask endOfSystemQueue = new NdexSystemTask() { @Override
	public void run () {/*We don't expect this to be run.*/}};

	private NdexServerQueue () {
		systemTaskQueue = new LinkedBlockingDeque<>();
		userTaskQueue = new LinkedBlockingDeque<>();
    }
	

	public NdexSystemTask takeNextSystemTask () throws InterruptedException {
		return systemTaskQueue.take();
	}

	public NdexTask takeNextUserTask () throws InterruptedException {
		return userTaskQueue.take();
	}
	
	public void addSystemTask (NdexSystemTask task)  {
		systemTaskQueue.add(task);
	}

	public void addFirstSystemTask (NdexSystemTask task)  {
		systemTaskQueue.addFirst(task);
	}
	
	public void addUserTask (NdexTask task)  {
		userTaskQueue.add(task);
	}
	
	public BlockingQueue<NdexSystemTask> getSystemTaskQueue () {
		return systemTaskQueue;
	}
	
	public BlockingQueue<NdexTask> getUserTaskQueue () {
		return userTaskQueue;
	}
	
	public void shutdown () {
		systemTaskQueue.add(endOfSystemQueue);
		userTaskQueue.add(endOfQueue);
		
	}
 }
