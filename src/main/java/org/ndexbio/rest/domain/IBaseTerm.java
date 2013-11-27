package org.ndexbio.rest.domain;

import com.tinkerpop.blueprints.Direction;
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

    @Adjacency(label = "namespaces", direction = Direction.OUT)
    public void addNamespace(INamespace namespace);

    @Adjacency(label = "namespaces", direction = Direction.OUT)
    public INamespace getNamespace();

    @Adjacency(label = "namespaces", direction = Direction.OUT)
    public void removeNamespace(INamespace namespace);
}
