package org.ndexbio.rest.helpers;

import java.util.Iterator;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.ndexbio.rest.domain.XGroup;
import org.ndexbio.rest.domain.XNetwork;
import org.ndexbio.rest.domain.XUser;
import com.orientechnologies.orient.core.id.ORID;

public class UserHelper
{
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    
    
    /**************************************************************************
    * Serializes the user's groups into JSON.
    * 
    * @param ownedGroups      The user's groups.
    * @param serializedGroups The serialized groups.
    **************************************************************************/
    public static void serializeGroups(Iterator<XGroup> ownedGroups, ArrayNode serializedGroups)
    {
        while (ownedGroups.hasNext())
        {
            final XGroup ownedGroup = ownedGroups.next();

            final ObjectNode serializedGroup = OBJECT_MAPPER.createObjectNode();
            serializedGroup.put("Name", ownedGroup.getGroupName());
            serializedGroup.put("OrganizationName", ownedGroup.getOrganizationName());
            serializedGroup.put("Id", RidConverter.convertToJid((ORID)ownedGroup.asVertex().getId()));
            serializedGroups.add(serializedGroup);
        }
    }
    
    /**************************************************************************
    * Serializes the user's networks into JSON.
    * 
    * @param ownedNetworks      The user's networks.
    * @param serializedNetworks The serialized networks.
    **************************************************************************/
    public static void serializeNetworks(Iterator<XNetwork> ownedNetworks, ArrayNode serializedNetworks)
    {
        while (ownedNetworks.hasNext())
        {
            final XNetwork ownedNetwork = ownedNetworks.next();

            final ObjectNode serializedNetwork = OBJECT_MAPPER.createObjectNode();
            serializedNetwork.put("Id", RidConverter.convertToJid((ORID)ownedNetwork.asVertex().getId()));
            serializedNetwork.put("Title", ownedNetwork.getProperties().get("title"));
            serializedNetwork.put("Description", ownedNetwork.getProperties().get("description"));
            serializedNetwork.put("Source", ownedNetwork.getProperties().get("source"));
            serializedNetwork.put("Format", ownedNetwork.getFormat());
            serializedNetwork.put("Edges", ownedNetwork.getEdgesCount());
            serializedNetwork.put("Nodes", ownedNetwork.getNodesCount());

            ArrayNode owners = OBJECT_MAPPER.createArrayNode();
            for (XUser owner : ownedNetwork.getOwners())
            {
                final ObjectNode nOwner = OBJECT_MAPPER.createObjectNode();
                nOwner.put("Id", owner.getUsername());
                owners.add(nOwner);
            }

            serializedNetwork.put("Owners", owners);
            serializedNetworks.add(serializedNetwork);
        }
    }

    /**************************************************************************
    * Serializes the user's profile into JSON.
    **************************************************************************/
    public static void serializeProfile(XUser user, ObjectNode serializedUser)
    {
        serializedUser.put("Username", user.getUsername());
        serializedUser.put("FirstName", user.getFirstName());
        serializedUser.put("LastName", user.getLastName());
        serializedUser.put("Website", user.getWebsite());
        serializedUser.put("Description", user.getDescription());
        serializedUser.put("ForegroundImage", user.getForegroundImg());
        serializedUser.put("BackgroundImage", user.getBackgroundImg());
    }
}
