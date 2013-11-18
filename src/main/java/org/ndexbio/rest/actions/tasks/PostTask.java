package org.ndexbio.rest.actions.tasks;

import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.server.network.protocol.http.OHttpRequest;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.frames.FramedGraph;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.ndexbio.rest.actions.NdexAction;
import org.ndexbio.rest.domain.XTask;
import org.ndexbio.rest.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.helpers.RidConverter;
import java.io.IOException;

/******************************************************************************
* HTTP POST /tasks 
******************************************************************************/
public class PostTask extends NdexAction<PostTask.UpdateTaskContext>
{
    public static final class UpdateTaskContext implements NdexAction.Context
    {
        private String taskId;
        private String taskStatus;
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    
    
    @Override
    public String[] getNames()
    {
        return new String[] { "POST|tasks/*" };
    }



    @Override
    protected void action(UpdateTaskContext requestContext, FramedGraph<OrientBaseGraph> orientDbGraph)
    {
        final ORID taskRid = RidConverter.convertToRid(requestContext.taskId);
        final XTask task = orientDbGraph.getVertex(taskRid, XTask.class);
        if (task == null)
            throw new ObjectNotFoundException("Task", requestContext.taskId);

        task.setStatus(requestContext.taskStatus);
    }

    @Override
    protected String getDescription()
    {
        return "Updates a task.";
    }

    @Override
    protected UpdateTaskContext parseRequest(OHttpRequest httpRequest) throws IOException
    {
        final JsonNode serializedTask = OBJECT_MAPPER.readTree(httpRequest.content);
        UpdateTaskContext requestContext = new UpdateTaskContext();
        requestContext.taskId = serializedTask.get("taskid").asText();
        requestContext.taskStatus = serializedTask.get("taskStatus").asText();

        return requestContext;
    }

    @Override
    protected Object serializeResult(UpdateTaskContext requestContext)
    {
        return null;
    }
}