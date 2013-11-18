package org.ndexbio.rest.domain;

import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.VertexFrame;
import com.tinkerpop.frames.modules.typedgraph.TypeField;
import com.tinkerpop.frames.modules.typedgraph.TypeValue;

/**
 * @author Andrey Lomakin <a href="mailto:lomakin.andrey@gmail.com">Andrey Lomakin</a>
 * @since 10/30/13
 */
@TypeField("type")
@TypeValue("xTerm")
public interface XTerm extends VertexFrame {
    @Property("name")
    public String getName();

    @Property("name")
    public void setName(String name);

    @Property("jdex_id")
    public String getJdexId();

    @Property("jdex_id")
    public void setJdexId(String jdexId);

    @Adjacency(label =  "namespace")
    public XNameSpace getNamespace();

    @Adjacency(label =  "namespace")
    public void setNamespace(XNameSpace namespace);
}
