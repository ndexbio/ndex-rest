package org.ndexbio.rest.domain;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.VertexFrame;

public interface IEdge extends VertexFrame
{
    @Adjacency(label = "network", direction = Direction.OUT)
    public void addNetwork(INetwork network);

    @Adjacency(label = "network", direction = Direction.OUT)
    public Iterable<INetwork> getNetworks();

    @Adjacency(label = "network", direction = Direction.OUT)
    public void removeNetwork(INetwork network);

    @Adjacency(label = "object", direction = Direction.OUT)
    public void addObject(INode object);

    @Adjacency(label = "object", direction = Direction.OUT)
    public Iterable<INode> getObject();

    @Adjacency(label = "object", direction = Direction.OUT)
    public void removeObject(INode object);

    @Adjacency(label = "predicate", direction = Direction.OUT)
    public void addPredicate(ITerm term);

    @Adjacency(label = "predicate", direction = Direction.OUT)
    public Iterable<ITerm> getPredicate();
    
    @Adjacency(label = "predicate", direction = Direction.OUT)
    public void removePredicate(ITerm term);

    @Adjacency(label = "subject", direction = Direction.OUT)
    public INode addSubject(INode subject);

    @Adjacency(label = "subject", direction = Direction.OUT)
    public Iterable<INode> getSubject();
    
    @Adjacency(label = "subject", direction = Direction.OUT)
    public void removeSubject(INode subject);
}
