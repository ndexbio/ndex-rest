package org.ndexbio.rest.domain;

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

    @Adjacency(label = "supportCitation")
    public void setSupportCitation(ICitation citation);

    @Adjacency(label = "supportCitation")
    public ICitation getSupportCitation();
    
    @Adjacency(label = "ndexEdges")
    public void addNdexEdge(IEdge edge);

    @Adjacency(label = "ndexEdges")
    public Iterable<IEdge> getNdexEdges();
    
    @Adjacency(label = "ndexEdges")
    public void removeNdexEdge(IEdge edge);
}
