package org.ndexbio.rest;

import java.io.File;
import java.net.URL;
import java.util.Collection;

import org.codehaus.jackson.map.ObjectMapper;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.ndexbio.rest.exceptions.NdexException;
import org.ndexbio.rest.gremlin.RepresentationCriteria;
import org.ndexbio.rest.gremlin.SearchType;
import org.ndexbio.rest.models.Network;
import org.ndexbio.rest.models.NetworkQueryParameters;
import org.ndexbio.rest.models.NewUser;
import org.ndexbio.rest.models.Term;
import org.ndexbio.rest.models.User;
import org.ndexbio.rest.services.NetworkService;
import org.ndexbio.rest.services.UserService;

public class NetworkQueryTest {

	private static final String jdexFile = "/resources/reactome-test.jdex";
	private static NetworkService networkService = new NetworkService();
	private static UserService userService = new UserService();
	private static User queryTester;
	private static Network queryNetwork;

	@BeforeClass
	public static void beforeMethod() {

		final ObjectMapper objectMapper = new ObjectMapper();
		// JsonNode rootNode =
		// objectMapper.readTree(NetworkServiceTest.class.getResourceAsStream(jdexFile));

		try {
			/*
			 * System.out.println("About to load network from: " + jdexFile);
			 * InputStream networkStream =
			 * NetworkQueryTest.class.getResourceAsStream(jdexFile);
			 * System.out.println("Got input stream"); Network networkToCreate =
			 * objectMapper.readValue(networkStream, Network.class);
			 * System.out.println("Got Network JDEx");
			 */

			URL testNetworkUrl = NetworkQueryTest.class.getResource(jdexFile);
			File networkFile = new File(testNetworkUrl.toURI());

			System.out.println("Creating network from file: "
					+ networkFile.getName() + ".");
			Network networkToCreate = objectMapper.readValue(networkFile,
					Network.class);

			if (networkToCreate != null) {
				System.out.println("Network JDEx is not null");
				NewUser newUser = new NewUser();
				newUser.setUsername("querytester");
				newUser.setPassword("querytester");
				newUser.setEmailAddress("dexterpratt.bio@gmail.com");

				System.out.println("Creating test user.");
				queryTester = userService.createUser(newUser);
				System.out.println("Creating test network owned by user "
						+ queryTester.getId());
				queryNetwork = networkService.createNetwork(
						queryTester.getId(), networkToCreate);
				System.out.println("Network created with id "
						+ queryNetwork.getId());
			} else {
				System.out.println("Failed to load network");
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			System.out.println("Failed in beforeMethod");
			Assert.fail(e.getMessage());
			e.printStackTrace();
		}

	}

	@AfterClass
	public static void afterMethod() {
		try {
			if (queryNetwork != null) {
				System.out.println("Deleting test network "
						+ queryNetwork.getId());
				networkService.deleteNetwork(queryNetwork.getId());
			}
			if (queryTester != null) {
				System.out.println("Deleting test user." + queryTester.getId());
				userService.deleteUser(queryTester.getId());
			}
		} catch (NdexException e) {
			System.out.println("Failed in afterMethod");
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			System.out.println("Failed in afterMethod");
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/*
	 * @Test public void testPermissiveSearch() { List<ODocument> terms =
	 * orientGraph.getBaseGraph().getRawGraph().query(new
	 * OSQLSynchQuery<ODocument>("select from baseTerm where name = 'AKT1'"));
	 * ORID termId = terms.get(0).getIdentity();
	 * 
	 * def nodesPipe =
	 * NetworkQueries.INSTANCE.getRepresentedVertices(orientGraph
	 * .getBaseGraph(), RepresentationCriteria.PERMISSIVE, [termId] as
	 * OIdentifiable[], new String[0]); def List<OrientVertex> nodes = [];
	 * nodesPipe.store(nodes).iterate();
	 * 
	 * Assert.assertTrue(!nodes.isEmpty());
	 * 
	 * for (def OrientVertex node in nodes) Assert.assertEquals("node",
	 * node.record.className) }
	 */

	@Test
	public void searchNeighborhoodByTerm() {
		// List<ODocument> terms =
		// orientGraph.getBaseGraph().getRawGraph().query(new
		// OSQLSynchQuery<ODocument>("select from baseTerm where name = 'AKT1'"));
		// ORID termId = terms.get(0).getIdentity();
		try {
			String termString = "RBL1_HUMAN";
			System.out.println("Finding term " + termString
					+ " in test network " + queryNetwork.getId());
			Collection<Term> terms = networkService.getBaseTermsByName(
					queryNetwork.getId(), termString);
			if (terms.size() == 0) {
				Assert.fail("no term found with name " + termString);
			} else {
				Term term = terms.iterator().next();

				NetworkQueryParameters networkQueryParameters = new NetworkQueryParameters();
				networkQueryParameters.addStartingTermId(term.getId());
				networkQueryParameters
						.setRepresentationCriterion(RepresentationCriteria.PERMISSIVE
								.toString());
				networkQueryParameters
						.setSearchType(SearchType.BOTH.toString());
				networkQueryParameters.setSearchDepth(1);

				System.out.println("Starting neighborhood query.");
				Network neighborhoodNetwork = networkService.queryNetwork(
						queryNetwork.getId(), networkQueryParameters);

				Assert.assertTrue(!neighborhoodNetwork.getEdges().isEmpty());
			}
		} catch (NdexException e) {
			// TODO Auto-generated catch block
			Assert.fail(e.getMessage());
			e.printStackTrace();
		}

	}
}
