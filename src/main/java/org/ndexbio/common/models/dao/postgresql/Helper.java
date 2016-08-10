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
	
    /**
     * Get direct privilege between the account and a network. Indirect privileges are not included.  
     * @param networkUUID
     * @param accountUUID
     * @return the permission allowed between them. Null if no permissions are found.
     */
    public static Permissions getNetworkPermissionByAccout(ODatabaseDocumentTx db, String networkUUID, 
    				String accountUUID) {
        String query = "select $path from (traverse out_admin,out_write,out_read from (select * from " + NdexClasses.Account + 
          		" where UUID='"+ accountUUID + "')) where UUID = '"+ networkUUID + "'";

	    final List<ODocument> result = db.query(new OSQLSynchQuery<ODocument>(query));

	    for ( ODocument d : result ) { 
	    	String s = d.field("$path");
	    	return getNetworkPermissionFromOutPath(s);
        }
    	
    	return null;
    }
    
    public static ODocument getDocumentByElementId(ODatabaseDocumentTx db, long id, String className) {
    		String query = "select from " + className + " where " + 
    		        NdexClasses.Element_ID + "=" + id;
    	    final List<ODocument> nss = db.query(new OSQLSynchQuery<ODocument>(query));
    	  
    	    if (!nss.isEmpty())
    	  	       return nss.get(0);
    	    return null;
    }

    public static Permissions getNetworkPermissionFromOutPath(String path) {
	    Pattern pattern = Pattern.compile("out_([a-z]+)");
	    Matcher matcher = pattern.matcher(path);
	    if (matcher.find())
	    {
	    	return Permissions.valueOf(matcher.group(1).toUpperCase());
	    }  
	    return null;
    }

    public static Permissions getNetworkPermissionFromInPath(String path) {
	    Pattern pattern = Pattern.compile("in_([a-z]+)");
	    Matcher matcher = pattern.matcher(path);
	    if (matcher.find())
	    {
	    	return Permissions.valueOf(matcher.group(1).toUpperCase());
	    }  
	    return null;
    }

    
    public static boolean isAdminOfNetwork(ODatabaseDocumentTx db, String networkUUID, 
			String accountUUID) {
    	String query = "select $path from (traverse out_admin,out_member,out_groupadmin from (select * from " + NdexClasses.Account + 
    			" where UUID='"+ accountUUID + "') while $depth < 3 ) where UUID = '"+ networkUUID + "'";

    	final List<ODocument> result = db.query(new OSQLSynchQuery<ODocument>(query));

    	for ( ODocument d : result ) { 
    		String s = d.field("$path");
    		Pattern pattern = Pattern.compile("out_admin");
    		Matcher matcher = pattern.matcher(s);
    		if (matcher.find())
    		{
    			return true;
    		}  
    	}

    	return false;
    }

    public static boolean checkPermissionOnNetworkByAccountName(ODatabaseDocumentTx db, String networkUUID, 
			String accountName, Permissions expectedPermission) {
    	String query = "select $path from (traverse out_admin,out_member,out_groupadmin,out_write,out_read from (select * from " + NdexClasses.Account + 
    			" where accountName='"+ accountName + "') while $depth < 3 ) where UUID = '"+ networkUUID + "'";

    	logger.debug("Checking permissiong, query string is: " + query);
    	
    	final List<ODocument> result = db.query(new OSQLSynchQuery<ODocument>(query));

    	for ( ODocument d : result ) { 
    		String s = d.field("$path");
    		
        	logger.debug("Got query result $path: " + s);
        	
    		Pattern pattern = Pattern.compile("(out_admin|out_write|out_read)");
    		Matcher matcher = pattern.matcher(s);
    		if (matcher.find())
    		{
    			Permissions p = Permissions.valueOf(matcher.group(1).substring(4).toUpperCase());
    			if ( permissionSatisfied( expectedPermission, p))
    				return true;
    		}  
    	}

    	return false;
    }
    
    public static VisibilityType getNetworkVisibility(ODatabaseDocumentTx db, String networkUUID) {
    	String query = "select " + NdexClasses.Network_P_visibility + " from " + NdexClasses.Network + 
    			" where UUID='"+ networkUUID + "'";

    	final List<ODocument> result = db.query(new OSQLSynchQuery<ODocument>(query));

    	if ( result.isEmpty()) return null;
 
    	String s = result.get(0).field(NdexClasses.Network_P_visibility);
    	return VisibilityType.valueOf(s);
    	
    }

    
    /**
     * Check if the actual permission meets the required permission level.
     * @param requiredPermission
     * @param actualPermission
     * @return
     */
    public static boolean permissionSatisfied(Permissions requiredPermission, Permissions actualPermission) {
    	if ( actualPermission == Permissions.ADMIN) return true;
    	if ( actualPermission == Permissions.WRITE) {
    		if (requiredPermission == Permissions.ADMIN)
    			return false;
    		return true;
    	}
    	if ( actualPermission == Permissions.READ && requiredPermission == Permissions.READ) 
    			return true;
    	return false;
    }
    
