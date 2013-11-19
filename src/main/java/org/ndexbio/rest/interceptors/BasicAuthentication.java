package org.ndexbio.rest.interceptors;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.ext.Provider;
import org.jboss.resteasy.annotations.interception.ServerInterceptor;
import org.jboss.resteasy.core.Headers;
import org.jboss.resteasy.core.ResourceMethodInvoker;
import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.spi.Failure;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.interception.PreProcessInterceptor;
import org.jboss.resteasy.util.Base64;
import org.ndexbio.rest.domain.XBaseTerm;
import org.ndexbio.rest.domain.XFunctionTerm;
import org.ndexbio.rest.domain.XTerm;
import org.ndexbio.rest.domain.XUser;
import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentPool;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.exception.ODatabaseException;
import com.orientechnologies.orient.core.exception.OSecurityAccessException;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.OCommandSQL;
import com.orientechnologies.orient.server.OServer;
import com.orientechnologies.orient.server.OServerMain;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.blueprints.impls.orient.OrientGraph;
import com.tinkerpop.frames.FramedGraph;
import com.tinkerpop.frames.FramedGraphFactory;
import com.tinkerpop.frames.modules.gremlingroovy.GremlinGroovyModule;
import com.tinkerpop.frames.modules.typedgraph.TypedGraphModuleBuilder;

@Provider
@ServerInterceptor
public class BasicAuthentication implements PreProcessInterceptor
{
    private static final String AUTHORIZATION_PROPERTY = "Authorization";
    private static final String AUTHENTICATION_SCHEME = "Basic";
    private static final ServerResponse ACCESS_DENIED = new ServerResponse("Invalid username or password.", 401, new Headers<Object>());
    private static final ServerResponse ACCESS_FORBIDDEN = new ServerResponse("Forbidden.", 403, new Headers<Object>());
    private static final ServerResponse SERVER_ERROR = new ServerResponse("Internal server error.", 500, new Headers<Object>());

    @Override
    public ServerResponse preProcess(HttpRequest httpRequest, ResourceMethodInvoker methodInvoked) throws Failure, WebApplicationException
    {
        Method method = methodInvoked.getMethod();

        if (method.isAnnotationPresent(PermitAll.class))
            return null;
        else if (method.isAnnotationPresent(DenyAll.class))
            return ACCESS_FORBIDDEN;

        try
        {
            String[] authInfo = getAuthInfo(httpRequest);
            if (authenticateUser(authInfo))
                return null;
            else
                return ACCESS_DENIED;
        }
        catch (Exception e)
        {
            return SERVER_ERROR;
        }
    }

    
    
    /**************************************************************************
    * Authenticates the user against the OrientDB database.
    * 
    * @param authInfo A string array containing the username/password.
    * @throws Exception Various exceptions if accessing the database fails.
    * @returns True if the user is authenticated, false otherwise.
    **************************************************************************/
    private boolean authenticateUser(final String[] authInfo) throws Exception
    {
        final OServer orientDbServer = OServerMain.create();
        orientDbServer.startup();
        orientDbServer.activate();
        
        final FramedGraphFactory graphFactory = new FramedGraphFactory(new GremlinGroovyModule(),
            new TypedGraphModuleBuilder()
            .withClass(XTerm.class)
            .withClass(XFunctionTerm.class)
            .withClass(XBaseTerm.class)
            .build());

        ODatabaseDocumentTx ndexDatabase = null;
        try
        {
            //TODO: Refactor this to connect using a configurable username/password, and database
            ndexDatabase = ODatabaseDocumentPool.global().acquire("plocal:/ndex", "admin", "admin");
            final FramedGraph<OrientBaseGraph> orientDbGraph = graphFactory.create((OrientBaseGraph)new OrientGraph(ndexDatabase));

            Collection<ODocument> usersFound = ndexDatabase.command(new OCommandSQL("select from xUser where username equals " + authInfo[0])).execute();
            if (usersFound.size() < 1)
                return false;
            
            XUser authUser = orientDbGraph.getVertex(usersFound.toArray()[0], XUser.class);
            
            if (authInfo[1] != authUser.getPassword())
                return false;

            return true;
        }
        catch (Exception e)
        {
            OLogManager.instance().error(this, "Cannot access database: " + "ndex" + ".", ODatabaseException.class, e);
        }
        finally
        {
            orientDbServer.shutdown();
        }

        return false;
    }
    
    /**************************************************************************
    * Base64 decodes the authentication header and splits the username and
    * password.
    * 
    * @param httpRequest The HTTP request.
    * @throws IOException Failure to Base64-decode the authentication info.
    * @returns A string array containing the username/password.
    **************************************************************************/
    private String[] getAuthInfo(HttpRequest httpRequest) throws IOException
    {
        final HttpHeaders requestHeaders = httpRequest.getHttpHeaders();
        final List<String> authHeader = requestHeaders.getRequestHeader(AUTHORIZATION_PROPERTY);

        if (authHeader == null || authHeader.isEmpty())
            throw new SecurityException("Invalid username or password.");

        final String encodedAuthInfo = authHeader.get(0).replaceFirst(AUTHENTICATION_SCHEME + " ", "");
        String decodedAuthInfo;
        decodedAuthInfo = new String(Base64.decode(encodedAuthInfo));

        return decodedAuthInfo.split(":");
    }
}
