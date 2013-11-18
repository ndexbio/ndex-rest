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
* HTTP POST /users 
******************************************************************************/
public class PostUser extends NdexAction<PostUser.PostUserContext>
{
    public final static class PostUserContext implements NdexAction.Context
    {
        private JsonNode profile;
        private String userId;
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();



    @Override
    public String[] getNames()
    {
        return new String[] { "POST|users/*" };
    }


    
    @Override
    protected void action(PostUserContext requestContext, FramedGraph<OrientBaseGraph> orientDbGraph)
    {
        final ORID userRid = RidConverter.convertToRid(requestContext.userId);
        XUser targetUser = orientDbGraph.getVertex(userRid, XUser.class);

        if (targetUser != null)
        {
            if (requestContext.profile.get("FirstName") != null)
                targetUser.setFirstName(requestContext.profile.get("FirstName").asText());

            if (requestContext.profile.get("LastName") != null)
                targetUser.setLastName(requestContext.profile.get("LastName").asText());

            if (requestContext.profile.get("Website") != null)
                targetUser.setWebsite(requestContext.profile.get("Website").asText());

            if (requestContext.profile.get("Description") != null)
                targetUser.setDescription(requestContext.profile.get("Description").asText());

            if (requestContext.profile.get("BackgroundImage") != null)
                targetUser.setBackgroundImg(requestContext.profile.get("BackgroundImage").asText());

            if (requestContext.profile.get("ForegroundImage") != null)
                targetUser.setForegroundImg(requestContext.profile.get("ForegroundImage").asText());
        }
    }

    @Override
    protected String getDescription()
    {
        return "Updates a user's profile.";
    }

    @Override
    protected PostUserContext parseRequest(OHttpRequest httpRequest) throws IOException
    {
        final JsonNode serializedUser = OBJECT_MAPPER.readTree(httpRequest.content);

        PostUserContext requestContext = new PostUserContext();
        requestContext.profile = serializedUser.get("Profile");
        requestContext.userId = serializedUser.get("UserId").asText();

        return requestContext;
    }

    @Override
    protected Object serializeResult(PostUserContext requestContext)
    {
        return null;
    }
}