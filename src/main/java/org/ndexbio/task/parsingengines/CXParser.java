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
package org.ndexbio.task.parsingengines;

import java.io.FileInputStream;
import java.util.List;
import java.util.UUID;

import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.models.dao.postgresql.Helper;
import org.ndexbio.common.models.dao.postgresql.NetworkDocDAO;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.common.persistence.orientdb.CXNetworkLoader;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.NdexProvenanceEventType;
import org.ndexbio.model.object.ProvenanceEntity;
import org.ndexbio.model.object.SimplePropertyValuePair;
import org.ndexbio.model.object.User;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.tools.ProvenanceHelpers;
import org.ndexbio.rest.Configuration;

public class CXParser implements IParsingEngine {
	
	private String fileName;
	private UUID ownerUUID;
	private UUID uuid;
	private String description;

	public CXParser(String fn, UUID ownerUUID, String description) {
		this.fileName = fn;
		this.ownerUUID = ownerUUID;
		this.description = description;
	}

	@Override
	public void parseFile() throws NdexException  {

	/*	
		try (CXNetworkLoader loader = new CXNetworkLoader(new FileInputStream(fileName), ownerUUID)) {
			uuid = loader.persistCXNetwork();
			
			try (NetworkDocDAO dao = new NetworkDocDAO()) {
				NetworkSummary currentNetwork = dao.getNetworkSummaryById(uuid.toString());
				
				String uri = Configuration.getInstance().getHostURI();
		        @SuppressWarnings("resource")
				UserDAO userDocDAO = new UserDAO() ;
		        User loggedInUser = userDocDAO.getUserById(ownerUUID,true);
		        	
				ProvenanceEntity provEntity = ProvenanceHelpers.createProvenanceHistory(currentNetwork,
                    uri, NdexProvenanceEventType.FILE_UPLOAD, currentNetwork.getCreationTime(), 
                    (ProvenanceEntity)null);
				Helper.populateProvenanceEntity(provEntity, currentNetwork);
				provEntity.getCreationEvent().setEndedAtTime(currentNetwork.getModificationTime());

				List<SimplePropertyValuePair> l = provEntity.getCreationEvent().getProperties();
				Helper.addUserInfoToProvenanceEventProperties( l, loggedInUser);
				l.add(	new SimplePropertyValuePair ( "filename",description) );

				loader.setNetworkProvenance(provEntity);
				loader.commit();
			} 
		} catch ( Exception e) {
			e.printStackTrace();
			throw new NdexException ("Failed to load CX file. " + e.getMessage());
		}  */
		
	}

	@Override
	public UUID getUUIDOfUploadedNetwork() {
		return uuid;
	}

}
