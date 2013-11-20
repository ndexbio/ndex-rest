package org.ndexbio.rest.domain;

import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.VertexFrame;

public interface INode extends VertexFrame
{
    @Property("name")
    public String getName();

    @Property("name")
    public void setName(String name);

    @Property("jdex_id")
    public void setJdexId(String jdexId);

    @Property("jdex_id")
    public String getJdexId();

    @Adjacency(label = "represents")
    public Iterable<ITerm> getRepresents();

    @Adjacency(label = "represents")
    public void addRepresents(ITerm term);
}
