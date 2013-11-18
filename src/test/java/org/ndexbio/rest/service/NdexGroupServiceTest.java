package org.ndexbio.rest.service;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.index.OIndexException;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.blueprints.impls.orient.OrientGraph;
import com.tinkerpop.frames.FramedGraph;
import com.tinkerpop.frames.FramedGraphFactory;
import com.tinkerpop.frames.modules.gremlingroovy.GremlinGroovyModule;
import org.ndexbio.rest.NdexSchemaManager;
import org.ndexbio.rest.domain.XUser;
import org.ndexbio.rest.exceptions.ValidationException;
import org.ndexbio.rest.helpers.RidConverter;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

//TODO: Refactor
/**
 * @author <a href="mailto:enisher@gmail.com">Artem Orobets</a>
 */
public class NdexGroupServiceTest
{
    // private final NdexGroupService ndexGroupService = new NdexGroupService();
    private FramedGraph<OrientBaseGraph> graph;

    @BeforeMethod
    public void setUp() throws Exception
    {
        ODatabaseDocumentTx databaseDocumentTx = new ODatabaseDocumentTx("memory:ndexNetworkServiceTest");
        databaseDocumentTx.create();

        FramedGraphFactory factory = new FramedGraphFactory(new GremlinGroovyModule());
        graph = factory.create((OrientBaseGraph) new OrientGraph(databaseDocumentTx));

        NdexSchemaManager.INSTANCE.init(graph.getBaseGraph());
    }

    @AfterMethod
    public void tearDown() throws Exception
    {
        graph.getBaseGraph().drop();
    }

    @Test
    public void testCreateGroup() throws Exception
    {
//        final XUser user = graph.addVertex("class:xUser", XUser.class);
//        user.setUsername("John");
//        graph.getBaseGraph().commit();
//
//        ndexGroupService.createGroup(RidConverter.convertFromRID((ORID)
//        user.asVertex().getId()), "newGroup", graph);
//        graph.getBaseGraph().commit();
    }

    //@Test(expectedExceptions = ValidationException.class, expectedExceptionsMessageRegExp = "\\QInvalid group name '++--='. Should contain only english letters and numbers and be at least 6 characters in length\\E")
    public void testCreateGroupWithInvalidName() throws Exception
    {
//        final XUser user = graph.addVertex("class:xUser", XUser.class);
//        user.setUsername("John");
//        graph.getBaseGraph().commit();
//
//        ndexGroupService.createGroup(RidConverter.convertFromRID((ORID)
//        user.asVertex().getId()), "++--=", graph);
//        graph.getBaseGraph().commit();
    }

    //@Test(expectedExceptions = { OIndexException.class })
    public void testCreateGroupsWithSameName() throws Exception
    {
//        final XUser user = graph.addVertex("class:xUser", XUser.class);
//        user.setUsername("John");
//        graph.getBaseGraph().commit();
//
//        ndexGroupService.createGroup(RidConverter.convertFromRID((ORID)
//        user.asVertex().getId()), "notUniqueGroup", graph);
//        graph.getBaseGraph().commit();
//        ndexGroupService.createGroup(RidConverter.convertFromRID((ORID)
//        user.asVertex().getId()), "notUniqueGroup", graph);
//        graph.getBaseGraph().commit();
    }
}
