package org.ndexbio.rest.actions.users;

import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException;
import com.orientechnologies.orient.server.network.protocol.http.OHttpRequest;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.frames.FramedGraph;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.ndexbio.rest.actions.NdexAction;
import org.ndexbio.rest.domain.XUser;
import org.ndexbio.rest.exceptions.DuplicateObjectException;
import org.ndexbio.rest.helpers.RidConverter;
import java.io.IOException;

/******************************************************************************
* HTTP PUT /users
******************************************************************************/
public class PutUser extends NdexAction<PutUser.CreateUserContext>
{
    public static final class CreateUserContext implements NdexAction.Context
    {
        private String username;
        private String password;
        private XUser user;
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    
    
    @Override
    public String[] getNames()
    {
        return new String[] { "PUT|users/*" };
    }
    

    
    @Override
    protected void action(CreateUserContext requestContext, FramedGraph<OrientBaseGraph> orientDbGraph)
    {
        try
        {
            final XUser newUser = orientDbGraph.addVertex("class:xUser", XUser.class);
            newUser.setUsername(requestContext.username);
            newUser.setPassword(requestContext.password);

            requestContext.user = newUser;
        }
        catch (ORecordDuplicatedException orde)
        {
            throw new DuplicateObjectException(requestContext.username + " already exists.", orde);
        }
    }

    @Override
    protected String getDescription()
    {
        return "Creates a user.";
    }

    @Override
    protected CreateUserContext parseRequest(OHttpRequest httpRequest) throws IOException
    {
        final CreateUserContext requestContext = new CreateUserContext();
        final JsonNode newUser = OBJECT_MAPPER.readTree(httpRequest.content);
        
        requestContext.username = newUser.get("Username").asText();
        requestContext.password = newUser.get("Password").asText();

        return requestContext;
    }

    @Override
    protected Object serializeResult(CreateUserContext requestContext)
    {
        final ObjectNode newUser = OBJECT_MAPPER.createObjectNode();
        newUser.put("Id", RidConverter.convertToJid((ORID)requestContext.user.asVertex().getId()));
        newUser.put("Username", requestContext.user.getUsername());

        return newUser;
    }
}