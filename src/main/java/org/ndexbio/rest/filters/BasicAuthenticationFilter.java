package org.ndexbio.rest.filters;

import java.io.IOException;
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
    private static final ServerResponse ACCESS_DENIED = new ServerResponse("Invalid username or password.", 401, new Headers<Object>());
    private static final ServerResponse FORBIDDEN = new ServerResponse("Forbidden.", 403, new Headers<Object>());
    private static final ServerResponse SERVER_ERROR = new ServerResponse("Internal server error.", 500, new Headers<Object>());

    
    
    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException
    {
        final ResourceMethodInvoker methodInvoker = (ResourceMethodInvoker) requestContext.getProperty("org.jboss.resteasy.core.ResourceMethodInvoker");
        final Method method = methodInvoker.getMethod();
        
        if (!method.isAnnotationPresent(PermitAll.class))
        {
            if (method.isAnnotationPresent(DenyAll.class))
            {
                requestContext.abortWith(FORBIDDEN);
                return;
            }
            
            try
            {
                final String[] authInfo = Security.parseCredentials(requestContext);
                if (authInfo == null)
                {
                    requestContext.abortWith(FORBIDDEN);
                    return;
                }
                
                final User authUser = Security.authenticateUser(authInfo); 
                if (authUser == null)
                    requestContext.abortWith(ACCESS_DENIED);
                else
                    requestContext.setProperty("User", authUser);
            }
            catch (Exception e)
            {
                requestContext.abortWith(SERVER_ERROR);
                return;
            }
        }
    }
}
