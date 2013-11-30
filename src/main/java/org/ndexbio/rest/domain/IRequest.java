package org.ndexbio.rest.domain;

import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.VertexFrame;
import com.tinkerpop.frames.modules.typedgraph.TypeField;
import java.util.Date;

@TypeField("requestType")
public interface IRequest extends VertexFrame
{
    @Property("message")
    public String getMessage();

    @Property("message")
    public void setMessage(String message);

    @Property("requestTime")
    public Date getRequestTime();

    @Property("requestTime")
    public void setRequestTime(Date startTime);
    
    @Property("responder")
    public String getResponder();
    
    @Property("responder")
    public void setResponder(String responder);
    
    @Property("response")
    public String getResponse();
    
    @Property("response")
    public void setResponse(String response);
}
