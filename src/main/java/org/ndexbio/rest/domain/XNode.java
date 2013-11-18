package org.ndexbio.rest.domain;

import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.VertexFrame;

/**
 * @author Andrey Lomakin <a href="mailto:lomakin.andrey@gmail.com">Andrey Lomakin</a>
 * @since 10/30/13
 */
public interface XNode extends VertexFrame {
    @Property("name")
    public String getName();

    @Property("name")
    public void setName(String name);

    @Property("jdex_id")
    public void setJdexId(String jdexId);

    @Property("jdex_id")
    public String getJdexId();

    @Adjacency(label = "represents")
    public Iterable<XTerm> getRepresents();

    @Adjacency(label = "represents")
    public void addRepresents(XTerm term);
}
