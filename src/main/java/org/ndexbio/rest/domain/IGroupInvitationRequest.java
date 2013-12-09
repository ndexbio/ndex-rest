package org.ndexbio.rest.domain;

import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.modules.typedgraph.TypeValue;

@TypeValue("groupInvitation")
public interface IGroupInvitationRequest extends IRequest
{
    @Adjacency(label = "fromGroup")
    public IGroup getFromGroup();
    
    @Adjacency(label = "fromGroup")
    public void setFromGroup(IGroup group);

    @Adjacency(label = "toUser")
    public IUser getToUser();
    
    @Adjacency(label = "toUser")
    public void setToUser(IUser user);
}
