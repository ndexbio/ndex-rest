package org.ndexbio.rest.domain;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.VertexFrame;

import java.util.Date;

/**
 * @author Dexter Pratt <a href="mailto:dexterpratt.bio@gmail.com">Dexter Pratt</a>
 */

public interface XRequest extends VertexFrame {
    @Adjacency(label = "fromAccount", direction = Direction.IN)
    public Iterable<XUser> getFromAccount();

    @Adjacency(label = "fromAccount", direction = Direction.IN)
    public void addFromAccount(XUser owner);

    @Adjacency(label = "toAccount", direction = Direction.IN)
    public Iterable<XAccount> getToAccount();

    @Adjacency(label = "toAccount", direction = Direction.IN)
    public void addToAccount(XAccount toAccount);

    @Adjacency(label = "about", direction = Direction.IN)
    public Iterable<XUser> getAbout();

    @Adjacency(label = "about", direction = Direction.IN)
    public void addAbout(VertexFrame about);

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
