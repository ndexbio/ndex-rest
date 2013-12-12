package org.ndexbio.rest.domain;

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
    
    @Adjacency(label = "citationEdges")
    public void addNdexEdge(IEdge edge);

    @Adjacency(label = "citationEdges")
    public Iterable<IEdge> getNdexEdges();
    
    @Adjacency(label = "citationEdges")
    public void removeNdexEdge(IEdge edge);
    
    @Adjacency(label = "citationSupports")
    public void addSupport(ISupport support);

    @Adjacency(label = "citationSupports")
    public Iterable<ISupport> getSupports();
    
    @Adjacency(label = "citationSupports")
    public void removeSupport(ISupport support);
}
