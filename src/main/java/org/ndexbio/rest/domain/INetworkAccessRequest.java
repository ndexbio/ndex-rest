package org.ndexbio.rest.domain;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.modules.typedgraph.TypeValue;

@TypeValue("networkAccess")
public interface INetworkAccessRequest extends IRequest
{
    @Adjacency(label = "fromUser", direction = Direction.IN)
    public IUser getFromUser();
    
    @Adjacency(label = "fromUser", direction = Direction.IN)
    public void setFromUser(IUser user);

    @Adjacency(label = "toNetwork", direction = Direction.OUT)
    public INetwork getToNetwork();
    
    @Adjacency(label = "toNetwork", direction = Direction.OUT)
    public void setToNetwork(INetwork network);
}
