package org.ndexbio.rest;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Application;

import org.ndexbio.common.access.NdexAOrientDBConnectionPool;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.models.dao.orientdb.UserDAO;
import org.ndexbio.model.exceptions.NdexException;
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
import org.ndexbio.task.utility.DatabaseInitializer;

import com.orientechnologies.orient.core.Orient;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;


public class NdexRestApi extends Application
{
    private final Set<Object> _providers = new HashSet<>();
    private final Set<Class<?>> _resources = new HashSet<>();
    
	    
    
    public NdexRestApi() throws NdexException
    {
    	// read configuration
/*    	Configuration configuration = Configuration.getInstance();
    	
    	//and initialize the db connections
    	NdexAOrientDBConnectionPool.createOrientDBConnectionPool(
    			configuration.getDBURL(),
    			configuration.getDBUser(),
    			configuration.getDBPasswd());
    	
    	NdexDatabase db = new NdexDatabase (configuration.getHostURI());
    	
    	System.out.println("Db created for " + db.getURIPrefix());
    	
    	ODatabaseDocumentTx conn = db.getAConnection();
    	UserDAO dao = new UserDAO(conn);
    	
    	DatabaseInitializer.createUserIfnotExist(dao, configuration.getSystmUserName(), "support@ndexbio.org", 
    				configuration.getSystemUserPassword());
    	conn.close();
    	db.close();
*/    	
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
        
 //       Runtime.getRuntime().addShutdownHook(new MyShutdown());
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
  /*  
    class MyShutdown extends Thread {

    	@Override
		public void run() {
            System.out.println("Database clean up Thread started");
            try {
            	//NdexAOrientDBConnectionPool.close();
            	Orient.instance().shutdown();
            	System.out.println ("Database has been closed.");
            } catch (Exception ee) {
                ee.printStackTrace();
            }
        }
    }
 */
    
}
