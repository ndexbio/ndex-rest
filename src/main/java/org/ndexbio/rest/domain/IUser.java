package org.ndexbio.rest.domain;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;

public interface IUser extends IAccount
{
    @Property("backgroundImg")
    public String getBackgroundImg();

    @Property("backgroundImg")
    public void setBackgroundImg(String backgroundImg);

    @Property("description")
    public String getDescription();

    @Property("description")
    public void setDescription(String description);

    @Property("emailAddress")
    public String getEmailAddress();

    @Property("firstName")
    public void setEmailAddress(String emailAddress);

    @Property("firstName")
    public String getFirstName();

    @Property("firstName")
    public void setFirstName(String firstName);

    @Property("foregroundImg")
    public String getForegroundImg();

    @Property("foregroundImg")
    public void setForegroundImg(String foregroundImg);

    @Property("lastName")
    public String getLastName();

    @Property("lastName")
    public void setLastName(String lastName);

    @Adjacency(label = "ownsGroup")
    public void addOwnedGroup(IGroup group);

    @Adjacency(label = "ownsGroup")
    public Iterable<IGroup> getOwnedGroups();

    @Adjacency(label = "ownsNetwork")
    public Iterable<INetwork> getOwnedNetworks();

    @Adjacency(label = "ownsNetwork", direction = Direction.OUT)
    public void addOwnsNetwork(INetwork network);

    @Adjacency(label = "ownsNetwork")
    public void removeOwnsNetwork(INetwork network);

    @Property("password")
    public String getPassword();

    @Property("password")
    public void setPassword(String password);

    @Property("username")
    public String getUsername();

    @Property("username")
    public void setUsername(String username);

    @Property("website")
    public void setWebsite(String website);

    @Property("website")
    public String getWebsite();

    @Adjacency(label = "workspace")
    public Iterable<INetwork> getWorkspace();

    @Adjacency(label = "workspace")
    public void addWorkspace(INetwork network);

    @Adjacency(label = "workspace")
    public void removeWorkspace(INetwork network);
}
