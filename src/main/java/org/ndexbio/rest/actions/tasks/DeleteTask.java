package org.ndexbio.rest.actions.tasks;

import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.server.network.protocol.http.OHttpRequest;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.frames.FramedGraph;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.ndexbio.rest.actions.NdexAction;
import org.ndexbio.rest.domain.XTask;
import org.ndexbio.rest.helpers.RidConverter;
import java.io.IOException;

/******************************************************************************
* HTTP DELETE /tasks 
******************************************************************************/
public class DeleteTask extends NdexAction<DeleteTask.DeleteTaskContext>
{
    public static final class DeleteTaskContext implements NdexAction.Context
    {
        private String taskId;
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    
    
    @Override
    public String[] getNames()
    {
        return new String[] { "DELETE|tasks/*" };
    }


    
    @Override
    protected void action(DeleteTaskContext requestContext, FramedGraph<OrientBaseGraph> orientDbGraph)
    {
        final ORID taskRid = RidConverter.convertToRid(requestContext.taskId);
        final XTask task = orientDbGraph.getVertex(taskRid, XTask.class);
        if (task != null)
            orientDbGraph.removeVertex(task.asVertex());
    }

    @Override
    protected String getDescription()
    {
        return "Deletes a task.";
    }

    @Override
    protected DeleteTaskContext parseRequest(OHttpRequest httpRequest) throws IOException
    {
        final JsonNode serializedTask = OBJECT_MAPPER.readTree(httpRequest.content);
        DeleteTaskContext requestContext = new DeleteTaskContext();
        requestContext.taskId = serializedTask.get("TaskId").asText();

        return requestContext;
    }

    @Override
    protected Object serializeResult(DeleteTaskContext requestContext)
    {
        return null;
    }
}