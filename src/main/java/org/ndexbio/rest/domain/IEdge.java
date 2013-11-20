package org.ndexbio.rest.domain;

import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.VertexFrame;

public interface IEdge extends VertexFrame
{
    @Adjacency(label = "n")
    public Iterable<INetwork> getNetworks();

    @Adjacency(label = "n")
    public void addNetwork(INetwork network);

    @Adjacency(label = "s")
    public Iterable<INode> getSubject();

    @Adjacency(label = "s")
    public INode addSubject(INode node);

    @Adjacency(label = "o")
    public Iterable<INode> getObject();

    @Adjacency(label = "o")
    public void addObject(INode node);

    @Adjacency(label = "p")
    public void addPredicate(ITerm term);

    @Adjacency(label = "p")
    public Iterable<ITerm> getPredicate();
}
