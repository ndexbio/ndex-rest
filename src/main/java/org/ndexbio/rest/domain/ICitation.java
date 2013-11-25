package org.ndexbio.rest.domain;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.VertexFrame;

import java.util.List;

public interface ICitation extends VertexFrame
{
    @Property("contributors")
    public List<String> getContributors();

    @Property("contributors")
    public void setContributors(List<String> contributors);

    @Property("identifier")
    public String getIdentifier();

    @Property("identifier")
    public void setIdentifier(String identifier);

    @Property("jdexId")
    public String getJdexId();

    @Property("jdexId")
    public void setJdexId(String jdexId);

    @Property("title")
    public String getTitle();

    @Property("title")
    public void setTitle(String title);

    @Property("type")
    public String getType();

    @Property("type")
    public void setType(String type);
    
    @Adjacency(label = "ndexEdges", direction = Direction.OUT)
    public void addNdexEdge(IEdge edge);

    @Adjacency(label = "ndexEdges", direction = Direction.OUT)
    public Iterable<IEdge> getNdexEdges();
    
    @Adjacency(label = "ndexEdges", direction = Direction.OUT)
    public void removeNdexEdge(IEdge edge);
}
