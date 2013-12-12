package org.ndexbio.rest.domain;

import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.modules.typedgraph.TypeValue;

@TypeValue("joinGroup")
public interface IJoinGroupRequest extends IRequest
{
    @Adjacency(label = "joinGroupRequestFromUser")
    public IUser getFromUser();
    
    @Adjacency(label = "joinGroupRequestFromUser")
    public void setFromUser(IUser user);

    @Adjacency(label = "joinGroupRequestToGroup")
    public IGroup getToGroup();
    
    @Adjacency(label = "joinGroupRequestToGroup")
    public void setToGroup(IGroup group);
}
