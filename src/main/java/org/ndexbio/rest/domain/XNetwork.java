package org.ndexbio.rest.domain;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.VertexFrame;

import java.util.Map;

/**
 * @author Andrey Lomakin <a href="mailto:lomakin.andrey@gmail.com">Andrey Lomakin</a>
 * @since 10/30/13
 */
public interface XNetwork extends VertexFrame {
    @Property("format")
    public void setFormat(String format);

    @Property("format")
    public String getFormat();

    @Adjacency(label = "namespaces")
    public Iterable<XNameSpace> getNamespaces();

    @Adjacency(label = "namespaces")
    public void addNameSpace(XNameSpace nameSpace);

    @Adjacency(label = "nodes")
    public Iterable<XNode> getNodes();

    @Adjacency(label = "nodes")
    public void addNode(XNode node);

    @Property("properties")
    public Map<String, String> getProperties();

    @Property("properties")
    public void setProperties(Map<String, String> properties);

    @Adjacency(label = "terms")
    public Iterable<XTerm> getTerms();

    @Adjacency(label = "terms")
    public void addTerm(XTerm term);

    @Adjacency(label = "supports")
    public Iterable<XSupport> getSupports();

    @Adjacency(label = "supports")
    public void addSupport(XSupport support);

    @Adjacency(label = "citations")
    public Iterable<XCitation> getCitations();

    @Adjacency(label = "citations")
    public void addCitations(XCitation citation);

    @Adjacency(label = "ndexEdges")
    public Iterable<XEdge> getNdexEdges();

    @Adjacency(label = "ndexEdges")
    public XEdge addNdexEdge(XEdge edge);

    @Property("nodesCount")
    public void setNodesCount(int nodesCount);

    @Property("nodesCount")
    public int getNodesCount();

    @Property("edgesCount")
    public void setEdgesCount(int edgesCount);

    @Property("edgesCount")
    public int getEdgesCount();

    @Adjacency(label = "ownsNetwork", direction = Direction.IN)
    public Iterable<XUser> getOwners();
}