/*    
    public static boolean isAdminOfNetworkByAccountName(ODatabaseDocumentTx db, String networkUUID, 
			String accountName) {
    	String query = "select $path from (traverse out_admin,out_member,out_groupadmin from (select * from " + NdexClasses.Account + 
    			" where accountName='"+ accountName + "') while $depth < 3 ) where UUID = '"+ networkUUID + "'";

    	final List<ODocument> result = db.query(new OSQLSynchQuery<ODocument>(query));

    	for ( ODocument d : result ) { 
    		String s = d.field("$path");
    		Pattern pattern = Pattern.compile("out_admin");
    		Matcher matcher = pattern.matcher(s);
    		if (matcher.find())
    		{
    			return true;
    		}  
    	}

    	return false;
    }
 */   
    /**
     * Check if an admin account exists on the given network other than the one specified in the parameter.
     *  Basically used to check if an admin edge are allowed to be removed between the network and given account. 
     * @param db
     * @param networkUUID
     * @param accountUUID
     * @return 
     */
    public static boolean canRemoveAdmin(ODatabaseDocumentTx db, String networkUUID, 
    				String accountUUID) {
    	
    	String query = "select count(*) as c from (traverse in_" + NdexClasses.E_admin + " from (select from " +
    	   NdexClasses.Network +" where UUID = '"+ networkUUID + "')) where UUID <> '"+ accountUUID +"'";

    	final List<ODocument> result = db.query(new OSQLSynchQuery<ODocument>(query));

	    if ((long) result.get(0).field("c") > 1 ) return true;
    	return false;
    }

    public static boolean canRemoveAdminOnGrp(ODatabaseDocumentTx db, String grpUUID, 
			String accountUUID) {

    	String query = "select count(*) as c from (traverse in_" + NdexClasses.GRP_E_admin + " from (select from " +
    	NdexClasses.Group +" where UUID = '"+ grpUUID + "')) where UUID <> '"+ accountUUID +"'";

    	final List<ODocument> result = db.query(new OSQLSynchQuery<ODocument>(query));

    	if ((long) result.get(0).field("c") > 1 ) return true;
    	return false;
    }

    public static boolean canRemoveAdminByAccount(ODatabaseDocumentTx db, String networkUUID, 
			String accountName) {

    	String query = "select count(*) as c from (traverse in_" + NdexClasses.E_admin + " from (select from " +
    			NdexClasses.Network +" where UUID = '"+ networkUUID + "')) where accountName <> '"+ accountName +"'";

    	final List<ODocument> result = db.query(new OSQLSynchQuery<ODocument>(query));

    	if ((long) result.get(0).field("c") > 1 ) return true;
		return false;
    }

