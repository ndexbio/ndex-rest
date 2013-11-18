package org.ndexbio.rest.domain;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.VertexFrame;

import java.util.Date;

/**
 * @author Andrey Lomakin <a href="mailto:lomakin.andrey@gmail.com">Andrey Lomakin</a>
 * @since 11/1/13
 */
public interface XTask extends VertexFrame {
    @Adjacency(label = "owner", direction = Direction.IN)
    public Iterable<XUser> getOwner();

    @Adjacency(label = "owner", direction = Direction.IN)
    public void addOwner(XUser owner);

    @Property("status")
    public void setStatus(String status);

    @Property("status")
    public String getStatus();

    @Property("startTime")
    public Date getStartTime();

    @Property("startTime")
    public void setStartTime(Date startTime);
}
