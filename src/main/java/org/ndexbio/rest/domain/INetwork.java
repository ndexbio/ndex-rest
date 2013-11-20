package org.ndexbio.rest.domain;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.VertexFrame;
import java.util.Map;

public interface INetwork extends VertexFrame
{
    @Property("format")
    public void setFormat(String format);

    @Property("format")
    public String getFormat();

    @Adjacency(label = "namespaces")
    public Iterable<INamespace> getNamespaces();

    @Adjacency(label = "namespaces")
    public void addNameSpace(INamespace nameSpace);

    @Adjacency(label = "nodes")
    public Iterable<INode> getNodes();

    @Adjacency(label = "nodes")
    public void addNode(INode node);

    @Property("properties")
    public Map<String, String> getProperties();

    @Property("properties")
    public void setProperties(Map<String, String> properties);

    @Adjacency(label = "terms")
    public Iterable<ITerm> getTerms();

    @Adjacency(label = "terms")
    public void addTerm(ITerm term);

    @Adjacency(label = "supports")
    public Iterable<ISupport> getSupports();

    @Adjacency(label = "supports")
    public void addSupport(ISupport support);

    @Adjacency(label = "citations")
    public Iterable<ICitation> getCitations();

    @Adjacency(label = "citations")
    public void addCitations(ICitation citation);

    @Adjacency(label = "ndexEdges")
    public Iterable<IEdge> getNdexEdges();

    @Adjacency(label = "ndexEdges")
    public IEdge addNdexEdge(IEdge edge);

    @Property("nodesCount")
    public void setNodesCount(int nodesCount);

    @Property("nodesCount")
    public int getNodesCount();

    @Property("edgesCount")
    public void setEdgesCount(int edgesCount);

    @Property("edgesCount")
    public int getEdgesCount();

    @Adjacency(label = "ownsNetwork", direction = Direction.IN)
    public Iterable<IUser> getOwners();
}