/*	public static NdexPropertyValuePair getNdexPropertyFromDoc(ODocument doc) {
		NdexPropertyValuePair p = new NdexPropertyValuePair();
		
		ODocument baseTermDoc = doc.field("out_" + NdexClasses.ndexProp_E_predicate);
		if ( baseTermDoc == null ) {
			p.setPredicateString((String)doc.field(NdexClasses.ndexProp_P_predicateStr));
		} else {
			p.setPredicateString(getBaseTermStrFromDocument (baseTermDoc));
//			p.setPredicateId((long)baseTermDoc.field(NdexClasses.Element_ID));
		}
		
		p.setValue((String)doc.field(NdexClasses.ndexProp_P_value)) ;
    	p.setDataType((String)doc.field(NdexClasses.ndexProp_P_datatype));
		return p;
	}
	
	private static String getBaseTermStrFromDocument(ODocument doc) {
		ODocument nsDoc = doc.field("out_" + NdexClasses.BTerm_E_Namespace);
		String localName = doc.field(NdexClasses.BTerm_P_name);
		if ( nsDoc !=null) {
			String prefix = nsDoc.field(NdexClasses.ns_P_prefix);
			if ( prefix != null)
				return prefix + ":" + localName;
			return nsDoc.field(NdexClasses.ns_P_uri) + localName;
		}
		return localName;
	}
*/	
	public static SimplePropertyValuePair getSimplePropertyFromDoc(ODocument doc) {
		SimplePropertyValuePair p = new SimplePropertyValuePair();
		p.setName((String)doc.field(NdexClasses.SimpleProp_P_name));
		p.setValue((String)doc.field(NdexClasses.SimpleProp_P_value)) ;
    	
		return p;
	}


	
	public static ODocument createSimplePropertyDoc(SimplePropertyValuePair property) {
		ODocument pDoc = new ODocument(NdexClasses.SimpleProperty)
			.fields(NdexClasses.SimpleProp_P_name,property.getName(),
					NdexClasses.SimpleProp_P_value, property.getValue())
			.save();
		return  pDoc;
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
	
	
	public static NetworkSourceFormat getSourceFormatFromNetworkDoc(ODocument networkDoc) {
		String s = networkDoc.field(NdexClasses.Network_P_source_format);
		if ( s == null)
			return null;
		return NetworkSourceFormat.valueOf(s);
	}


	//TODO: this is a quick fix. Need to review Orientdb string escape rules to properly implement it.
	public static String escapeOrientDBSQL(String str) {
		return str.replace("'", "\\'");
	}

    // Added by David Welker
 /*    public static void populateProvenanceEntity(ProvenanceEntity entity, NetworkDocDAO dao, String networkId) throws NdexException
    {
        NetworkSummary summary = NetworkDocDAO.getNetworkSummary(dao.getRecordByUUIDStr(networkId, null));
        populateProvenanceEntity(entity, summary);
    } */

	
    //Added by David Welker
    public static void populateProvenanceEntity(ProvenanceEntity entity, NetworkSummary summary) throws NdexException
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

        if( user.getAccountName() != null )
            eventProperties.add( new SimplePropertyValuePair("account name", user.getAccountName()) );
    }


    public static Iterable<ODocument> getNetworkElements(ODocument networkDoc, String elementEdgeString) {	
    	
    	Object f = networkDoc.field("out_"+ elementEdgeString);
    	
    	if ( f == null) return emptyDocs;
    	
    	if ( f instanceof ODocument)
    		 return new OrientDBIterableSingleLink((ODocument)f);
    	
    	return ((Iterable<ODocument>)f);
    	     
    }

    
    public static Iterable<ODocument> getDocumentLinks(ODocument doc, String direction, String elementEdgeString) {	
    	
    	Object f = doc.field(direction+ elementEdgeString);
    	
    	if ( f == null) return emptyDocs;
    	
    	if ( f instanceof ODocument)
    		 return new OrientDBIterableSingleLink((ODocument)f);
    	
    	return ((Iterable<ODocument>)f);
    	     
    }
    
    
	public static void createUserIfnotExist(UserDAO dao, String accountName, String email, String password) throws NdexException {
		try {
			User u = dao.getUserByAccountName(accountName);
			if ( u!= null) return;
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
			throw new NdexException ("Failed to create new user after creating database. " + e.getMessage());
		} catch ( ObjectNotFoundException e2) {
			
		}
		
		NewUser newUser = new NewUser();
        newUser.setEmailAddress(email);
        newUser.setPassword(password);
        newUser.setAccountName(accountName);
        newUser.setFirstName("");
        newUser.setLastName("");
        dao.createNewUser(newUser, null);
        

	}
	
	
}
