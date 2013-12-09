package org.ndexbio.orientdb.service;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.ndexbio.rest.domain.INetwork;
import org.ndexbio.rest.domain.Permissions;
import org.ndexbio.rest.exceptions.NdexException;
import org.ndexbio.rest.models.Membership;
import org.ndexbio.rest.models.Network;
import org.ndexbio.rest.models.SearchParameters;
import org.ndexbio.rest.models.SearchResult;
import org.ndexbio.rest.models.User;
import org.ndexbio.rest.services.UserService;

/*
 * A Singleton to provide a instance of a test network with fixed metadata
 * 
 * FOR TESTING PURPOSES ONLY
 */
public enum OrientdbNetworkFactory {
	INSTANCE;
	private String testUserName = "jstegall";
	
	public INetwork createTestNetwork(String title) {
		 final INetwork testNetwork = XBelNetworkService.getInstance().createNewNetwork(network);
		 List<Membership> membershipList = new ArrayList<Membership>();
			Membership membership = new Membership();
			User testUser = XBelNetworkService.getInstance().resolveUserUserByUsername(testUserName);
			membership.setResourceId(testUser.getId());
			membership.setResourceName(testUser.getUsername());
			membership.setPermissions(Permissions.ADMIN);
			membershipList.add(membership);
			//testNetwork.setMembers(membershipList);
			testNetwork.setTitle(title);
		 
		 return  testNetwork;
	}
	
}
