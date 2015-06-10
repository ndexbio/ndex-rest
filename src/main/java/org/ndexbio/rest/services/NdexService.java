/**
 *   Copyright (c) 2013, 2015
 *  	The Regents of the University of California
 *  	The Cytoscape Consortium
 *
 *   Permission to use, copy, modify, and distribute this software for any
 *   purpose with or without fee is hereby granted, provided that the above
 *   copyright notice and this permission notice appear in all copies.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 *   WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 *   MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 *   ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 *   WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 *   ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 *   OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */
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

import org.ndexbio.model.object.RestResource;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.annotations.ApiDoc;
import org.slf4j.Logger;
import org.slf4j.MDC;

public abstract class NdexService
{
	public static final String NdexZipFlag = "NdexZipped";
	
    private HttpServletRequest _httpRequest;
    private String threadId;
    
    /**************************************************************************
    * Injects the HTTP request into the base class to be used by
    * getLoggedInUser(). 
    * 
    * @param httpRequest The HTTP request injected by RESTEasy's context.
    **************************************************************************/
    public NdexService(HttpServletRequest httpRequest) {
        _httpRequest = httpRequest;
        
        // we need to log thread id.  
        // The parameter ThreadId can be accessed in <pattern> element from logback.xml like this: %X{ThreadId} 
        // The MDC manages contextual information on a per thread basis.  Typically, while starting to service a new client request, 
        // the developer will insert pertinent contextual information, such as the client id, client's IP address, request parameters etc. into the MDC. 
        // Logback components, if appropriately configured, will automatically include this information in each log entry.
        // See http://logback.qos.ch/manual/mdc.html for more info.
        this.threadId =  String.valueOf(Thread.currentThread().getId());
        MDC.put("ThreadId", this.threadId);
    }
    
    /**************************************************************************
    * Gets API information for the service.
    **************************************************************************/
    @GET
    @PermitAll
    @Path("/api")
	@NdexOpenFunction
    @Produces("application/json")
    @ApiDoc("Retrieves the REST API documentation for network related operations as a list of RestResource objects.")
    public Collection<RestResource> getApi()
    {
        final Collection<RestResource> resourceList = new ArrayList<>();
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
                	resource.setAuthentication(false);
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
    
    protected void setZipFlag() {
    	_httpRequest.setAttribute(NdexZipFlag, Boolean.TRUE);
    }

    protected String userNameForLog () {
    	final Object user = _httpRequest.getAttribute("User");
    	return (user != null) ? ("[" + ((org.ndexbio.model.object.User)user).getAccountName() + "]\t") : "[anonymous]\t" ;
    }  
    
    protected void logInfo (Logger logger, String message) {
    	final Object user = _httpRequest.getAttribute("User");
    	
    	String userPrefix = (user != null) ?
            "[USER:"+ ((org.ndexbio.model.object.User)user).getAccountName()+ "]\t": 
            	"[ANONYMOUS-USER]\t";
    	
    	logger.info(userPrefix + message);
    }
   
    protected String getThreadId() {
    	return this.threadId;
    }
}
