package org.ndexbio.rest.domain;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;

public interface IUser extends IAccount
{
    @Property("username")
    public String getUsername();

    @Property("username")
    public void setUsername(String username);

    @Property("password")
    public String getPassword();

    @Property("password")
    public void setPassword(String password);

    @Property("firstName")
    public String getFirstName();

    @Property("firstName")
    public void setFirstName(String firstName);

    @Property("lastName")
    public String getLastName();

    @Property("lastName")
    public void setLastName(String lastName);

    @Property("description")
    public String getDescription();

    @Property("description")
    public void setDescription(String description);

    @Property("website")
    public void setWebsite(String website);

    @Property("website")
    public String getWebsite();

    @Property("foregroundImg")
    public String getForegroundImg();

    @Property("foregroundImg")
    public void setForegroundImg(String foregroundImg);

    @Property("backgroundImg")
    public String getBackgroundImg();

    @Property("backgroundImg")
    public void setBackgroundImg(String backgroundImg);

    @Adjacency(label = "workspace")
    public Iterable<INetwork> getWorkspace();

    @Adjacency(label = "workspace")
    public void addWorkspace(INetwork network);

    @Adjacency(label = "workspace")
    public void removeWorkspace(INetwork network);

    @Adjacency(label = "ownsNetwork")
    public Iterable<INetwork> getOwnedNetworks();

    @Adjacency(label = "ownsNetwork", direction = Direction.OUT)
    public void addOwnsNetwork(INetwork network);

    @Adjacency(label = "ownsNetwork")
    public void removeOwnsNetwork(INetwork network);

    @Adjacency(label = "ownsGroup")
    public void addOwnedGroup(IGroup group);

    @Adjacency(label = "ownsGroup")
    public Iterable<IGroup> getOwnedGroups();
}
