package org.ndexbio.rest.domain;

import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.VertexFrame;

public interface INetwork extends VertexFrame
{
    @Adjacency(label = "citations")
    public void addCitation(ICitation citation);

    @Adjacency(label = "citations")
    public Iterable<ICitation> getCitations();
    
    @Adjacency(label = "citations")
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
    
    @Property("isPublic")
    public boolean getIsPublic();
    
    @Property("isPublic")
    public void setIsPublic(boolean isPublic);
    
    @Adjacency(label = "members")
    public void addMember(INetworkMembership newMember);
    
    @Adjacency(label = "members")
    public Iterable<INetworkMembership> getMembers();
    
    @Adjacency(label = "members")
    public void removeMember(INetworkMembership member);

    @Adjacency(label = "namespaces")
    public void addNamespace(INamespace namespace);

    @Adjacency(label = "namespaces")
    public Iterable<INamespace> getNamespaces();
    
    @Adjacency(label = "namespaces")
    public void removeNamespace(INamespace namespace);

    @Property("ndexEdgeCount")
    public int getNdexEdgeCount();

    @Property("ndexEdgeCount")
    public void setNdexEdgeCount(int edgesCount);

    @Adjacency(label = "ndexEdges")
    public void addNdexEdge(IEdge edge);

    @Adjacency(label = "ndexEdges")
    public Iterable<IEdge> getNdexEdges();
    
    @Adjacency(label = "ndexEdges")
    public void removeNdexEdge(IEdge edge);

    @Property("ndexNodeCount")
    public int getNdexNodeCount();

    @Property("ndexNodeCount")
    public void setNdexNodeCount(int nodesCount);

    @Adjacency(label = "ndexNodes")
    public void addNdexNode(INode node);

    @Adjacency(label = "ndexNodes")
    public Iterable<INode> getNdexNodes();
    
    @Adjacency(label = "ndexNodes")
    public void removeNdexNode(INode node);
    
    @Adjacency(label = "requests")
    public void addRequest(IRequest request);
    
    @Adjacency(label = "requests")
    public Iterable<IRequest> getRequests();
    
    @Adjacency(label = "requests")
    public void removeRequest(IRequest request);

    @Property("source")
    public String getSource();

    @Property("source")
    public void setSource(String source);

    @Adjacency(label = "supports")
    public void addSupport(ISupport support);

    @Adjacency(label = "supports")
    public Iterable<ISupport> getSupports();
    
    @Adjacency(label = "supports")
    public void removeSupport(ISupport support);

    @Adjacency(label = "terms")
    public void addTerm(ITerm term);

    @Adjacency(label = "terms")
    public Iterable<ITerm> getTerms();

    @Adjacency(label = "terms")
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
