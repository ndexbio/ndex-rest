package org.ndexbio.rest.services;

import java.util.Collection;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import org.ndexbio.rest.domain.ITask;
import org.ndexbio.rest.domain.IUser;
import org.ndexbio.rest.exceptions.NdexException;
import org.ndexbio.rest.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.exceptions.ValidationException;
import org.ndexbio.rest.helpers.RidConverter;
import org.ndexbio.rest.models.Task;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.OCommandSQL;
import com.tinkerpop.blueprints.Vertex;

@Path("/tasks")
public class TaskService extends NdexService
{
    /**************************************************************************
    * Injects the HTTP request into the base class to be used by
    * getLoggedInUser(). 
    * 
    * @param httpRequest The HTTP request injected by RESTEasy's context.
    **************************************************************************/
    public TaskService(@Context HttpServletRequest httpRequest)
    {
        super(httpRequest);
    }
    
    
    
    /**************************************************************************
    * Creates a task. 
    * 
    * @param ownerId  The owner's ID.
    * @param newGroup The task to create.
    **************************************************************************/
    @PUT
    @Produces("application/json")
    public Task createTask(final String ownerId, final Task newTask) throws Exception
    {
        final ORID userRid = RidConverter.convertToRid(ownerId);

        final IUser taskOwner = _orientDbGraph.getVertex(userRid, IUser.class);
        if (taskOwner == null)
            throw new ObjectNotFoundException("User", ownerId);

        try
        {
            setupDatabase();
            
            final ITask task = _orientDbGraph.addVertex("class:task", ITask.class);
            task.setStatus(newTask.getStatus());
            task.setStartTime(newTask.getCreatedDate());

            _orientDbGraph.getBaseGraph().commit();

            newTask.setId(RidConverter.convertToJid((ORID) task.asVertex().getId()));
            return newTask;
        }
        catch (Exception e)
        {
            _orientDbGraph.getBaseGraph().rollback(); 
            throw e;
        }
        finally
        {
            teardownDatabase();
        }
    }

    /**************************************************************************
    * Deletes a task. 
    * 
    * @param taskId The ID of the task to delete.
    **************************************************************************/
    @DELETE
    @Path("/{taskId}")
    @Produces("application/json")
    public void deleteTask(@PathParam("taskId")final String taskJid) throws Exception
    {
        final ORID taskId = RidConverter.convertToRid(taskJid);

        try
        {
            setupDatabase();
            
            final Vertex taskToDelete = _orientDbGraph.getVertex(taskId);
            if (taskToDelete == null)
                throw new ObjectNotFoundException("Task", taskJid);
    
            //TODO: Need to remove orphaned vertices
            _orientDbGraph.removeVertex(taskToDelete);
            _orientDbGraph.getBaseGraph().commit();
        }
        catch (Exception e)
        {
            _orientDbGraph.getBaseGraph().rollback(); 
            throw e;
        }
        finally
        {
            teardownDatabase();
        }
    }

    /**************************************************************************
    * Gets a request by ID.
    * 
    * @param requestId The ID of the request.
    **************************************************************************/
    @GET
    @Path("/{taskId}")
    @Produces("application/json")
    public Task getTask(@PathParam("taskId")final String taskJid) throws NdexException
    {
        if (taskJid == null || taskJid.isEmpty())
            throw new ValidationException("No task ID was specified.");

        try
        {
            final ORID taskId = RidConverter.convertToRid(taskJid);
            
            setupDatabase();
            
            final ITask task = _orientDbGraph.getVertex(taskId, ITask.class);
            if (task != null)
                return new Task(task);
        }
        catch (ValidationException ve)
        {
            //The task ID is actually a task name
            final Collection<ODocument> matchingtasks = _orientDbGraph
                .getBaseGraph()
               .command(new OCommandSQL("select from Task where taskname = ?"))
               .execute(taskJid);

           if (matchingtasks.size() > 0)
               return new Task(_orientDbGraph.getVertex(matchingtasks.toArray()[0], ITask.class));
        }
        finally
        {
            teardownDatabase();
        }
        
        return null;
    }

    /**************************************************************************
    * Updates a request.
    * 
    * @param updatedRequest The updated request information.
    **************************************************************************/
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

            //TODO: Map remaining Task fields to ITask
            _orientDbGraph.getBaseGraph().commit();
        }
        catch (Exception e)
        {
            _orientDbGraph.getBaseGraph().rollback(); 
            throw e;
        }
        finally
        {
            teardownDatabase();
        }
    }
}
