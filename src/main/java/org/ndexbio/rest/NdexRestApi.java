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
package org.ndexbio.rest;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Application;

import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.rest.exceptions.mappers.DuplicateObjectExceptionMapper;
import org.ndexbio.rest.exceptions.mappers.NdexExceptionMapper;
import org.ndexbio.rest.exceptions.mappers.ObjectNotFoundExceptionMapper;
import org.ndexbio.rest.exceptions.mappers.UnauthorizedOperationExceptionMapper;
//import org.ndexbio.rest.exceptions.mappers.ForbiddenOperationExceptionMapper;
import org.ndexbio.rest.filters.BasicAuthenticationFilter;
import org.ndexbio.rest.filters.NdexDefaultResponseFilter;
import org.ndexbio.rest.filters.NdexPreZippedInterceptor;
import org.ndexbio.rest.services.AdminService;
import org.ndexbio.rest.services.AdminServiceV2;
import org.ndexbio.rest.services.BatchServiceV2;
import org.ndexbio.rest.services.GroupService;
import org.ndexbio.rest.services.GroupServiceV2;
import org.ndexbio.rest.services.NetworkService;
import org.ndexbio.rest.services.NetworkServiceV2;
import org.ndexbio.rest.services.RequestService;
import org.ndexbio.rest.services.SearchServiceV2;
import org.ndexbio.rest.services.TaskService;
import org.ndexbio.rest.services.TaskServiceV2;
import org.ndexbio.rest.services.UserService;
import org.ndexbio.rest.services.UserServiceV2;

public class NdexRestApi extends Application
{
    private final Set<Object> _providers = new HashSet<>();
    private final Set<Class<?>> _resources = new HashSet<>();
        
    public NdexRestApi() throws NdexException
    {
    	_resources.add(GroupService.class); 
    	_resources.add(GroupServiceV2.class); 
        _resources.add(UserService.class); 
        _resources.add(UserServiceV2.class); 
        _resources.add(RequestService.class);
        _resources.add(TaskService.class); 
        _resources.add(TaskServiceV2.class); 
        _resources.add(NetworkService.class);
        _resources.add(NetworkServiceV2.class);
        _resources.add(AdminService.class);
        _resources.add(AdminServiceV2.class);
        _resources.add(BatchServiceV2.class);
        _resources.add(SearchServiceV2.class);
        
        _providers.add(new BasicAuthenticationFilter());
        _providers.add(new NdexDefaultResponseFilter());
        _providers.add(new DuplicateObjectExceptionMapper());
        _providers.add(new NdexExceptionMapper());
        _providers.add(new ObjectNotFoundExceptionMapper());
        _providers.add(new UnauthorizedOperationExceptionMapper());
    //    _providers.add(new ForbiddenOperationExceptionMapper());
        _providers.add(new NdexPreZippedInterceptor());
    }
    
    
    
    @Override
    public Set<Class<?>> getClasses()
    {
        return _resources;
    }
    
    @Override
    public Set<Object> getSingletons()
    {
        return _providers;
    }
    
}
