package org.ndexbio.rest.domain;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.VertexFrame;
import java.util.Map;

public interface INetwork extends VertexFrame
{
    @Adjacency(label = "citations", direction = Direction.OUT)
    public void addCitation(ICitation citation);

    @Adjacency(label = "citations", direction = Direction.OUT)
    public Iterable<ICitation> getCitations();
    
    @Adjacency(label = "citations", direction = Direction.OUT)
    public void removeCitation(ICitation citation);

    @Property("format")
    public String getFormat();

    @Property("format")
    public void setFormat(String format);

    @Adjacency(label = "namespaces", direction = Direction.OUT)
    public void addNamespace(INamespace namespace);

    @Adjacency(label = "namespaces", direction = Direction.OUT)
    public Iterable<INamespace> getNamespaces();
    
    @Adjacency(label = "namespaces", direction = Direction.OUT)
    public void removeNamespace(INamespace namespace);

    @Property("ndexEdgeCount")
    public int getNdexEdgeCount();

    @Property("ndexEdgeCount")
    public void setNdexEdgeCount(int edgesCount);

    @Adjacency(label = "ndexEdges", direction = Direction.OUT)
    public void addNdexEdge(IEdge edge);

    @Adjacency(label = "ndexEdges", direction = Direction.OUT)
    public Iterable<IEdge> getNdexEdges();
    
    @Adjacency(label = "ndexEdges", direction = Direction.OUT)
    public void removeNdexEdge(IEdge edge);

    @Property("ndexNodeCount")
    public int getNdexNodeCount();

    @Property("ndexNodeCount")
    public void setNdexNodeCount(int nodesCount);

    @Adjacency(label = "ndexNodes", direction = Direction.OUT)
    public void addNdexNode(INode node);

    @Adjacency(label = "ndexNodes", direction = Direction.OUT)
    public Iterable<INode> getNdexNodes();
    
    @Adjacency(label = "ndexNodes", direction = Direction.OUT)
    public void removeNdexNode(INode node);

    @Adjacency(label = "owners", direction = Direction.IN)
    public Iterable<IUser> getOwners();

    @Property("properties")
    public Map<String, String> getProperties();

    @Property("properties")
    public void setProperties(Map<String, String> properties);

    @Adjacency(label = "supports", direction = Direction.OUT)
    public void addSupport(ISupport support);

    @Adjacency(label = "supports", direction = Direction.OUT)
    public Iterable<ISupport> getSupports();
    
    @Adjacency(label = "supports", direction = Direction.OUT)
    public void removeSupport(ISupport support);

    @Adjacency(label = "terms", direction = Direction.OUT)
    public void addTerm(ITerm term);

    @Adjacency(label = "terms", direction = Direction.OUT)
    public Iterable<ITerm> getTerms();

    @Adjacency(label = "terms", direction = Direction.OUT)
    public void removeTerm(ITerm term);
}
