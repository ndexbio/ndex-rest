package org.ndexbio.rest.actions.groups;

import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.server.network.protocol.http.OHttpRequest;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.frames.FramedGraph;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.ndexbio.rest.actions.NdexAction;
import org.ndexbio.rest.domain.XGroup;
import org.ndexbio.rest.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.helpers.RidConverter;
import java.io.IOException;

/******************************************************************************
* HTTP POST /groups
******************************************************************************/
public class PostGroup extends NdexAction<PostGroup.PostGroupContext>
{
    public class PostGroupContext implements NdexAction.Context
    {
        private String groupId;
        private JsonNode profile;
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    
    
    @Override
    public String[] getNames()
    {
        return new String[] { "POST|groups/*" };
    }

    
    
    @Override
    protected void action(PostGroupContext requestContext, FramedGraph<OrientBaseGraph> orientDbGraph)
    {
        ORID groupRid = RidConverter.convertToRid(requestContext.groupId);

        final XGroup groupToUpdate = orientDbGraph.getVertex(groupRid, XGroup.class);
        if (groupToUpdate == null)
            throw new ObjectNotFoundException("Group", requestContext.groupId);

        groupToUpdate.setOrganizationName(requestContext.profile.get("OrganizationName").asText());
        groupToUpdate.setWebsite(requestContext.profile.get("Website").asText());
        groupToUpdate.setDescription(requestContext.profile.get("Description").asText());
    }

    @Override
    protected String getDescription()
    {
        return "Updates a group's profile.";
    }

    @Override
    protected PostGroupContext parseRequest(OHttpRequest httpRequest) throws IOException
    {
        final PostGroupContext requestContext = new PostGroupContext();

        final JsonNode updatedProfile = OBJECT_MAPPER.readTree(httpRequest.content);
        requestContext.groupId = updatedProfile.get("GroupId").asText();
        requestContext.profile = updatedProfile.get("Profile");

        return requestContext;
    }

    @Override
    protected Object serializeResult(PostGroupContext requestContext)
    {
        return null;
    }
}
