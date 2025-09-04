package org.ndexbio.common.solr;

import java.io.IOException;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;

/**
 *
 * @author churas
 */
public class SolrClientAndCoreAdminFactoryImpl implements SolrClientAndCoreAdminFactory {

	@Override
	public SolrClient getSolrClient(String baseSolrUrl) {
		return new Http2SolrClient.Builder(baseSolrUrl).build();
	}

	@Override
	public CoreAdminResponse getCoreAdminRequestGetStatus(String coreName, SolrClient client) throws IOException, SolrServerException {
		return CoreAdminRequest.getStatus(coreName, client);
	}

	@Override
	public CoreAdminRequest.Create getCoreAdminRequestCreate() {
		return new CoreAdminRequest.Create();
	}

	@Override
	public CoreAdminResponse getCoreAdminRequestUnloadCore(String name, boolean deleteIndex, boolean deleteInstanceDir, SolrClient client) throws IOException, SolrServerException {
		CoreAdminRequest.unloadCore(name, deleteIndex, deleteInstanceDir, client);
	}
	
}
