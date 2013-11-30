package org.ndexbio.rest.domain;

import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.modules.typedgraph.TypeValue;

@TypeValue("networkAccess")
public interface INetworkAccessRequest extends IRequest
{
    @Adjacency(label = "fromUser")
    public IUser getFromUser();
    
    @Adjacency(label = "fromUser")
    public void setFromUser(IUser user);

    @Adjacency(label = "toNetwork")
    public INetwork getToNetwork();
    
    @Adjacency(label = "toNetwork")
    public void setToNetwork(INetwork network);
}
