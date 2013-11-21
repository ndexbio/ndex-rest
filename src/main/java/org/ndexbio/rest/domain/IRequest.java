package org.ndexbio.rest.domain;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.VertexFrame;
import java.util.Date;

/*
 * mod 20NOV2013 FJC
 * change cardinality of fromAccount, toAccount, & about properties from >= 1 to 1
 */
public interface IRequest extends VertexFrame
{
    @Adjacency(label = "fromAccount", direction = Direction.IN)
    public IUser getFromAccount();

    @Adjacency(label = "fromAccount", direction = Direction.IN)
    public void setFromAccount(IUser owner);

    @Adjacency(label = "toAccount", direction = Direction.IN)
    public IAccount getToAccount();

    @Adjacency(label = "toAccount", direction = Direction.IN)
    public void setToAccount(IAccount toAccount);

    @Adjacency(label = "about", direction = Direction.IN)
    public IAccount getAbout();

    @Adjacency(label = "about", direction = Direction.IN)
    public void setAbout(IAccount about);

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
}
