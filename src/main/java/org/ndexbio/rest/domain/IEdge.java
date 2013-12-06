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
    
    @Adjacency(label = "network")
    public void setNetwork(INetwork network);

    @Adjacency(label = "network")
    public INetwork getNetwork();

    @Adjacency(label = "object")
    public void setObject(INode object);

    @Adjacency(label = "object")
    public INode getObject();

    @Adjacency(label = "predicate")
    public void setPredicate(IBaseTerm term);

    @Adjacency(label = "predicate")
    public IBaseTerm getPredicate();

    @Adjacency(label = "subject", direction = Direction.IN)
    public INode setSubject(INode subject);

    @Adjacency(label = "subject", direction = Direction.IN)
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
