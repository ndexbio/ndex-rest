package org.ndexbio.orientdb.service;

import java.util.concurrent.ExecutionException;

import org.ndexbio.rest.domain.IBaseTerm;
import org.ndexbio.rest.domain.ICitation;
import org.ndexbio.rest.domain.INamespace;
import org.ndexbio.rest.domain.INetwork;
import org.ndexbio.rest.domain.INetworkMembership;
import org.ndexbio.rest.domain.ISupport;
import org.ndexbio.rest.domain.IUser;
import org.ndexbio.rest.exceptions.NdexException;
import org.ndexbio.rest.models.Network;
import org.ndexbio.xbel.cache.XbelCacheService;
import org.ndexbio.xbel.model.Citation;
import org.ndexbio.xbel.model.Namespace;
import org.ndexbio.xbel.model.Parameter;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;


/*
 * represents a class responsible for mapping XBel model objects to new Ndex domain objects
 * 
 * The primary justification for this class is to separate the use of XBel
 * model objects from identically named NDEx model objects
 */
public class XBelNetworkService  {
	
	private static XBelNetworkService instance;
	
	private NDExPersistenceService persistenceService;
	private static Joiner idJoiner = Joiner.on(":").skipNulls();
	
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
	
	public INetwork createNewNetwork() throws Exception {
		return this.persistenceService.getCurrentNetwork();
	}
	
	public IUser createNewUser(String username) {
		Preconditions.checkArgument(!Strings.isNullOrEmpty(username));
		IUser user = this.persistenceService.getCurrentUser();
		user.setUsername(username);
		return user;
	}
	
	public INetworkMembership createNewMember() {
		return this.persistenceService.createNetworkMembership();
	}
	
	public SearchResult<IUser> findUsers(SearchParameters searchParameters) throws NdexException
	{
		return this.persistenceService.findUsers(searchParameters);
	}
	
	public void closeDatabaseConnection(){
		this.persistenceService.closeDatabase();
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
	    
	    /*
	     * public method to map a XBEL model Citation object to a orientdb ICitation object
	     * n.b. this method may result in a new vertex in the orientdb database being created
	     */
	    public ICitation findOrCreateICitation(Citation citation) throws ExecutionException {
	    	Preconditions.checkArgument(null != citation, "A Citation object is required");
	    	String citationIdentifier = idJoiner.join(citation.getName(), citation.getReference());
	    	Long jdexId = XbelCacheService.INSTANCE.accessIdentifierCache().get(citationIdentifier);
	    	boolean persisted = persistenceService.isEntityPersisted(jdexId);
	    	ICitation iCitation = persistenceService.findOrCreateICitation(jdexId);
	    	if (persisted) return iCitation;
	    	iCitation.setTitle(citation.getName());
            iCitation.setType(citation.getType().value());
            iCitation.setContributors(citation.getAuthorGroup().getAuthor());
	    	return iCitation;
	    	
	    }
	    
	    /*
	     * public method to map a XBEL model evidence string in the context of a Citation to a orientdb ISupport object
	     * n.b. this method may result in a new vertex in the orientdb database being created
	     */
	    public ISupport findOrCreateISupport(String evidenceString, ICitation iCitation) throws ExecutionException {
	    	Preconditions.checkArgument(null != evidenceString, "An evidence string is required");
	    	String supportIdentifier = idJoiner.join(iCitation.getJdexId(), (String) evidenceString);
	    	Long jdexId = XbelCacheService.INSTANCE.accessIdentifierCache().get(supportIdentifier);
	    	boolean persisted = persistenceService.isEntityPersisted(jdexId);
	    	ISupport iSupport = persistenceService.findOrCreateISupport(jdexId);
	    	if (persisted) return iSupport;
	    	iSupport.setText(evidenceString);
	    	if (null != iCitation){
	    		iSupport.setCitation(iCitation);
	    	}
	    	return iSupport;
	    }
	    


}
