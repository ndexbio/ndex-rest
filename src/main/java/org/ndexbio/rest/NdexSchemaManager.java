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

        if (orientDbGraph.getVertexType("xNetwork") == null)
        {
            OClass networkClass = orientDbGraph.createVertexType("xNetwork");
            networkClass.createProperty("format", OType.STRING);
            networkClass.createProperty("properties", OType.EMBEDDEDMAP);
            networkClass.createProperty("edgesCount", OType.INTEGER);
            networkClass.createProperty("nodesCount", OType.INTEGER);
        }

        if (orientDbGraph.getVertexType("xNameSpace") == null)
        {
            OClass nameSpaceClass = orientDbGraph.createVertexType("xNameSpace");
            nameSpaceClass.createProperty("jdex_id", OType.STRING);
            nameSpaceClass.createProperty("prefix", OType.STRING);
            nameSpaceClass.createProperty("uri", OType.STRING);
        }

        if (orientDbGraph.getVertexType("xTerm") == null)
        {
            OClass termClass = orientDbGraph.createVertexType("xTerm");
            termClass.createProperty("type", OType.STRING);
            termClass.createProperty("jdex_id", OType.STRING);
            termClass.createProperty("name", OType.STRING);
        }

        if (orientDbGraph.getVertexType("xBaseTerm") == null)
        {
            OClass baseTermClass = orientDbGraph.createVertexType("xBaseTerm", "xTerm");
        }

        if (orientDbGraph.getVertexType("xFunctionTerm") == null)
        {
            OClass functionTermClass = orientDbGraph.createVertexType("xFunctionTerm", "xTerm");
            functionTermClass.createProperty("textParameters", OType.EMBEDDEDSET);
        }

        if (orientDbGraph.getVertexType("xNode") == null)
        {
            OClass nodeClass = orientDbGraph.createVertexType("xNode");
            nodeClass.createProperty("name", OType.STRING);
            nodeClass.createProperty("jdex_id", OType.STRING);
        }

        if (orientDbGraph.getVertexType("xEdge") == null)
        {
            OClass edgeClass = orientDbGraph.createVertexType("xEdge");
        }

        if (orientDbGraph.getVertexType("xCitation") == null)
        {
            OClass citationClass = orientDbGraph.createVertexType("xCitation");

            citationClass.createProperty("identifier", OType.STRING);
            citationClass.createProperty("type", OType.STRING);
            citationClass.createProperty("title", OType.STRING);
            citationClass.createProperty("contributors", OType.STRING);
            citationClass.createProperty("jdex_id", OType.STRING);
        }

        if (orientDbGraph.getVertexType("xSupport") == null)
        {
            OClass supportClass = orientDbGraph.createVertexType("xSupport");
            supportClass.createProperty("jdex_id", OType.STRING);
            supportClass.createProperty("text", OType.STRING);
        }

        if (orientDbGraph.getVertexType("xAccount") == null)
        {
            OClass accountClass = orientDbGraph.createVertexType("xAccount");
        }

        if (orientDbGraph.getVertexType("xUser") == null)
        {
            OClass userClass = orientDbGraph.createVertexType("xUser");

            userClass.createProperty("username", OType.STRING);
            userClass.createProperty("password", OType.STRING);
            userClass.createProperty("firstName", OType.STRING);
            userClass.createProperty("lastName", OType.STRING);
            userClass.createProperty("description", OType.STRING);
            userClass.createProperty("website", OType.STRING);
            userClass.createProperty("foregroundImg", OType.STRING);
            userClass.createProperty("backgroundImg", OType.STRING);

            userClass.createIndex("user_name_index", OClass.INDEX_TYPE.UNIQUE_HASH_INDEX, "username");
        }

        if (orientDbGraph.getVertexType("xGroup") == null)
        {
            OClass groupClass = orientDbGraph.createVertexType("xGroup");
            groupClass.createProperty("groupName", OType.STRING);
            groupClass.createIndex("group_name_index", OClass.INDEX_TYPE.UNIQUE, "groupName");
        }

        if (orientDbGraph.getVertexType("xTask") == null)
        {
            OClass taskClass = orientDbGraph.createVertexType("xTask");
            taskClass.createProperty("status", OType.STRING);
            taskClass.createProperty("startTime", OType.DATETIME);
        }

        if (orientDbGraph.getVertexType("xRequest") == null)
        {
            OClass requestClass = orientDbGraph.createVertexType("xRequest");
            requestClass.createProperty("requestType", OType.STRING);
            requestClass.createProperty("message", OType.STRING);
            requestClass.createProperty("requestTime", OType.DATETIME);
        }
    }
}
