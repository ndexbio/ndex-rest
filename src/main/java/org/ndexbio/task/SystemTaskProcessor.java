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

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.logging.Logger;

import org.ndexbio.common.NdexClasses;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.models.dao.postgresql.RequestDAO;
import org.ndexbio.common.models.dao.postgresql.TaskDAO;
import org.ndexbio.common.util.NdexUUIDFactory;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.Request;
import org.ndexbio.model.object.ResponseType;
import org.ndexbio.model.object.Status;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.TaskType;
import org.ndexbio.rest.Configuration;

public class SystemTaskProcessor extends NdexTaskProcessor {

    private Logger logger = Logger.getLogger(SystemTaskProcessor.class.getSimpleName());
	
	public SystemTaskProcessor () {
		super();
	}
	
	@Override
	public void run() {
		while ( !shutdown) {
			Task task = null;
			try {
				task = NdexServerQueue.INSTANCE.takeNextSystemTask();
				if ( task == NdexServerQueue.endOfQueue) {
					logger.info("End of queue signal received. Shutdown processor.");
					return;
				}
			} catch (InterruptedException e) {
				logger.info("takeNextSystemTask Interrupted.");
				return;
			}

			TaskType type = task.getTaskType();

		}
	}
	
		
/*	
	private void sendEmailNotification(Task task) throws NdexException {
  		 try (ODatabaseDocumentTx db = NdexDatabase.getInstance().getAConnection()){
  		
  			OSQLSynchQuery<ODocument> query = new OSQLSynchQuery<>(
  						"SELECT FROM " + NdexClasses.Request +
  						" WHERE sysdate().asLong()  - modificationTime.asLong()  < 24*3600000 and  isDeleted=false"
  								 );

  			List<ODocument> records = db.command(query).execute();

  			for (ODocument request : records) {
  				Request r = RequestDAO.getRequestFromDocument(request);
  			}
  		 }
	} */
}
