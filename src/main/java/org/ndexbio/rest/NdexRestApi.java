package org.ndexbio.rest;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Application;

import org.ndexbio.common.access.NdexAOrientDBConnectionPool;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.exceptions.NdexException;
import org.ndexbio.rest.exceptions.mappers.DuplicateObjectExceptionMapper;
import org.ndexbio.rest.exceptions.mappers.IllegalArgumentExceptionMapper;
import org.ndexbio.rest.exceptions.mappers.NdexExceptionMapper;
import org.ndexbio.rest.exceptions.mappers.ObjectNotFoundExceptionMapper;
import org.ndexbio.rest.exceptions.mappers.SecurityExceptionMapper;
import org.ndexbio.rest.filters.BasicAuthenticationFilter;
import org.ndexbio.rest.filters.CrossOriginResourceSharingFilter;
import org.ndexbio.rest.services.AdminService;
import org.ndexbio.rest.services.GroupService;
import org.ndexbio.rest.services.NetworkAService;
import org.ndexbio.rest.services.RequestService;
import org.ndexbio.rest.services.TaskService;
import org.ndexbio.rest.services.UserService;
import org.ndexbio.task.Configuration;


public class NdexRestApi extends Application
{
    private final Set<Object> _providers = new HashSet<Object>();
    private final Set<Class<?>> _resources = new HashSet<Class<?>>();
    
	    
    
    public NdexRestApi() throws NdexException
    {
    	// read configuration
    	Configuration configuration = Configuration.getInstance();
    	
    	//and initialize the db connections
    	NdexAOrientDBConnectionPool.createOrientDBConnectionPool(
    			configuration.getDBURL(),
    			configuration.getDBUser(),
    			configuration.getDBPasswd());
    	
    	NdexDatabase db = new NdexDatabase (configuration.getHostURI());
    	
    	System.out.println("Db created for " + db.getURIPrefix());
    	
    	db.close();
    	_resources.add(GroupService.class); 
        _resources.add(UserService.class); 
        _resources.add(RequestService.class);
        _resources.add(TaskService.class); 
        _resources.add(NetworkAService.class);
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
