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
import org.ndexbio.common.exceptions.ObjectNotFoundException;
import org.jboss.resteasy.core.Headers;
import org.jboss.resteasy.core.ResourceMethodInvoker;
import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.util.Base64;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.services.NdexOpenFunction;
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
    private static final Logger _logger = LoggerFactory.getLogger(BasicAuthenticationFilter.class);
    private static final ServerResponse ACCESS_DENIED = new ServerResponse("Invalid username or password.", 401, new Headers<>());
    private static final ServerResponse ACCESS_DENIED_USER_NOT_FOUND = 
    		new ServerResponse("User not found.", 401, new Headers<>());
    private static final ServerResponse FORBIDDEN = new ServerResponse("Forbidden.", 403, new Headers<>());
    private static LDAPAuthenticator ADAuthenticator = null;
    private boolean authenticatedUserOnly = false;
    private static final String AUTHENTICATED_USER_ONLY="AUTHENTICATED_USER_ONLY";
    
    public BasicAuthenticationFilter() throws NdexException {
    	super();
    	
    	Configuration config = Configuration.getInstance();
    	
    	if ( config.getUseADAuthentication() && ADAuthenticator == null) {
    		ADAuthenticator = new LDAPAuthenticator(Configuration.getInstance());
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
            if(authInfo != null) {  // server need to authenticate the user.
            	if (ADAuthenticator !=null ) {
            		if ( ADAuthenticator.authenticateUser(authInfo[0], authInfo[1]) ) {
            			authenticated = true;
            			_logger.info("User " + authInfo[0] + " authenticated by AD.");
                		try ( UserDAO dao = new UserDAO(NdexDatabase.getInstance().getAConnection()) ) {
                   		 authUser = dao.getUserByAccountName(authInfo[0]);
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
            		return;
            	}
    /*        	else { 
            		_logger.error("Can't get user object in authentication. URL:" + requestContext.getUriInfo().getPath());
                    requestContext.abortWith(ACCESS_DENIED);
            	}
                return ; */
            }
        } catch (SecurityException | UnauthorizedOperationException e2 ) {
            _logger.info("Failed to authenticate a user: " + authInfo[0] + " Path:" +
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
            else
                _logger.error("Failed to authenticate a user; credential information unknown.");
            
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
    **************************************************************************/
    private static String[] parseCredentials(ContainerRequestContext requestContext) throws IOException
    {
        final MultivaluedMap<String, String> headers = requestContext.getHeaders();
        final List<String> authHeader = headers.get("Authorization");
        
        if (authHeader == null || authHeader.isEmpty())
            return null;

        final String encodedAuthInfo = authHeader.get(0).replaceFirst("Basic" + " ", "");
        final String decodedAuthInfo = new String(Base64.decode(encodedAuthInfo));
        
        String[] result = new String[2];
        
        int idx = decodedAuthInfo.indexOf(":");
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
