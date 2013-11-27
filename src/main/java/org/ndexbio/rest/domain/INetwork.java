package org.ndexbio.rest.domain;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.VertexFrame;

public interface INetwork extends VertexFrame
{
    @Adjacency(label = "citations", direction = Direction.OUT)
    public void addCitation(ICitation citation);

    @Adjacency(label = "citations", direction = Direction.OUT)
    public Iterable<ICitation> getCitations();
    
    @Adjacency(label = "citations", direction = Direction.OUT)
    public void removeCitation(ICitation citation);

    @Property("copyright")
    public String getCopyright();

    @Property("copyright")
    public void setCopyright(String copyright);

    @Property("description")
    public String getDescription();

    @Property("description")
    public void setDescription(String description);

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
    public Iterable<IAccount> getOwners();
    
    @Adjacency(label = "requests", direction = Direction.IN)
    public Iterable<IRequest> getRequests();
    
    @Adjacency(label = "requests", direction = Direction.IN)
    public void setRequests(Iterable<IRequest> requests);

    @Property("source")
    public String getSource();

    @Property("source")
    public void setSource(String source);

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

    @Property("title")
    public String getTitle();

    @Property("title")
    public void setTitle(String title);

    @Property("version")
    public String getVersion();

    @Property("version")
    public void setVersion(String version);
}
