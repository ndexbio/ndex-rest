package org.ndexbio.rest.services;

import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;

import org.ndexbio.common.access.NdexAOrientDBConnectionPool;
import org.ndexbio.common.exceptions.NdexException;
import org.ndexbio.common.exceptions.ObjectNotFoundException;
import org.ndexbio.common.models.dao.orientdb.TaskDAO;
import org.ndexbio.model.object.Task;
import org.ndexbio.rest.annotations.ApiDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

@Path("/task")
public class TaskService extends NdexService
{
    private static final Logger _logger = LoggerFactory.getLogger(TaskService.class);
//	private  TaskDAO dao;
//	private  ODatabaseDocumentTx  localConnection;  //all DML will be in this connection, in one transaction.
    
    
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
    @POST
    @Produces("application/json")
	@ApiDoc("Create a new task owned by the authenticated user based on the supplied JSON task object.")
    public UUID createTask(final Task newTask) throws IllegalArgumentException, NdexException
    {
    	Preconditions.checkArgument(null != newTask, 
    			" A task object is required");
        
        
        final String userAccount = this.getLoggedInUser().getAccountName();

        try (TaskDAO dao = new TaskDAO(NdexAOrientDBConnectionPool.getInstance().acquire()))    {
            UUID taskId = dao.createTask(userAccount, newTask);
            
            dao.commit();
            _logger.info("task " + taskId + " created for " + newTask.getType());
            return taskId;
        }
        catch (Exception e)
        {
            _logger.error("Error creating task for: " + userAccount + ".", e);
            throw new NdexException("Error creating a task.");
        }
    }
    

/*    
	@PUT
	@Path("/{taskId}/status/{status}")
	@Produces("application/json")
	@ApiDoc("Sets the status of the task, throws exception if status is not recognized.")
	public Task updateTaskStatus(@PathParam("status") final String status,
			@PathParam("taskId") final String taskId) throws NdexException {
		
		Preconditions.checkArgument(!Strings.isNullOrEmpty(taskId),
				"A task ID is required");

		
		try (TaskDAO dao = new TaskDAO(NdexAOrientDBConnectionPool.getInstance().acquire())){
			
			Status s = Status.valueOf(status);

			Task t= dao.updateTaskStatus(s,taskId, this.getLoggedInUser());
			
			dao.commit();
			return t;
			
		} catch (Exception e) {
			_logger.error("Error changing task status for: "
					+ this.getLoggedInUser().getAccountName() + ".", e);
			throw new NdexException("Error changing task status." + e.getMessage());
			
		} 
	}
*/	
/*	
    private void openDatabase() throws NdexException {
//		database = new NdexDatabase();
		localConnection = NdexAOrientDBConnectionPool.getInstance().acquire();
//		graph = new OrientGraph(localConnection);
		dao = new TaskDAO(localConnection);
	}
	private void closeDatabase() {
		localConnection.close();
//		database.close();
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
    @DELETE
    @Path("/{taskId}")
    @Produces("application/json")
	@ApiDoc("Delete the task specified by taskId. Errors if no task found or if authenticated user does not own task.")
    public void deleteTask(@PathParam("taskId")final String taskUUID) throws IllegalArgumentException, ObjectNotFoundException, SecurityException, NdexException
    {
    	Preconditions.checkArgument(!Strings.isNullOrEmpty(taskUUID), 
    			"A task id is required");
       
    	
    	try (TaskDAO tdao= new TaskDAO(NdexAOrientDBConnectionPool.getInstance().acquire())) {
//			_logger.info("Starting delete for task " + taskUUID);
            
            final Task taskToDelete = tdao.getTaskByUUID(taskUUID);
            
            if (taskToDelete == null)
                throw new ObjectNotFoundException("Task", taskUUID);
            else if (!taskToDelete.getTaskOwnerId().equals(this.getLoggedInUser().getExternalId()))
                throw new SecurityException("You cannot delete a task you don't own.");
    
            tdao.deleteTask(taskToDelete.getExternalId());
            
            tdao.commit();
            _logger.info("Task " + taskUUID + " is deleted by user " + this.getLoggedInUser().getAccountName());
        }
        catch (SecurityException | ObjectNotFoundException onfe)
        {
            throw onfe;
        }
        catch (Exception e)
        {
        	e.printStackTrace(System.out);
        	if (e.getMessage().indexOf("cluster: null") > -1){
                throw new ObjectNotFoundException("Task", taskUUID);
            }
            
            _logger.error("Failed to delete task: " + taskUUID + ".", e);
        
            throw new NdexException("Failed to delete a task.");
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
    
}
