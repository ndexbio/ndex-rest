package org.ndexbio.rest.services;

import java.io.InputStream;
import java.util.Properties;
import javax.servlet.http.HttpServletRequest;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.ndexbio.rest.NdexSchemaManager;
import org.ndexbio.rest.NdexServicesTestSuite;
import org.ndexbio.rest.domain.IBaseTerm;
import org.ndexbio.rest.domain.IFunctionTerm;
import org.ndexbio.rest.domain.IGroup;
import org.ndexbio.rest.domain.IGroupInvitationRequest;
import org.ndexbio.rest.domain.IGroupMembership;
import org.ndexbio.rest.domain.IJoinGroupRequest;
import org.ndexbio.rest.domain.INetworkAccessRequest;
import org.ndexbio.rest.domain.INetworkMembership;
import org.ndexbio.rest.domain.IUser;
import org.ndexbio.rest.exceptions.NdexException;
import org.ndexbio.rest.models.Group;
import org.ndexbio.rest.models.SearchParameters;
import org.ndexbio.rest.models.User;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentPool;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.blueprints.impls.orient.OrientGraph;
import com.tinkerpop.frames.FramedGraph;
import com.tinkerpop.frames.FramedGraphFactory;
import com.tinkerpop.frames.modules.gremlingroovy.GremlinGroovyModule;
import com.tinkerpop.frames.modules.typedgraph.TypedGraphModuleBuilder;

public class TestGroupService
{
    private static FramedGraphFactory _graphFactory = null;
    private static ODatabaseDocumentTx _ndexDatabase = null;
    private static FramedGraph<OrientBaseGraph> _orientDbGraph = null;
    private static String _newGroupId = null;
    
    private static final HttpServletRequest _mockRequest = EasyMock.createMock(HttpServletRequest.class);
    private static final GroupService _groupService = new GroupService(_mockRequest);
    private static final Properties _testProperties = new Properties();

    
    
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

            final User loggedInUser = NdexServicesTestSuite.getUser("dexterpratt", _ndexDatabase, _orientDbGraph);
            NdexServicesTestSuite.setLoggedInUser(loggedInUser, _mockRequest);
        }
        catch (Exception e)
        {
            Assert.fail("Failed to initialize database. Cause: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    
    
    @Test
    public void createGroup()
    {
        final Group newGroup = new Group();
        newGroup.setDescription("This is a test group.");
        newGroup.setName("Test Group");
        newGroup.setOrganizationName("Unit Tested Group");
        newGroup.setWebsite("http://www.ndexbio.org");

        try
        {
            _newGroupId = _groupService.createGroup(newGroup).getId();
            Assert.assertNotNull(_newGroupId);
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void createGroupInvalid() throws IllegalArgumentException, NdexException
    {
        _groupService.createGroup(null);
    }
    
    @Test
    public void deleteGroup()
    {
        try
        {
            _groupService.deleteGroup(_newGroupId);
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
        
    }

    @Test(expected = IllegalArgumentException.class)
    public void deleteGroupInvalid() throws IllegalArgumentException, NdexException
    {
        _groupService.deleteGroup(_newGroupId);
    }

    @Test
    public void findGroups()
    {
        SearchParameters searchParameters = new SearchParameters();
        searchParameters.setSearchString("triptychjs");
        searchParameters.setSkip(0);
        searchParameters.setTop(25);
        
        try
        {
            _groupService.findGroups(searchParameters);
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void findGroupsInvalid() throws IllegalArgumentException, NdexException
    {
        SearchParameters searchParameters = new SearchParameters();
        searchParameters.setSearchString("");
        searchParameters.setSkip(0);
        searchParameters.setTop(25);
        
        _groupService.findGroups(null);
    }

    @Test
    public void getGroupById()
    {
    }

    @Test
    public void getGroupByName()
    {
    }

    @Test(expected = IllegalArgumentException.class)
    public void getGroupInvalid()
    {
    }

    @Test
    public void updateGroup()
    {
    }

    @Test(expected = IllegalArgumentException.class)
    public void updateGroupInvalid()
    {
    }
}