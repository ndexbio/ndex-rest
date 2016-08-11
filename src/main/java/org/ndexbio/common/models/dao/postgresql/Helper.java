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
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.ndexbio.common.NdexClasses;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.object.*;
import org.ndexbio.model.object.network.NetworkSourceFormat;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.object.network.VisibilityType;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;

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

	
	public static Account populateAccountFromResultSet(Account accObj, ResultSet rs) throws SQLException, JsonParseException, JsonMappingException, IOException {
		populateExternalObjectFromResultSet(accObj,rs);
		accObj.setImage(rs.getString(NdexClasses.Account_imageURL));
		accObj.setWebsite(rs.getString(NdexClasses.Account_websiteURL));
		accObj.setDescription(rs.getString(NdexClasses.Account_description));
		String propStr = rs.getString(NdexClasses.Account_otherAttributes);
		
		if ( propStr != null) {
			ObjectMapper mapper = new ObjectMapper(); 
			TypeReference<HashMap<String,Object>> typeRef 
	            = new TypeReference<HashMap<String,Object>>() {};

	            HashMap<String,Object> o = mapper.readValue(propStr, typeRef); 		
	            accObj.setProperties(o);
		}
		
		return accObj;
	}
	

	public static ODocument updateNetworkProfile(ODocument doc, NetworkSummary newSummary){
	
	   boolean needResetModificationTime = false;
	   
	   if ( newSummary.getName() != null) {
		 doc.field( NdexClasses.Network_P_name, newSummary.getName());
		 needResetModificationTime = true;
	   }
		
	  if ( newSummary.getDescription() != null) {
		doc.field( NdexClasses.Network_P_desc, newSummary.getDescription());
		needResetModificationTime = true;
	  }
	
	  if ( newSummary.getVersion()!=null ) {
		doc.field( NdexClasses.Network_P_version, newSummary.getVersion());
		needResetModificationTime = true;
	  }
	  
	  if ( newSummary.getVisibility()!=null )
		doc.field( NdexClasses.Network_P_visibility, newSummary.getVisibility());
	  
	  if (needResetModificationTime) 
	     doc.field(NdexClasses.ExternalObj_mTime, new Date());
      
	  doc.save();
	  return doc;
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

    //Added by David Welker
    public static void addUserInfoToProvenanceEventProperties(List<SimplePropertyValuePair> eventProperties, User user)
    {
        String firstName = user.getFirstName();
        String lastName = user.getLastName();
        if( firstName != null || lastName != null )
        {
            String name = "";
            if( firstName == null )
                name = lastName;
            else if( lastName == null )
                name = firstName;
            else
                name = firstName + " " + lastName;
            eventProperties.add( new SimplePropertyValuePair("user", name));
        }

        if( user.getUserName() != null )
            eventProperties.add( new SimplePropertyValuePair("user name", user.getUserName()) );
    }


     
	public static void createUserIfnotExist(UserDAO dao, String accountName, String email, String password) throws NdexException, JsonParseException, JsonMappingException, IllegalArgumentException, NoSuchAlgorithmException, SQLException, IOException {
		try {
			User u = dao.getUserByAccountName(accountName,true);
			if ( u!= null) return;
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
			throw new NdexException ("Failed to create new user after creating database. " + e.getMessage());
		} catch ( ObjectNotFoundException e2) {
			
		}
		
		User newUser = new User();
        newUser.setEmailAddress(email);
        newUser.setPassword(password);
        newUser.setUserName(accountName);
        newUser.setFirstName("");
        newUser.setLastName("");
        dao.createNewUser(newUser, null);
        

	}
	
	
}
