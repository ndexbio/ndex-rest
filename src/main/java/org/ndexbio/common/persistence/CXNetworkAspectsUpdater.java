package org.ndexbio.common.persistence;

import java.io.IOException;
import java.util.UUID;

import org.apache.solr.client.solrj.SolrServerException;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;

public class CXNetworkAspectsUpdater extends CXNetworkLoader {

	private UUID aspectsCXNetworkID;
	
	public CXNetworkAspectsUpdater(UUID networkUUID, String ownerUserName, NetworkDAO networkDao, UUID aspectsCXUUID)
			throws SolrServerException, IOException {
		super(networkUUID, ownerUserName, true, networkDao);
		
		this.aspectsCXNetworkID = aspectsCXUUID;
	}

	public void update() {
		
	}
	
}
