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
import org.jboss.resteasy.core.Headers;
import org.jboss.resteasy.core.ResourceMethodInvoker;
import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.util.Base64;
import org.ndexbio.model.object.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.tinkerpop.blueprints.impls.orient.OrientGraphNoTx;

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
    private static final ServerResponse ACCESS_DENIED = new ServerResponse("Invalid username or password.", 401, new Headers<Object>());
    private static final ServerResponse FORBIDDEN = new ServerResponse("Forbidden.", 403, new Headers<Object>());

    
    
    @Override
    public void filter(ContainerRequestContext requestContext)
    {
        final ResourceMethodInvoker methodInvoker = (ResourceMethodInvoker)requestContext.getProperty("org.jboss.resteasy.core.ResourceMethodInvoker");
        final Method method = methodInvoker.getMethod();
        NdexDatabase database = null; //not best coding practice? done to avoid compilation errors

        String[] authInfo = null;
        User authUser = null;
        try
        {
        	database = new NdexDatabase();
        	ODatabaseDocumentTx localConnection = database.getTransactionConnection();
        	OrientGraphNoTx graph = new OrientGraphNoTx(localConnection);
        	final UserDAO dao = new UserDAO(localConnection, graph);
        	
            authInfo = parseCredentials(requestContext);
            if(authInfo != null)
            	authUser = dao.authenticateUser(authInfo[0],authInfo[1]);
            if (authUser != null)
                requestContext.setProperty("User", authUser);
        }
        catch (Exception e)
        {
            if (authInfo != null && authInfo.length >= 2)
                _logger.error("Failed to authenticate a user: " + authInfo[0] + "/" + authInfo[1] + ".", e);
            else
                _logger.error("Failed to authenticate a user; credential information unknown.");
        } finally 
        {
        	database.close();
        }
        
        if (!method.isAnnotationPresent(PermitAll.class))
        {
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
    public static String[] parseCredentials(ContainerRequestContext requestContext) throws IOException
    {
        final MultivaluedMap<String, String> headers = requestContext.getHeaders();
        final List<String> authHeader = headers.get("Authorization");
        
        if (authHeader == null || authHeader.isEmpty())
            return null;

        final String encodedAuthInfo = authHeader.get(0).replaceFirst("Basic" + " ", "");
        final String decodedAuthInfo = new String(Base64.decode(encodedAuthInfo));
        
        return decodedAuthInfo.split(":");
    }
    

}
