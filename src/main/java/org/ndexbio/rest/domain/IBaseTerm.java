package org.ndexbio.rest.domain;

import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.VertexFrame;
import com.tinkerpop.frames.modules.typedgraph.TypeField;
import com.tinkerpop.frames.modules.typedgraph.TypeValue;

@TypeValue("baseTerm")
public interface IBaseTerm extends ITerm
{

    @Property("name")
    public String getName();

    @Property("name")
    public void setName(String name);

    @Adjacency(label = "namespace")
    public INamespace getNamespace();

    @Adjacency(label = "namespace")
    public void setNamespace(INamespace namespace);
}
