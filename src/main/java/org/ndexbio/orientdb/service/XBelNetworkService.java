package org.ndexbio.orientdb.service;

import org.ndexbio.rest.domain.INamespace;
import org.ndexbio.rest.domain.INetwork;
import org.ndexbio.rest.domain.INetworkMembership;
import org.ndexbio.rest.domain.IUser;
import org.ndexbio.rest.domain.Permissions;
import org.ndexbio.rest.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.exceptions.ValidationException;
import org.ndexbio.rest.helpers.RidConverter;
import org.ndexbio.rest.models.Membership;
import org.ndexbio.rest.models.Network;
import org.ndexbio.xbel.model.Namespace;

import com.orientechnologies.orient.core.id.ORID;

/*
 * represents a collection of orientdb database operations specific to
 * loading networks from XBEL data
 * The primary justification for this class is to separate the use of XBel
 * model objects from identically named NDEx model objects
 */
public class XBelNetworkService extends NetworkService {
	
	private static XBelNetworkService instance;
	
	private static boolean databaseConnected = false;
	
	public static XBelNetworkService getInstance(){
		if( null == instance){
			instance = new XBelNetworkService();
		}
		return instance;
	}
	
	
 
	private XBelNetworkService() {
		super();
		 setupDatabase();
		 System.out.println("Connected to OrientDb databse established. ");
		 databaseConnected = true;
	}
	
	public boolean isDatabaseConnected() {
		return databaseConnected;
	}
	
	public INetwork createNewNetwork() {
		 return _orientDbGraph.addVertex("class:network", INetwork.class);
	}
	
	 /**************************************************************************
	    * Creates a network.
	    * 
	    * @param ownerId
	    *            The ID of the user creating the group.
	    * @param newNetwork
	    *            The network to create.
	    **************************************************************************/
	    
	    public Network createNetwork(final Network newNetwork) throws Exception
	    {
	        
	        if (newNetwork == null)
	            throw new ValidationException("The network to create is null.");
	        else if (newNetwork.getMembers() == null || newNetwork.getMembers().size() == 0)
	            throw new ValidationException("The network to create has no members specified.");

	        try
	        {
	           
	            final Membership newNetworkMembership = newNetwork.getMembers().get(0);
	            final ORID userRid = RidConverter.convertToRid(newNetworkMembership.getResourceId());

	            final IUser networkOwner = _orientDbGraph.getVertex(userRid, IUser.class);
	            if (networkOwner == null)
	                throw new ObjectNotFoundException("User", newNetworkMembership.getResourceId());

	            final INetwork network = _orientDbGraph.addVertex("class:network", INetwork.class);

	            final INetworkMembership membership = _orientDbGraph.addVertex("class:networkMembership", INetworkMembership.class);
	            membership.setPermissions(Permissions.ADMIN);
	            membership.setMember(networkOwner);
	            membership.setNetwork(network);
	            networkOwner.addNetwork(membership);
	            network.addMember(membership);
	            network.setIsPublic(false);
	            network.setFormat(newNetwork.getFormat());
	            network.setSource(newNetwork.getSource());
	            network.setTitle(newNetwork.getTitle());

	            
	            return new Network(network);
	        }catch (Exception e)
	        {
	            _orientDbGraph.getBaseGraph().rollback();
	            throw e;
	        }
	    }
	    
	    public void closeDatabase() {
	    	if(databaseConnected){
	    		teardownDatabase();
	    		System.out.println("Connection to OrientDb database closed");
	    		databaseConnected = false;
	    	}
	    }
	    
	    public INamespace createNamespace(Namespace ns, Long jdexId) {
	    	final INamespace newNamespace = _orientDbGraph.addVertex("class:namespace", INamespace.class);
	    	newNamespace.setJdexId(jdexId.toString());
	    	newNamespace.setPrefix(ns.getPrefix());
	    	newNamespace.setUri(ns.getResourceLocation());
	    	return newNamespace;
	    	
	    }

}
