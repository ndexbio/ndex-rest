package org.ndexbio.rest.models;

import org.ndexbio.rest.domain.ITask;

public class Task extends NdexObject
{
    private String ownerId;
    private String status;

    
    
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
        
        this.setOwnerId(resolveVertexId(task.getOwner()));
        this.setStatus(task.getStatus());
        this.setCreatedDate(task.getStartTime());
    }

    
    
    public String getOwnerId()
    {
        return ownerId;
    }

    public void setOwnerId(String ownerId)
    {
        this.ownerId = ownerId;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }
}
