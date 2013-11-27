package org.ndexbio.rest;

import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;

/**
 * @author Andrey Lomakin <a href="mailto:lomakin.andrey@gmail.com">Andrey Lomakin</a>
 * @since 10/24/13
 */
public class NdexSchemaManager
{
    public static final NdexSchemaManager INSTANCE = new NdexSchemaManager();

    public synchronized void init(OrientBaseGraph orientDbGraph)
    {
        orientDbGraph.getRawGraph().commit();

        /**********************************************************************
        * Create base types first. 
        **********************************************************************/
        if (orientDbGraph.getVertexType("account") == null)
        {
            OClass accountClass = orientDbGraph.createVertexType("account");
            accountClass.createProperty("backgroundImage", OType.STRING);
            accountClass.createProperty("createdDate", OType.DATE);
            accountClass.createProperty("description", OType.STRING);
            accountClass.createProperty("foregroundImage", OType.STRING);
        }

        if (orientDbGraph.getVertexType("request") == null)
        {
            OClass requestClass = orientDbGraph.createVertexType("request");
            requestClass.createProperty("message", OType.STRING);
            requestClass.createProperty("requestTime", OType.DATETIME);
        }

        if (orientDbGraph.getVertexType("term") == null)
        {
            OClass termClass = orientDbGraph.createVertexType("term");
            termClass.createProperty("jdexId", OType.STRING);
        }

        /**********************************************************************
        * Then create inherited types and uninherited types. 
        **********************************************************************/
        if (orientDbGraph.getVertexType("baseTerm") == null)
        {
            OClass termClass = orientDbGraph.createVertexType("baseTerm", "term");
            termClass.createProperty("name", OType.STRING);

            termClass.createIndex("index-term-name", OClass.INDEX_TYPE.NOTUNIQUE, "name");
        }

        if (orientDbGraph.getVertexType("citation") == null)
        {
            OClass citationClass = orientDbGraph.createVertexType("citation");

            citationClass.createProperty("contributors", OType.STRING);
            citationClass.createProperty("identifier", OType.STRING);
            citationClass.createProperty("jdexId", OType.STRING);
            citationClass.createProperty("title", OType.STRING);
            citationClass.createProperty("type", OType.STRING);
        }

        if (orientDbGraph.getVertexType("edge") == null)
        {
            OClass edgeClass = orientDbGraph.createVertexType("edge");
            edgeClass.createProperty("jdexId", OType.STRING);
        }

        if (orientDbGraph.getVertexType("functionTerm") == null)
        {
            OClass functionTermClass = orientDbGraph.createVertexType("functionTerm", "term");
            functionTermClass.createProperty("termParameters", OType.EMBEDDEDMAP, OType.LINK);
            functionTermClass.createProperty("textParameters", OType.EMBEDDEDSET);

            functionTermClass.createIndex("functionTermLinkParametersIndex", OClass.INDEX_TYPE.NOTUNIQUE, "termParameters by value");
        }

        if (orientDbGraph.getVertexType("group") == null)
        {
            OClass groupClass = orientDbGraph.createVertexType("group", "account");
            groupClass.createProperty("name", OType.STRING);
            groupClass.createProperty("organizationName", OType.STRING);
            groupClass.createProperty("website", OType.STRING);

            groupClass.createIndex("index-group-name", OClass.INDEX_TYPE.UNIQUE, "name");
        }
        
        if (orientDbGraph.getVertexType("groupInvite") == null)
            orientDbGraph.createVertexType("groupInvite", "request");

        if (orientDbGraph.getVertexType("joinGroup") == null)
            orientDbGraph.createVertexType("joinGroup", "request");


        if (orientDbGraph.getVertexType("namespace") == null)
        {
            OClass nameSpaceClass = orientDbGraph.createVertexType("namespace");
            nameSpaceClass.createProperty("jdexId", OType.STRING);
            nameSpaceClass.createProperty("prefix", OType.STRING);
            nameSpaceClass.createProperty("uri", OType.STRING);
        }

        if (orientDbGraph.getVertexType("network") == null)
        {
            OClass networkClass = orientDbGraph.createVertexType("network");
            networkClass.createProperty("copyright", OType.STRING);
            networkClass.createProperty("description", OType.STRING);
            networkClass.createProperty("edgeCount", OType.INTEGER);
            networkClass.createProperty("format", OType.STRING);
            networkClass.createProperty("nodeCount", OType.INTEGER);
            networkClass.createProperty("source", OType.STRING);
            networkClass.createProperty("title", OType.STRING);
            networkClass.createProperty("version", OType.STRING);

            networkClass.createIndex("index-network-title", OClass.INDEX_TYPE.UNIQUE, "title");
        }

        if (orientDbGraph.getVertexType("networkAccess") == null)
            orientDbGraph.createVertexType("networkAccess", "request");

        if (orientDbGraph.getVertexType("node") == null)
        {
            OClass nodeClass = orientDbGraph.createVertexType("node");
            nodeClass.createProperty("name", OType.STRING);
            nodeClass.createProperty("jdexId", OType.STRING);
        }

        if (orientDbGraph.getVertexType("support") == null)
        {
            OClass supportClass = orientDbGraph.createVertexType("support");
            supportClass.createProperty("jdexId", OType.STRING);
            supportClass.createProperty("text", OType.STRING);
        }

        if (orientDbGraph.getVertexType("task") == null)
        {
            OClass taskClass = orientDbGraph.createVertexType("task");
            taskClass.createProperty("status", OType.STRING);
            taskClass.createProperty("startTime", OType.DATETIME);
        }

        if (orientDbGraph.getVertexType("user") == null)
        {
            OClass userClass = orientDbGraph.createVertexType("user", "account");

            userClass.createProperty("username", OType.STRING);
            userClass.createProperty("password", OType.STRING);
            userClass.createProperty("firstName", OType.STRING);
            userClass.createProperty("lastName", OType.STRING);
            userClass.createProperty("emailAddress", OType.STRING);
            userClass.createProperty("website", OType.STRING);

            userClass.createIndex("index-user-username", OClass.INDEX_TYPE.UNIQUE_HASH_INDEX, "username");
            userClass.createIndex("index-user-emailAddress", OClass.INDEX_TYPE.UNIQUE_HASH_INDEX, "emailAddress");
        }
    }
}
