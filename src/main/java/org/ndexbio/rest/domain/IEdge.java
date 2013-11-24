package org.ndexbio.rest.domain;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.VertexFrame;

public interface IEdge extends VertexFrame
{
    @Property("jdexId")
    public void setJdexId(String jdexId);

    @Property("jdexId")
    public String getJdexId();
    
    @Adjacency(label = "network", direction = Direction.OUT)
    public void setNetwork(INetwork network);

    @Adjacency(label = "network", direction = Direction.OUT)
    public INetwork getNetwork();

    @Adjacency(label = "object", direction = Direction.OUT)
    public void setObject(INode object);

    @Adjacency(label = "object", direction = Direction.OUT)
    public INode getObject();

    @Adjacency(label = "predicate", direction = Direction.OUT)
    public void setPredicate(IBaseTerm term);

    @Adjacency(label = "predicate", direction = Direction.OUT)
    public IBaseTerm getPredicate();

    @Adjacency(label = "subject", direction = Direction.OUT)
    public INode setSubject(INode subject);

    @Adjacency(label = "subject", direction = Direction.OUT)
    public INode getSubject();
    
    @Adjacency(label = "supports", direction = Direction.IN)
    public void addSupport(ISupport support);

    @Adjacency(label = "supports", direction = Direction.IN)
    public Iterable<ISupport> getSupports();
    
    @Adjacency(label = "supports", direction = Direction.IN)
    public void removeSupport(ISupport support);
    
    @Adjacency(label = "citations", direction = Direction.IN)
    public void addCitation(ICitation citation);

    @Adjacency(label = "citations", direction = Direction.IN)
    public Iterable<ICitation> getCitations();
    
    @Adjacency(label = "citations", direction = Direction.IN)
    public void removeCitation(ICitation citation);
    
}
