package org.ndexbio.rest.domain;

import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.modules.typedgraph.TypeValue;

@TypeValue("networkAccess")
public interface INetworkAccessRequest extends IRequest
{
    @Adjacency(label = "networkAccessRequestFromUser")
    public IUser getFromUser();
    
    @Adjacency(label = "networkAccessRequestFromUser")
    public void setFromUser(IUser user);

    @Adjacency(label = "networkAccessRequestToNetwork")
    public INetwork getToNetwork();
    
    @Adjacency(label = "networkAccessRequestToNetwork")
    public void setToNetwork(INetwork network);
}
