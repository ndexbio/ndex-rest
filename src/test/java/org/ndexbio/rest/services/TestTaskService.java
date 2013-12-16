package org.ndexbio.rest.services;

import javax.servlet.http.HttpServletRequest;
import org.easymock.EasyMock;
import org.junit.Test;

public class TestTaskService
{
    private static final HttpServletRequest _mockRequest = EasyMock.createMock(HttpServletRequest.class);
    private static final TaskService _taskService = new TaskService(_mockRequest);

    
    
    @Test
    public void createTask()
    {
    }

    @Test
    public void createTaskInvalid()
    {
    }

    @Test
    public void deleteTask()
    {
    }

    @Test
    public void deleteTaskInvalid()
    {
    }

    @Test
    public void getTask()
    {
    }

    @Test
    public void getTaskInvalid()
    {
    }

    @Test
    public void updateTask()
    {
    }

    @Test
    public void updateTaskInvalid()
    {
    }
}
