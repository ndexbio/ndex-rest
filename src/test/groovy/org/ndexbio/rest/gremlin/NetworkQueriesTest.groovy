/*package org.ndexbio.rest.gremlin

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.db.record.OIdentifiable
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph
import com.tinkerpop.blueprints.impls.orient.OrientGraph
import com.tinkerpop.blueprints.impls.orient.OrientVertex
import com.tinkerpop.frames.FramedGraph
import com.tinkerpop.frames.FramedGraphFactory
import com.tinkerpop.frames.modules.gremlingroovy.GremlinGroovyModule

import org.codehaus.jackson.JsonNode
import org.codehaus.jackson.map.ObjectMapper
import org.junit.AfterClass
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test
import org.ndexbio.rest.NdexSchemaManager
import org.ndexbio.rest.domain.IUser
import org.ndexbio.rest.services.NetworkService
import org.ndexbio.rest.services.UserService
import org.ndexbio.rest.helpers.RidConverter
import org.ndexbio.rest.models.Network;
import org.ndexbio.rest.models.User;
import org.ndexbio.rest.models.NewUser;


class NetworkQueriesTest {
    private static final String jdexFile = "/resources/small_corpus.jdex";
    private static NetworkService networkService = new NetworkService();
	private static UserService userService = new UserService();
	private static User queryTester;
	private static Network queryNetwork;

    @BeforeClass
    public static void beforeMethod() {

        final ObjectMapper objectMapper = new ObjectMapper();
        //JsonNode rootNode = objectMapper.readTree(NetworkServiceTest.class.getResourceAsStream(jdexFile));
		Network networkToCreate = objectMapper.readValue(jdexFile, Network.class);
		
		NewUser newUser = new NewUser();
		newUser.setUsername("querytester");
		newUser.setPassword("querytester");
		newUser.setEmailAddress("dexterpratt.bio@gmail.com");
		
		queryTester = userService.createUser(newUser);
        String userId = queryTester.getId();

        queryNetwork = NetworkService.createNetwork(queryTester.getId(), Network);
    }

    @AfterClass
    public static void afterMethod() {
		networkService.deleteNetwork(queryNetwork.getId());
        userService.deleteUser(queryTester.getId());
		networkService.closeOrientDbConnection();
		userService.closeOrientDbConnection();
    }

    @Test
    public void testPermissiveSearch() {
        List<ODocument> terms = orientGraph.getBaseGraph().getRawGraph().query(new OSQLSynchQuery<ODocument>("select from baseTerm where name = 'AKT1'"));
        ORID termId = terms.get(0).getIdentity();

        def nodesPipe = NetworkQueries.INSTANCE.getRepresentedVertices(orientGraph.getBaseGraph(), RepresentationCriteria.PERMISSIVE, [termId] as OIdentifiable[], new String[0]);
        def List<OrientVertex> nodes = [];
        nodesPipe.store(nodes).iterate();

        Assert.assertTrue(!nodes.isEmpty());

        for (def OrientVertex node in nodes)
            Assert.assertEquals("node", node.record.className)
    }

    @Test
    public void searchNeighborhoodByTerm() {
        List<ODocument> terms = orientGraph.getBaseGraph().getRawGraph().query(new OSQLSynchQuery<ODocument>("select from baseTerm where name = 'AKT1'"));
        ORID termId = terms.get(0).getIdentity();

        SearchSpec searchSpec = new SearchSpec();
        searchSpec.startingTerms = [termId];
        searchSpec.startingTermStrings = [];
        searchSpec.representationCriterion = RepresentationCriteria.PERMISSIVE;
        searchSpec.searchType = SearchType.BOTH;
        searchSpec.searchDepth = 1;

        def Set<OrientVertex> foundEdges = NetworkQueries.INSTANCE.searchNeighborhoodByTerm(orientGraph.getBaseGraph(), searchSpec);

        Assert.assertTrue(!foundEdges.isEmpty());

        for (def OrientVertex edge in foundEdges)
            Assert.assertEquals("edge", edge.record.className)
    }
}


*/
