package org.ndexbio.common.solr;

import java.io.IOException;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;
import org.ndexbio.common.models.search.UnifiedSearchManager;

/**
 * SolrClient and CoreAdmin factory implementation using Http2SolrClient
 * object and wrappers around CoreAdminRequest calls
 * 
 * @author churas
 */
public class SolrObjectFactoryImpl implements SolrObjectFactory {

	private final String _baseSolrUrl;
	private static final String SLASH = "/";
	
	/**
	 * Constructor
	 * 
	 * @param baseSolrUrl Should be URL to solr service ie http://localhost:8983/solr
	 */
	public SolrObjectFactoryImpl(final String baseSolrUrl){
		_baseSolrUrl = baseSolrUrl;
	}
	
	/**
	 * Gets Solr client
	 * @param coreName Core to tether client to. If null or empty string then base client is returned
	 * @return Solr client of type Http2SolrClient
	 */
	@Override
	public SolrClient getSolrClient(final String coreName) {
		if (coreName == null || coreName.trim().isEmpty()){
			return new Http2SolrClient.Builder(_baseSolrUrl).build();
		}
		var sb = new StringBuilder().append(_baseSolrUrl).append(SLASH).append(coreName);
		return new Http2SolrClient.Builder(sb.toString()).build();
	}

	/**
	 * Gets status of core
	 * @param coreName Name of core to query
	 * @return Response of query to Solr
	 * @throws IOException
	 * @throws SolrServerException 
	 */
	@Override
	public CoreAdminResponse getCoreAdminRequestGetStatus(final String coreName) throws IOException, SolrServerException {
		return CoreAdminRequest.getStatus(coreName, getSolrClient(null));
	}

	/**
	 * Creates CoreAdminRequest.Create.
	 * @return 
	 */
	@Override
	public CoreAdminRequest.Create getCoreAdminRequestCreate() {
		return new CoreAdminRequest.Create();
	}

	/**
	 * Requests unload of Core from Solr. 
	 * @param coreName Name of core to unload
	 * @param deleteIndex denotes whether to delete index
	 * @param deleteInstanceDir denotes whether to remove index directory
	 * @return response of request
	 * @throws IOException
	 * @throws SolrServerException 
	 */
	@Override
	public CoreAdminResponse getCoreAdminRequestUnloadCore(final String coreName, boolean deleteIndex, boolean deleteInstanceDir) throws IOException, SolrServerException {
		return CoreAdminRequest.unloadCore(coreName, deleteIndex, deleteInstanceDir, getSolrClient(null));
	}

	@Override
	public PublicNFSIndexManager getPublicNFSIndexManager() {
		return new PublicNFSIndexManager(new SolrClientWrapperImpl(this));
	}

	@Override
	public PrivateNFSIndexManager getPrivateNFSIndexManager() {
		return new PrivateNFSIndexManager(new SolrClientWrapperImpl(this));
	}

	@Override
	public GlobalNetworkIndexManager getGlobalNetworkIndexManager() {
		return new GlobalNetworkIndexManager(new SolrClientWrapperImpl(this));
	}

	@Override
	public FolderIndexManager getFolderIndexManager() {
		return new FolderIndexManager(new SolrClientWrapperImpl(this));
	}

	@Override
	public ShortcutIndexManager getShortcutIndexManager() {
		return new ShortcutIndexManager(new SolrClientWrapperImpl(this));
	}

	@Override
	public UnifiedSearchManager getIndexSearchManager() {
		return new UnifiedSearchManager(new SolrClientWrapperImpl(this));
	}


}
