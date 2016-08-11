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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import java.util.UUID;

import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;

public abstract class NdexDBDAO implements AutoCloseable {

	protected Connection db;

	public NdexDBDAO(Connection connection) {
		this.db = connection;
	}

	protected NdexDBDAO() throws SQLException {
		this.db = NdexDatabase.getInstance().getConnection();
	}
	
	/*
	 * resolving a user is a common requirement across all DAO classes
	 * 
	 */

	@Deprecated
	protected ResultSet getRecordByUUID(UUID id, String entityClass) 
			throws ObjectNotFoundException, NdexException, SQLException {
		return getRecordByUUIDStr(id.toString(), entityClass);
		
	}
	
	@Deprecated
	protected ResultSet getRecordByUUIDStr(String id, String entityClass) 
			throws ObjectNotFoundException, NdexException, SQLException {
		

		Statement st = db.createStatement();
		ResultSet rs = st.executeQuery("SELECT * FROM \"" + entityClass + "\" where \"UUID\" = " + id );
		if (rs.next())
			return rs;
		rs.close();
		st.close();
		throw new ObjectNotFoundException("[Class "+ entityClass + "] Object with ID: " + id.toString() + " doesn't exist.");
		
		
	}
	
	
	public Connection getDBConnection() {
		return db;
	}




	@Override
	public void close () throws SQLException {
		db.close();
	}

    public void commit () throws SQLException {
    	db.commit();
    }
    
    public void rollback() throws SQLException {
    	db.rollback();
    } 
}