package org.ndexbio.rest;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.ndexbio.rest.domain.*;
import org.ndexbio.rest.helpers.Configuration;
import org.ndexbio.rest.models.*;
import org.ndexbio.rest.services.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orientechnologies.orient.client.remote.OServerAdmin;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentPool;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.OCommandSQL;
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
    private User requestingUser = new User();
    private HttpServletRequest mockRequest = EasyMock.createMock(HttpServletRequest.class);
    

	
    @Test
    public void checkDatabase()
    {
        try
        {
            //Can't use 'admin' as the username or password here, OrientDB
            //seems to have a hard-coded failure if either is 'admin'
        	/*
            OServerAdmin orientDbAdmin = new OServerAdmin(Configuration.getInstance().getProperty("OrientDB-URL"))
                .connect(Configuration.getInstance().getProperty("OrientDB-Admin-Username"), Configuration.getInstance().getProperty("OrientDB-Admin-Password"));
            */
        	
        	OServerAdmin orientDbAdmin = new OServerAdmin("remote:localhost/ndex").connect("ndex","ndex");

            if (orientDbAdmin.existsDatabase("local"))
            {
                System.out.println("Dropping existing database.");
                orientDbAdmin.dropDatabase("ndex");
            }

            System.out.println("Creating new database.");
            orientDbAdmin.createDatabase("ndex", "document", "local");

            System.out.println("Creating Tinkerpop Framed Graph Factory.");
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

            System.out.println("Connecting to database.");
            _ndexDatabase = ODatabaseDocumentPool.global().acquire("remote:localhost/ndex", "admin", "admin");

            System.out.println("Acquiring base graph.");
            _orientDbGraph = _graphFactory.create((OrientBaseGraph)new OrientGraph(_ndexDatabase));

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
    	// Setup requesting user to be the "logged in user"
    	requestingUser.setUsername("dexterpratt");
    	mockRequest.setAttribute("User", requestingUser);
        final UserServiceTest userService = new UserServiceTest();
        URL testUserUrl = getClass().getResource("/resources/users-and-groups.json");

        try
        {
            HashMap<User, JsonNode> usersCreated = new HashMap<User, JsonNode>();
            JsonNode rootNode = _jsonMapper.readTree(new File(testUserUrl.toURI()));
            
            Iterator<JsonNode> usersIterator = rootNode.elements();
            while (usersIterator.hasNext())
            {
                final JsonNode userNode = usersIterator.next();
                System.out.println("Creating test user: " + userNode.get("username").toString() + ".");

                final NewUser newUser = _jsonMapper.readValue(userNode.toString(), NewUser.class);
                
                User testUser = userService.createUser(newUser);
                String userId = testUser.getId();
                testUser = _jsonMapper.readValue(userNode.toString(), User.class);
                testUser.setId(userId);
                userService.updateUser(testUser);
                usersCreated.put(testUser, userNode);
/*
                System.out.println("Creating " + testUser.getUsername() + "'s networks.");
                createTestUserNetworks(userNode.get("networkFilenames"), testUser);

                System.out.println("Creating " + testUser.getUsername() + "'s groups.");
                createTestUserGroups(userNode.get("ownedGroups"), testUser);
                */
            }
            /*
            //Have to do a second loop to create requests because we need all
            //users, groups, and networks to exist
            for (Entry<User, JsonNode> newUser : usersCreated.entrySet())
            {
                System.out.println("Creating " + newUser.getKey().getUsername() + "'s requests.");
                createTestUserRequests(newUser.getValue().get("requests"), newUser.getKey());

                if (newUser.getKey().getGroups().isEmpty())
                    continue;
                
                for (Membership ownedGroup : newUser.getKey().getGroups())
                {
                    Iterator<JsonNode> groupIterator = newUser.getValue().get("ownedGroups").elements();
                    while(groupIterator.hasNext())
                        createTestGroupRequests(groupIterator.next().get("requests"), newUser.getKey(), ownedGroup.getResourceId());
                }
            }
            */
        }
        catch (Exception e)
        {
        	 e.printStackTrace();
            Assert.fail(e.getMessage());
           
        }
    }


    
    private void createTestGroupRequests(JsonNode requestsNode, User testUser, String groupId) throws Exception
    {
        final RequestService requestService = new RequestService(mockRequest);

        final Iterator<JsonNode> requestsIterator = requestsNode.elements();
        while (requestsIterator.hasNext())
        {
            Request newRequest = _jsonMapper.readValue(requestsIterator.next().toString(), Request.class);
            newRequest.setFromId(groupId);
            
            final Iterable<ODocument> usersFound = _orientDbGraph
                .getBaseGraph()
                .command(new OCommandSQL("select from User where firstName.append(\" \").append(lastName) = ?"))
                .execute(newRequest.getTo());
            
            final Iterator<ODocument> usersIterator = usersFound.iterator(); 
            if (usersIterator.hasNext())
            {
                final User invitedUser = new User(_orientDbGraph.getVertex(usersIterator.next(), IUser.class));
                newRequest.setToId(invitedUser.getId());
                requestService.createRequest(newRequest);
            }
        }        
    }

    private void createTestUserGroups(JsonNode groupsNode, User testUser) throws Exception
    {
        final GroupService groupService = new GroupService(mockRequest);
        final ArrayList<Membership> ownedGroups = new ArrayList<Membership>();
        
        final Iterator<JsonNode> groupsIterator = groupsNode.elements();
        
        Membership userMembership = new Membership();
        userMembership.setPermissions(Permissions.ADMIN);
        userMembership.setResourceId(testUser.getId());
        userMembership.setResourceName(testUser.getUsername());
        List<Membership> memberList = new ArrayList<Membership>();
        memberList.add(userMembership);
        
        while (groupsIterator.hasNext())
        {
            final JsonNode groupNode = groupsIterator.next();
            final Group groupToCreate = _jsonMapper.readValue(groupNode.toString(), Group.class);
            
            groupToCreate.setMembers(memberList);
            final Group newGroup = groupService.createGroup(groupToCreate);
            
            Assert.assertNotNull(newGroup);
            
            Membership groupMembership = new Membership();
            groupMembership.setPermissions(Permissions.ADMIN);
            groupMembership.setResourceId(newGroup.getId());
            groupMembership.setResourceName(newGroup.getName());
            ownedGroups.add(groupMembership);
        }
        
        testUser.setGroups(ownedGroups);
    }
    
    private void createTestUserNetworks(JsonNode networkFilesNode, User testUser) throws Exception
    {
        final NetworkService networkService = new NetworkService(mockRequest);

        final Iterator<JsonNode> networksIterator = networkFilesNode.elements();
        while (networksIterator.hasNext())
        {
            final URL testNetworkUrl = getClass().getResource("/resources/" + networksIterator.next().asText());
            final File networkFile = new File(testNetworkUrl.toURI());

            System.out.println("Creating network from file: " + networkFile.getName() + ".");
            final Network networkToCreate = _jsonMapper.readValue(networkFile, Network.class);
            List<Membership> membershipList = new ArrayList<Membership>();
            Membership membership = new Membership();
            membership.setResourceId(testUser.getId());
            membership.setResourceName(testUser.getUsername());
            membership.setPermissions(Permissions.ADMIN);
            membershipList.add(membership);
            networkToCreate.setMembers(membershipList);
            final Network newNetwork = networkService.createNetwork(networkToCreate);
            
            Assert.assertNotNull(newNetwork);
        }
    }

    private void createTestUserRequests(JsonNode requestsNode, User testUser) throws Exception
    {
        final RequestService requestService = new RequestService(mockRequest);

        final Iterator<JsonNode> requestsIterator = requestsNode.elements();
        while (requestsIterator.hasNext())
        {
            Request newRequest = _jsonMapper.readValue(requestsIterator.next().toString(), Request.class);
            newRequest.setFromId(testUser.getId());
            
            if (newRequest.getRequestType().equals("Join Group"))
            {
                final Iterable<ODocument> groupsFound = _orientDbGraph
                    .getBaseGraph()
                    .command(new OCommandSQL("select from Group where name = ?"))
                    .execute(newRequest.getTo());
                
                final Iterator<ODocument> groupIterator = groupsFound.iterator(); 
                if (groupIterator.hasNext())
                {
                    final Group requestedGroup = new Group(_orientDbGraph.getVertex(groupIterator.next(), IGroup.class));
                    newRequest.setToId(requestedGroup.getId());
                    requestService.createRequest(newRequest);
                }
            }
            else if (newRequest.getRequestType().equals("Network Access"))
            {
                final Iterable<ODocument> networksFound = _orientDbGraph
                    .getBaseGraph()
                    .command(new OCommandSQL("select from Network where title = ?"))
                    .execute(newRequest.getTo());
                
                final Iterator<ODocument> networkIterator = networksFound.iterator(); 
                if (networkIterator.hasNext())
                {
                    final Network requestedNetwork = new Network(_orientDbGraph.getVertex(networkIterator.next(), INetwork.class));
                    newRequest.setToId(requestedNetwork.getId());
                    requestService.createRequest(newRequest);
                }
            }
        }
    }
}
