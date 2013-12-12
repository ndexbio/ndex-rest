package org.ndexbio.rest.domain;

import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.modules.typedgraph.TypeValue;

@TypeValue("groupInvitation")
public interface IGroupInvitationRequest extends IRequest
{
    @Adjacency(label = "groupInvitationFromGroup")
    public IGroup getFromGroup();
    
    @Adjacency(label = "groupInvitationFromGroup")
    public void setFromGroup(IGroup group);

    @Adjacency(label = "groupInvitationToUser")
    public IUser getToUser();
    
    @Adjacency(label = "groupInvitationToUser")
    public void setToUser(IUser user);
}
