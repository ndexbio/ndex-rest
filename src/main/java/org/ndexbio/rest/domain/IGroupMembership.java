package org.ndexbio.rest.domain;

import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.modules.typedgraph.TypeValue;

@TypeValue("groupMembership")
public interface IGroupMembership extends IMembership
{
    @Adjacency(label = "memberOf")
    public IGroup getGroup();

    @Adjacency(label = "memberOf")
    public void setGroup(IGroup group);
}
