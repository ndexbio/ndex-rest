/**
 * Copyright (c) 2013, 2016, The Regents of the University of California, The Cytoscape Consortium
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */
package org.ndexbio.common.solr;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.ndexbio.cx2.aspect.element.core.CxNode;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.tools.SearchUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the per-network Solr core that backs node queries on a single network.
 *
 * <p>Every index-creating method returns the number of documents that actually landed in
 * Solr, verified by querying the core after the commit. Callers pass the node count the
 * database reports so that a rebuild which produces no documents for a network that has
 * nodes fails loudly instead of leaving an empty core behind reporting success.
 */
public class SingleNetworkSolrIdxManager implements AutoCloseable {

	private static final Logger logger = LoggerFactory.getLogger(SingleNetworkSolrIdxManager.class);

	private final String collectionName;
	private final SolrClientWrapper client;
	private final Cx2NodeIndexService nodeIndexService;

	static private final int batchSize = NodeIndexDocumentBuilder.DEFAULT_BATCH_SIZE;

	//NDEx will auto create index for networks with node count larger than this value
	// other wise it will delay the creation until the first time this network is queried.
	static public final int AUTOCREATE_THRESHHOLD = 100;

	private static final String NODE_CONFIG_SET = "ndex-nodes";
	private static final String NODE_CONFIG_SET_TEMPLATE = "ndex-nodes-template";

	/**
	 * Guards core creation so two concurrent queries on the same unindexed network don't both
	 * try to create its core. Striped rather than one lock per network: bounded in size, and
	 * unrelated networks no longer serialize behind a single global lock.
	 */
	private static final int CORE_CREATION_STRIPES = 64;
	private static final Object[] CORE_CREATION_LOCKS = newLockStripes();

	private int counter;
	private Collection<SolrInputDocument> docs;

	public static final String ID = "id";

	private static final String NAME = "nodeName";
	public static final String TEXT = "text";

	private static Object[] newLockStripes() {
		Object[] stripes = new Object[CORE_CREATION_STRIPES];
		for (int i = 0; i < stripes.length; i++) {
			stripes[i] = new Object();
		}
		return stripes;
	}

	public SingleNetworkSolrIdxManager(String networkId, SolrClientWrapper client,
			Cx2NodeIndexService nodeIndexService) {
		this.collectionName = networkId;
		this.client = client;
		this.nodeIndexService = nodeIndexService;
	}

	public SolrDocumentList getNodeIdsByQuery(String query, int limit)
			throws SolrServerException, IOException, NdexException {

		SolrQuery solrQuery = new SolrQuery();

		solrQuery.setQuery(SearchUtilities.preprocessSearchTerm(query)).setFields(ID);
		solrQuery.set("defType", "edismax");
		solrQuery.set("qf", NAME + " " + CxNode.REPRESENTS + " " + NodeIndexFields.ALIAS + " " + TEXT);
		solrQuery.setStart(0);
		if (limit > 0)
			solrQuery.setRows(limit);
		else
			solrQuery.setRows(30000000);

		return client.query(collectionName, solrQuery).getResults();
	}

	/**
	 * Makes sure this network's query core exists, creating and populating it when the network
	 * is small enough to index on demand.
	 *
	 * <p>Any failure while building the index propagates unchanged: the reason a rebuild could
	 * not read a network's aspect files is the whole diagnostic, so it must not be replaced by
	 * a generic message here.
	 *
	 * @param expectedNodeCount node count the database reports for this network
	 * @throws NdexException when the core is absent and cannot be created now
	 */
	public void ensureReady(int expectedNodeCount)
			throws SolrServerException, IOException, NdexException {

		if (client.coreExists(collectionName)) {
			return;
		}

		if (expectedNodeCount >= AUTOCREATE_THRESHHOLD) {
			throw new NdexException(
					"NDEx server hasn't finished creating index on this network yet. Please try again later");
		}

		synchronized (coreCreationLock()) {
			// another thread may have created it while we waited
			if (client.coreExists(collectionName)) {
				return;
			}
			createDefaultIndex(expectedNodeCount);
		}
	}

	private Object coreCreationLock() {
		int stripe = Math.abs(collectionName.hashCode() % CORE_CREATION_STRIPES);
		return CORE_CREATION_LOCKS[stripe];
	}

