package org.ndexbio.rest.services;

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
import org.ndexbio.rest.helpers.RidConverter;
import org.ndexbio.rest.models.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.orientechnologies.orient.core.id.ORID;

@Path("/tasks")
public class TaskService extends NdexService
{
    private static final Logger _logger = LoggerFactory.getLogger(TaskService.class);
    
    
    
    /**************************************************************************
    * Injects the HTTP request into the base class to be used by
    * getLoggedInUser(). 
    * 
    * @param httpRequest
    *            The HTTP request injected by RESTEasy's context.
    **************************************************************************/
    public TaskService(@Context HttpServletRequest httpRequest)
    {
        super(httpRequest);
    }
    
    
    
    /**************************************************************************
    * Creates a task. 
    * 
    * @param newGroup
    *            The task to create.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws NdexException
    *            Failed to create the task in the database.
    * @return The newly created task.
    **************************************************************************/
    @PUT
    @Produces("application/json")
    public Task createTask(final Task newTask) throws IllegalArgumentException, NdexException
    {
        if (newTask == null)
            throw new IllegalArgumentException("The task to create is empty.");
        
        final ORID userRid = RidConverter.convertToRid(this.getLoggedInUser().getId());

        try
        {
            setupDatabase();
            
            final IUser taskOwner = _orientDbGraph.getVertex(userRid, IUser.class);
            
            final ITask task = _orientDbGraph.addVertex("class:task", ITask.class);
            task.setDescription(newTask.getDescription());
            task.setOwner(taskOwner);
            task.setPriority(newTask.getPriority());
            task.setProgress(newTask.getProgress());
            task.setResource(newTask.getResource());
            task.setStatus(newTask.getStatus());
            task.setStartTime(newTask.getCreatedDate());
            task.setType(newTask.getType());

            _orientDbGraph.getBaseGraph().commit();

            newTask.setId(RidConverter.convertToJid((ORID) task.asVertex().getId()));
            return newTask;
        }
        catch (Exception e)
        {
            _logger.error("Failed to create a task for: " + this.getLoggedInUser().getUsername() + ".", e);
            _orientDbGraph.getBaseGraph().rollback(); 
            throw new NdexException("Failed to create a task.");
        }
        finally
        {
            teardownDatabase();
        }
    }

    /**************************************************************************
    * Deletes a task. 
    * 
    * @param taskId
    *            The task ID.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws ObjectNotFoundException
    *            The task doesn't exist.
    * @throws SecurityException
    *            The user doesn't own the task.
    * @throws NdexException
    *            Failed to delete the task from the database.
    **************************************************************************/
    @DELETE
    @Path("/{taskId}")
    @Produces("application/json")
    public void deleteTask(@PathParam("taskId")final String taskId) throws IllegalArgumentException, ObjectNotFoundException, SecurityException, NdexException
    {
        if (taskId == null || taskId.isEmpty())
            throw new IllegalArgumentException("The task ID was not specified.");
        
        final ORID taskRid = RidConverter.convertToRid(taskId);

        try
        {
            setupDatabase();
            
            final ITask taskToDelete = _orientDbGraph.getVertex(taskRid, ITask.class);
            if (taskToDelete == null)
                throw new ObjectNotFoundException("Task", taskId);
            else if (!taskToDelete.getOwner().getUsername().equals(this.getLoggedInUser().getUsername()))
                throw new SecurityException("You cannot delete a task you don't own.");
    
            _orientDbGraph.removeVertex(taskToDelete.asVertex());
            _orientDbGraph.getBaseGraph().commit();
        }
        catch (SecurityException | ObjectNotFoundException onfe)
        {
            throw onfe;
        }
        catch (Exception e)
        {
            if (e.getMessage().indexOf("cluster: null") > -1)
                throw new ObjectNotFoundException("Task", taskId);
            
            _logger.error("Failed to delete task: " + taskId + ".", e);
            _orientDbGraph.getBaseGraph().rollback(); 
            throw new NdexException("Failed to delete a task.");
        }
        finally
        {
            teardownDatabase();
        }
    }

    /**************************************************************************
    * Gets a task by ID.
    * 
    * @param taskId
    *            The task ID.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws SecurityException
    *            The user doesn't own the task.
    * @throws NdexException
    *            Failed to query the database.
    **************************************************************************/
    @GET
    @Path("/{taskId}")
    @Produces("application/json")
    public Task getTask(@PathParam("taskId")final String taskId) throws IllegalArgumentException, SecurityException, NdexException
    {
        if (taskId == null || taskId.isEmpty())
            throw new IllegalArgumentException("No task ID was specified.");

        try
        {
            final ORID taskRid = RidConverter.convertToRid(taskId);
            
            setupDatabase();
            
            final ITask task = _orientDbGraph.getVertex(taskRid, ITask.class);
            if (task != null)
            {
                if (!task.getOwner().getUsername().equals(this.getLoggedInUser().getUsername()))
                    throw new SecurityException("Access denied.");
                else
                    return new Task(task);
            }
        }
        catch (SecurityException se)
        {
            throw se;
        }
        catch (Exception e)
        {
            _logger.error("Failed to get task: " + taskId + ".", e);
            throw new NdexException("Failed to retrieve the task.");
        }
        finally
        {
            teardownDatabase();
        }
        
        return null;
    }

    /**************************************************************************
    * Updates a task.
    * 
    * @param updatedTask
    *            The updated request.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws ObjectNotFoundException
    *            The task doesn't exist.
    * @throws SecurityException
    *            The user doesn't own the task.
    * @throws NdexException
    *            Failed to update the task in the database.
    **************************************************************************/
    @POST
    @Produces("application/json")
    public void updateTask(final Task updatedTask) throws IllegalArgumentException, ObjectNotFoundException, SecurityException, NdexException
    {
        if (updatedTask == null)
            throw new IllegalArgumentException("The task to update is empty.");
        
        ORID taskRid = RidConverter.convertToRid(updatedTask.getId());

        try
        {
            setupDatabase();
            
            final ITask taskToUpdate = _orientDbGraph.getVertex(taskRid, ITask.class);
            if (taskToUpdate == null)
                throw new ObjectNotFoundException("Task", updatedTask.getId());
            else if (!taskToUpdate.getOwner().getUsername().equals(this.getLoggedInUser().getUsername()))
                throw new SecurityException("Access denied.");

            taskToUpdate.setDescription(updatedTask.getDescription());
            taskToUpdate.setPriority(updatedTask.getPriority());
            taskToUpdate.setProgress(updatedTask.getProgress());
            taskToUpdate.setStatus(updatedTask.getStatus());
            taskToUpdate.setType(updatedTask.getType());

            _orientDbGraph.getBaseGraph().commit();
        }
        catch (SecurityException | ObjectNotFoundException onfe)
        {
            throw onfe;
        }
        catch (Exception e)
        {
            if (e.getMessage().indexOf("cluster: null") > -1)
                throw new ObjectNotFoundException("Task", updatedTask.getId());
            
            _logger.error("Failed to update task: " + updatedTask.getId() + ".", e);
            _orientDbGraph.getBaseGraph().rollback(); 
            throw new NdexException("Failed to update task: " + updatedTask.getId() + ".");
        }
        finally
        {
            teardownDatabase();
        }
    }
}
