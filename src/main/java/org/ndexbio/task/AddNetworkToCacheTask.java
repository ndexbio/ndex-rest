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

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import org.ndexbio.common.NdexClasses;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.TaskAttribute;
import org.ndexbio.model.object.network.Network;
import org.ndexbio.rest.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orientechnologies.orient.core.record.impl.ODocument;

public class AddNetworkToCacheTask extends NdexTask {
	
	static Logger logger = LoggerFactory.getLogger(AddNetworkToCacheTask.class);

	public AddNetworkToCacheTask(Task itask) throws NdexException {
		super(itask);
	}
	
	@Override
	public Task call() throws Exception {
		logger.info("[start: creating cache.]");
		this.createNetworkCache();
		logger.info("[end: finished creating cache.]");
		return this.getTask();
	}

	private void createNetworkCache() throws NdexException {
		
		String networkIdStr = this.getTask().getResource();
		
		try ( NetworkDAO dao = new NetworkDAO(NdexDatabase.getInstance().getAConnection())) {
			Long taskCommitId = (Long)getTask().getAttribute(TaskAttribute.readOnlyCommitId);

			String fullpath = Configuration.getInstance().getNdexNetworkCachePath() + taskCommitId+".gz";

			ODocument d = dao.getNetworkDocByUUIDString(networkIdStr);
			d.reload();
			Long actId = d.field(NdexClasses.Network_P_readOnlyCommitId);
			
			if ( ! actId.equals(taskCommitId)) {
				// stop task
				getTask().setMessage("Network cache not created. readOnlyCommitId="
						+ actId + " in db, but in task we have commitId=" + taskCommitId);
				return;
			}
			
			// create cache.
			
			Network n = dao.getNetworkById(UUID.fromString(networkIdStr));
			n.setReadOnlyCacheId(taskCommitId);
			n.setReadOnlyCommitId(taskCommitId);
			
			try (GZIPOutputStream w = new GZIPOutputStream( new FileOutputStream(fullpath), 16384)) {
					//  String s = mapper.writeValueAsString( original);
					ObjectMapper mapper = new ObjectMapper();
					mapper.writeValue(w, n);
			} catch (FileNotFoundException e) {
				throw new NdexException ("Can't create network cache file in server: " + fullpath + ". Exception: " + e.getLocalizedMessage());
			} catch (IOException e) {
				throw new NdexException ("IO Error when writing cache file: " + fullpath + ". Cause: " + e.getMessage());
			}
			 
			//check again.	
			d.reload();
			actId = d.field(NdexClasses.Network_P_readOnlyCommitId);
			if ( ! actId.equals(taskCommitId)) {
				// stop task
				getTask().setMessage("Network cache not created. Second check found network readOnlyCommitId is"+ 
				   actId + ", task has commitId " + taskCommitId);
				return;
			}

			d.field(NdexClasses.Network_P_cacheId,taskCommitId).save();
			logger.info("Cache " + actId + " created.");
			dao.commit();
	    }
	}
	
	
}
