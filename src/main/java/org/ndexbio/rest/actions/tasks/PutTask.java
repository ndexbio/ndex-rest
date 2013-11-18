package org.ndexbio.rest.actions.tasks;

import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.server.network.protocol.http.OHttpRequest;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.frames.FramedGraph;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.ndexbio.rest.actions.NdexAction;
import org.ndexbio.rest.domain.XTask;
import org.ndexbio.rest.domain.XUser;
import org.ndexbio.rest.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.helpers.RidConverter;
import java.io.IOException;
import java.util.Date;

/******************************************************************************
* HTTP PUT /tasks 
******************************************************************************/
public class PutTask extends NdexAction<PutTask.PutTaskContext>
{
    public static final class PutTaskContext implements NdexAction.Context
    {
        private String userId;
        private XTask task;
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    
    
    @Override
    public String[] getNames()
    {
        return new String[] { "PUT|tasks/*" };
    }



    @Override
    protected void action(PutTaskContext requestContext, FramedGraph<OrientBaseGraph> orientDbGraph)
    {
        final ORID userRid = RidConverter.convertToRid(requestContext.userId);

        final XUser owningUser = orientDbGraph.getVertex(userRid, XUser.class);
        if (owningUser == null)
            throw new ObjectNotFoundException("User", requestContext.userId);

        final XTask task = orientDbGraph.addVertex("class:xTask", XTask.class);
        task.setStatus("active");
        task.setStartTime(new Date());
        task.addOwner(owningUser);

        requestContext.task = task;
    }

    @Override
    protected String getDescription()
    {
        return "Creates a task.";
    }

    @Override
    protected PutTaskContext parseRequest(OHttpRequest httpRequest) throws IOException
    {
        final JsonNode serializedTask = OBJECT_MAPPER.readTree(httpRequest.content);
        PutTaskContext requestContext = new PutTaskContext();
        requestContext.userId = serializedTask.get("UserId").asText();

        return requestContext;
    }

    @Override
    protected Object serializeResult(PutTaskContext requestContext)
    {
        ObjectNode result = OBJECT_MAPPER.createObjectNode();
        result.put("Id", RidConverter.convertToJid((ORID)requestContext.task.asVertex().getId()));

        return result;
    }
}