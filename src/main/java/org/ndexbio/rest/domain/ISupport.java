package org.ndexbio.rest.domain;

import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.VertexFrame;

public interface ISupport extends VertexFrame
{
    @Property("jdexId")
    public void setJdexId(String jdexId);

    @Property("jdexId")
    public String getJdexId();

    @Property("text")
    public void setText(String text);

    @Property("text")
    public String getText();
}
