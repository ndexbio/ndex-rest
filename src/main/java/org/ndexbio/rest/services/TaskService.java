package org.ndexbio.rest.services;

import java.util.Collection;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import org.ndexbio.rest.domain.ITask;
import org.ndexbio.rest.domain.IUser;
import org.ndexbio.rest.exceptions.NdexException;
import org.ndexbio.rest.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.helpers.RidConverter;
import org.ndexbio.rest.models.Task;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.OCommandSQL;
import com.tinkerpop.blueprints.Vertex;

/*
 * class represents a service that supports  RESTful operations to perform
 * CRUD actions for task entities in the OrientDB  database
 * subclass of abstract class NdexService
 * FJC 19NOV2013
 */

@Path("/tasks")
public class TaskService extends NdexService
{
    public TaskService()
    {
        super();
    }

    

    @DELETE
    @Path("/{taskId}")
    @Produces("application/json")
    public void deleteTask(@PathParam("taskId")final String taskJid) throws Exception
    {
        final ORID taskId = RidConverter.convertToRid(taskJid);

        final Vertex taskToDelete = _orientDbGraph.getVertex(taskId);
        if (taskToDelete == null)
            throw new ObjectNotFoundException("Task", taskJid);

        deleteVertex(taskToDelete);
    }

    @GET
    @Path("/{taskId}")
    @Produces("application/json")
    public Task getTask(@PathParam("taskId")final String taskJid) throws NdexException
    {
        final ORID taskId = RidConverter.convertToRid(taskJid);
        final ITask task = _orientDbGraph.getVertex(taskId, ITask.class);

        if (task == null)
        {
            final Collection<ODocument> matchingtasks = _orientDbGraph.getBaseGraph().command(new OCommandSQL("select from xtask where taskname = ?")).execute(taskJid);

            if (matchingtasks.size() < 1)
                return null;
            else
                return new Task(_orientDbGraph.getVertex(matchingtasks.toArray()[0], ITask.class));
        }
        else
            return new Task(task);
    }

    @POST
    @Produces("application/json")
    public void updateTask(final Task updatedTask) throws Exception
    {
        ORID taskRid = RidConverter.convertToRid(updatedTask.getId());

        final ITask taskToUpdate = _orientDbGraph.getVertex(taskRid, ITask.class);
        if (taskToUpdate == null)
            throw new ObjectNotFoundException("Task", updatedTask.getId());

        try
        {
            taskToUpdate.setStartTime(updatedTask.getCreatedDate());
            taskToUpdate.setStatus(updatedTask.getStatus());

            // TODO map remaining Task fields to ITask
            _orientDbGraph.getBaseGraph().commit();
        }
        catch (Exception e)
        {
            handleOrientDbException(e);
        }
        finally
        {
            closeOrientDbConnection();
        }
    }

    @PUT
    @Produces("application/json")
    public Task createTask(final String ownerId, final Task newTask) throws Exception
    {
        ORID userRid = RidConverter.convertToRid(ownerId);

        final IUser taskOwner = _orientDbGraph.getVertex(userRid, IUser.class);
        if (taskOwner == null)
            throw new ObjectNotFoundException("User", ownerId);


        try
        {
            final ITask task = _orientDbGraph.addVertex("class:task", ITask.class);
            task.setStatus(newTask.getStatus());
            task.setStartTime(newTask.getCreatedDate());

            _orientDbGraph.getBaseGraph().commit();

            newTask.setId(RidConverter.convertToJid((ORID) task.asVertex().getId()));
            return newTask;
        }
        catch (Exception e)
        {
            handleOrientDbException(e);
        }
        finally
        {
            closeOrientDbConnection();
        }
        return newTask;
    }
}
