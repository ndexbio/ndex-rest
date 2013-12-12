package org.ndexbio.rest.domain;

import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.modules.typedgraph.TypeValue;

@TypeValue("groupMembership")
public interface IGroupMembership extends IMembership
{
    @Adjacency(label = "groupMembershipMemberOf")
    public IGroup getGroup();

    @Adjacency(label = "groupMembershipMemberOf")
    public void setGroup(IGroup group);
}
