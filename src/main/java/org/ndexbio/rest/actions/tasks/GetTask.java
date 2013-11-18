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
import org.ndexbio.rest.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.helpers.RidConverter;
import java.io.IOException;
import java.text.DateFormat;

/******************************************************************************
* HTTP GET /tasks 
******************************************************************************/
public class GetTask extends NdexAction<GetTask.GetTaskContext>
{
    public static final class GetTaskContext implements NdexAction.Context
    {
        private String taskId;
        private JsonNode task;
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();


    
    @Override
    public String[] getNames()
    {
        return new String[] { "GET|tasks/*" };
    }

    
    
    @Override
    protected void action(GetTaskContext requestContext, FramedGraph<OrientBaseGraph> orientDbGraph)
    {
        final ORID taskRid = RidConverter.convertToRid(requestContext.taskId);
        final XTask task = orientDbGraph.getVertex(taskRid, XTask.class);
        if (task == null)
            throw new ObjectNotFoundException("Task", requestContext.taskId);

        ObjectNode serializedTask = OBJECT_MAPPER.createObjectNode();
        serializedTask.put("Id", RidConverter.convertToJid((ORID)task.asVertex().getId()));
        serializedTask.put("Status", task.getStatus());

        DateFormat dateFormat = DateFormat.getDateTimeInstance();
        serializedTask.put("StartTime", dateFormat.format(task.getStartTime()));

        requestContext.task = serializedTask;
    }

    @Override
    protected String getDescription()
    {
        return "Gets a task by ID.";
    }

    @Override
    protected GetTaskContext parseRequest(OHttpRequest httpRequest) throws IOException
    {
        GetTaskContext requestContext = new GetTaskContext();
        requestContext.taskId = httpRequest.getParameter("TaskId");

        return requestContext;
    }

    @Override
    protected Object serializeResult(GetTaskContext requestContext)
    {
        return requestContext.task;
    }
}