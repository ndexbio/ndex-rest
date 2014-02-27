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

import org.ndexbio.common.exceptions.NdexException;
import org.ndexbio.common.exceptions.ObjectNotFoundException;
import org.ndexbio.common.helpers.IdConverter;
import org.ndexbio.common.models.data.ITask;
import org.ndexbio.common.models.data.IUser;
import org.ndexbio.common.models.object.Task;
import org.ndexbio.rest.annotations.ApiDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
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
    * @param newTask
    *            The task to create.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws NdexException
    *            Failed to create the task in the database.
    * @return The newly created task.
    **************************************************************************/
    /*
     * refactored for non-transactional database operation
     */
    @PUT
    @Produces("application/json")
	@ApiDoc("Create a new task owned by the authenticated user based on the supplied JSON task object.")
    public Task createTask(final Task newTask) throws IllegalArgumentException, NdexException
    {
    	Preconditions.checkArgument(null != newTask, 
    			" A task object is required");
        
        
        final ORID userRid = IdConverter.toRid(this.getLoggedInUser().getId());

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
            newTask.setId(IdConverter.toJid((ORID) task.asVertex().getId()));
            return newTask;
        }
        catch (Exception e)
        {
            _logger.error("Error creating task for: " + this.getLoggedInUser().getUsername() + ".", e);
            throw new NdexException("Error creating a task.");
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
    /*
     * refactored for non-transactional database operations
     */
    @DELETE
    @Path("/{taskId}")
    @Produces("application/json")
	@ApiDoc("Delete the task specified by taskId. Errors if no task found or if authenticated user does not own task.")
    public void deleteTask(@PathParam("taskId")final String taskId) throws IllegalArgumentException, ObjectNotFoundException, SecurityException, NdexException
    {
    	Preconditions.checkArgument(!Strings.isNullOrEmpty(taskId), 
    			"A task id is required");
       
        final ORID taskRid = IdConverter.toRid(taskId);

        try
        {
            setupDatabase();
            
            final ITask taskToDelete = _orientDbGraph.getVertex(taskRid, ITask.class);
            if (taskToDelete == null)
                throw new ObjectNotFoundException("Task", taskId);
            else if (!taskToDelete.getOwner().getUsername().equals(this.getLoggedInUser().getUsername()))
                throw new SecurityException("You cannot delete a task you don't own.");
    
            _orientDbGraph.removeVertex(taskToDelete.asVertex());
           
        }
        catch (SecurityException | ObjectNotFoundException onfe)
        {
            throw onfe;
        }
        catch (Exception e)
        {
            if (e.getMessage().indexOf("cluster: null") > -1){
                throw new ObjectNotFoundException("Task", taskId);
            }
            
            _logger.error("Failed to delete task: " + taskId + ".", e);
        
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
	@ApiDoc("Return a JSON task object for the task specified by taskId. Errors if no task found or if authenticated user does not own task.")
    public Task getTask(@PathParam("taskId")final String taskId) throws IllegalArgumentException, SecurityException, NdexException
    {
        if (taskId == null || taskId.isEmpty())
            throw new IllegalArgumentException("No task ID was specified.");

        try
        {
            final ORID taskRid = IdConverter.toRid(taskId);
            
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
	@ApiDoc("Updates the task specified by taskId in the POSTed task JSON structure. Properties of the task are changed to match the properties in the JSON structure. Errors if no task found or if authenticated user does not own task.")
    public void updateTask(final Task updatedTask) throws IllegalArgumentException, ObjectNotFoundException, SecurityException, NdexException
    {
       Preconditions.checkArgument(null != updatedTask, 
    		   "A task is required");
    	
        ORID taskRid = IdConverter.toRid(updatedTask.getId());

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

        }
        catch (SecurityException | ObjectNotFoundException onfe)
        {
            throw onfe;
        }
        catch (Exception e)
        {
            if (e.getMessage().indexOf("cluster: null") > -1){
                throw new ObjectNotFoundException("Task", updatedTask.getId());
            }
            
            _logger.error("Failed to update task: " + updatedTask.getId() + ".", e);
           
            throw new NdexException("Failed to update task: " + updatedTask.getId() + ".");
        }
        finally
        {
            teardownDatabase();
        }
    }
}
