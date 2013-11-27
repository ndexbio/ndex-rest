package org.ndexbio.rest.domain;

import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.VertexFrame;
import com.tinkerpop.frames.modules.typedgraph.TypeField;
import java.util.Date;

@TypeField("requestType")
public interface IRequest extends VertexFrame
{
    @Property("message")
    public void setMessage(String message);

    @Property("message")
    public String getMessage();

    @Property("requestTime")
    public Date getRequestTime();

    @Property("requestTime")
    public void setRequestTime(Date startTime);
}
