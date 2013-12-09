package org.ndexbio.orientdb.service;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.ndexbio.rest.domain.INetwork;
import org.ndexbio.rest.domain.INetworkMembership;
import org.ndexbio.rest.domain.IUser;
import org.ndexbio.rest.domain.Permissions;
import org.ndexbio.rest.exceptions.NdexException;
import org.ndexbio.rest.models.Membership;
import org.ndexbio.rest.models.Network;
import org.ndexbio.rest.models.SearchParameters;
import org.ndexbio.rest.models.SearchResult;
import org.ndexbio.rest.models.User;
import org.ndexbio.rest.services.UserService;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

/*
 * A Singleton to provide a instance of a test network with fixed metadata
 * 
 * FOR TESTING PURPOSES ONLY
 */
public enum OrientdbNetworkFactory {
	INSTANCE;
	private String testUserName = "jstegall";
	
	public INetwork createTestNetwork(String title) throws Exception {
		 final INetwork testNetwork = XBelNetworkService.getInstance().createNewNetwork();
		
		 List<Membership> membershipList = new ArrayList<Membership>();
			Membership membership = new Membership();
			IUser testUser = this.resolveUserUserByUsername(testUserName);
			INetworkMembership newMember = XBelNetworkService.getInstance().createNewMember();
			//membership.setResourceId();
			membership.setResourceName(testUser.getUsername());
			membership.setPermissions(Permissions.ADMIN);
			membershipList.add(membership);
			testNetwork.addMember(newMember);
			testNetwork.setTitle(title);
		 
		 return  testNetwork;
	}
	 public IUser resolveUserUserByUsername(String userName) {
	    	Preconditions.checkArgument(!Strings.isNullOrEmpty(userName), 
	    			"A username is required");
			SearchParameters searchParameters = new SearchParameters();
			searchParameters.setSearchString(userName);
			searchParameters.setSkip(0);
			searchParameters.setTop(1);

			try {
				SearchResult<IUser> result = XBelNetworkService.getInstance().findUsers(searchParameters);
				return  (IUser) result.getResults().iterator().next();
				
			} catch (NdexException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();		
			}
			return null;
		}
}
