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
/*package org.ndexbio.rest.filters;

import java.io.IOException;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.ext.Provider;

@Provider
public class CrossOriginResourceSharingFilter implements Filter
{
    @Override
    public void destroy()
    {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException
    {
        HttpServletResponse httpResponse = (HttpServletResponse)response;
        httpResponse.addHeader("Access-Control-Allow-Origin", "*");
        httpResponse.addHeader("Access-Control-Allow-Methods", "DELETE,GET,OPTIONS,POST,PUT");
        httpResponse.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, Content-Length, X-Requested-With");
        
        chain.doFilter(request, response);
    }

    @Override
    public void init(FilterConfig config) throws ServletException
    {
    }
}*/
package org.ndexbio.rest.filters;

import java.io.IOException;
import java.lang.reflect.Method;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.Provider;

import org.jboss.resteasy.core.ResourceMethodInvoker;
import org.ndexbio.rest.services.AuthenticationNotRequired;
import org.ndexbio.rest.services.NdexOpenFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@Provider
public class NdexDefaultResponseFilter implements ContainerResponseFilter //, Filter
{
	
	static Logger logger = LoggerFactory.getLogger(BasicAuthenticationFilter.accessLoggerName);

	//ContainerResponseFilter Implementation
	@Override
	public void filter(ContainerRequestContext arg0,
			ContainerResponseContext responseContext) throws IOException {

		MultivaluedMap<String, Object> headers = responseContext.getHeaders();
		headers.putSingle("Access-Control-Allow-Origin", "*");
		headers.putSingle("Access-Control-Allow-Methods", "HEAD, DELETE,GET,OPTIONS,POST,PUT");
		//headers.putSingle("Access-Control-Allow-Headers", "Accept, Content-Type, Authorization, Content-Length, X-Requested-With, NDEx-application");
		headers.putSingle("Access-Control-Allow-Credentials", Boolean.TRUE); 
	
		final ResourceMethodInvoker methodInvoker = (ResourceMethodInvoker)arg0.getProperty("org.jboss.resteasy.core.ResourceMethodInvoker");
	    if ( methodInvoker != null) {
	    	final Method method = methodInvoker.getMethod();
	      
	    	if (!method.isAnnotationPresent(AuthenticationNotRequired.class) && 
	    		  !method.isAnnotationPresent(NdexOpenFunction.class) && 
	    		  !BasicAuthenticationFilter.setAuthHeaderIsFalse(arg0)) {
	           	   headers.putSingle("WWW-Authenticate", "Basic");
	    	}
	    	
	    	int responseCode = responseContext.getStatus();
	    	
	    	String error = MDC.get("error");
	    	
	    	logger.info("[end]\t[status: " + responseCode + "]" + 
	    			(error !=null? "\t[error: "+ error + "]" : "" ));
	    	
	    	if ( error !=null)
	    		MDC.remove("error");
	    }
	    
	    
	}
	
/*	//Filter Implementation
	@Override
    public void destroy()
    {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException
    {
        HttpServletResponse httpResponse = (HttpServletResponse)response;
        httpResponse.addHeader("Access-Control-Allow-Origin", "*");
        httpResponse.addHeader("Access-Control-Allow-Methods", "HEAD, DELETE,GET,OPTIONS,POST,PUT");
        httpResponse.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, Content-Length, X-Requested-With");
        
        
        
        chain.doFilter(request, response);
    }

    @Override
    public void init(FilterConfig config) throws ServletException
    {
    } */
	
}

