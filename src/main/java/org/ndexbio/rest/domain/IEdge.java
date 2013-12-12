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
    
    @Adjacency(label = "edgeNetwork")
    public void setNetwork(INetwork network);

    @Adjacency(label = "edgeNetwork")
    public INetwork getNetwork();

    @Adjacency(label = "edgeObject")
    public void setObject(INode object);

    @Adjacency(label = "edgeObject")
    public INode getObject();

    @Adjacency(label = "edgePredicate")
    public void setPredicate(IBaseTerm term);

    @Adjacency(label = "edgePredicate")
    public IBaseTerm getPredicate();

    @Adjacency(label = "edgeSubject", direction = Direction.IN)
    public INode setSubject(INode subject);

    @Adjacency(label = "edgeSubject", direction = Direction.IN)
    public INode getSubject();
    
    @Adjacency(label = "edgeSupports")
    public void addSupport(ISupport support);

    @Adjacency(label = "edgeSupports")
    public Iterable<ISupport> getSupports();
    
    @Adjacency(label = "edgeSupports")
    public void removeSupport(ISupport support);
    
    @Adjacency(label = "edgeCitations")
    public void addCitation(ICitation citation);

    @Adjacency(label = "edgeCitations")
    public Iterable<ICitation> getCitations();
    
    @Adjacency(label = "edgeCitations")
    public void removeCitation(ICitation citation);
    
}
