package org.ndexbio.orientdb.service;

import java.util.concurrent.ExecutionException;

import org.ndexbio.rest.domain.IBaseTerm;
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
import org.ndexbio.xbel.model.Parameter
;

import com.google.common.base.Preconditions;
import com.orientechnologies.orient.core.id.ORID;


/*
 * represents a class responsible for mapping XBel model objects to new Ndex domain objects
 * 
 * The primary justification for this class is to separate the use of XBel
 * model objects from identically named NDEx model objects
 */
public class XBelNetworkService  {
	
	private static XBelNetworkService instance;
	
	private static boolean databaseConnected = false;
	private NDExPersistenceService persistenceService;
	
	public static XBelNetworkService getInstance(){
		if( null == instance){
			instance = new XBelNetworkService();
		}
		return instance;
	}
	
	
 
	private XBelNetworkService() {
		super();
		 this.persistenceService = NDExPersistenceServiceFactory.INSTANCE.getNDExPersistenceService();
	}
	
	
	
	public INetwork createNewNetwork(Network network) throws Exception {
		return this.persistenceService.createNetwork(network);
	}
	
	  
	    /*
	     * public method to map a XBEL model Parameter object to a orientdb IBaseTerm object
	     * n.b. this method creates a vertex in the orientdb database
	     */
	    public IBaseTerm createIBaseTerm(Parameter p, Long jdexId) throws ExecutionException{
	    	Preconditions.checkArgument(null != p, "A Parameter object is required");
	    	Preconditions.checkArgument(null != jdexId && jdexId.longValue() >0  , "A valid jdex id is required");
	    	final IBaseTerm bt = persistenceService.findOrCreateIBaseTerm(jdexId);
	    	
	    	bt.setName(p.getValue());
	    	// resolve INamespace reference for this parameter from cache	    	
        	bt.setNamespace(persistenceService.findNamespaceByPrefix(p.getNs()));	    	
	    	bt.setJdexId(jdexId.toString());
	    	return bt;    	
	    }
	    
	    /*
	     * public method to map a XBEL model namespace object to a orientdb INamespace object
	     * n.b. this method may result in a new vertex in the orientdb database being created
	     */
	    public INamespace createINamespace(Namespace ns, Long jdexId) throws ExecutionException {
	    	Preconditions.checkArgument(null != ns, "A Namespace object is required");
	    	Preconditions.checkArgument(null != jdexId && jdexId.longValue() >0  , "A valid jdex id is required");
	    	INamespace newNamespace = persistenceService.findOrCreateINamespace(jdexId);
	    	newNamespace.setJdexId(jdexId.toString());
	    	newNamespace.setPrefix(ns.getPrefix());
	    	newNamespace.setUri(ns.getResourceLocation());
	    	return newNamespace;
	    	
	    }

}
