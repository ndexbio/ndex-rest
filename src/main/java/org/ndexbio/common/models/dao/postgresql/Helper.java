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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.ndexbio.common.NdexClasses;
import org.ndexbio.model.object.Account;
import org.ndexbio.model.object.NdexExternalObject;
import org.ndexbio.model.object.ProvenanceEntity;
import org.ndexbio.model.object.SimplePropertyValuePair;
import org.ndexbio.model.object.network.NetworkSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Helper {
	
	static Logger logger = LoggerFactory.getLogger(Helper.class);

	
	/**
	 * Populate a NdexExternalObject using data from an ODocument object.
	 * @param obj The NdexExternaObject to be populated.
	 * @param doc
	 * @return the Pouplated NdexExernalObject.
	 * @throws SQLException 
	 */
	public static NdexExternalObject populateExternalObjectFromResultSet(NdexExternalObject obj, ResultSet rs) throws SQLException {
		
		obj.setExternalId(rs.getObject(NdexClasses.ExternalObj_ID, java.util.UUID.class));
		obj.setCreationTime(rs.getTimestamp(NdexClasses.ExternalObj_cTime));
		obj.setModificationTime(rs.getTimestamp(NdexClasses.ExternalObj_mTime));
       	obj.setIsDeleted( rs.getBoolean(NdexClasses.ExternalObj_isDeleted));
		return obj;
	}

	
	public static Account populateAccountFromResultSet(Account accObj, ResultSet rs, boolean fullRecord) throws SQLException, JsonParseException, JsonMappingException, IOException {
		populateExternalObjectFromResultSet(accObj,rs);
		accObj.setImage(rs.getString(NdexClasses.Account_imageURL));
		accObj.setWebsite(rs.getString(NdexClasses.Account_websiteURL));
		accObj.setDescription(rs.getString(NdexClasses.Account_description));
		
		if ( fullRecord) {
			String propStr = rs.getString(NdexClasses.Account_otherAttributes);
		
			if ( propStr != null) {
				ObjectMapper mapper = new ObjectMapper(); 
				TypeReference<HashMap<String,Object>> typeRef 
	            	= new TypeReference<HashMap<String,Object>>() {/**/};

	            HashMap<String,Object> o = mapper.readValue(propStr, typeRef); 		
	            accObj.setProperties(o);
			}
		}
		
		return accObj;
	}
	
	
    //Added by David Welker
    public static void populateProvenanceEntity(ProvenanceEntity entity, NetworkSummary summary)
    {

        List<SimplePropertyValuePair> entityProperties = new ArrayList<>();

        entityProperties.add( new SimplePropertyValuePair("edge count", Integer.toString( summary.getEdgeCount() )) );
        entityProperties.add( new SimplePropertyValuePair("node count", Integer.toString( summary.getNodeCount() )) );

        if ( summary.getName() != null)
            entityProperties.add( new SimplePropertyValuePair("dc:title", summary.getName()) );

        if ( summary.getDescription() != null)
            entityProperties.add( new SimplePropertyValuePair("description", summary.getDescription()) );

        if ( summary.getVersion()!=null )
            entityProperties.add( new SimplePropertyValuePair("version", summary.getVersion()) );

        entity.setProperties(entityProperties);
    }

 

	
}
