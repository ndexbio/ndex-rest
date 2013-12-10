package org.ndexbio.xbel.splitter;

import java.util.concurrent.ExecutionException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.ndexbio.orientdb.service.NDExPersistenceService;
import org.ndexbio.orientdb.service.NDExPersistenceServiceFactory;
import org.ndexbio.orientdb.service.XBelNetworkService;
import org.ndexbio.rest.domain.INamespace;
import org.ndexbio.xbel.cache.XbelCacheService;
import org.ndexbio.xbel.model.Namespace;
import org.ndexbio.xbel.model.NamespaceGroup;

public class NamespaceGroupSplitter extends XBelSplitter {
	
	private static final String xmlElement = "namespaceGroup";
 /*
  * Extension of XBelSplitter to parse NamespaceGroup data from an XBEL document
  */
	public NamespaceGroupSplitter(JAXBContext context) {
		super(context, xmlElement);
	}
	@Override
	/*
	 * method to process unmarshaled  XBEL namespace elements from XBEL document
	 * responsible for registering novel namespace prefixes in the identifier cache,
	 * for determining the new or existing jdex id for the namespace and for persisting
	 * new namespaces into the orientdb databases
	 * 
	 * @see org.ndexbio.xbel.splitter.XBelSplitter#process()
	 */
	protected void process() throws JAXBException {
		NamespaceGroup ng = (NamespaceGroup) unmarshallerHandler
				.getResult();
		 System.out.println("The XBEL document has "  +ng.getNamespace().size() 
		            +" namespaces");
		 NDExPersistenceService persistenceService = NDExPersistenceServiceFactory.
				 INSTANCE.getNDExPersistenceService();
		 	// create BEL namespace
		    Namespace bel = new Namespace();
		    bel.setPrefix("BEL");
		    bel.setResourceLocation("XYZ");
		    Long jdex;
			try {
				jdex = XbelCacheService.INSTANCE.accessIdentifierCache()
						.get(bel.getPrefix());
				INamespace ibel = XBelNetworkService.getInstance().createINamespace(bel, jdex);
			} catch (ExecutionException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}	
		   
		        for( Namespace ns : ng.getNamespace()){
		        	
		        	try {
		        		// get a existing or new JDEXid from cache
						Long jdexId = XbelCacheService.INSTANCE.accessIdentifierCache()
							.get(ns.getPrefix());
						// create a INamespace instance using data from the Namespace model object	
						// n.b. this method may create a VertexFrame in the orientdb database
						INamespace ins = XBelNetworkService.getInstance().createINamespace(ns, jdexId);
						
					} catch (ExecutionException e) {
						
						//TODO utilize a central application logger
						e.printStackTrace();
					}
		        	
		        }
		        
	}	
	
		
	
}
