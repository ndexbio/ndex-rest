package org.ndexbio.common.solr;

import java.io.IOException;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;

/**
 *
 * @author churas
 */
public interface SolrClientAndCoreAdminFactory {
	
	public SolrClient getSolrClient(final String baseSolrUrl);
	
	public CoreAdminResponse getCoreAdminRequestGetStatus(final String coreName, SolrClient client) throws IOException, SolrServerException;
	
	public CoreAdminRequest.Create getCoreAdminRequestCreate();
	
	public CoreAdminResponse getCoreAdminRequestUnloadCore(String name, boolean deleteIndex, boolean deleteInstanceDir, SolrClient client) throws IOException, SolrServerException;
}
