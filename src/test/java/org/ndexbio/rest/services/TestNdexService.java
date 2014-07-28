package org.ndexbio.rest.services;

import java.io.InputStream;
import java.util.List;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;

import org.easymock.EasyMock;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.ndexbio.model.object.NewUser;
import org.ndexbio.model.object.User;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.exceptions.NdexException;
import org.ndexbio.common.models.dao.orientdb.UserDAO;
import org.ndexbio.common.models.data.*;
import org.ndexbio.model.object.SimpleUserQuery;
import org.ndexbio.orientdb.NdexSchemaManager;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentPool;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.blueprints.impls.orient.OrientGraph;

public abstract class TestNdexService
{
//    protected static FramedGraphFactory _graphFactory = null;
    protected static ODatabaseDocumentTx _ndexDatabase = null;
 //   protected static FramedGraph<OrientBaseGraph> _orientDbGraph = null;
    
    protected static final HttpServletRequest _mockRequest = EasyMock.createMock(HttpServletRequest.class);
    protected static final Properties _testProperties = new Properties();

    
    
    @BeforeClass
    public static void initializeTests() throws Exception
    {
        final InputStream propertiesStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("ndex.properties");
        _testProperties.load(propertiesStream);

        try
        {
   /*         _graphFactory = new FramedGraphFactory(new GremlinGroovyModule(),
                new TypedGraphModuleBuilder()
                    .withClass(IGroup.class)
                    .withClass(IUser.class)
                    .withClass(IGroupMembership.class)
                    .withClass(INetworkMembership.class)
                    .withClass(IGroupInvitationRequest.class)
                    .withClass(IJoinGroupRequest.class)
                    .withClass(INetworkAccessRequest.class)
                    .withClass(IBaseTerm.class)
                    .withClass(IFunctionTerm.class)
                    .build());
    */        
            _ndexDatabase = ODatabaseDocumentPool.global().acquire("remote:localhost/ndex", "admin", "admin");
 //           _orientDbGraph = _graphFactory.create((OrientBaseGraph)new OrientGraph(_ndexDatabase));
            NdexSchemaManager.INSTANCE.init(_ndexDatabase);
        }
        catch (Exception e)
        {
            Assert.fail("Failed to initialize database. Cause: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @AfterClass
    public static void cleanUp()
    {
 //       _graphFactory = null;
        _ndexDatabase.close();
  //      _orientDbGraph = null;
    }

    
    
    @After
    public void resetLoggedInUser()
    {
        EasyMock.reset(_mockRequest);
    }

    @Before
    public void setLoggedInUser() throws NdexException {
    	
    	final NdexDatabase database = new NdexDatabase();
    	final ODatabaseDocumentTx  localConnection = database.getAConnection();  
    	localConnection.begin();
    	final UserDAO dao = new UserDAO(localConnection);
    	final User loggedInUser;
    	
    	final SimpleUserQuery search = new SimpleUserQuery();
    	search.setSearchString("dexter");
    	
    	final List<User> users = dao.findUsers(search, 0, 5);
    	
    	if(users.isEmpty()) {
   
	    	final NewUser newUser = new NewUser();
	    	newUser.setEmailAddress("dexterpratt@ndexbio.org");
	        newUser.setPassword("insecure");
	        newUser.setAccountName("dexterpratt");
	        newUser.setFirstName("Dexter");
	        newUser.setLastName("Pratt");
	        newUser.setDescription("Apart from my work at the Cytoscape Consortium building NDEx, I collect networks around some of my favorite biomolecules, such as FOX03, RBL2, and MUC1");
	        newUser.setWebsite("www.triptychjs.com");
	        newUser.setImage("http://i.imgur.com/09oVvZg.jpg");
	        loggedInUser = dao.createNewUser(newUser);
        
    	} else {
    		
    		loggedInUser = users.get(0);
    		
    	}
        
        EasyMock.expect(_mockRequest.getAttribute("User")).andReturn(loggedInUser)
            .anyTimes();

        EasyMock.replay(_mockRequest); 
        
        localConnection.commit();
        localConnection.close();
        database.close();
        
    }
    
    @Test
    public void connectionPool() throws NdexException {
    	
    	NdexDatabase database = new NdexDatabase();
    	final ODatabaseDocumentTx[]  localConnection = new ODatabaseDocumentTx[10]; //= database.getAConnection();  //all DML will be in this connection, in one transaction.
    	
    	try {
	    	for(int jj=0; jj<10; jj++) {
	    		database = new NdexDatabase();
		    	for(int ii=0; ii<10; ii++) {
		    		localConnection[ii] = database.getAConnection();
		    	}
		    	
		    	for(int ii=0; ii<10; ii++) {
		    		localConnection[ii].close();
		    	}
		    	database.close();
	    	}
	    	
	    	
    	} catch (Throwable e) {
    		Assert.fail(e.getMessage());
    	}
    	
    }
    
    
    /**************************************************************************
    * Gets the record ID of an object by its name from the database.
    * 
    * @param objectName
    *            The name of the object.
    * @return An ORID object containing the record ID.
    **************************************************************************/
    protected ORID getRid(String objectName) throws IllegalArgumentException
    {
        objectName = objectName.replace("'", "\\'");
        
   /*     final List<ODocument> matchingUsers = _ndexDatabase.query(new OSQLSynchQuery<Object>("select from User where username = '" + objectName + "'"));
        if (!matchingUsers.isEmpty())
            return (ORID)_orientDbGraph.getVertex(matchingUsers.get(0)).getId();
        
        final List<ODocument> matchingGroups = _ndexDatabase.query(new OSQLSynchQuery<Object>("select from Group where name = '" + objectName + "'"));
        if (!matchingGroups.isEmpty())
            return (ORID)_orientDbGraph.getVertex(matchingGroups.get(0)).getId();

        final List<ODocument> matchingNetworks = _ndexDatabase.query(new OSQLSynchQuery<Object>("select from Network where name = '" + objectName + "'"));
        if (!matchingNetworks.isEmpty())
            return (ORID)_orientDbGraph.getVertex(matchingNetworks.get(0)).getId();

        final List<ODocument> matchingRequests = _ndexDatabase.query(new OSQLSynchQuery<Object>("select from Request where message = '" + objectName + "'"));
        if (!matchingRequests.isEmpty())
            return (ORID)_orientDbGraph.getVertex(matchingRequests.get(0)).getId();

        final List<ODocument> matchingTasks = _ndexDatabase.query(new OSQLSynchQuery<Object>("select from Task where description = '" + objectName + "'"));
        if (!matchingTasks.isEmpty())
            return (ORID)_orientDbGraph.getVertex(matchingTasks.get(0)).getId();
      */  
        throw new IllegalArgumentException(objectName + " is not a user, group, network, request, or task.");
    }

    
    /**************************************************************************
    * Emulates a user logged into the system via the mock HTTP request.
    * 
    * @param loggedInUser
    *            The user to emulate being logged in.
    **************************************************************************/
    protected void setLoggedInUser(final User loggedInUser)
    {
        EasyMock.expect(_mockRequest.getAttribute("User"))
        .andReturn(loggedInUser)
        .anyTimes();

        EasyMock.replay(_mockRequest);
    }
}
