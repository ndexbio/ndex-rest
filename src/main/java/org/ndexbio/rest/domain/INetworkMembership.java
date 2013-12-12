package org.ndexbio.rest.domain;

import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.modules.typedgraph.TypeValue;

@TypeValue("networkMembership")
public interface INetworkMembership extends IMembership
{
    @Adjacency(label = "networkMembershipMemberOf")
    public INetwork getNetwork();

    @Adjacency(label = "networkMembershipMemberOf")
    public void setNetwork(INetwork network);
}
