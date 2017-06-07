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
package org.ndexbio.rest.filters;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.Provider;

import org.jboss.resteasy.core.ResourceMethodInvoker;
import org.jboss.resteasy.util.Base64;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
//import org.ndexbio.model.exceptions.ForbiddenOperationException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.services.AuthenticationNotRequired;
import org.ndexbio.rest.services.NdexOpenFunction;
import org.ndexbio.security.GoogleOpenIDAuthenticator;
import org.ndexbio.security.LDAPAuthenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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
	
	 @Context
	 private HttpServletRequest httpRequest;
	 
	private static final String basicAuthPrefix = "Basic "; 
	public static final String accessLoggerName = "accesslog";
	
    private static final Logger _logger = LoggerFactory.getLogger(BasicAuthenticationFilter.class);
    private static final Logger accessLogger = LoggerFactory.getLogger(accessLoggerName);

    //private static final ServerResponse ACCESS_DENIED = new ServerResponse("Invalid username or password.", 401, new Headers<>());
    //private static final ServerResponse ACCESS_DENIED_USER_NOT_FOUND = 
    //		new ServerResponse("User not found.", 401, new Headers<>());
    //private static final ServerResponse FORBIDDEN = new ServerResponse("Forbidden.", 403, new Headers<>());
    private static LDAPAuthenticator ADAuthenticator = null;
    private static GoogleOpenIDAuthenticator googleOAuthAuthenticator = null;
    
    private boolean authenticatedUserOnly = false;
    private static final String AUTHENTICATED_USER_ONLY="AUTHENTICATED_USER_ONLY";
    private static final String AD_CREATE_USER_AUTOMATICALLY="AD_CREATE_USER_AUTOMATICALLY";
    private static final String USE_GOOGLE_OAUTH = "USE_GOOGLE_AUTHENTICATION";
    
    public BasicAuthenticationFilter() throws NdexException {
    	super();
    	
    	Configuration config = Configuration.getInstance();
    	if ( config == null) {
    		System.err.println("Creating configure object !!!!");
    		config = Configuration.createInstance();
    	}
    	
    	if ( config.getUseADAuthentication() && ADAuthenticator == null) {
    		    ADAuthenticator = new LDAPAuthenticator(config);
    	}
    	String value = config.getProperty(AUTHENTICATED_USER_ONLY);
		_logger.info("authenticatedUserOnly setting is " + value);
		
		String useGoogleOAuth = config.getProperty(USE_GOOGLE_OAUTH);
		if ( useGoogleOAuth !=null && useGoogleOAuth.equalsIgnoreCase("true")) {
			googleOAuthAuthenticator = new GoogleOpenIDAuthenticator(config);
		}

    	if ( value !=null && Boolean.parseBoolean(value)) {
    		_logger.info("Server running in authenticatedUserOnly mode.");
    		authenticatedUserOnly = true;
    	}
    }
    
    public static LDAPAuthenticator getLDAPAuthenticator() {
    	return ADAuthenticator;
    }
    
    public static GoogleOpenIDAuthenticator getGoogleOAuthAuthenticatior() { return googleOAuthAuthenticator;}
    
    @Override
    public void filter(ContainerRequestContext requestContext)
    {
        final ResourceMethodInvoker methodInvoker = (ResourceMethodInvoker)requestContext.getProperty("org.jboss.resteasy.core.ResourceMethodInvoker");
        final Method method = methodInvoker.getMethod();
        
        String[] authInfo = null;
        User authUser = null;
        boolean authenticated = false;
        
		// write the log context
		MDC.put("RequestsUniqueId", 
				"tid:"+System.currentTimeMillis() + "-" + Thread.currentThread().getId());

		NdexException authorizationException = null;
        try
        {
            authInfo = parseCredentials(requestContext);

            if(authInfo != null) {  // server need to authenticate the user.

            	if (authInfo.length == 1) { // google OAuth for now
            		if ( googleOAuthAuthenticator == null) {
            			throw new NdexException("Google OAuth is not enabled in NDEx server.");
            		}
  
            		String token = authInfo[0].substring(7);
            	
            		try ( UserDAO dao = new UserDAO() ) {
            			UUID uuid = googleOAuthAuthenticator.GetUserUUIDFromAccessToke(token);
            			authUser = dao.getUserById(uuid,true,true);
            		}	
            	} else {
            
            		if (ADAuthenticator !=null ) {
            			if ( ADAuthenticator.authenticateUser(authInfo[0], authInfo[1]) ) {
            				authenticated = true;
            				_logger.debug("User {} authenticated by AD.", authInfo[0]);
            				try ( UserDAO dao = new UserDAO() ) {
            					try {
            						authUser = dao.getUserByAccountName(authInfo[0],true,true);
            					} catch (ObjectNotFoundException e) {
            						String autoCreateAccount = Configuration.getInstance().getProperty(AD_CREATE_USER_AUTOMATICALLY);
            						if ( autoCreateAccount !=null && Boolean.parseBoolean(autoCreateAccount)) {
            							User newUser = ADAuthenticator.getNewUser(authInfo[0], authInfo[1]);
            							authUser = dao.createNewUser(newUser,null);
            							dao.commit();
            						} else 
            							throw e;
            					}	
            				} 
            			}
            		} else {
            			authInfo[0] = authInfo[0].toLowerCase();
            			try ( UserDAO dao = new UserDAO() ) {
            				authUser = dao.authenticateUser(authInfo[0],authInfo[1]);
            			}
            		}
            	}
            	
            	if (authUser != null) {   // user is authenticated
            		requestContext.setProperty("User", authUser);
       
            	} else {   // not user can be found based on the credentials in the header.
            		authorizationException = new UnauthorizedOperationException("Credentials in HTTP head is invalid.");
            	}           	
            }
        } catch (SecurityException | UnauthorizedOperationException e2 ) {
            _logger.info("Failed to authenticate a user: " + (authInfo == null? "": authInfo[0]) + " Path:" +
            		requestContext.getUriInfo().getPath(), e2);
        	// instantiate NdexException exception, transform it to JSON, and send it back to the client 
            authorizationException = 
            		new UnauthorizedOperationException("Invalid password for user " + (authInfo == null? "": authInfo[0]) + ".");
        } catch (ObjectNotFoundException e0) {
            _logger.info("User: " + authInfo[0] +" not found in Ndex db." /*requestContext.getUriInfo().getPath()*/);
            String mName = method.getName();
            if ( !mName.equals("createUser")) {
            	// instantiate NdexException exception, transform it to JSON, and send it back to the client 
            	authorizationException = e0;
            }
        } catch (Exception e) {
            if (authInfo != null && authInfo.length >= 2 && (! authenticated)) {
                _logger.error("Failed to authenticate a user: " + authInfo[0] /*+ " Path:"+ requestContext.getUriInfo().getPath() */, e);
                authorizationException = new UnauthorizedOperationException("Failed to authenticate user " + authInfo[0] + " : " + e.getMessage());
            } else {
                _logger.error("Failed to authenticate a user; credential information unknown: " + e.getMessage() );
                authorizationException = new UnauthorizedOperationException("Failed to authenticate user; credential information unknown: "
                		 + e.getMessage() );
            }
        } 
         
        if ( authorizationException == null ) {  // so far so good 
        	if ( authUser != null ||    // is a authenticated user
         		  method.isAnnotationPresent(NdexOpenFunction.class) ||  // functions that are open to anonymous users
        				((!authenticatedUserOnly) && 
        						(  method.isAnnotationPresent(PermitAll.class)
        								|| method.isAnnotationPresent(AuthenticationNotRequired.class)) )) {
        			//log the info in log and continue;         		

                	accessLogger.info("[start]\t" + buildLogString(authUser,requestContext,method) );    
        			return;
        		}
       
        	authorizationException = new UnauthorizedOperationException(
            		"Attempted to access resource requiring authentication.");
        }
        
        requestContext.abortWith(
           			Response
                       .status(Status.UNAUTHORIZED)
                       .entity(authorizationException.getNdexExceptionInJason())
                       .header("WWW-Authenticate", "Basic")
                       .type("application/json")
                       .build()); 
        
    	MDC.put("error", authorizationException.getMessage());

		accessLogger.info("[start]\t" + buildLogString(authUser,requestContext,method) + "\t[Unauthorized exception: "+ authorizationException.getMessage() + "]"  );
    }
    
    private String buildLogString(User authUser, ContainerRequestContext requestContext, Method method ) {
        
    	String clientIPs = (null == httpRequest.getHeader("X-FORWARDED-FOR")) ? 
                httpRequest.getRemoteAddr() :
                httpRequest.getHeader("X-FORWARDED-FOR");
                
        UriInfo uriInfo = requestContext.getUriInfo();
        
        String userAgent = httpRequest.getHeader("User-Agent");
                
        String result =  "[" + requestContext.getMethod() + "]\t["+ (authUser == null? "" : authUser.getUserName()) + "]\t["
        		+ clientIPs.toString() + "]\t[" + (userAgent == null? "" : userAgent )+ "]\t[" + method.getName() + "]\t[" + 
       
        uriInfo.getPath(true)  + "]\t" ;
        
     //   ObjectMapper mapper = new ObjectMapper();
        MultivaluedMap<String,String> f = uriInfo.getQueryParameters();
        MultivaluedMap<String,String> f2 = uriInfo.getPathParameters();
        try {
        	result += "[" + buildJSONStringForLog(f) + "]\t[" + buildJSONStringForLog(f2) + "]";
		//	System.out.println(mapper.writeValueAsString(f) + mapper.writeValueAsString(f2));
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	return result;
    }
    
    private static String buildJSONStringForLog(MultivaluedMap<String,String> m) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        HashMap<String,String> m2 = new HashMap<>(m.size());
        for (Map.Entry<String,List<String>> e:  m.entrySet()) {
        	m2.put(e.getKey(), e.getValue() == null? "": e.getValue().get(0));
        }
        return mapper.writeValueAsString(m2).replaceAll("\n"," ");
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

        String authenticationStr =  authHeader.get(0); 
        if (  authenticationStr == null )
        	throw new UnauthorizedOperationException("Authorization value was null in HTTP request header.");
        
        if (  authenticationStr.startsWith(basicAuthPrefix)) {
        
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
        
        if ( authenticationStr.startsWith("Bearer ")) { // user OAuth 
            String[] result = new String[1];
            result [0] = authenticationStr;
            return result;
        }
        
        throw new UnauthorizedOperationException("Authorization is not using Basic auth.");
    }
    
/*
    private boolean authenticate (String authenticationString) throws IOException {
    	if (authenticationString.startsWith("Basic ")) {
    		
    	} else if ( authenticationString.startsWith("SAML ")) {
    		String encodedAuthInfo = authenticationString.replaceFirst("SAML " + " ", "");
    		String decodedAuthInfo = new String(Base64.decode(encodedAuthInfo));
    		
    		
    		SAMLSignatureProfileValidator profileValidator = new SAMLSignatureProfileValidator();
    		 
    		profileValidator.validate(entityDescriptor.getSignature());
    		 
    		SignatureValidator sigValidator = new SignatureValidator(cred);
    		 
    		sigValidator.validate(entityDescriptor.getSignature()); 
    	}
    	return true;
    }
    
    */
}
