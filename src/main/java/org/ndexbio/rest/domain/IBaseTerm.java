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

    @Adjacency(label = "namespaces")
    public void addNamespace(INamespace namespace);

    @Adjacency(label = "namespaces")
    public Iterable<INamespace> getNamespaces();

    @Adjacency(label = "namespaces")
    public void removeNamespace(INamespace namespace);
}
