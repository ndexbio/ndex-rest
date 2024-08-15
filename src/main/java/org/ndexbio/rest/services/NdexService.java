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
package org.ndexbio.rest.services;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.SQLException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.io.IOUtils;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.common.util.NdexUUIDFactory;
import org.ndexbio.model.exceptions.BadRequestException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.NetworkConcurrentModificationException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.filters.BasicAuthenticationFilter;
import org.ndexbio.security.GoogleOpenIDAuthenticator;
import org.ndexbio.security.OAuthAuthenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class NdexService
{
	public static final String NdexZipFlag = "NdexZipped";
	
    protected HttpServletRequest _httpRequest;
    private static OAuthAuthenticator oauthAuthenticator = null;
    
	static Logger logger = LoggerFactory.getLogger(NdexService.class);

    
    /**************************************************************************
    * Injects the HTTP request into the base class to be used by
    * getLoggedInUser(). 
    * 
    * @param httpRequest The HTTP request injected by RESTEasy's context.
    **************************************************************************/
    public NdexService(HttpServletRequest httpRequest) {
        _httpRequest = httpRequest;
        
    }
    
  
    /**************************************************************************
    * Gets the authenticated user that made the request.
    * 
    * @return The authenticated user, or null if anonymous.
    **************************************************************************/
    protected User getLoggedInUser()
    {
        final Object user = _httpRequest.getAttribute("User");
        if (user != null)
            return (org.ndexbio.model.object.User)user;
        
        return null;
    }
    
    protected UUID getLoggedInUserId()
    {
        final Object user = _httpRequest.getAttribute("User");
        if (user != null)
            return ((org.ndexbio.model.object.User)user).getExternalId();
        
        return null;
    }
    
    protected void setZipFlag() {
    	_httpRequest.setAttribute(NdexZipFlag, Boolean.TRUE);
    }
    
    protected InputStream getInputStreamFromRequest() throws IOException {
    		return _httpRequest.getInputStream();
    }
    
    protected static OAuthAuthenticator getOAuthAuthenticator() {return oauthAuthenticator;}
    public static void setOAuthAuthenticator(OAuthAuthenticator a) {
    	oauthAuthenticator = a;
    }
    
	protected static UUID getUserIdFromBasicAuthString(String encodedAuthInfo) throws Exception {
		final String decodedAuthInfo = new String(Base64.getDecoder().decode(encodedAuthInfo));
		int idx = decodedAuthInfo.indexOf(":");
		if (idx == -1)
			throw new UnauthorizedOperationException("Malformed authorization value received.");

		String username = decodedAuthInfo.substring(0, idx);
		String password = decodedAuthInfo.substring(idx + 1);

		if (BasicAuthenticationFilter.getLDAPAuthenticator() != null) {
			if (!BasicAuthenticationFilter.getLDAPAuthenticator().authenticateUser(username, password)) {
				throw new UnauthorizedOperationException("Invalid username or password for AD authentication.");
			}
			try (UserDAO dao = new UserDAO()) {
				return dao.getUserByAccountName(username.toLowerCase(), true, true).getExternalId();
			}
		}
		try (UserDAO dao = new UserDAO()) {
			 return dao.authenticateUser(username.toLowerCase(), password).getExternalId();
		}

	}
	
	   protected static UUID storeRawNetworkFromStream(InputStream in, String fileName) throws IOException {
		   
		   UUID uuid = NdexUUIDFactory.INSTANCE.createNewNDExUUID();
		   String pathPrefix = Configuration.getInstance().getNdexRoot() + "/data/" + uuid.toString();
		   
		   //Create dir
		   java.nio.file.Path dir = Paths.get(pathPrefix);
		   Set<PosixFilePermission> perms =
				    PosixFilePermissions.fromString("rwxrwxr-x");
				FileAttribute<Set<PosixFilePermission>> attr =
				    PosixFilePermissions.asFileAttribute(perms);
		   Files.createDirectory(dir,attr);
		   
		   //write content to file
		   String cxFilePath = pathPrefix + "/" + fileName;
		   
		   try (OutputStream outputStream = new FileOutputStream(cxFilePath)) {
			   IOUtils.copy(in, outputStream);
			   outputStream.close();
		   } 
		   return uuid;
	   }

	   
	   protected static UUID storeRawNetworkFromMultipart (MultipartFormDataInput input, String fileName) throws IOException, BadRequestException {
		   Map<String, List<InputPart>> uploadForm = input.getFormDataMap();
	       			       
		   //Get file data to save
		   List<InputPart> inputParts = uploadForm.get("CXNetworkStream");
		   if (inputParts == null)
			   throw new BadRequestException("Field CXNetworkStream is not found in the POSTed Data.");
			 
		   byte[] bytes = new byte[8192];
		   UUID uuid = NdexUUIDFactory.INSTANCE.createNewNDExUUID();
		   String pathPrefix = Configuration.getInstance().getNdexRoot() + "/data/" + uuid.toString();
				   
		   //Create dir
		   java.nio.file.Path dir = Paths.get(pathPrefix);
		   Set<PosixFilePermission> perms =
						    PosixFilePermissions.fromString("rwxrwxr-x");
		   FileAttribute<Set<PosixFilePermission>> attr =
						    PosixFilePermissions.asFileAttribute(perms);
		   Files.createDirectory(dir,attr);
				   
		   //write content to file
		   String cxFilePath = pathPrefix + "/" + fileName;
		   try (FileOutputStream out = new FileOutputStream (cxFilePath ) ){     
			   for (InputPart inputPart : inputParts) {
				     // convert the uploaded file to inputstream and write it to disk
				   org.jboss.resteasy.plugins.providers.multipart.MultipartInputImpl.PartImpl p =
				        	(org.jboss.resteasy.plugins.providers.multipart.MultipartInputImpl.PartImpl) inputPart;
				   try (InputStream inputStream = p.getBody()) {
				              
				       int read = 0;
				       while ((read = inputStream.read(bytes)) != -1) {
				            out.write(bytes, 0, read);
				       }
				   }
				               
			   }
		   }
		   return uuid; 
	   }
	   
	   
	   /**
	    * Caller should put this function in a try () ressource statement so that the NetworkDAO object can be 
	    * closed properly when an exception was thrown. 
	    * @param networkId
	    * @return
	    * @throws SQLException
	    * @throws NdexException
	    */
	   protected NetworkDAO lockNetworkForUpdate(UUID networkId) throws SQLException, NdexException {
		   
			try (UserDAO dao = new UserDAO()) {
				   dao.checkDiskSpace(getLoggedInUserId());
			}
	    	
	        @SuppressWarnings("resource")
			NetworkDAO daoNew = new NetworkDAO() ;
	        User user = getLoggedInUser();
	           
		  	   if( daoNew.isReadOnly(networkId)) {
		  		    daoNew.close();
					throw new NdexException ("Error: Unable to update a read-only network.");				
				} 
				
				if ( !daoNew.isWriteable(networkId, user.getExternalId())) {
					daoNew.close();
			        throw new UnauthorizedOperationException("You do not have write permissions for this network.");
				} 
				
				if ( daoNew.networkIsLocked(networkId)) {
					daoNew.close();
					throw new NetworkConcurrentModificationException ();
			   } 
				
			try {
				daoNew.lockNetwork(networkId);
			} catch (SQLException sqlErr) {
				daoNew.close();
				throw new NdexException("Failed to lock network " + networkId.toString() + 
						". Cause: " + sqlErr.getMessage());
			} catch (NetworkConcurrentModificationException e) {
				daoNew.close();
				throw e;
			}
			
			return daoNew;
		   
	   }
	
}
