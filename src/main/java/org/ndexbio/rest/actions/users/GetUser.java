package org.ndexbio.rest.actions.users;

import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.OCommandSQL;
import com.orientechnologies.orient.server.network.protocol.http.OHttpRequest;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.frames.FramedGraph;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.ndexbio.rest.actions.NdexAction;
import org.ndexbio.rest.domain.XUser;
import org.ndexbio.rest.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.helpers.RidConverter;
import org.ndexbio.rest.helpers.UserHelper;
import java.io.IOException;
import java.util.Collection;

/******************************************************************************
* HTTP GET /users
******************************************************************************/
public class GetUser extends NdexAction<GetUser.GetUserContext>
{
    public static final class GetUserContext implements NdexAction.Context
    {
        private String userId;
        private String username;
        private JsonNode user;
    }
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();


    
    @Override
    public String[] getNames()
    {
        return new String[] { "GET|users/*" };
    }


    
    @Override
    protected void action(GetUserContext requestContext, FramedGraph<OrientBaseGraph> orientDbGraph)
    {
        XUser matchingUser = null;
        ObjectNode serializedUser = OBJECT_MAPPER.createObjectNode();

        if (requestContext.userId != null)
        {
            ORID userId = RidConverter.convertToRid(requestContext.userId);
            matchingUser = orientDbGraph.getVertex(userId, XUser.class);

            if (matchingUser == null)
                throw new ObjectNotFoundException("User", requestContext.userId);
        }
        else if (requestContext.username != null)
        {
            Collection<ODocument> searchResult = orientDbGraph.getBaseGraph().command(new OCommandSQL("select from xUser where username equals " + requestContext.username + " limit 10")).execute();

            if (searchResult.size() < 1)
                throw new ObjectNotFoundException("User", requestContext.username);
            
            matchingUser = orientDbGraph.getVertex(searchResult.toArray()[0], XUser.class);
        }

        serializedUser.put("Id", RidConverter.convertToJid((ORID)matchingUser.asVertex().getId()));
        serializedUser.put("Username", matchingUser.getUsername());

        ObjectNode profile = OBJECT_MAPPER.createObjectNode();
        UserHelper.serializeProfile(matchingUser, profile);
        serializedUser.put("Profile", profile);

        ArrayNode ownedNetworks = OBJECT_MAPPER.createArrayNode();
        UserHelper.serializeNetworks(matchingUser.getOwnedNetworks().iterator(), ownedNetworks);
        serializedUser.put("OwnedNetworks", ownedNetworks);

        ArrayNode ownedGroups = OBJECT_MAPPER.createArrayNode();
        UserHelper.serializeGroups(matchingUser.getOwnedGroups().iterator(), ownedGroups);
        serializedUser.put("OwnedGroups", ownedGroups);
        
        //TODO: Add user requests

        requestContext.user = serializedUser;
    }

    @Override
    protected String getDescription()
    {
        return "Gets a user by ID or username.";
    }

    @Override
    protected GetUserContext parseRequest(OHttpRequest httpRequest) throws IOException
    {
        final GetUserContext requestContext = new GetUserContext();
        requestContext.userId = httpRequest.getParameter("userid");
        requestContext.username = httpRequest.getParameter("username");

        return requestContext;
    }

    @Override
    protected Object serializeResult(GetUserContext requestContext)
    {
        return requestContext.user;
    }
}