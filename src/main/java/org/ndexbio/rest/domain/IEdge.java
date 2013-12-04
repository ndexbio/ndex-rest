package org.ndexbio.rest.domain;

import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.VertexFrame;

public interface IEdge extends VertexFrame
{
    @Property("jdexId")
    public void setJdexId(String jdexId);

    @Property("jdexId")
    public String getJdexId();
    
    @Adjacency(label = "network")
    public void setNetwork(INetwork network);

    @Adjacency(label = "network")
    public INetwork getNetwork();

    @Adjacency(label = "o")
    public void setObject(INode object);

    @Adjacency(label = "o")
    public INode getObject();

    @Adjacency(label = "p")
    public void setPredicate(IBaseTerm term);

    @Adjacency(label = "p")
    public IBaseTerm getPredicate();

    @Adjacency(label = "s")
    public INode setSubject(INode subject);

    @Adjacency(label = "s")
    public INode getSubject();
    
    @Adjacency(label = "supports")
    public void addSupport(ISupport support);

    @Adjacency(label = "supports")
    public Iterable<ISupport> getSupports();
    
    @Adjacency(label = "supports")
    public void removeSupport(ISupport support);
    
    @Adjacency(label = "citations")
    public void addCitation(ICitation citation);

    @Adjacency(label = "citations")
    public Iterable<ICitation> getCitations();
    
    @Adjacency(label = "citations")
    public void removeCitation(ICitation citation);
    
}
