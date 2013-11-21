package org.ndexbio.rest.domain;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.VertexFrame;
import java.util.Date;

public interface IRequest extends VertexFrame
{
    @Adjacency(label = "about", direction = Direction.IN)
    public IAccount getAbout();

    @Adjacency(label = "about", direction = Direction.IN)
    public void setAbout(IAccount about);

    @Adjacency(label = "fromAccount", direction = Direction.IN)
    public IUser getFromAccount();

    @Adjacency(label = "fromAccount", direction = Direction.IN)
    public void setFromAccount(IUser owner);

    @Property("message")
    public void setMessage(String message);

    @Property("message")
    public String getMessage();

    @Property("requestType")
    public String getRequestType();

    @Property("requestType")
    public void setRequestType(String requestType);

    @Property("requestTime")
    public Date getRequestTime();

    @Property("requestTime")
    public void setRequestTime(Date startTime);

    @Adjacency(label = "toAccount", direction = Direction.IN)
    public IAccount getToAccount();

    @Adjacency(label = "toAccount", direction = Direction.IN)
    public void setToAccount(IAccount toAccount);
}
