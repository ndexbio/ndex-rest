package org.ndexbio.rest.domain;

import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.VertexFrame;
import java.util.Date;

public interface ITask extends VertexFrame
{
    @Property("description")
    public String getDescription();
    
    @Property("description")
    public void setDescription(String description);
    
    @Adjacency(label = "taskOwner")
    public IUser getOwner();

    @Adjacency(label = "taskOwner")
    public void setOwner(IUser owner);
    
    @Property("priority")
    public Priority getPriority();
    
    @Property("priority")
    public void setPriority(Priority priority);
    
    @Property("progress")
    public int getProgress();
    
    @Property("progress")
    public void setProgress(int progress);
    
    @Property("resource")
    public String getResource();
    
    @Property("resource")
    public void setResource(String resource);

    @Property("startTime")
    public Date getStartTime();

    @Property("startTime")
    public void setStartTime(Date startTime);

    @Property("status")
    public void setStatus(Status status);

    @Property("status")
    public Status getStatus();
    
    @Property("type")
    public TaskType getType();
    
    @Property("type")
    public void setType(TaskType type);
}
