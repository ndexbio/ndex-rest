package org.ndexbio.rest.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.ndexbio.rest.domain.ITask;
import org.ndexbio.rest.domain.Priority;
import org.ndexbio.rest.domain.Status;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Task extends NdexObject
{
    private String _description;
    private User _owner;
    private Priority _priority;
    private int _progress;
    private String _resource;
    private Status _status;

    
    
    /**************************************************************************
    * Default constructor.
    **************************************************************************/
    public Task()
    {
        super();
    }
    
    /**************************************************************************
    * Populates the class (from the database) and removes circular references.
    * 
    * @param task The Task with source data.
    **************************************************************************/
    public Task(ITask task)
    {
        super(task);
        
        this.setCreatedDate(task.getStartTime());
        
        _description = task.getDescription();
        _owner = new User(task.getOwner());
        _priority = task.getPriority();
        _progress = task.getProgress();
        _resource = task.getResource();
        _status = task.getStatus();
    }


    
    public String getDescription()
    {
        return _description;
    }
    
    public void setDescription(String description)
    {
        _description = description;
    }
    
    public User getOwner()
    {
        return _owner;
    }

    public void setOwner(User owner)
    {
        _owner = owner;
    }
    
    public Priority getPriority()
    {
        return _priority;
    }
    
    public void setPriority(Priority priority)
    {
        _priority = priority;
    }
    
    public int getProgress()
    {
        return _progress;
    }
    
    public void setProgress(int progress)
    {
        _progress = progress;
    }
    
    public String getResource()
    {
        return _resource;
    }
    
    public void setResource(String resource)
    {
        _resource = resource;
    }

    public Status getStatus()
    {
        return _status;
    }

    public void setStatus(Status status)
    {
        _status = status;
    }
}
