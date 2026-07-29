package org.ndexbio.common.solr;

import java.io.IOException;
import java.util.Map;

import org.ndexbio.model.exceptions.NdexException;

/**
 * Reads a network's CX2 aspect files and produces the entries that make up its
 * per-network Solr node index.
 */
public interface Cx2NodeIndexService {

	/**
	 * Builds one {@link NodeIndexEntry} per indexable node in {@code networkId}, keyed by node id.
	 *
	 * <p>Returns an empty map only when the network genuinely declares no indexable node
	 * attributes. Any inability to <em>read</em> the network's aspect files is an error and
	 * throws — callers must be able to tell "this network has nothing to index" apart from
	 * "this network's data could not be read".
	 *
	 * @param networkId network UUID, which is also its aspect directory name
	 * @throws NdexException if the attribute declarations cannot be read
	 * @throws IOException   if an aspect file exists but cannot be parsed
	 */
	Map<Long, NodeIndexEntry> loadNodeIndexEntries(String networkId) throws NdexException, IOException;
}
