package org.ndexbio.rest.services;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import org.ndexbio.rest.NdexSchemaManager;
import org.ndexbio.rest.domain.IBaseTerm;
import org.ndexbio.rest.domain.IFunctionTerm;
import org.ndexbio.rest.domain.IGroup;
import org.ndexbio.rest.domain.IGroupInvitationRequest;
import org.ndexbio.rest.domain.IGroupMembership;
import org.ndexbio.rest.domain.IJoinGroupRequest;
import org.ndexbio.rest.domain.INetworkAccessRequest;
import org.ndexbio.rest.domain.INetworkMembership;
import org.ndexbio.rest.domain.IUser;
import org.ndexbio.rest.exceptions.NdexException;
import org.ndexbio.rest.helpers.Configuration;
import org.ndexbio.rest.models.User;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentPool;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.blueprints.impls.orient.OrientGraph;
import com.tinkerpop.frames.FramedGraph;
import com.tinkerpop.frames.FramedGraphFactory;
import com.tinkerpop.frames.modules.gremlingroovy.GremlinGroovyModule;
import com.tinkerpop.frames.modules.typedgraph.TypedGraphModuleBuilder;

public abstract class NdexService
{
    private HttpServletRequest _httpRequest;
    
    protected FramedGraphFactory _graphFactory = null;
    protected ODatabaseDocumentTx _ndexDatabase = null;
    protected FramedGraph<OrientBaseGraph> _orientDbGraph = null;
    
    
    
    /**************************************************************************
    * Injects the HTTP request into the base class to be used by
    * getLoggedInUser(). 
    * 
    * @param httpRequest The HTTP request injected by RESTEasy's context.
    **************************************************************************/
    public NdexService(HttpServletRequest httpRequest)
    {
        _httpRequest = httpRequest;
    }
    

    
    /**************************************************************************
    * Gets API information for the service.
    **************************************************************************/
    @GET
    @PermitAll
    @Path("/api")
    @Produces("application/json")
    public Collection<Collection<String>> getApi()
    {
        final Collection<Collection<String>> methodAnnotationList = new ArrayList<Collection<String>>();
        for (Method method : this.getClass().getMethods())
        {
            final Collection<String> methodAnnotationStrings = new ArrayList<String>();
            for (Annotation annotation : method.getDeclaredAnnotations())
                methodAnnotationStrings.add(annotation.toString());
            
            if (methodAnnotationStrings.size() > 0)
                methodAnnotationList.add(methodAnnotationStrings);
        }
    
        return methodAnnotationList;
    }

    /**************************************************************************
    * Gets status for the service.
    **************************************************************************/
    @GET
    @PermitAll
    @Path("/status")
    @Produces("application/json")
    public String getStatus() throws NdexException
    {
        return "RUNNING";
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
            return (User)user;
        else
            return null;
    }

    /**************************************************************************
    * Opens a connection to OrientDB and initializes the OrientDB Graph ORM.
    **************************************************************************/
    protected void setupDatabase()
    {
        //When starting up this application, tell OrientDB's global
        //configuration to close the storage; this is required here otherwise
        //OrientDB connection pooling doesn't work as expected
        //OGlobalConfiguration.STORAGE_KEEP_OPEN.setValue(false);

        _graphFactory = new FramedGraphFactory(new GremlinGroovyModule(),
            new TypedGraphModuleBuilder()
                .withClass(IGroup.class)
                .withClass(IUser.class)
                .withClass(IGroupMembership.class)
                .withClass(INetworkMembership.class)
                .withClass(IGroupInvitationRequest.class)
                .withClass(IJoinGroupRequest.class)
                .withClass(INetworkAccessRequest.class)
                .withClass(IBaseTerm.class)
                .withClass(IFunctionTerm.class).build());

        _ndexDatabase = ODatabaseDocumentPool.global().acquire(
            Configuration.getInstance().getProperty("OrientDB-URL"),
            Configuration.getInstance().getProperty("OrientDB-Username"),
            Configuration.getInstance().getProperty("OrientDB-Password"));
        
        _orientDbGraph = _graphFactory.create((OrientBaseGraph)new OrientGraph(_ndexDatabase));
        NdexSchemaManager.INSTANCE.init(_orientDbGraph.getBaseGraph());
    }
    
    /**************************************************************************
    * Cleans up the OrientDB resources. These steps are all necessary or
    * OrientDB connections won't be released from the pool.
    **************************************************************************/
    protected void teardownDatabase()
    {
        if (_graphFactory != null)
            _graphFactory = null;
        
        if (_ndexDatabase != null)
        {
            _ndexDatabase.close();
            _ndexDatabase = null;
        }
        
        if (_orientDbGraph != null)
        {
            _orientDbGraph.shutdown();
            _orientDbGraph = null;
        }
    }
}
