package org.ndexbio.common.solr;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.request.ConfigSetAdminRequest;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;

/**
 * SolrClient and CoreAdmin factory implementation, owning the one Http2SolrClient this JVM
 * uses to talk to Solr.
 *
 * <p>The client is built once and shared by every manager and wrapper this factory hands out;
 * callers pass the core name on each request. Nothing a caller gets from here needs closing -
 * the client is closed once, by {@link #close()}, when the process shuts down.</p>
 *
 * @author churas
 */
public class SolrObjectFactoryImpl implements SolrObjectFactory {

	static final long CONNECT_TIMEOUT_SECONDS = 10;

	private final String _ndexRoot;
	private final Http2SolrClient _solrClient;

	/**
	 * Constructor
	 *
	 * @param baseSolrUrl Should be URL to solr service ie http://localhost:8983/solr
	 * @param ndexRoot NDEx data root, used to locate the CX2 aspect files that node indexes
	 *                 are built from ie /opt/ndex
	 * @param requestTimeoutSeconds Upper bound on any one Solr request, so an unresponsive Solr
	 *                 cannot hold the calling thread
	 */
	public SolrObjectFactoryImpl(final String baseSolrUrl, final String ndexRoot, final long requestTimeoutSeconds){
		_ndexRoot = ndexRoot;
		_solrClient = new Http2SolrClient.Builder(baseSolrUrl)
				.withConnectionTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
				.withRequestTimeout(requestTimeoutSeconds, TimeUnit.SECONDS)
				.build();
	}

	/**
	 * Gets the shared Solr client, bound to the Solr base URL
	 * @return the same Http2SolrClient on every call
	 */
	@Override
	public SolrClient getSolrClient() {
		return _solrClient;
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
		return CoreAdminRequest.getStatus(coreName, _solrClient);
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
		return CoreAdminRequest.unloadCore(coreName, deleteIndex, deleteInstanceDir, _solrClient);
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

	@Override
	public UserIndexManager getUserIndexManager() {
		return new UserIndexManager(new SolrClientWrapperImpl(this));
	}

	/**
	 * Closes the shared client, stopping its threads. Only for process shutdown: every manager
	 * handed out by this factory stops working afterwards.
	 */
	@Override
	public void close() throws IOException {
		_solrClient.close();
	}
}
