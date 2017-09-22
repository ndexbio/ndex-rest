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
package org.ndexbio.rest.services;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.UUID;

import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.jboss.resteasy.util.Base64;
import org.ndexbio.model.object.RestResource;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.annotations.ApiDoc;
import org.ndexbio.security.GoogleOpenIDAuthenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public abstract class NdexService
{
	public static final String NdexZipFlag = "NdexZipped";
	
    protected HttpServletRequest _httpRequest;
    private String requestsUniqueId;
    private static GoogleOpenIDAuthenticator googleAuthtenticator = null;
    
	static Logger logger = LoggerFactory.getLogger(NdexService.class);
//	static DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS");
	
	private static final String basicAuthPrefix = "Basic ";
    
    /**************************************************************************
    * Injects the HTTP request into the base class to be used by
    * getLoggedInUser(). 
    * 
    * @param httpRequest The HTTP request injected by RESTEasy's context.
    **************************************************************************/
    public NdexService(HttpServletRequest httpRequest) {
        _httpRequest = httpRequest;
        
        
        // we need to log request id.  
        // The parameter UniqueRequestId can be accessed in <pattern> element from logback.xml like this: %X{UniqueRequestId} 
        // The MDC manages contextual information on a per thread basis.  Typically, while starting to service a new client request, 
        // the developer will insert pertinent contextual information, such as the client id, client's IP address, request parameters etc. into the MDC. 
        // Logback components, if appropriately configured, will automatically include this information in each log entry.
        // See http://logback.qos.ch/manual/mdc.html for more info.
        this.setRequestsUniqueId();
        MDC.put("RequestsUniqueId", this.getRequestsUniqueId());
        MDC.put("UserName", parseCredentials());
        
        // get IP address of the client
        // the argument for httpRequest.getHeader() method is case insensitive
        MDC.put("ClientIP",
                (null == httpRequest.getHeader("X-FORWARDED-FOR")) ? 
                    httpRequest.getRemoteAddr() :
                    httpRequest.getHeader("X-FORWARDED-FOR"));

  //      logger.info("[start: httpRequest received; stamped with {}]", this.getRequestsUniqueId());
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
		logger.info("[start: getApi()]");
		
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
                } else if ( annotation instanceof NdexOpenFunction ) {
                	resource.setIsOpenFunction(true);
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
 
		logger.info("[end: getApi()]");
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
    
    protected UUID getLoggedInUserId()
    {
        final Object user = _httpRequest.getAttribute("User");
        if (user != null)
            return ((org.ndexbio.model.object.User)user).getExternalId();
        
        return null;
    }
    
    protected void setZipFlag() {
    	_httpRequest.setAttribute(NdexZipFlag, Boolean.TRUE);
    }

    
    private String parseCredentials()
    {	
    	String authHeader = _httpRequest.getHeader("Authorization");
    	if (null == authHeader) {
    		return "anonymous";
    	}

    	String encodedAuthInfo = authHeader.substring(basicAuthPrefix.length());
        String decodedAuthInfo;
		try {
			decodedAuthInfo = new String(Base64.decode(encodedAuthInfo));
		} catch (IOException e) {
            return null;
		}
        
        int idx = decodedAuthInfo.indexOf(":");
        
        if (idx == -1) {
        	return null;
        }
        
        return decodedAuthInfo.substring(0, idx);
    }
     
    protected void logInfo (Logger locallogger, String message) {
    	final Object user = _httpRequest.getAttribute("User");
    	
    	String userPrefix = (user != null) ?
            "[USER:"+ ((org.ndexbio.model.object.User)user).getUserName()+ "]\t": 
            	"[ANONYMOUS-USER]\t";
    	
    	locallogger.info(userPrefix + message);
    }
   
    protected String getRequestsUniqueId() {
    	return this.requestsUniqueId;
    }

    private void setRequestsUniqueId() {
    	long currentSystemTimeInMs = System.currentTimeMillis();
    //	Calendar cal = Calendar.getInstance();
    //	cal.setTimeInMillis(currentSystemTimeInMs);
    	
    	this.requestsUniqueId = currentSystemTimeInMs + "-" + Thread.currentThread().getId();
    }
    
    protected static GoogleOpenIDAuthenticator getGoogleAuthenticator() {return googleAuthtenticator;}
    public static void setGoogleAuthenticator(GoogleOpenIDAuthenticator a) {
    	googleAuthtenticator = a;
    }
}
