package org.ndexbio.common.solr;

import java.io.IOException;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.request.ConfigSetAdminRequest;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;

/**
 * SolrClient and CoreAdmin factory implementation using Http2SolrClient
 * object and wrappers around CoreAdminRequest calls
 * 
 * @author churas
 */
public class SolrObjectFactoryImpl implements SolrObjectFactory {

	private final String _baseSolrUrl;
	private final String _ndexRoot;
	private static final String SLASH = "/";

	/**
	 * Constructor
	 *
	 * @param baseSolrUrl Should be URL to solr service ie http://localhost:8983/solr
	 * @param ndexRoot NDEx data root, used to locate the CX2 aspect files that node indexes
	 *                 are built from ie /opt/ndex
	 */
	public SolrObjectFactoryImpl(final String baseSolrUrl, final String ndexRoot){
		_baseSolrUrl = baseSolrUrl;
		_ndexRoot = ndexRoot;
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
		// getSolrClient builds a new client per call, and Http2SolrClient owns non-daemon Jetty
		// threads - leaving it open keeps a CLI process alive after main() returns.
		try (SolrClient client = getSolrClient(null)) {
			return CoreAdminRequest.getStatus(coreName, client);
		}
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
	 * Creates ConfigSetAdminRequest.Create.
	 * @return
	 */
	@Override
	public ConfigSetAdminRequest.Create getConfigSetAdminRequestCreate() {
		return new ConfigSetAdminRequest.Create();
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
		try (SolrClient client = getSolrClient(null)) {
			return CoreAdminRequest.unloadCore(coreName, deleteIndex, deleteInstanceDir, client);
		}
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
	public SingleNetworkSolrIdxManager getSingleNetworkSolrIdxManager(final String networkId) {
		return new SingleNetworkSolrIdxManager(networkId, new SolrClientWrapperImpl(this),
				new Cx2NodeIndexServiceImpl(_ndexRoot));
	}



}
