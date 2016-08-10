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
package org.ndexbio.common.models.dao.postgresql;

import java.io.IOException;

import org.ndexbio.common.NdexClasses;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.Status;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.TaskAttribute;
import org.ndexbio.model.object.TaskType;
import org.slf4j.MDC;

import com.orientechnologies.orient.core.record.impl.ODocument;


public class NetworkDAOTx extends NdexDBDAO {

	public NetworkDAOTx () throws NdexException {
	    super(NdexDatabase.getInstance().getAConnection());

	}

	
	/** 
	 * Set a flag of a network. We currently only support setting readOnly flag for download optimization purpose.
	 * @param UUIDstr
	 * @param parameter
	 * @param value
	 * @return
	 * @throws NdexException 
	 * @throws IOException 
	 */
	public long setReadOnlyFlag(String UUIDstr,  boolean value, String userAccountName) throws NdexException {
		
		
		ODocument networkDoc =this.getRecordByUUIDStr(UUIDstr, null);
		Long commitId = networkDoc.field(NdexClasses.Network_P_readOnlyCommitId);

		if ( commitId == null || commitId.longValue() < 0 ) {
		   if ( value ) { // set the flag to true
			    long newCommitId = NdexDatabase.getCommitId();
				networkDoc.fields(NdexClasses.Network_P_readOnlyCommitId, newCommitId).save();
				db.commit();
				Task createCache = new Task();
				createCache.setTaskType(TaskType.CREATE_NETWORK_CACHE);
				createCache.setResource(UUIDstr); 
				createCache.setStatus(Status.QUEUED);
				createCache.setAttribute(TaskAttribute.readOnlyCommitId, Long.valueOf(newCommitId));
                createCache.setAttribute("RequestsUniqueId", MDC.get("RequestsUniqueId"));
                
				TaskDAO taskDAO = new TaskDAO(this.db);
				taskDAO.createTask(userAccountName, createCache);
				db.commit();
			   
		   } 
		   return -1;
			   
		} 
		
		// was readOnly
		if ( !value ) { // unset the flag
			networkDoc.fields(NdexClasses.Network_P_readOnlyCommitId, Long.valueOf(-1),
					          NdexClasses.Network_P_cacheId, Long.valueOf(-1)).save();
			db.commit();
			Task deleteCache = new Task();
			deleteCache.setTaskType(TaskType.DELETE_NETWORK_CACHE);
			deleteCache.setResource(UUIDstr); 
			deleteCache.setStatus(Status.QUEUED);
			deleteCache.setAttribute(TaskAttribute.readOnlyCommitId, commitId);
			
			TaskDAO taskDAO = new TaskDAO(this.db);
			taskDAO.createTask(userAccountName, deleteCache);
			db.commit();
			
		} 
		return commitId.longValue();
		
		
	}

}
