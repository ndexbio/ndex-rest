package org.ndexbio.rest.services;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;

import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.ndexbio.common.exceptions.NdexException;
import org.ndexbio.common.helpers.Configuration;
import org.ndexbio.common.models.object.RestResource;
import org.ndexbio.model.object.User;
import org.ndexbio.orientdb.NdexSchemaManager;
import org.ndexbio.rest.annotations.ApiDoc;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentPool;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.blueprints.impls.orient.OrientGraph;
import com.tinkerpop.blueprints.impls.orient.OrientGraphNoTx;

public abstract class NdexService
{
    private HttpServletRequest _httpRequest;
    
//    protected FramedGraphFactory _graphFactory = null;
    protected ODatabaseDocumentTx _ndexDatabase = null;
 //   protected FramedGraph<OrientBaseGraph> _orientDbGraph = null;
    
    
    
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
    @ApiDoc("Retrieves the REST API documentation for the service as an array of RestResources")
    public Collection<RestResource> getApi()
    {
        final Collection<RestResource> resourceList = new ArrayList<RestResource>();
        Path serviceClassPathAnnotation = this.getClass().getAnnotation(Path.class);
        for (Method method : this.getClass().getMethods())
        {
        	RestResource resource = new RestResource();
        	resource.setMethodName(method.getName());

            for (Class<?> parameterType : method.getParameterTypes()){
            	resource.addParameterType(parameterType.getSimpleName());
            }
                       
            for (final Annotation annotation : method.getDeclaredAnnotations()){
            	if(annotation instanceof GET){
            		resource.setRequestType("GET");
                } else if (annotation instanceof PUT){
                	resource.setRequestType("PUT");
                } else if (annotation instanceof DELETE){
                	resource.setRequestType("DELETE");
                } else if (annotation instanceof POST){
                	resource.setRequestType("POST");
                }  else if (annotation instanceof Path){
                	Path pathAnnotation = (Path)annotation;
                	resource.setPath(serviceClassPathAnnotation.value() + pathAnnotation.value());
                }  else if (annotation instanceof Consumes){
                	Consumes consumesAnnotation = (Consumes)annotation;
                	if (consumesAnnotation.value() != null && consumesAnnotation.value().length > 0){
                		String[] consumes = consumesAnnotation.value();
                		resource.setConsumes(consumes[0]);
                	}
                }  else if (annotation instanceof Produces){
                	Produces producesAnnotation = (Produces)annotation;
                	if (producesAnnotation.value() != null && producesAnnotation.value().length > 0){
                		String[] produces = producesAnnotation.value();
                		resource.setProduces(produces[0]);
                	}
                } else if (annotation instanceof ApiDoc){
                	ApiDoc apiDocAnnotation = (ApiDoc)annotation;
                	resource.setApiDoc(apiDocAnnotation.value());
                } else if (annotation instanceof PermitAll){
                	resource.setAuthentication("PermitAll");
                } else {
                	// annotation class not handled
                	System.out.println(annotation.toString() + " not handled");
                }
                
            }
            
            if (resource.getPath() == null){
            	resource.setPath(serviceClassPathAnnotation.value());
            }
            if (resource.getRequestType() != null)
                resourceList.add(resource);
        }
    
        return resourceList;
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

    /**************************************************************************
    * Opens a connection to OrientDB and initializes the OrientDB Graph ORM.
     * @throws NdexException 
    **************************************************************************/
    protected void setupDatabase() throws NdexException
    {
        //When starting up this application, tell OrientDB's global
        //configuration to close the storage; this is required here otherwise
        //OrientDB connection pooling doesn't work as expected
        //OGlobalConfiguration.STORAGE_KEEP_OPEN.setValue(false);

   /*     _graphFactory = new FramedGraphFactory(new GremlinGroovyModule(),
            new TypedGraphModuleBuilder()
                .withClass(IGroup.class)
                .withClass(IUser.class)
                .withClass(IGroupMembership.class)
                .withClass(INetworkMembership.class)
                .withClass(IGroupInvitationRequest.class)
                .withClass(IJoinGroupRequest.class)
                .withClass(INetworkAccessRequest.class)
                .withClass(IBaseTerm.class)
                .withClass(IReifiedEdgeTerm.class)
                .withClass(IFunctionTerm.class).build());
*/
        _ndexDatabase = ODatabaseDocumentPool.global().acquire(
            Configuration.getInstance().getProperty("OrientDB-URL"),
            Configuration.getInstance().getProperty("OrientDB-Username"),
            Configuration.getInstance().getProperty("OrientDB-Password"));
        
  /*      if (Boolean.parseBoolean(Configuration.getInstance().getProperty("OrientDB-Use-Transactions")))
            _orientDbGraph = _graphFactory.create((OrientBaseGraph)new OrientGraph(_ndexDatabase));
        else
            _orientDbGraph = _graphFactory.create((OrientBaseGraph) new OrientGraphNoTx(_ndexDatabase));

    */    
        NdexSchemaManager.INSTANCE.init(_ndexDatabase);
    }
    
    /**************************************************************************
    * Cleans up the OrientDB resources. These steps are all necessary or
    * OrientDB connections won't be released from the pool.
    **************************************************************************/
    protected void teardownDatabase()
    {
      /*  if (_graphFactory != null)
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
        } */
    }
}
