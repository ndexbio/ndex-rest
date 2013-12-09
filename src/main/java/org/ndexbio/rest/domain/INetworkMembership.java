package org.ndexbio.rest.domain;

import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.modules.typedgraph.TypeValue;

@TypeValue("networkMembership")
public interface INetworkMembership extends IMembership
{
    @Adjacency(label = "memberOf")
    public INetwork getNetwork();

    @Adjacency(label = "memberOf")
    public void setNetwork(INetwork network);
}
