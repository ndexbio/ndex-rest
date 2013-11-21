package org.ndexbio.rest;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Application;

import org.ndexbio.rest.services.GroupService;
import org.ndexbio.rest.services.RequestService;
import org.ndexbio.rest.services.TaskService;
import org.ndexbio.rest.services.UserService;

public class NdexRestApi extends Application
{
    private final Set<Object> _providers = new HashSet<Object>();
    private final Set<Class<?>> _resources = new HashSet<Class<?>>();
    
    
    
    public NdexRestApi()
    {
    	_resources.add(GroupService.class); 
        _resources.add(UserService.class); 
        _resources.add(RequestService.class);
        _resources.add(TaskService.class); 
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
