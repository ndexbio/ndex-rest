package org.ndexbio.rest.service;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.storage.OCluster;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.blueprints.impls.orient.OrientGraph;
import com.tinkerpop.frames.FramedGraph;
import com.tinkerpop.frames.FramedGraphFactory;
import com.tinkerpop.frames.modules.gremlingroovy.GremlinGroovyModule;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.ndexbio.rest.NdexSchemaManager;
import org.ndexbio.rest.domain.XNetwork;
import org.ndexbio.rest.domain.XUser;
import org.ndexbio.rest.helpers.RidConverter;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import java.util.HashSet;
import java.util.Set;

//TODO: Refactor
@Test
public class NdexNetworkServiceTest
{
    private final String jdexFile = "/org/ndexbio/rest/small_corpus.jdex";
    private FramedGraph<OrientBaseGraph> orientGraph;

    // private NdexNetworkService ndexNetworkService = new NdexNetworkService();

    @BeforeMethod
    public void beforeMethod()
    {
        FramedGraphFactory factory = new FramedGraphFactory(new GremlinGroovyModule());
        ODatabaseDocumentTx databaseDocumentTx = new ODatabaseDocumentTx("memory:ndexNetworkServiceTest");
        databaseDocumentTx.create();

        orientGraph = factory.create((OrientBaseGraph) new OrientGraph(databaseDocumentTx));
        NdexSchemaManager.INSTANCE.init(orientGraph.getBaseGraph());
    }

    @AfterMethod
    public void afterMethod()
    {
        orientGraph.getBaseGraph().drop();
    }

    public void basicNetworkLoadingTest() throws Exception
    {
//        final ObjectMapper objectMapper = new ObjectMapper();
//        JsonNode rootNode = objectMapper.readTree(NdexNetworkServiceTest.class.getResourceAsStream(jdexFile));
//
//        final XUser xUser = orientGraph.addVertex("class:xUser", XUser.class);
//        orientGraph.getBaseGraph().commit();
//
//        ndexNetworkService.createNetwork(RidConverter.convertFromRID((ORID)
//        xUser.asVertex().getId()), rootNode, orientGraph);
//        orientGraph.getBaseGraph().commit();
    }

    public void getNetworkTest() throws Exception
    {
//        final ObjectMapper objectMapper = new ObjectMapper();
//        JsonNode rootNode = objectMapper.readTree(NdexNetworkServiceTest.class.getResourceAsStream(jdexFile));
//
//        final XUser xUser = orientGraph.addVertex("class:xUser", XUser.class);
//        orientGraph.getBaseGraph().commit();
//
//        XNetwork network = ndexNetworkService.createNetwork(RidConverter.convertFromRID((ORID)
//        xUser.asVertex().getId()), rootNode, orientGraph);
//
//        orientGraph.getBaseGraph().commit();
//
//        ORID networkRid = (ORID) network.asVertex().getId();
//
//        ndexNetworkService.getNetwork(RidConverter.convertFromRID(networkRid), orientGraph);
    }

    public void deleteNetworkTest() throws Exception
    {
//        final ObjectMapper objectMapper = new ObjectMapper();
//        JsonNode rootNode = objectMapper.readTree(NdexNetworkServiceTest.class.getResourceAsStream(jdexFile));
//
//        final XUser xUser = orientGraph.addVertex("class:xUser", XUser.class);
//        orientGraph.getBaseGraph().commit();
//
//        XNetwork network = ndexNetworkService.createNetwork(RidConverter.convertFromRID((ORID)
//        xUser.asVertex().getId()), rootNode, orientGraph);
//
//        orientGraph.getBaseGraph().commit();
//
//        ORID networkRid = (ORID) network.asVertex().getId();
//
//        orientGraph.getBaseGraph().getRawGraph().begin();
//        ndexNetworkService.deleteNetwork(RidConverter.convertFromRID(networkRid), orientGraph);
//        orientGraph.getBaseGraph().getRawGraph().commit();
//
//        ODatabaseDocumentTx databaseDocumentTx = orientGraph.getBaseGraph().getRawGraph();
//        Set<String> clusterNames = new HashSet<String>(databaseDocumentTx.getClusterNames());
//
//        for (String clusterName : clusterNames)
//        {
//            if (!(clusterName.startsWith("x") || clusterName.equals("default")) || clusterName.equals("xuser"))
//                continue;
//
//            int clusterId = databaseDocumentTx.getClusterIdByName(clusterName);
//            OCluster cluster = databaseDocumentTx.getStorage().getClusterById(clusterId);
//            Assert.assertEquals(cluster.getEntries(), 0);
//        }
//
//        int userClusterId = databaseDocumentTx.getClusterIdByName("xUser");
//        OCluster userCluster = databaseDocumentTx.getStorage().getClusterById(userClusterId);
//        Assert.assertEquals(userCluster.getEntries(), 1);
    }

    public void testFindNetwork() throws Exception
    {
//        final ObjectMapper objectMapper = new ObjectMapper();
//        JsonNode rootNode = objectMapper.readTree(NdexNetworkServiceTest.class.getResourceAsStream(jdexFile));
//
//        final XUser xUser = orientGraph.addVertex("class:xUser", XUser.class);
//        orientGraph.getBaseGraph().commit();
//
//        ndexNetworkService.createNetwork(RidConverter.convertFromRID((ORID)xUser.asVertex().getId()), rootNode, orientGraph);
//
//        orientGraph.getBaseGraph().commit();
//
//        JsonNode result = ndexNetworkService.findNetworks("BEL", 10, 0, orientGraph, objectMapper);
//        Assert.assertEquals(((ArrayNode) result.get("networks")).size(), 1);
    }

    public void testGetNetworkByEdges() throws Exception
    {
//        final ObjectMapper objectMapper = new ObjectMapper();
//        JsonNode rootNode = objectMapper.readTree(NdexNetworkServiceTest.class.getResourceAsStream(jdexFile));
//
//        final XUser xUser = orientGraph.addVertex("class:xUser", XUser.class);
//        orientGraph.getBaseGraph().commit();
//
//        XNetwork network = ndexNetworkService.createNetwork(RidConverter.convertFromRID((ORID)
//        xUser.asVertex().getId()), rootNode, orientGraph);
//        orientGraph.getBaseGraph().commit();
//
//        final ORID networkId = (ORID) network.asVertex().getId();
//
//        ndexNetworkService.getNetworkByEdges(RidConverter.convertFromRID(networkId), 0, 100, orientGraph, objectMapper);
    }

    public void testGetNetworkByNodes() throws Exception
    {
//        final ObjectMapper objectMapper = new ObjectMapper();
//        JsonNode rootNode = objectMapper.readTree(NdexNetworkServiceTest.class.getResourceAsStream(jdexFile));
//
//        final XUser xUser = orientGraph.addVertex("class:xUser", XUser.class);
//        orientGraph.getBaseGraph().commit();
//
//        XNetwork network = ndexNetworkService.createNetwork(RidConverter.convertFromRID((ORID)xUser.asVertex().getId()), rootNode, orientGraph);
//        orientGraph.getBaseGraph().commit();
//
//        final ORID networkId = (ORID) network.asVertex().getId();
//
//        ndexNetworkService.getNetworkByNodes(RidConverter.convertFromRID(networkId), 0, 100, orientGraph, objectMapper);
    }
}
