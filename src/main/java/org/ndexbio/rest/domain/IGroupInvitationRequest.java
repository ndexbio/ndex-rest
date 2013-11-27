package org.ndexbio.rest.domain;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.modules.typedgraph.TypeValue;

@TypeValue("groupInvite")
public interface IGroupInvitationRequest extends IRequest
{
    @Adjacency(label = "fromGroup", direction = Direction.IN)
    public IGroup getFromGroup();
    
    @Adjacency(label = "fromGroup", direction = Direction.IN)
    public void setFromGroup(IGroup group);

    @Adjacency(label = "toUser", direction = Direction.OUT)
    public IUser getToUser();
    
    @Adjacency(label = "toUser", direction = Direction.OUT)
    public void setToUser(IUser user);
}
