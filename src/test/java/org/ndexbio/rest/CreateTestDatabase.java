package org.ndexbio.rest;

import java.io.File;
import java.net.URL;
import java.util.Iterator;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.ndexbio.rest.domain.IFunctionTerm;
import org.ndexbio.rest.domain.IGroup;
import org.ndexbio.rest.domain.ITerm;
import org.ndexbio.rest.domain.IUser;
import org.ndexbio.rest.models.Group;
import org.ndexbio.rest.models.NewUser;
import org.ndexbio.rest.models.User;
import org.ndexbio.rest.services.GroupService;
import org.ndexbio.rest.services.NetworkService;
import org.ndexbio.rest.services.UserService;
import com.orientechnologies.orient.client.remote.OServerAdmin;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentPool;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.blueprints.impls.orient.OrientGraph;
import com.tinkerpop.frames.FramedGraph;
import com.tinkerpop.frames.FramedGraphFactory;
import com.tinkerpop.frames.modules.gremlingroovy.GremlinGroovyModule;
import com.tinkerpop.frames.modules.typedgraph.TypedGraphModuleBuilder;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class CreateTestDatabase
{
    private static FramedGraphFactory _graphFactory = null;
    private static ODatabaseDocumentTx _ndexDatabase = null;
    private static FramedGraph<OrientBaseGraph> _orientDbGraph = null;

    private static final GroupService _groupService = new GroupService();
    private static final NetworkService _networkService = new NetworkService();
    private static final ObjectMapper _jsonMapper = new ObjectMapper();
    private static final UserService _userService = new UserService();
    
    @Test
    public void checkDatabase()
    {
        try
        {
            //TODO: Refactor this to connect using a configurable username/password, and database
            _ndexDatabase = ODatabaseDocumentPool.global().acquire("remote:localhost/ndex", "admin", "admin");
            _ndexDatabase.drop();
            System.out.println("Existing ndex database found and dropped");
        }
        catch (Exception e)
        {
        }
        finally
        {
            _ndexDatabase.close();
        }

        try
        {
            System.out.println("Creating new ndex database");
            new OServerAdmin("remote:localhost")
                .connect("admin", "admin")
                .createDatabase("ndex", "document", "local");
            
            System.out.println("Connecting to ndex database");
            _ndexDatabase = ODatabaseDocumentPool.global().acquire("remote:localhost/ndex", "admin", "admin");
            
            _graphFactory = new FramedGraphFactory(new GremlinGroovyModule(),
                new TypedGraphModuleBuilder()
                    .withClass(IGroup.class)
                    .withClass(IUser.class)
                    .withClass(ITerm.class)
                    .withClass(IFunctionTerm.class)
                    .build());
            
            _orientDbGraph = _graphFactory.create((OrientBaseGraph)new OrientGraph(_ndexDatabase));
            NdexSchemaManager.INSTANCE.init(_orientDbGraph.getBaseGraph());
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
        }
    }
    
    @Test
    public void createTestUser()
    {
        final URL testUserUrl = getClass().getResource("dexterpratt.json");
        
        try
        {
            final JsonNode rootNode = _jsonMapper.readTree(new File(testUserUrl.toURI()));
            
            System.out.println("Creating test user: " + rootNode.get("username").asText());
            
            final NewUser newTestUser = new NewUser();
            newTestUser.setEmailAddress(rootNode.get("emailAddress").asText());
            newTestUser.setPassword(rootNode.get("password").asText());
            newTestUser.setUsername(rootNode.get("username").asText());
            
            final User testUser = _userService.createUser(newTestUser);

            System.out.println("Updating " + rootNode.get("username").asText() + "'s profile");
            createTestUserProfile(rootNode.get("profile"), testUser);
            
            System.out.println("Creating " + rootNode.get("username").asText() + "'s networks");
            createTestUserNetworks(rootNode.get("networkFilenames"), testUser);
            
            System.out.println("Creating " + rootNode.get("username").asText() + "'s groups");
            createTestUserGroups(rootNode.get("ownedGroups"), testUser);
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
        }
    }
    
    private void createTestUserProfile(JsonNode profileNode, User testUser) throws Exception
    {
        testUser.setDescription(profileNode.get("description").asText());
        testUser.setFirstName(profileNode.get("firstName").asText());
        testUser.setLastName(profileNode.get("lastName").asText());
        testUser.setWebsite(profileNode.get("website").asText());
        
        _userService.updateUser(testUser);
    }

    private void createTestUserNetworks(JsonNode networkFilesNode, User testUser) throws Exception
    {
        final Iterator<JsonNode> networksIterator = networkFilesNode.getElements();  
        while (networksIterator.hasNext())
        {
            //TODO:
            //JsonNode networkNode = _jsonMapper.readTree(new File(networksIterator.next().asText()));
            //_networkService.createNetwork(networkNode);
        }
    }
    
    private void createTestUserGroups(JsonNode groupsNode, User testUser) throws Exception
    {
        final Iterator<JsonNode> groupsIterator = groupsNode.getElements();  
        while (groupsIterator.hasNext())
        {
            final JsonNode groupNode = groupsIterator.next();

            final Group newGroup = new Group();
            newGroup.setName(groupNode.get("name").asText());
            
            final JsonNode profileNode = groupNode.get("profile");
            newGroup.setDescription(profileNode.get("description").asText());
            newGroup.setOrganizationName(profileNode.get("organizationName").asText());
            newGroup.setWebsite(profileNode.get("website").asText());
            
            _groupService.createGroup(testUser.getId(), newGroup);
        }
    }
}
