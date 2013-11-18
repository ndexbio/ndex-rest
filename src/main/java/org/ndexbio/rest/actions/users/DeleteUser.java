package org.ndexbio.rest.actions.users;

import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.server.network.protocol.http.OHttpRequest;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.frames.FramedGraph;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.ndexbio.rest.actions.NdexAction;
import org.ndexbio.rest.domain.XUser;
import org.ndexbio.rest.helpers.RidConverter;
import java.io.IOException;

/******************************************************************************
* HTTP DELETE /users
******************************************************************************/
public class DeleteUser extends NdexAction<DeleteUser.DeleteUserContext>
{
    public static final class DeleteUserContext implements NdexAction.Context
    {
        private String userId;
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    
    
    @Override
    public String[] getNames()
    {
        return new String[] { "DELETE|users/*" };
    }

    
    
    @Override
    protected void action(DeleteUserContext requestContext, FramedGraph<OrientBaseGraph> orientDbGraph)
    {
        final ORID userRid = RidConverter.convertToRid(requestContext.userId);
        final XUser targetUser = orientDbGraph.getVertex(userRid, XUser.class);
        orientDbGraph.removeVertex(targetUser.asVertex());
    }

    @Override
    protected String getDescription()
    {
        return "Deletes a user.";
    }

    @Override
    protected DeleteUserContext parseRequest(OHttpRequest httpRequest) throws IOException
    {
        JsonNode serializedUser = OBJECT_MAPPER.readTree(httpRequest.content);
        DeleteUserContext requestContext = new DeleteUserContext();
        requestContext.userId = serializedUser.get("UserId").asText();

        return requestContext;
    }

    @Override
    protected Object serializeResult(DeleteUserContext requestContext)
    {
        return null;
    }
}