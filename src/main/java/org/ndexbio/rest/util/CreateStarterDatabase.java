package org.ndexbio.rest.util;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orientechnologies.orient.client.remote.OServerAdmin;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentPool;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.blueprints.impls.orient.OrientGraph;

import org.easymock.EasyMock;
import org.ndexbio.model.object.*;
import org.ndexbio.model.object.network.Network;
import org.ndexbio.common.models.dao.orientdb.UserOrientdbDAO;
import org.ndexbio.common.models.data.*;
import org.ndexbio.orientdb.NdexSchemaManager;
import org.ndexbio.rest.services.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/******************************************************************************
 * mod 26Feb2014 refactor this class to be a standalone Java application
* This class creates the bare minimum for a test database needed to develop
* and test the web site.
******************************************************************************/

public class CreateStarterDatabase
{
    private static OServerAdmin _orientDbAdmin;
  // static FramedGraphFactory _graphFactory = null;
    private static ODatabaseDocumentTx _ndexDatabase = null;
  //  private static FramedGraph<OrientBaseGraph> _orientDbGraph = null;

    private  final HttpServletRequest _mockRequest;
    private static final ObjectMapper _jsonMapper = new ObjectMapper();
   
    private static final Logger log= LoggerFactory.getLogger(CreateStarterDatabase.class);

    
    public static void main (String...args){
    	try {
			CreateStarterDatabase csd = new CreateStarterDatabase();
			csd.exec();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			log.error("IOException opeing ndex.properties file:");
			log.error(e.getMessage());
			e.printStackTrace();
		}
	
    }
   
    public CreateStarterDatabase() throws IOException {
    	this._mockRequest = EasyMock.createMock(HttpServletRequest.class);

     
    }
    
    private void exec() {
    	this.deleteExistingDatabase();
    	this.generateStarterDatabase();
    //	this.insertStarterUser();
    	this.insertStarterGroups();
    	this.insertStarterNetwork();
    	 	
    }
    
    
    private void deleteExistingDatabase()
    {
        try
        {
            //Can't use 'admin' as the username or password here, OrientDB
            //seems to have a hard-coded failure if either is 'admin'
        	_orientDbAdmin = new OServerAdmin("remote:localhost/ndex")
        	    .connect("admin",
        	        "admin");

            if (_orientDbAdmin.existsDatabase("local"))
            {
                _orientDbAdmin.dropDatabase("ndex");
                log.info("Existing ndex database deleted");
                
            }
        }
        catch (Exception e)
        {
        	log.error("Exception deleting existing ndex database");
			log.error(e.getMessage());
            
            e.printStackTrace();
        }
    }
    
   
    private void generateStarterDatabase()
    {
        try
        {
            _orientDbAdmin.createDatabase("ndex", "document", "local");
           
    /*        _graphFactory = new FramedGraphFactory(new GremlinGroovyModule(),
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
           
            
        //    _orientDbGraph = _graphFactory.create((OrientBaseGraph)new OrientGraph(_ndexDatabase));
            NdexSchemaManager.INSTANCE.init(_ndexDatabase);
            log.info("New ndex database creaated");
        }
        catch (Exception e)
        {
        	log.error("Exception creating new ndex database");
			log.error(e.getMessage());
            e.printStackTrace();
        }
    }

/*   
   private void insertStarterUser()
    {
        final URL testUsersUrl = getClass().getResource("starter-users.json");
        final UserService userService = new UserService(_mockRequest);
        try {
        
            final JsonNode serializedUsers = _jsonMapper.readTree(new File(testUsersUrl.toURI()));
            final Iterator<JsonNode> usersIterator = serializedUsers.elements();
            
            while (usersIterator.hasNext())
            {
                final JsonNode serializedUser = usersIterator.next();              
                final NewUser newUser = _jsonMapper.readValue(serializedUser.toString(), NewUser.class);
                final User loggedInUser = userService.createUser(newUser);              
                setLoggedInUser(loggedInUser);
                final User updatedUser = _jsonMapper.readValue(serializedUser.toString(), User.class);
                updatedUser.setExternalId(loggedInUser.getExternalId());            
                userService.updateUser(updatedUser);            
                //Mocking the HTTP request inside a loop, so reset it
                EasyMock.reset(_mockRequest);
                log.info("New user " +updatedUser.getAccountName() +" created");
            }
        }
        catch (Exception e)
        {
        	log.error("Exception inserting user");
			log.error(e.getMessage());
            e.printStackTrace();
        }
    }
*/   
   
    private void insertStarterGroups()
    {
        final URL testGroupsUrl = getClass().getResource("starter-group.json");
        final GroupService groupService = new GroupService(_mockRequest);

        try
        {
            final JsonNode serializedGroups = _jsonMapper.readTree(new File(testGroupsUrl.toURI()));
            final Iterator<JsonNode> groupsIterator = serializedGroups.elements();
            
            while (groupsIterator.hasNext())
            {
                final JsonNode serializedGroup = groupsIterator.next();
                final Group newGroup = _jsonMapper.readValue(serializedGroup.toString(), Group.class);

                //Get the group owner name from the members, then clear the
                //members since we don't have the member ID
                final User loggedInUser = getUser(newGroup.getMembers().get(0).getResourceName());
                setLoggedInUser(loggedInUser);
                newGroup.getMembers().clear();               
                groupService.createGroup(newGroup);               
                //Mocking the HTTP request inside a loop, so reset it
                EasyMock.reset(_mockRequest);
                log.info("Group: " +newGroup.getAccountName() +" created");
            }
        }
        catch (Exception e)
        {
        	log.error("Exception inserting group");
			log.error(e.getMessage());
            e.printStackTrace();
        }
    }
    
   
    private void insertStarterNetwork()
    {
        final NetworkService networkService = new NetworkService(_mockRequest);
        final String networkFilenames[] =
        {
            "NCI_NATURE.FoxO family signaling.517135.jdex"
        };

        for (String networkFilename : networkFilenames)
        {
            try
            {
                final URL testNetworkUrl = getClass().getResource( networkFilename);
                final Network newNetwork = _jsonMapper.readValue(new File(testNetworkUrl.toURI()), Network.class);
                //Get the network owner name from the members, then clear the
                //members since we don't have the member ID
                final User loggedInUser;
                loggedInUser = getUser("biologist1");             
                setLoggedInUser(loggedInUser);
                //newNetwork.getMembers().clear();               
                networkService.createNetwork(newNetwork);                
                //Mocking the HTTP request inside a loop, so reset it
                EasyMock.reset(_mockRequest);
                log.info("New network " + newNetwork.getName() +" created");
            }
            catch (Exception e)
            {
            	log.error("Exception inserting network");
    			log.error(e.getMessage()); 
                e.printStackTrace();
            }
        }
    }
    
  

    private ORID getRid(String objectName) throws IllegalArgumentException
    {
        objectName = objectName.replace("'", "''");
     /*   
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
       */ 
        throw new IllegalArgumentException(objectName + " is not a user, group, network, request, or task.");
    }

    private User getUser(final String username)
    {
        final List<ODocument> matchingUsers = _ndexDatabase.query(new OSQLSynchQuery<Object>("select from User where username = '" + username + "'"));
        if (!matchingUsers.isEmpty())
            return UserOrientdbDAO.getUserFromDocument(matchingUsers.get(0));
        else
            return null;
    }
    
    private void setLoggedInUser(final User loggedInUser)
    {
        EasyMock.expect(_mockRequest.getAttribute("User"))
        .andReturn(loggedInUser)
        .anyTimes();

        EasyMock.replay(_mockRequest);
    }
}
