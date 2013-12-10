package org.ndexbio.rest.domain;

import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.modules.typedgraph.TypeValue;

@TypeValue("Base")
public interface IBaseTerm extends ITerm
{
    @Property("name")
    public String getName();

    @Property("name")
    public void setName(String name);

    @Adjacency(label = "termNamespace")
    public void setTermNamespace(INamespace namespace);

    @Adjacency(label = "termNamespace")
    public INamespace getTermNamespace();
}
