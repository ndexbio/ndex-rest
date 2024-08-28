/**
 * Copyright (c) 2013, 2016, The Regents of the University of California, The Cytoscape Consortium
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */
package org.ndexbio.rest;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.NewUser;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.services.UserService;

import com.fasterxml.jackson.databind.ObjectMapper;

public class NetworkQueryTest {

	
    private static User requestingUser = new User();
    private static HttpServletRequest mockRequest = EasyMock.createMock(HttpServletRequest.class);
	private static final String jdexFile = "/resources/reactome-test.jdex";
	private static UserService userService = new UserService(mockRequest);
	private static User queryTester;
	//private static Network queryNetwork;

	

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
