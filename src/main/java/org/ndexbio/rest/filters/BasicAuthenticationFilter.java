package org.ndexbio.rest.filters;

import java.lang.reflect.Method;
import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.ext.Provider;
import org.jboss.resteasy.core.Headers;
import org.jboss.resteasy.core.ResourceMethodInvoker;
import org.jboss.resteasy.core.ServerResponse;
import org.ndexbio.rest.helpers.Security;
import org.ndexbio.rest.models.User;
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
    private static final ServerResponse ACCESS_DENIED = new ServerResponse("Invalid username or password.", 401, new Headers<Object>());
    private static final ServerResponse FORBIDDEN = new ServerResponse("Forbidden.", 403, new Headers<Object>());

    
    
    @Override
    public void filter(ContainerRequestContext requestContext)
    {
        final ResourceMethodInvoker methodInvoker = (ResourceMethodInvoker)requestContext.getProperty("org.jboss.resteasy.core.ResourceMethodInvoker");
        final Method method = methodInvoker.getMethod();

        String[] authInfo = null;
        User authUser = null;
        try
        {
            authInfo = Security.parseCredentials(requestContext);
            authUser = Security.authenticateUser(authInfo);
            if (authUser != null)
                requestContext.setProperty("User", authUser);
        }
        catch (Exception e)
        {
            if (authInfo != null && authInfo.length >= 2)
                _logger.error("Failed to authenticate a user: " + authInfo[0] + "/" + authInfo[1] + ".", e);
            else
                _logger.error("Failed to authenticate a user; credential information unknown.");
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
}
