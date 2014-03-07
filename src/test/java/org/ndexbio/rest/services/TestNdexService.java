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
import org.ndexbio.common.models.object.User;
import org.ndexbio.common.models.data.*;
import org.ndexbio.orientdb.NdexSchemaManager;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentPool;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.blueprints.impls.orient.OrientGraph;
import com.tinkerpop.frames.FramedGraph;
import com.tinkerpop.frames.FramedGraphFactory;
import com.tinkerpop.frames.modules.gremlingroovy.GremlinGroovyModule;
import com.tinkerpop.frames.modules.typedgraph.TypedGraphModuleBuilder;

public abstract class TestNdexService
{
    protected static FramedGraphFactory _graphFactory = null;
    protected static ODatabaseDocumentTx _ndexDatabase = null;
    protected static FramedGraph<OrientBaseGraph> _orientDbGraph = null;
    
    protected static final HttpServletRequest _mockRequest = EasyMock.createMock(HttpServletRequest.class);
    protected static final Properties _testProperties = new Properties();

    
    
    @BeforeClass
    public static void initializeTests() throws Exception
    {
        final InputStream propertiesStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("ndex.properties");
        _testProperties.load(propertiesStream);

        try
        {
            _graphFactory = new FramedGraphFactory(new GremlinGroovyModule(),
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
            
            _ndexDatabase = ODatabaseDocumentPool.global().acquire("remote:localhost/ndex", "admin", "admin");
            _orientDbGraph = _graphFactory.create((OrientBaseGraph)new OrientGraph(_ndexDatabase));
            NdexSchemaManager.INSTANCE.init(_orientDbGraph.getBaseGraph());
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
        _graphFactory = null;
        _ndexDatabase.close();
        _orientDbGraph = null;
    }

    
    
    @After
    public void resetLoggedInUser()
    {
        EasyMock.reset(_mockRequest);
    }

    @Before
    public void setLoggedInUser()
    {
        //final User loggedInUser = getUser("dexterpratt");
    	
    	final TestUserAnswer testUserAnswer = new TestUserAnswer(this);

        EasyMock.expect(_mockRequest.getAttribute("User"))
            .andAnswer(testUserAnswer)
            .anyTimes();

        EasyMock.replay(_mockRequest);
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
        
        final List<ODocument> matchingUsers = _ndexDatabase.query(new OSQLSynchQuery<Object>("select from User where username = '" + objectName + "'"));
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
        
        throw new IllegalArgumentException(objectName + " is not a user, group, network, request, or task.");
    }

    /**************************************************************************
    * Queries the database for the user's ID by the username. Necessary to
    * mock the logged in user.
    * 
    * @param username
    *            The username.
    **************************************************************************/
    protected User getUser(final String username)
    {
        final List<ODocument> matchingUsers = _ndexDatabase.query(new OSQLSynchQuery<Object>("select from User where username = '" + username + "'"));
        if (!matchingUsers.isEmpty())
            return new User(_orientDbGraph.getVertex(matchingUsers.get(0), IUser.class), true);
        else
            return null;
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
