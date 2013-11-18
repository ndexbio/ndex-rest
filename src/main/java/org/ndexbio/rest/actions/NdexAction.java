package org.ndexbio.rest.actions;

import com.orientechnologies.common.concur.lock.OLockException;
import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.exception.OConcurrentModificationException;
import com.orientechnologies.orient.core.exception.ODatabaseException;
import com.orientechnologies.orient.core.exception.OSecurityAccessException;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.serialization.serializer.OStringSerializerHelper;
import com.orientechnologies.orient.core.sql.OCommandSQL;
import com.orientechnologies.orient.server.network.protocol.http.OHttpRequest;
import com.orientechnologies.orient.server.network.protocol.http.OHttpRequestException;
import com.orientechnologies.orient.server.network.protocol.http.OHttpResponse;
import com.orientechnologies.orient.server.network.protocol.http.OHttpSession;
import com.orientechnologies.orient.server.network.protocol.http.OHttpSessionManager;
import com.orientechnologies.orient.server.network.protocol.http.OHttpUtils;
import com.orientechnologies.orient.server.network.protocol.http.command.OServerCommandAuthenticatedDbAbstract;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.blueprints.impls.orient.OrientGraph;
import com.tinkerpop.frames.FramedGraph;
import com.tinkerpop.frames.FramedGraphFactory;
import com.tinkerpop.frames.modules.gremlingroovy.GremlinGroovyModule;
import com.tinkerpop.frames.modules.typedgraph.TypedGraphModuleBuilder;
import org.ndexbio.rest.NdexSchemaManager;
import org.ndexbio.rest.domain.XBaseTerm;
import org.ndexbio.rest.domain.XFunctionTerm;
import org.ndexbio.rest.domain.XTerm;
import org.ndexbio.rest.domain.XUser;
import org.ndexbio.rest.exceptions.NdexException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/******************************************************************************
* Handles the basic plumbing for all requests made by the NDEx web site.
* Requests are separated into the following phases:
* 
* <ul>
*   <li>Authentication</li>
*   <li>Session Verification</li>
*   <li>Request Parsing</li>
*   <li>Action Execution</li>
*   <li>Response Preparation</li>
* </ul>
******************************************************************************/
public abstract class NdexAction<T extends NdexAction.Context> extends OServerCommandAuthenticatedDbAbstract
{
    /**************************************************************************
    * The request context - holds parameters for the request.
    **************************************************************************/
    public interface Context
    {
    }

    private static final Object commandLock = new Object();
    private static final FramedGraphFactory GRAPH_FACTORY = new FramedGraphFactory(new GremlinGroovyModule(), new TypedGraphModuleBuilder().withClass(XTerm.class).withClass(XFunctionTerm.class).withClass(XBaseTerm.class).build());



    /**************************************************************************
    * The action to execute.
    * 
    * @param context The context.
    * @param graph   The OrientDB graph.
    **************************************************************************/
    protected abstract void action(T context, FramedGraph<OrientBaseGraph> graph);

    /**************************************************************************
    * Gets the description of the action.
    **************************************************************************/
    protected abstract String getDescription();

    /**************************************************************************
    * Parses the request.
    * 
    * @param httpRequest The HTTP request.
    * @throws IOException
    **************************************************************************/
    protected abstract T parseRequest(OHttpRequest httpRequest) throws IOException;

    /**************************************************************************
    * Serializes the result to JSON.
    * 
    * @param context The context.
    * @throws Exception
    **************************************************************************/
    protected abstract Object serializeResult(T context) throws Exception;



