package org.ndexbio.rest;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.ndexbio.common.exceptions.NdexException;
import org.ndexbio.common.models.object.NetworkQueryParameters;
import org.ndexbio.model.object.NewUser;
import org.ndexbio.model.object.User;
import org.ndexbio.model.object.network.Network;
import org.ndexbio.rest.services.NetworkService;
import org.ndexbio.rest.services.UserService;

import com.fasterxml.jackson.databind.ObjectMapper;

public class NetworkQueryTest {

	
    private static User requestingUser = new User();
    private static HttpServletRequest mockRequest = EasyMock.createMock(HttpServletRequest.class);
	private static final String jdexFile = "/resources/reactome-test.jdex";
	private static NetworkService networkService = new NetworkService(mockRequest);
	private static UserService userService = new UserService(mockRequest);
	private static User queryTester;
	private static Network queryNetwork;

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
				newUser.setAccountName("querytester");
				newUser.setPassword("querytester");
				newUser.setEmailAddress("dexterpratt.bio+networkQT@gmail.com");

				System.out.println("Creating test user.");
		/*		queryTester = userService.createUser(newUser);
				System.out.println("Creating test network owned by user "
						+ queryTester.getId());
				
				List<Membership> membershipList = new ArrayList<Membership>();
	            Membership membership = new Membership();
	            membership.setResourceId(queryTester.getId());
	            membership.setResourceName(queryTester.getUsername());
	            membership.setPermissions(Permissions.ADMIN);
	            membershipList.add(membership);
	            networkToCreate.setMembers(membershipList);
				queryNetwork = networkService.createNetwork(networkToCreate);
				System.out.println("Network created with id "
						+ queryNetwork.getId()); */
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

/*	public static void afterMethod() {
		try {
			if (queryNetwork != null) {
				System.out.println("Deleting test network "
						+ queryNetwork.getExternalId());
			//	networkService.deleteNetwork(queryNetwork.getExternalId());
			}
			if (queryTester != null) {
				System.out.println("Deleting test user." ) ; //queryTester.getId());
			//	userService.deleteUser();
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
*/
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

/*	public void searchNeighborhoodByTerm() {
		// List<ODocument> terms =
		// orientGraph.getBaseGraph().getRawGraph().query(new
		// OSQLSynchQuery<ODocument>("select from baseTerm where name = 'AKT1'"));
		// ORID termId = terms.get(0).getIdentity();
		try {
			String termString = "RBL1_HUMAN";
			System.out.println("Finding term " + termString
					+ " in test network " + queryNetwork.getId());
			List<String> termStrings = new ArrayList<String>();
			termStrings.add(termString);


				NetworkQueryParameters networkQueryParameters = new NetworkQueryParameters();
				networkQueryParameters.setStartingTermStrings(termStrings);
				networkQueryParameters
						.setRepresentationCriterion(RepresentationCriteria.STRICT
								.toString());
				networkQueryParameters
						.setSearchType(SearchType.BOTH.toString());
				networkQueryParameters.setSearchDepth(1);

				System.out.println("Starting neighborhood query.");
				Network neighborhoodNetwork = networkService.queryNetwork(
						queryNetwork.getId(), networkQueryParameters);

				Assert.assertNotNull(neighborhoodNetwork);

		} catch (NdexException e) {
			// TODO Auto-generated catch block
			Assert.fail(e.getMessage());
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	} */
}
