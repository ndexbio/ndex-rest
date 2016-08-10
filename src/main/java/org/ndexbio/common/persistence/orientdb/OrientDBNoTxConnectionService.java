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
package org.ndexbio.common.persistence.orientdb;

import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.orientdb.NdexSchemaManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;

import com.tinkerpop.blueprints.impls.orient.OrientGraph;

/*
 * abstract class supporting common interactions with the orientdb database
 * specifically acquiring a database connection  initiating a graph module and
 * closing the database connection
 */

public class OrientDBNoTxConnectionService {
	private static final Logger logger = LoggerFactory
			.getLogger(OrientDBNoTxConnectionService.class);
	protected ODatabaseDocumentTx _ndexDatabase = null;
	private boolean setup;
	private OrientGraph graph;

	public OrientDBNoTxConnectionService() {
		this.setSetup(false);
		
	}

	
	/**************************************************************************
	 * Opens a connection to OrientDB and initializes the OrientDB Graph ORM.
	 * @throws NdexException 
	 **************************************************************************/
	protected void setupDatabase() throws NdexException {
		// When starting up this application, tell OrientDB's global
		// configuration to close the storage; this is required here otherwise
		// OrientDB connection pooling doesn't work as expected
		// OGlobalConfiguration.STORAGE_KEEP_OPEN.setValue(false);
		_ndexDatabase = NdexDatabase.getInstance().getAConnection(); 

		graph = new OrientGraph(_ndexDatabase,false);
		graph.setAutoScaleEdgeType(true);
		graph.setEdgeContainerEmbedded2TreeThreshold(40);
		graph.setUseLightweightEdges(true);
		NdexSchemaManager.INSTANCE.init(_ndexDatabase);
		this.setSetup(true);
		logger.info("Connection to OrientDB established");
		
		

	}

	/**************************************************************************
	 * Cleans up the OrientDB resources. These steps are all necessary or
	 * OrientDB connections won't be released from the pool.
	 **************************************************************************/
	protected void teardownDatabase() {

		if (_ndexDatabase != null) {
			_ndexDatabase.close();
			_ndexDatabase = null;
		}

/*		if (_orientDbGraph != null) {
			_orientDbGraph.shutdown();
			_orientDbGraph = null;
		} */
		this.setSetup(false);
		logger.info("Connection to OrientDB closed");
	}
	protected boolean isSetup() {
		return setup;
	}


	private void setSetup(boolean setup) {
		this.setup = setup;
	}
	
	public OrientGraph getGraph() { return this.graph;}
}