    /**************************************************************************
    * Authenticates the user's credentials.
    * 
    * @param httpRequest  The HTTP request.
    * @param httpResponse The HTTP response.
    * @param authInfo     The user's credentials.
    * @param databaseName The database name.
    * @throws IOException
    **************************************************************************/
    @Override
    protected boolean authenticate(final OHttpRequest httpRequest, final OHttpResponse httpResponse, final List<String> authInfo, final String databaseName) throws IOException
    {
        ODatabaseDocumentTx database = null;

        try
        {
            //TODO: Refactor this to connect using a configurable username/password
            database = (ODatabaseDocumentTx) server.openDatabase("graph", databaseName, "admin", "admin");
            
            Collection<ODocument> usersFound = database.command(new OCommandSQL("select from xUser where username equals " + authInfo.get(0))).execute();

            if (usersFound.size() < 1)
                throw new OSecurityAccessException("Invalid username or password.");
            
            FramedGraph<OrientBaseGraph> orientDbGraph = GRAPH_FACTORY.create((OrientBaseGraph) new OrientGraph(getProfiledDatabaseInstance(httpRequest)));
            XUser authUser = orientDbGraph.getVertex(usersFound.toArray()[0], XUser.class);
            if (authInfo.get(1) != authUser.getPassword())
                throw new OSecurityAccessException("Invalid username or password.");

            return true;
        }
        catch (OSecurityAccessException e)
        {
        }
        catch (OLockException e)
        {
            OLogManager.instance().error(this, "Cannot access database: " + databaseName + ".", ODatabaseException.class, e);
        }
        catch (InterruptedException e)
        {
            OLogManager.instance().error(this, "Cannot access database: " + databaseName + ".", ODatabaseException.class, e);
        }
        finally
        {
            if (database != null)
                database.close();

            sendAuthorizationRequest(httpRequest, httpResponse, databaseName);
        }

        return false;
    }

    /**************************************************************************
    * Validates the database and command, authenticates the user, and verifies
    * the user's session.
    * 
    * @param httpRequest  The HTTP request.
    * @param httpResponse The HTTP response.
    * @throws IOException
    **************************************************************************/
    @Override
    public boolean beforeExecute(final OHttpRequest httpRequest, OHttpResponse httpResponse) throws IOException
    {
        final String[] orientDbParameters = httpRequest.url.substring(1).split("/");
        
        System.out.println();
        System.out.println("URL: " + httpRequest.url);
        System.out.println("URL.substring(1): " + httpRequest.url.substring(1));
        System.out.println("Param 0: " + orientDbParameters[0]);
        System.out.println("Param 1: " + orientDbParameters[1]);
        System.out.println("Param 2: " + orientDbParameters[2]);
        System.out.println();
        
        if (orientDbParameters.length < 2)
            throw new OHttpRequestException("OrientDB URL syntax error. Expected URL format: database/command[/...].");
        else
            httpRequest.databaseName = orientDbParameters[0];

        final List<String> authInfo = new ArrayList<String>(2);
        if (httpRequest.authorization != null)
        {
            authInfo.add(httpRequest.authorization.split(":")[0]);
            authInfo.add(httpRequest.authorization.split(":")[1]);
        }

        return authenticate(httpRequest, httpResponse, authInfo, httpRequest.databaseName);
    }

    @Override
    protected String[] checkSyntax(final String url, final int argumentCount, final String commandSyntax)
    {
        System.out.println();
        System.out.println("URL: " + url);
        
        final List<String> parts = OStringSerializerHelper.smartSplit(url, OHttpResponse.URL_SEPARATOR, 1, -1, true, true, false);
        
        System.out.println("Parts: " + parts);
        System.out.println();
        
        if (parts.size() < argumentCount)
          throw new OHttpRequestException(commandSyntax);

        final String[] array = new String[parts.size()];
        return parts.toArray(array);
      }

    /**************************************************************************
    * Executes all actions inside OrientDB transactions.
    * 
    * @throws Exception
    **************************************************************************/
    @Override
    public boolean execute(final OHttpRequest httpRequest, OHttpResponse httpResponse) throws Exception
    {
        httpRequest.data.commandInfo = getDescription();

        final T requestContext = parseRequest(httpRequest);

        int retries = 0;
        FramedGraph<OrientBaseGraph> orientDbGraph = null;
        while (true)
        {
            try
            {
                //Execute all actions against OrientDB in a transaction
                orientDbGraph = GRAPH_FACTORY.create((OrientBaseGraph) new OrientGraph(getProfiledDatabaseInstance(httpRequest)));
                NdexSchemaManager.INSTANCE.init(orientDbGraph.getBaseGraph());

                synchronized (commandLock)
                {
                    action(requestContext, orientDbGraph);
                    orientDbGraph.getBaseGraph().commit();
                }

                final Object actionResult = serializeResult(requestContext);
                httpResponse.send(OHttpUtils.STATUS_OK_CODE, OHttpUtils.STATUS_OK_DESCRIPTION, OHttpUtils.CONTENT_JSON, actionResult.toString(), null, true);
                break;
            }
            catch (OConcurrentModificationException e)
            {
                retries++;

                if (retries > 10)
                    throw e;
            }
            catch (Exception e)
            {
                if (orientDbGraph != null)
                    orientDbGraph.getBaseGraph().rollback();

                if (getNames().length > 0)
                    OLogManager.instance().error(this, "An exception occurred in action: " + getNames()[0] + ".", e);

                if (!tryHandleException(e, httpResponse))
                    throw e;
                else
                    break;
            }
            finally
            {
                if (orientDbGraph != null)
                    orientDbGraph.shutdown();
            }
        }

        return false;
    }

