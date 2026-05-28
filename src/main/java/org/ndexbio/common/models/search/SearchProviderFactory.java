package org.ndexbio.common.models.search;

import org.ndexbio.model.exceptions.NdexException;

/**
 * Interface for Factory object that generates SearchProvider object
 * 
 * @author churas
 */
public interface SearchProviderFactory {
	
	/**
	 * Gets search provider
	 * 
	 * @return Search Provider
	 * @throws NdexException if there was an error
	 */
	SearchProvider getSearchProvider() throws NdexException;
}
