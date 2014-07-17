package org.ndexbio.rest.services;

import java.util.Date;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;

import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;
import org.ndexbio.common.exceptions.NdexException;
import org.ndexbio.common.exceptions.ObjectNotFoundException;
import org.ndexbio.common.helpers.IdConverter;
import org.ndexbio.common.models.data.INetwork;
import org.ndexbio.common.models.data.ITask;
import org.ndexbio.common.models.data.IUser;
import org.ndexbio.common.models.object.Status;
import org.ndexbio.common.models.object.TaskType;
import org.ndexbio.model.object.Task;
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
/*    @PUT
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
*/
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
/*    @DELETE
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
*/
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
/*    @GET
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
*/
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
/*    @POST
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
    
	@PUT
	@Path("/{taskId}/status/{status}")
	@Produces("application/json")
	@ApiDoc("Sets the status of the task, throws exception if status is not recognized.")
	public Task setTaskStatus(@PathParam("taskId") final String taskId,
			@PathParam("status") final String status) throws NdexException {
		Preconditions.checkArgument(!Strings.isNullOrEmpty(taskId),
				"A task ID is required");

		setupDatabase();
		try {
			ORID taskRid = IdConverter.toRid(taskId);
			final ITask taskToUpdate = _orientDbGraph.getVertex(taskRid,
					ITask.class);
			if (taskToUpdate == null)
				throw new ObjectNotFoundException("Task", taskId);
			else if (!taskToUpdate.getOwner().getUsername()
					.equals(this.getLoggedInUser().getUsername()))
				throw new SecurityException("Access denied.");

			if (!isValidTaskStatus(status))
				throw new IllegalArgumentException(status
						+ " is not a known TaskStatus");

			Status s = Status.valueOf(status);

			taskToUpdate.setStatus(s);
			Task updatedTask = new Task(taskToUpdate);
			return updatedTask;
		} catch (Exception e) {
			_logger.error("Error changing task status for: "
					+ this.getLoggedInUser().getUsername() + ".", e);
			throw new NdexException("Error changing task status.");
		} finally {
			teardownDatabase();
		}
	}

	private boolean isValidTaskStatus(String status) {
		for (Status value : Status.values()) {
			if (value.name().equals(status)) {
				return true;
			}
		}

		return false;
	}
	
  */  
    
	/**************************************************************************
	 * Exports a network to an xbel-formatted file. Creates a network upload task
	 * 
	 * @param networkId
	 *            The id of the network to export
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws NdexException
	 *             Failed to create a network export task
	 **************************************************************************/
   /* @PUT
    @Path("/exportNetwork/xbel/{networkId}")
    @Produces("application/json")
	@ApiDoc("Creates a queued task  for asynchronous exporting of a NDEx network to an external "
			+ "XML file meeting the XBEL validation rules. An Exception is thrown if an invalid "
			+ "network id is specified")
	public Task createXBELExportNetworkTask(@PathParam("networkId")final String networkId)
			throws IllegalArgumentException, SecurityException, NdexException {

		
			Preconditions
					.checkArgument(!Strings.isNullOrEmpty(networkId), "A network ID is required");
		
			setupDatabase();




				try {
					final IUser taskOwner = _orientDbGraph.getVertex(
							IdConverter.toRid(this.getLoggedInUser().getId()),
							IUser.class);
					
					final INetwork network = _orientDbGraph.getVertex(
							IdConverter.toRid(networkId), INetwork.class);
					if (network == null)
						throw new ObjectNotFoundException("Network", networkId);
					
					
					ITask processNetworkTask = _orientDbGraph.addVertex(
							"class:task", ITask.class);
					processNetworkTask.setDescription(network.getName() + ".xbel");
					processNetworkTask.setType(TaskType.EXPORT_NETWORK_TO_FILE);
					processNetworkTask.setOwner(taskOwner);
					processNetworkTask.setPriority(Priority.LOW);
					processNetworkTask.setProgress(0);
					processNetworkTask.setResource(networkId);
					processNetworkTask.setStartTime(new Date());
					processNetworkTask.setStatus(Status.QUEUED);
					// retain commit statement for planned return to transaction-based operation
					_orientDbGraph.getBaseGraph().commit();
					Task newTask = new Task(processNetworkTask);
					return newTask;
				} 
				catch (Exception e)
		        {
		            _logger.error("Error creating task for: " + this.getLoggedInUser().getUsername() + ".", e);
		            throw new NdexException("Error creating a task.");
		        } 
				finally {
					teardownDatabase();
				}
			
		
	}  */
}
