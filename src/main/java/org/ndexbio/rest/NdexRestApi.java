package org.ndexbio.rest;

import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.core.Application;
import org.ndexbio.rest.exceptions.mappers.*;
import org.ndexbio.rest.filters.*;
import org.ndexbio.rest.services.*;

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
        _resources.add(NetworkService.class);
        _resources.add(AdminService.class);
        
        _providers.add(new BasicAuthenticationFilter());
        _providers.add(new CrossOriginResourceSharingFilter());
        _providers.add(new DuplicateObjectExceptionMapper());
        _providers.add(new IllegalArgumentExceptionMapper());
        _providers.add(new NdexExceptionMapper());
        _providers.add(new ObjectNotFoundExceptionMapper());
        _providers.add(new SecurityExceptionMapper());
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
