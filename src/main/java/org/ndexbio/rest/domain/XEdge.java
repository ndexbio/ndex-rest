package org.ndexbio.rest.domain;

import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.VertexFrame;

/**
 * @author Andrey Lomakin <a href="mailto:lomakin.andrey@gmail.com">Andrey Lomakin</a>
 * @since 10/30/13
 */
public interface XEdge extends VertexFrame {
    @Adjacency(label = "n")
    public Iterable<XNetwork> getNetworks();

    @Adjacency(label = "n")
    public void addNetwork(XNetwork network);

    @Adjacency(label = "s")
    public Iterable<XNode> getSubject();

    @Adjacency(label = "s")
    public XNode addSubject(XNode node);

    @Adjacency(label = "o")
    public Iterable<XNode> getObject();

    @Adjacency(label = "o")
    public void addObject(XNode node);

    @Adjacency(label = "p")
    public void addPredicate(XTerm term);

    @Adjacency(label = "p")
    public Iterable<XTerm> getPredicate();
}
