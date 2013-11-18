package org.ndexbio.rest.actions.groups;

import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.server.network.protocol.http.OHttpRequest;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.frames.FramedGraph;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.ndexbio.rest.actions.NdexAction;
import org.ndexbio.rest.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.helpers.RidConverter;
import java.io.IOException;

/******************************************************************************
* HTTP DELETE /groups
******************************************************************************/
public class DeleteGroup extends NdexAction<DeleteGroup.DeleteGroupContext>
{
    public class DeleteGroupContext implements NdexAction.Context
    {
        private String groupId;
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    
    
    @Override
    public String[] getNames()
    {
        return new String[] { "DELETE|groups/*" };
    }

    
    
    @Override
    protected void action(DeleteGroupContext requestContext, FramedGraph<OrientBaseGraph> orientDbGraph)
    {
        final ORID groupId = RidConverter.convertToRid(requestContext.groupId);

        final Vertex groupToDelete = orientDbGraph.getVertex(groupId);
        if (groupToDelete == null)
            throw new ObjectNotFoundException("Group", requestContext.groupId);

        orientDbGraph.removeVertex(groupToDelete);
    }

    @Override
    protected String getDescription()
    {
        return "Deletes a group.";
    }

    @Override
    protected DeleteGroupContext parseRequest(OHttpRequest httpRequest) throws IOException
    {
        final JsonNode serializedGroupId = OBJECT_MAPPER.readTree(httpRequest.content);

        final DeleteGroupContext requestContext = new DeleteGroupContext();
        requestContext.groupId = serializedGroupId.get("groupId").asText();

        return requestContext;
    }

    @Override
    protected Object serializeResult(DeleteGroupContext requestContext)
    {
        return null;
    }
}
