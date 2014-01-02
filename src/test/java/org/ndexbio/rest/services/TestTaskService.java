package org.ndexbio.rest.services;

import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.ndexbio.common.exceptions.NdexException;
import org.ndexbio.common.models.object.Task;
import org.ndexbio.rest.domain.TaskType;
import org.ndexbio.rest.helpers.IdConverter;
import com.orientechnologies.orient.core.id.ORID;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestTaskService extends TestNdexService
{
    private static final TaskService _taskService = new TaskService(_mockRequest);

    
    
    @Test
    public void createTask()
    {
        Assert.assertTrue(createNewTask());
    }

    @Test(expected = IllegalArgumentException.class)
    public void createTaskInvalid() throws IllegalArgumentException, NdexException
    {
        _taskService.createTask(null);
    }

    @Test
    public void deleteTask()
    {
        Assert.assertTrue(createNewTask());

        final ORID testTaskRid = getRid("This is a test task.");
        Assert.assertTrue(deleteTargetTask(IdConverter.toJid(testTaskRid)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void deleteTaskInvalid() throws IllegalArgumentException, NdexException
    {
        _taskService.deleteTask("");
    }

    @Test
    public void getTask()
    {
        try
        {
            Assert.assertTrue(createNewTask());
            
            final ORID testTaskRid = getRid("This is a test task.");
            final Task testTask = _taskService.getTask(IdConverter.toJid(testTaskRid));
            Assert.assertNotNull(testTask);

            Assert.assertTrue(deleteTargetTask(testTask.getId()));
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void getTaskInvalid() throws IllegalArgumentException, NdexException
    {
        _taskService.getTask("");
    }

    @Test
    public void updateTask()
    {
        try
        {
            Assert.assertTrue(createNewTask());
            
            final ORID testTaskRid = getRid("This is a test task.");
            final Task testTask = _taskService.getTask(IdConverter.toJid(testTaskRid));

            testTask.setDescription("This is an updated test task.");
            _taskService.updateTask(testTask);
            Assert.assertEquals(_taskService.getTask(testTask.getId()).getDescription(), testTask.getDescription());
            
            Assert.assertTrue(deleteTargetTask(testTask.getId()));
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void updateTaskInvalid() throws IllegalArgumentException, NdexException
    {
        _taskService.updateTask(null);
    }
    
    
    
    private boolean createNewTask()
    {
        final Task newTask = new Task();
        newTask.setDescription("This is a test task.");
        newTask.setType(TaskType.PROCESS_UPLOADED_NETWORK);
        
        try
        {
            final Task createdTask = _taskService.createTask(newTask);
            Assert.assertNotNull(createdTask);
            
            return true;
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
        
        return false;
    }
    
    private boolean deleteTargetTask(String taskId)
    {
        try
        {
            _taskService.deleteTask(taskId);
            Assert.assertNull(_taskService.getTask(taskId));
            
            return true;
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
        
        return false;
    }
}
