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
import org.ndexbio.rest.models.Network;
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
    private static final ObjectMapper _jsonMapper = new ObjectMapper();

    @Test
    public void checkDatabase()
    {
        try
        {
            //TODO: Refactor this to connect using a configurable username/password, and database
            OServerAdmin orientDbAdmin = new OServerAdmin("remote:localhost/ndex").connect("ndex", "ndex");
            if (orientDbAdmin.existsDatabase("local"))
            {
                System.out.println("Dropping existing database.");
                orientDbAdmin.dropDatabase("ndex");
            }

            System.out.println("Creating new database.");
            orientDbAdmin.createDatabase("ndex", "document", "local");

            System.out.println("Connecting to database.");
            _ndexDatabase = ODatabaseDocumentPool.global().acquire("remote:localhost/ndex", "ndex", "ndex");

            System.out.println("Creating Tinkerpop Framed Graph Factory.");
            _graphFactory = new FramedGraphFactory(new GremlinGroovyModule(), new TypedGraphModuleBuilder().withClass(IGroup.class).withClass(IUser.class).withClass(ITerm.class).withClass(IFunctionTerm.class).build());

            System.out.println("Acquiring base graph.");
            _orientDbGraph = _graphFactory.create((OrientBaseGraph) new OrientGraph(_ndexDatabase));

            System.out.println("Acquiring instance of schema manager.");
            NdexSchemaManager.INSTANCE.init(_orientDbGraph.getBaseGraph());
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
    }

    @Test
    public void createTestUser()
    {
        final UserService userService = new UserService();
        URL testUserUrl = getClass().getResource("/resources/dexterpratt.json");

        try
        {
            JsonNode rootNode = _jsonMapper.readTree(new File(testUserUrl.toURI()));

            System.out.println("Creating test user: " + rootNode.get("username").asText());
            NewUser newUser = new NewUser();
            newUser.setEmailAddress(rootNode.get("emailAddress").asText());
            newUser.setPassword(rootNode.get("password").asText());
            newUser.setUsername(rootNode.get("username").asText());
            User testUser = userService.createUser(newUser);

            System.out.println("Updating " + rootNode.get("username").asText() + "'s profile");
            createTestUserProfile(rootNode.get("profile"), testUser, userService);

            System.out.println("Creating " + rootNode.get("username").asText() + "'s networks");
            createTestUserNetworks(rootNode.get("networkFilenames"), testUser);

            System.out.println("Creating " + rootNode.get("username").asText() + "'s groups");
            createTestUserGroups(rootNode.get("ownedGroups"), testUser);
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
    }

    
    
    private void createTestUserProfile(JsonNode profileNode, User testUser, UserService userService) throws Exception
    {
        testUser.setDescription(profileNode.get("description").asText());
        testUser.setFirstName(profileNode.get("firstName").asText());
        testUser.setLastName(profileNode.get("lastName").asText());
        testUser.setWebsite(profileNode.get("website").asText());

        userService.updateUser(testUser);
    }

    private void createTestUserNetworks(JsonNode networkFilesNode, User testUser) throws Exception
    {
        final NetworkService networkService = new NetworkService();
        
        final Iterator<JsonNode> networksIterator = networkFilesNode.getElements();
        while (networksIterator.hasNext())
        {
            URL testNetworkUrl = getClass().getResource("/resources/" + networksIterator.next().asText());
            File networkFile = new File(testNetworkUrl.toURI());

            System.out.println("Creating network from file: " + networkFile.getName());
            Network networkToCreate = _jsonMapper.readValue(networkFile, Network.class);
            Network newNetwork = networkService.createNetwork(testUser.getId(), networkToCreate);
            
            Assert.assertNotNull(newNetwork);
        }
    }

    private void createTestUserGroups(JsonNode groupsNode, User testUser) throws Exception
    {
        final GroupService groupService = new GroupService();

        final Iterator<JsonNode> groupsIterator = groupsNode.getElements();
        while (groupsIterator.hasNext())
        {
            JsonNode groupNode = groupsIterator.next();

            Group newGroup = new Group();
            newGroup.setName(groupNode.get("name").asText());

            JsonNode profileNode = groupNode.get("profile");
            newGroup.setDescription(profileNode.get("description").asText());
            newGroup.setOrganizationName(profileNode.get("organizationName").asText());
            newGroup.setWebsite(profileNode.get("website").asText());

            groupService.createGroup(testUser.getId(), newGroup);
        }
    }
}
