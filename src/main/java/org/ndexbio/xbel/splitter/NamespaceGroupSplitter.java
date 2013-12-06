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
	protected void process() throws JAXBException {
		NamespaceGroup ng = (NamespaceGroup) unmarshallerHandler
				.getResult();
		 System.out.println("The XBEL document has "  +ng.getNamespace().size() 
		            +" namespaces");
		 NDExPersistenceService persistenceService = NDExPersistenceServiceFactory.
				 INSTANCE.getNDExPersistenceService();
		        for( Namespace ns : ng.getNamespace()){
		        	
		        	try {
		        		// get a existing or new JDEXid from cache
						Long jdexId = XbelCacheService.INSTANCE.accessIdentifierCache()
							.get(ns.getPrefix());
						// create a INamespace instance using data from the Namespace model object	
						// n.b. this method creates a VertexFrame in the orientdb database
						INamespace ins = XBelNetworkService.getInstance().createNamespace(ns, jdexId);
						//  cache the INamespace and persist  if new
						persistenceService.findOrCreateNdexEntity(ins);
					} catch (ExecutionException e) {
						// TODO Auto-generated catch block
						//TODO utilize a central application logger
						e.printStackTrace();
					}
		        	
		        }
		        
	}	
	
		
	
}
