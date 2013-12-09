package org.ndexbio.rest.domain;

import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.modules.typedgraph.TypeValue;

@TypeValue("joinGroup")
public interface IJoinGroupRequest extends IRequest
{
    @Adjacency(label = "fromUser")
    public IUser getFromUser();
    
    @Adjacency(label = "fromUser")
    public void setFromUser(IUser user);

    @Adjacency(label = "toGroup")
    public IGroup getToGroup();
    
    @Adjacency(label = "toGroup")
    public void setToGroup(IGroup group);
}
