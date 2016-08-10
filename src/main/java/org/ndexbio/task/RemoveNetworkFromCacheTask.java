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

import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.TaskAttribute;
import org.ndexbio.rest.Configuration;


public class RemoveNetworkFromCacheTask extends NdexTask {

	public RemoveNetworkFromCacheTask(Task itask) throws NdexException {
		super(itask);
	}

	@Override
	public Task call() throws NdexException {
		this.deleteNetworkCache();
		return this.getTask();
		
	}

	private void deleteNetworkCache() throws NdexException {
		
//		String networkIdStr = this.getTask().getResource();
		
	//	try ( NetworkDAO dao = new NetworkDAO(NdexDatabase.getInstance().getAConnection())) {
			
			Long commitId = (Long)getTask().getAttribute(TaskAttribute.readOnlyCommitId);
			String fullpath = Configuration.getInstance().getNdexNetworkCachePath() + commitId +".gz";
			
			File file = new File(fullpath);

			file.delete();
			
/*			ODocument d = dao.getNetworkDocByUUIDString(networkIdStr);
			
			Long cacheId = d.field(NdexClasses.Network_P_cacheId);
			
			
			if ( cacheId !=null && cacheId.equals(commitId) ) {
				d.field(NdexClasses.Network_P_cacheId, -1).save();
			} else {
				getTask().setMessage("Network CacheId not cleared because unmatched CacheId.");
			}
			
			dao.commit(); */
	//    }
	}
	

	
}
