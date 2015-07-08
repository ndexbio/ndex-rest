/**
 * Copyright (c) 2013, 2015, The Regents of the University of California, The Cytoscape Consortium
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
package org.ndexbio.rest.filters;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;

import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;

import org.ndexbio.common.models.dao.orientdb.UserDAO;
import org.ndexbio.common.access.NdexDatabase;
import org.apache.log4j.MDC;
import org.jboss.resteasy.core.Headers;
import org.jboss.resteasy.core.ResourceMethodInvoker;
import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.util.Base64;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.NewUser;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.services.NdexOpenFunction;
import org.ndexbio.security.DelegatedLDAPAuthenticator;
import org.ndexbio.security.LDAPAuthenticator;
import org.ndexbio.task.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * class represents a RestEasy request filter that will validate
 * a user supplied credentials against those stored in the OrientDb database
 * 
 * this class is a modest refactoring of the legacy BasicAuthentication class.
 * the major change is that it now implements the ContainerRequestFilter interface
 */
@Provider
public class BasicAuthenticationFilter implements ContainerRequestFilter
{
	private static final String basicAuthPrefix = "Basic "; 
	
    private static final Logger _logger = LoggerFactory.getLogger(BasicAuthenticationFilter.class);
    private static final ServerResponse ACCESS_DENIED = new ServerResponse("Invalid username or password.", 401, new Headers<>());
    private static final ServerResponse ACCESS_DENIED_USER_NOT_FOUND = 
    		new ServerResponse("User not found.", 401, new Headers<>());
    private static final ServerResponse FORBIDDEN = new ServerResponse("Forbidden.", 403, new Headers<>());
    private static LDAPAuthenticator ADAuthenticator = null;
    private boolean authenticatedUserOnly = false;
    private static final String AUTHENTICATED_USER_ONLY="AUTHENTICATED_USER_ONLY";
    private static final String AD_CREATE_USER_AUTOMATICALLY="AD_CREATE_USER_AUTOMATICALLY";
    
    public BasicAuthenticationFilter() throws NdexException {
    	super();
    	
    	Configuration config = Configuration.getInstance();
    	
    	if ( config.getUseADAuthentication() && ADAuthenticator == null) {
    		    ADAuthenticator = new LDAPAuthenticator(config);
    	}
    	String value = config.getProperty(AUTHENTICATED_USER_ONLY);
		_logger.info("authenticatedUserOnly setting is " + value);

    	if ( value !=null && Boolean.parseBoolean(value)) {
    		_logger.info("Server running in authenticatedUserOnly mode.");
    		authenticatedUserOnly = true;
    	}
    }
    
    public static LDAPAuthenticator getLDAPAuthenticator() {
    	return ADAuthenticator;
    }
    
