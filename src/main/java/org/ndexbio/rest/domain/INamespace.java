package org.ndexbio.rest.domain;

import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.VertexFrame;

public interface INamespace extends VertexFrame
{
    @Property("jdexId")
    public String getJdexId();

    @Property("jdexId")
    public void setJdexId(String jdexId);

    @Property("prefix")
    public String getPrefix();

    @Property("prefix")
    public void setPrefix(String prefix);

    @Property("uri")
    public void setUri(String uri);

    @Property("uri")
    public String getUri();
}
