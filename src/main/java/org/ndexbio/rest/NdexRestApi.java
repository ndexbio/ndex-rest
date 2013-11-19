package org.ndexbio.rest;

import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.core.Application;
import org.ndexbio.rest.services.GroupService;
import org.ndexbio.rest.services.UserService;

public class NdexRestApi extends Application
{
    private Set<Object> _services = new HashSet<Object>();
    private Set<Class<?>> _empty = new HashSet<Class<?>>();
    
    
    
    public NdexRestApi()
    {
        _services.add(new GroupService());
        _services.add(new UserService());
    }
    
    
    
    @Override
    public Set<Class<?>> getClasses()
    {
        return _empty;
    }
    
    @Override
    public Set<Object> getSingletons()
    {
        return _services;
    }
}
