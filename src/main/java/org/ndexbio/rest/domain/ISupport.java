package org.ndexbio.rest.domain;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.frames.Adjacency;
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

    @Property("citation")
    public void setCitation(ICitation citation);

    @Property("citation")
    public ICitation getCitation();
    
    @Adjacency(label = "ndexEdges", direction = Direction.OUT)
    public void addNdexEdge(IEdge edge);

    @Adjacency(label = "ndexEdges", direction = Direction.OUT)
    public Iterable<IEdge> getNdexEdges();
    
    @Adjacency(label = "ndexEdges", direction = Direction.OUT)
    public void removeNdexEdge(IEdge edge);
}
