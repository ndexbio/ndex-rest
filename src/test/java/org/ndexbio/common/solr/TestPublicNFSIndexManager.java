package org.ndexbio.common.solr;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.Ignore;
import org.ndexbio.model.object.Folder;
import org.ndexbio.model.object.Shortcut;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.object.network.VisibilityType;
/**
 *
 * @author churas
 */
public class TestPublicNFSIndexManager {
	
	@Ignore
	public void testCreatePublicNFSIndex() throws Exception {
		SolrObjectFactoryImpl factory = new SolrObjectFactoryImpl("http://localhost:8983/solr");
		
		SolrClientWrapperImpl client = new SolrClientWrapperImpl(factory);
		
		client.createCoreIfNeeded(PublicNFSIndexManager.CORE_NAME);
		client.dropCore(PublicNFSIndexManager.CORE_NAME);
		client.createCoreIfNeeded(PublicNFSIndexManager.CORE_NAME);
		
		PublicNFSIndexManager manager = factory.getPublicNFSIndexManager();
		
		// add a folder
		Folder folder = new Folder();
		folder.setDescription("test description");
		folder.setName("foldername");
		folder.setExternalId(UUID.fromString("E19CA69A-6ED0-409E-984A-45B42CCC34D4"));
		folder.setCreationTime(Timestamp.from(Instant.now()));
		
		folder.setModificationTime(Timestamp.from(Instant.now()));
		manager.createIndexForDocument(folder);
		
		// add a network
		NetworkSummary ns = new NetworkSummary();
		ns.setCompleted(true);
		ns.setCreationTime(Timestamp.from(Instant.now()));
		ns.setModificationTime(Timestamp.from(Instant.now()));
		ns.setName("mynetwork");
		ns.setExternalId(UUID.fromString("14f69a40-2191-4dc4-83ec-84335899b77c"));
		ns.setDescription("network desc");
		ns.setVisibility(VisibilityType.PUBLIC);
		manager.createIndexForDocument(ns, null, null, null, null, null);
		
		
		// add a shortcut
		Shortcut shortcut = new Shortcut();
		shortcut.setName("shortcut");
		shortcut.setCreationTime(Timestamp.from(Instant.now()));
		shortcut.setModificationTime(Timestamp.from(Instant.now()));
		shortcut.setExternalId(UUID.fromString("028E4F7B-55BD-4135-AEAE-7D969DE88CEC"));
		manager.createIndexForDocument(shortcut);
		
	}
}
