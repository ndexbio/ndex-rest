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
package org.ndexbio.rest;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Application;

import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.rest.exceptions.mappers.DuplicateObjectExceptionMapper;
import org.ndexbio.rest.exceptions.mappers.NdexExceptionMapper;
import org.ndexbio.rest.exceptions.mappers.ObjectNotFoundExceptionMapper;
import org.ndexbio.rest.exceptions.mappers.UnauthorizedOperationExceptionMapper;
import org.ndexbio.rest.filters.BasicAuthenticationFilter;
import org.ndexbio.rest.filters.CrossOriginResourceSharingFilter;
import org.ndexbio.rest.filters.NdexPreZippedInterceptor;
import org.ndexbio.rest.services.AdminService;
import org.ndexbio.rest.services.GroupService;
import org.ndexbio.rest.services.NetworkAService;
import org.ndexbio.rest.services.RequestService;
import org.ndexbio.rest.services.TaskService;
import org.ndexbio.rest.services.UserService;

public class NdexRestApi extends Application
{
    private final Set<Object> _providers = new HashSet<>();
    private final Set<Class<?>> _resources = new HashSet<>();
        
    public NdexRestApi() throws NdexException
    {
    	_resources.add(GroupService.class); 
        _resources.add(UserService.class); 
        _resources.add(RequestService.class);
        _resources.add(TaskService.class); 
        _resources.add(NetworkAService.class);
        _resources.add(AdminService.class);
        
        _providers.add(new BasicAuthenticationFilter());
        _providers.add(new CrossOriginResourceSharingFilter());
        _providers.add(new DuplicateObjectExceptionMapper());
        _providers.add(new NdexExceptionMapper());
        _providers.add(new ObjectNotFoundExceptionMapper());
        _providers.add(new UnauthorizedOperationExceptionMapper());
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
