package org.ndexbio.rest.domain;

import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;

import java.util.Date;

/**
 * @author Andrey Lomakin <a href="mailto:lomakin.andrey@gmail.com">Andrey Lomakin</a>
 * @author <a href="mailto:enisher@gmail.com">Artem Orobets</a>
 * @since 10/30/13
 */
public interface XGroup extends XAccount {
    @Property("groupName")
    void setGroupName(String name);

    @Property("groupName")
    String getGroupName();

    @Property("organizationName")
    void setOrganizationName(String organizationName);

    @Property("organizationName")
    String getOrganizationName();

    @Property("website")
    void setWebsite(String website);

    @Property("website")
    String getWebsite();

    @Property("description")
    void setDescription(String description);

    @Property("description")
    String getDescription();

    @Property("creation_date")
    public void setCreationDate(Date date);

    @Property("creation_date")
    public Date getCreationDate();

    @Property("foregroundImg")
    public void setForegroundImg(String img);

    @Property("foregroundImg")
    public String getForegroundImg();

    @Property("backgroundImg")
    public void setBackgroundImg(String date);

    @Property("backgroundImg")
    public String getBackgroundImg();

    @Adjacency(label = "ownsNetwork")
    Iterable<XNetwork> getOwnedNetworks();
}
