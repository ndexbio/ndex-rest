package org.ndexbio.rest.domain;

import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.VertexFrame;
import java.util.Date;

public interface ITask extends VertexFrame
{
    @Adjacency(label = "owner")
    public IUser getOwner();

    @Adjacency(label = "owner")
    public void setOwner(IUser owner);

    @Property("startTime")
    public Date getStartTime();

    @Property("startTime")
    public void setStartTime(Date startTime);

    @Property("status")
    public void setStatus(String status);

    @Property("status")
    public String getStatus();
}
