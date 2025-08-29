package org.ndexbio.common.models.search;

import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.FileSearchResult;
import org.ndexbio.model.object.FileVisibilityType;
import org.ndexbio.model.object.SimpleFileQuery;


/**
 *
 * @author churas
 */
public interface SearchProvider extends AutoCloseable {
	
	FileSearchResult searchFiles(SimpleFileQuery query,
			FileVisibilityType visibilityType,
			int skipBlocks, int blockSize) throws NdexException;
}
