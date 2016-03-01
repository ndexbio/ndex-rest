/**
 * Copyright (c) 2013, 2016, The Regents of the University of California, The Cytoscape Consortium
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */
package org.ndexbio.rest.services;

import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.TaskType;
import org.ndexbio.model.object.Task;

import com.orientechnologies.orient.core.id.ORID;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestTaskService extends TestNdexService
{
    private static final TaskService _taskService = new TaskService(_mockRequest);

    
  /*  
    @Test
    public void createTask()
    {
        Assert.assertTrue(createNewTask());
    }

    @Test(expected = IllegalArgumentException.class)
    public void createTaskInvalid() throws IllegalArgumentException, NdexException
    {
 //       _taskService.createTask(null);
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
 //       _taskService.deleteTask("");
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
   //     _taskService.getTask("");
    }
*/
//    @Test
/*    public void updateTask()
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
*/
 /*   @Test(expected = IllegalArgumentException.class)
    public void updateTaskInvalid() throws IllegalArgumentException, NdexException
    {
  //      _taskService.updateTask(null);
    }
    
    
    
    private boolean createNewTask()
    {
        final Task newTask = new Task();
        newTask.setDescription("This is a test task.");
        newTask.setType(TaskType.PROCESS_UPLOADED_NETWORK);
        
        try
        {
        //    final Task createdTask = _taskService.createTask(newTask);
          //  Assert.assertNotNull(createdTask);
            
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
          //  _taskService.deleteTask(taskId);
          //  Assert.assertNull(_taskService.getTask(taskId));
            
            return true;
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
        
        return false;
    } */
}