    /**************************************************************************
    * Sends the user an authentication request.
    * 
    * @param httpRequest  The HTTP request.
    * @param httpResponse The HTTP response.
    * @param databaseName The database name.
    * @throws IOException
    **************************************************************************/
    @Override
    protected void sendAuthorizationRequest(final OHttpRequest httpRequest, final OHttpResponse httpResponse, final String databaseName) throws IOException
    {
        httpRequest.sessionId = SESSIONID_UNAUTHORIZED;
        String header = null;

        if (httpRequest.authentication == null || httpRequest.authentication.equalsIgnoreCase("basic"))
            header = "WWW-Authenticate: Basic realm=\"OrientDB: " + httpRequest.databaseName + "\"";

        httpResponse.send(OHttpUtils.STATUS_AUTH_CODE, OHttpUtils.STATUS_AUTH_DESCRIPTION, OHttpUtils.CONTENT_TEXT_PLAIN, "401 Unauthorized", header, false);
    }



    /**************************************************************************
    * Returns HTTP error messages for all NdexExceptions.
    * 
    * @param e            The exception.
    * @param httpResponse The HTTP response.
    * @throws Exception
    **************************************************************************/
    private boolean tryHandleException(Exception e, OHttpResponse httpResponse) throws Exception
    {
        if (e instanceof NdexException)
        {
            NdexException ndexException = (NdexException) e;
            httpResponse.send(ndexException.getHttpStatus(), ndexException.getHttpStatusDescription(), OHttpUtils.CONTENT_TEXT_PLAIN, e.getMessage(), null, true);

            return true;
        }

        return false;
    }

    /**************************************************************************
    * Validates a user's session and authenticates them if necessary. Not used
    * at this time (everything is stored in the browser's local storage). Will
    * be useful if authentication is moved to Forms Authentication or OAuth.
    * 
    * @param httpRequest  The HTTP request.
    * @param httpResponse The HTTP response.
    * @param authInfo     The user's credentials.
    * @throws Exception 
    **************************************************************************/
    @SuppressWarnings("unused")
    private boolean validateSession(final OHttpRequest httpRequest, final OHttpResponse httpResponse, final List<String> authInfo) throws Exception
    {
        OHttpSession userSession = null;
        if (httpRequest.sessionId != null && httpRequest.sessionId.length() > 1)
            userSession = OHttpSessionManager.getInstance().getSession(httpRequest.sessionId);

        if (userSession == null)
        {
            if (httpRequest.authorization == null || SESSIONID_LOGOUT.equals(httpRequest.sessionId))
            {
                sendAuthorizationRequest(httpRequest, httpResponse, httpRequest.databaseName);
                return false;
            }
            else
                return authenticate(httpRequest, httpResponse, authInfo, httpRequest.databaseName);
        }
        else
        {
            if (!httpRequest.databaseName.equals(userSession.getDatabaseName()))
            {
                OLogManager.instance().warn(this, "%s is trying to access database: %s. They were originally authenticated against database %s.", httpRequest.getUser(), httpRequest.databaseName, userSession.getDatabaseName());

                sendAuthorizationRequest(httpRequest, httpResponse, httpRequest.databaseName);
                return false;
            }
            else if (authInfo != null && !authInfo.get(0).equals(userSession.getUserName()))
            {
                OLogManager.instance().warn(this, "Session %s is trying to access database: %s as user: %s. The session was originally authenticated with user: %s.", httpRequest.sessionId, httpRequest.databaseName, authInfo.get(0), userSession.getUserName());

                sendAuthorizationRequest(httpRequest, httpResponse, httpRequest.databaseName);
                return false;
            }

            return true;
        }
    }
}