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

        if (orientDbGraph.getVertexType("network") == null)
        {
            OClass networkClass = orientDbGraph.createVertexType("network");
            networkClass.createProperty("format", OType.STRING);
            networkClass.createProperty("properties", OType.EMBEDDEDMAP);
            networkClass.createProperty("edgeCount", OType.INTEGER);
            networkClass.createProperty("nodeCount", OType.INTEGER);
        }

        if (orientDbGraph.getVertexType("namespace") == null)
        {
            OClass nameSpaceClass = orientDbGraph.createVertexType("namespace");
            nameSpaceClass.createProperty("jdexId", OType.STRING);
            nameSpaceClass.createProperty("prefix", OType.STRING);
            nameSpaceClass.createProperty("uri", OType.STRING);
        }

        if (orientDbGraph.getVertexType("term") == null)
        {
            OClass termClass = orientDbGraph.createVertexType("term");
            termClass.createProperty("type", OType.STRING);
            termClass.createProperty("jdexId", OType.STRING);
        }
        
        if (orientDbGraph.getVertexType("baseTerm") == null)
        {
            OClass termClass = orientDbGraph.createVertexType("baseTerm", "term");
            termClass.createProperty("name", OType.STRING);
        }

        if (orientDbGraph.getVertexType("functionTerm") == null)
        {
            OClass functionTermClass = orientDbGraph.createVertexType("functionTerm", "term");
            functionTermClass.createProperty("textParameters", OType.EMBEDDEDSET);
        }

        if (orientDbGraph.getVertexType("node") == null)
        {
            OClass nodeClass = orientDbGraph.createVertexType("node");
            nodeClass.createProperty("name", OType.STRING);
            nodeClass.createProperty("jdexId", OType.STRING);
        }

        if (orientDbGraph.getVertexType("edge") == null)
        {
            OClass edgeClass = orientDbGraph.createVertexType("edge");
        }

        if (orientDbGraph.getVertexType("citation") == null)
        {
            OClass citationClass = orientDbGraph.createVertexType("citation");

            citationClass.createProperty("identifier", OType.STRING);
            citationClass.createProperty("type", OType.STRING);
            citationClass.createProperty("title", OType.STRING);
            citationClass.createProperty("contributors", OType.STRING);
            citationClass.createProperty("jdexId", OType.STRING);
        }

        if (orientDbGraph.getVertexType("support") == null)
        {
            OClass supportClass = orientDbGraph.createVertexType("support");
            supportClass.createProperty("jdexId", OType.STRING);
            supportClass.createProperty("text", OType.STRING);
        }

        if (orientDbGraph.getVertexType("user") == null)
        {
            OClass userClass = orientDbGraph.createVertexType("user");

            userClass.createProperty("username", OType.STRING);
            userClass.createProperty("password", OType.STRING);
            userClass.createProperty("firstName", OType.STRING);
            userClass.createProperty("lastName", OType.STRING);
            userClass.createProperty("description", OType.STRING);
            userClass.createProperty("website", OType.STRING);
            userClass.createProperty("foregroundImage", OType.STRING);
            userClass.createProperty("backgroundImage", OType.STRING);

            userClass.createIndex("user_name_index", OClass.INDEX_TYPE.UNIQUE_HASH_INDEX, "username");
        }

        if (orientDbGraph.getVertexType("group") == null)
        {
            OClass groupClass = orientDbGraph.createVertexType("group");
            groupClass.createProperty("name", OType.STRING);
            groupClass.createIndex("group_name_index", OClass.INDEX_TYPE.UNIQUE, "name");
        }

        if (orientDbGraph.getVertexType("task") == null)
        {
            OClass taskClass = orientDbGraph.createVertexType("task");
            taskClass.createProperty("status", OType.STRING);
            taskClass.createProperty("startTime", OType.DATETIME);
        }

        if (orientDbGraph.getVertexType("request") == null)
        {
            OClass requestClass = orientDbGraph.createVertexType("request");
            requestClass.createProperty("requestType", OType.STRING);
            requestClass.createProperty("message", OType.STRING);
            requestClass.createProperty("requestTime", OType.DATETIME);
        }
    }
}