	/**
	 * Creates this network's query core and populates it.
	 *
	 * @param extraIndexFields extra node attributes to index; when null the shared
	 *                         {@value #NODE_CONFIG_SET} configSet is used
	 * @param expectedNodeCount node count the database reports for this network
	 * @return number of documents committed to Solr
	 */
	public int createIndex(Set<String> extraIndexFields, int expectedNodeCount)
			throws SolrServerException, IOException, NdexException {

		if (extraIndexFields == null) {
			return createDefaultIndex(expectedNodeCount);
		}

		//create a configSet from template first.
		client.createConfigSet(collectionName, NODE_CONFIG_SET_TEMPLATE);
		client.createCore(collectionName, collectionName);

		return populateIndex(expectedNodeCount);
	}

	/**
	 * Creates this network's query core from its CX2 aspect files and populates it.
	 *
	 * @param extraIndexFields must be null; extra attribute indexing is not implemented
	 * @param expectedNodeCount node count the database reports for this network
	 * @return number of documents committed to Solr
	 */
	public int createIndexFromCx2(Set<String> extraIndexFields, int expectedNodeCount)
			throws NdexException, SolrServerException, IOException {

		if (extraIndexFields != null)
			throw new NdexException("Additional node attribute indexing is not implmented yet.");

		return createDefaultIndex(expectedNodeCount);
	}

	private int createDefaultIndex(int expectedNodeCount)
			throws SolrServerException, IOException, NdexException {

		client.createCore(collectionName, NODE_CONFIG_SET);

		return populateIndex(expectedNodeCount);
	}

	/**
	 * Loads the node index entries, sends them to Solr in batches, commits, then reports how
	 * many documents Solr actually holds.
	 */
	private int populateIndex(int expectedNodeCount)
			throws SolrServerException, IOException, NdexException {

		counter = 0;
		docs = new ArrayList<>(batchSize);

		Map<Long, NodeIndexEntry> tab = nodeIndexService.loadNodeIndexEntries(collectionName);
		for (NodeIndexEntry e : tab.values()) {
			addNodeIndex(e.getId(), e.getName(), e.getRepresents(), e.getAliases(), e.getText());
		}

		// Commit unconditionally. The trailing batch may be empty - when the entry count is an
		// exact multiple of batchSize every document has already been flushed - and skipping
		// the commit in that case would leave the whole index uncommitted and unsearchable.
		client.commit(collectionName, docs, true);
		docs.clear();

		int committed = getCommittedDocCount();
		verifyDocCount(committed, expectedNodeCount);
		return committed;
	}

	/**
	 * Asks Solr how many documents are in the core, rather than trusting the count of documents
	 * we tried to send. This catches a failed commit as well as a failed build.
	 */
	private int getCommittedDocCount() throws SolrServerException, IOException, NdexException {
		SolrQuery countQuery = new SolrQuery("*:*");
		countQuery.setRows(0);
		return (int) client.query(collectionName, countQuery).getResults().getNumFound();
	}

	private void verifyDocCount(int committed, int expectedNodeCount) throws NdexException {

		if (committed == 0 && expectedNodeCount > 0) {
			throw new NdexException("Created Solr query index for network " + collectionName
					+ " but it holds no documents, while the database reports " + expectedNodeCount
					+ " nodes. Queries on this network would return no results.");
		}

		if (committed < expectedNodeCount) {
			// Normal: nodes carrying no name, represents or alias attribute are not indexed.
			logger.warn("Solr query index for network {} holds {} documents for {} nodes; "
					+ "nodes without an indexable attribute are not indexed.",
					collectionName, committed, expectedNodeCount);
		}
	}

	public void dropIndex() throws IOException, SolrServerException, NdexException {
		client.dropCore(collectionName);
	}

	private void addNodeIndex(Long id, String name, Collection<String> represents,
			Collection<String> alias, Collection<String> txtArray)
			throws SolrServerException, IOException {

		SolrInputDocument doc = NodeIndexDocumentBuilder.buildNodeDocument(id, name, represents,
				alias, txtArray);
		docs.add(doc);
		counter++;
		if (counter % batchSize == 0) {
			client.add(collectionName, docs);
			docs.clear();
		}
	}

	@Override
	public void close() {
		client.close();
	}

	protected String getNetworkId() {
		return collectionName;
	}
}
