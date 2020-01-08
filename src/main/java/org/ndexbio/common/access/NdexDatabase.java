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
package org.ndexbio.common.access;


import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

import org.apache.commons.dbcp2.BasicDataSource;
import org.ndexbio.model.exceptions.NdexException;

public class NdexDatabase {
	
	private static NdexDatabase INSTANCE = null;
	
	private BasicDataSource connectionPool;
	
	
	private static final Logger logger = Logger
			.getLogger(NdexDatabase.class.getName());
	
	private static long currentId = System.currentTimeMillis(); 
	
/*	private static  String[] NdexSupportedAspects ={NodesElement.ASPECT_NAME,EdgesElement.ASPECT_NAME,NetworkAttributesElement.ASPECT_NAME,
			NodeAttributesElement.ASPECT_NAME, EdgeAttributesElement.ASPECT_NAME, CitationElement.ASPECT_NAME, SupportElement.ASPECT_NAME,
			EdgeCitationLinksElement.ASPECT_NAME, EdgeSupportLinksElement.ASPECT_NAME, NodeCitationLinksElement.ASPECT_NAME,
			NodeSupportLinksElement.ASPECT_NAME, FunctionTermElement.ASPECT_NAME, NamespacesElement.ASPECT_NAME, NdexNetworkStatus.ASPECT_NAME,
			Provenance.ASPECT_NAME,ReifiedEdgeElement.ASPECT_NAME}; */
	
	private NdexDatabase(String dbURL, String dbUserName,
			String dbPassword, int size) {
			
	//	Arrays.sort(NdexSupportedAspects) ;
		
		connectionPool = new BasicDataSource();
		
		connectionPool.setUsername(dbUserName);
		connectionPool.setPassword(dbPassword);
		
		connectionPool.setDriverClassName("org.postgresql.Driver");
		connectionPool.setUrl(dbURL);
		connectionPool.setInitialSize(3);		
		connectionPool.setMaxIdle(5);
		connectionPool.setMaxConnLifetimeMillis(-1);
		connectionPool.setMaxTotal(size);
		
		
	    logger.info("Connection pool to " + dbUserName + "@" + dbURL + " ("+ size + ") created.");
		
	}
	
	public static synchronized long getCommitId () {
		return currentId++;
	}
	
	/**
	 * This function create a NDEx database object. It connects to the specified back end database if it exists, otherwise it will create one and connect to it. 
	 * @param HostURI  The URI of this NDEX server. It will be used to construct URIs for the networks that are created in this database.
	 * @param dbURL   Specify where the database is and what protocol we should use to connect to it.
	 * @param dbUserName   the account that administrator that backend database.
	 * @param dbPassword
	 * @param size
	 * @return
	 * @throws NdexException
	 */
	public static synchronized NdexDatabase createNdexDatabase ( String dbURL, String dbUserName,
			String dbPassword, int size) throws NdexException {
		if(INSTANCE == null) {
	         INSTANCE = new NdexDatabase(dbURL, dbUserName, dbPassword, size);
	         return INSTANCE;
		} 
		
		throw new NdexException("Database has already been opened.");
		
	}

	
	
	public static synchronized NdexDatabase getInstance() {
	      return INSTANCE;
	}
    
    public static synchronized void close () throws SQLException {
    	if ( INSTANCE != null ) {
    		logger.info("Closing database.");
    		INSTANCE.connectionPool.close();
    		INSTANCE.connectionPool = null;
    		INSTANCE = null;
    		logger.info("Database closed.");
    	} else 
    		logger.info("Database is already closed.");
    }
    
    /**
     * Get a connection from the connection pool. The connection is set to autoCommit=false and user need to do a explicit commit and close the connection at the end.
     * @return
     * @throws SQLException
     */
    public Connection getConnection() throws SQLException {
    	
//	    logger.info("Current idle connection in db connection pool is " + connectionPool.getNumIdle());

    	Connection conn = connectionPool.getConnection();
    	conn.setAutoCommit(false);
    	return conn;
    }


 
}
