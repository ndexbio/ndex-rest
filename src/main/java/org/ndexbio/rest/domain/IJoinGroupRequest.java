package org.ndexbio.rest.domain;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.modules.typedgraph.TypeValue;

@TypeValue("joinGroup")
public interface IJoinGroupRequest extends IRequest
{
    @Adjacency(label = "fromUser", direction = Direction.IN)
    public IUser getFromUser();
    
    @Adjacency(label = "fromUser", direction = Direction.IN)
    public void setFromUser(IUser user);

    @Adjacency(label = "toGroup", direction = Direction.OUT)
    public IGroup getToGroup();
    
    @Adjacency(label = "toGroup", direction = Direction.OUT)
    public void setToGroup(IGroup group);
}