    @Override
    public void filter(ContainerRequestContext requestContext)
    {
        final ResourceMethodInvoker methodInvoker = (ResourceMethodInvoker)requestContext.getProperty("org.jboss.resteasy.core.ResourceMethodInvoker");
        final Method method = methodInvoker.getMethod();
//        ODatabaseDocumentTx localConnection = null;
        
        String[] authInfo = null;
        User authUser = null;
        boolean authenticated = false;
        try
        {
            authInfo = parseCredentials(requestContext);
            MDC.put("UserName", ((authInfo != null) ? authInfo[0] : ""));
    		_logger.debug("[start: User={}]",  (authInfo != null) ? authInfo[0] : "");
            if(authInfo != null) {  // server need to authenticate the user.
            	if (ADAuthenticator !=null ) {
            		if ( ADAuthenticator.authenticateUser(authInfo[0], authInfo[1]) ) {
            			authenticated = true;
            			_logger.debug("User {} authenticated by AD.", authInfo[0]);
                		try ( UserDAO dao = new UserDAO(NdexDatabase.getInstance().getAConnection()) ) {
                   		  try {
                			authUser = dao.getUserByAccountName(authInfo[0]);
                   		  } catch (ObjectNotFoundException e) {
                     			String autoCreateAccount = Configuration.getInstance().getProperty(AD_CREATE_USER_AUTOMATICALLY);
                       			if ( autoCreateAccount !=null && Boolean.parseBoolean(autoCreateAccount)) {
                       				NewUser newUser = ADAuthenticator.getNewUser(authInfo[0], authInfo[1]);
                       				authUser = dao.createNewUser(newUser);
                       			} else 
                       				throw e;
                       	  }	
                   		} 
            		}
            	} else {
            		authInfo[0] = authInfo[0].toLowerCase();
  //          		localConnection = NdexDatabase.getInstance().getAConnection();
            		try ( UserDAO dao = new UserDAO(NdexDatabase.getInstance().getAConnection()) ) {
            		 authUser = dao.authenticateUser(authInfo[0],authInfo[1]);
            		}
            	}
            
            	if (authUser != null) {
            		requestContext.setProperty("User", authUser);
            		_logger.debug("[end: User {} authenticated]", authInfo[0]);
            		return;
            	}
    /*        	else { 
            		_logger.error("Can't get user object in authentication. URL:" + requestContext.getUriInfo().getPath());
                    requestContext.abortWith(ACCESS_DENIED);
            	}
                return ; */
            }
        } catch (SecurityException | UnauthorizedOperationException e2 ) {
            _logger.info("Failed to authenticate a user: " + (authInfo == null? "": authInfo[0]) + " Path:" +
            		requestContext.getUriInfo().getPath(), e2);
             requestContext.abortWith(ACCESS_DENIED);
             return;
        } catch (ObjectNotFoundException e0) {
            _logger.info("User: " + authInfo[0] +" not found in Ndex db." /*requestContext.getUriInfo().getPath()*/);
            String mName = method.getName();
            if ( !mName.equals("createUser")) {
                requestContext.abortWith(ACCESS_DENIED_USER_NOT_FOUND);
                return;
            }
        } catch (Exception e) {
            if (authInfo != null && authInfo.length >= 2 && (! authenticated))
                _logger.error("Failed to authenticate a user: " + authInfo[0] /*+ " Path:"+ requestContext.getUriInfo().getPath() */, e);
            else {
                _logger.error("Failed to authenticate a user; credential information unknown: " + e.getMessage() );
                e.printStackTrace();
            }
           requestContext.abortWith(ACCESS_DENIED);
           return ;
        } 
        
        if ( authenticatedUserOnly) {
        	if ( !method.isAnnotationPresent(NdexOpenFunction.class) ) {
                _logger.warn(" attempted to access a resource for which requires authentication.");
                requestContext.abortWith(ACCESS_DENIED);
        	}
        } else if (!method.isAnnotationPresent(PermitAll.class)) {
            if (method.isAnnotationPresent(DenyAll.class))
            {
                requestContext.abortWith(FORBIDDEN);
                return;
            }
            
            if (authInfo == null)
            {
                _logger.warn("No credentials to authenticate.");
                requestContext.abortWith(FORBIDDEN);
                return;
            }
            
            if (authUser == null)
            {
                _logger.warn(authInfo[0] + " attempted to access a resource for which they were denied.");
                requestContext.abortWith(ACCESS_DENIED);
            }
        }
        
      
    }
    
    /**************************************************************************
    * Base64-decodes and parses the Authorization header to get the username
    * and password.
    * 
    * @param requestContext
    *            The servlet HTTP request context.
    * @throws IOException
    *            Decoding the Authorization header failed.
    * @return a String array containing the username and password.
     * @throws UnauthorizedOperationException 
    **************************************************************************/
    private static String[] parseCredentials(ContainerRequestContext requestContext) throws IOException, UnauthorizedOperationException
    {
        final MultivaluedMap<String, String> headers = requestContext.getHeaders();
        final List<String> authHeader = headers.get("Authorization");
        
        if (authHeader == null || authHeader.isEmpty())
            return null;

        if ( authHeader.get(0) == null )
        	throw new UnauthorizedOperationException("Authorization value was null in HTTP request header.");
        
        if ( ! authHeader.get(0).startsWith(basicAuthPrefix))
        	throw new UnauthorizedOperationException("Authorization is not using Basic auth.");
        
        final String encodedAuthInfo = authHeader.get(0).substring(basicAuthPrefix.length());
        final String decodedAuthInfo = new String(Base64.decode(encodedAuthInfo));
        
        String[] result = new String[2];
        
        int idx = decodedAuthInfo.indexOf(":");
        
        if ( idx == -1) 
        	throw new UnauthorizedOperationException("Malformed authorization value in HTTP request header.");
        
        result[0] = decodedAuthInfo.substring(0, idx);
        result[1] = decodedAuthInfo.substring(idx+1);
        return result; //decodedAuthInfo.split(":");
    }
    

    private boolean authenticate (String authenticationString) throws IOException {
    	if (authenticationString.startsWith("Basic ")) {
    		
    	} else if ( authenticationString.startsWith("SAML ")) {
    		String encodedAuthInfo = authenticationString.replaceFirst("SAML " + " ", "");
    		String decodedAuthInfo = new String(Base64.decode(encodedAuthInfo));
    		
    		/*
    		SAMLSignatureProfileValidator profileValidator = new SAMLSignatureProfileValidator();
    		 
    		profileValidator.validate(entityDescriptor.getSignature());
    		 
    		SignatureValidator sigValidator = new SignatureValidator(cred);
    		 
    		sigValidator.validate(entityDescriptor.getSignature()); */
    	}
    	return true;
    }
    
    
}
