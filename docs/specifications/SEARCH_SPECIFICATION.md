
# NDEx Search Specification

## Overview

This specification defines the behavior of user, folder, shortcut, and network search of NDEx Service

## Core Principles



## REST endpoints

### POST /v2/search/network/{networkId}/advancedquery

### POST /v2/search/group

### POST /v2/search/user

### POST /v2/search/network/{networkId}/interconnectquery


### POST /v2/search/network/{networkId}/query


### POST /v2/search/network/{networkId}/nodes


### POST /v2/search/network


### POST /v2/search/network/genes

### POST /v3/search/networks/{networkId}/edges


### POST /v3/search/networks/{networkId}/nodes


### POST /v3/search/networks/{networkId}/interconnectquery

### POST /v3/search/networks/{networkId}/query

### POST /v3/search/files

	Returns a FileSearchResult object which contains an array of FileItemSummary objects and total hit count of the search. Currently only supports searching networks, but the response format is designed to support folders and shortcuts in the future.

	NOTE: Omitting `visibility` searches everything the caller may see — public files plus, when
          authenticated, their own private and unlisted files and everything shared with them — returned
          as one relevance-ranked result set. Supplying it narrows to one partition.

	NOTE: `visibility=PUBLIC` returns public files plus the caller's own UNLISTED files. It narrows on the
          partition, not on the literal field value, so an owner keeps finding their own unlisted files
          exactly as they did when this parameter selected a Solr core. An UNLISTED file belonging to
          someone else stays unlisted whatever folder grants exist: a grant changes who can open an item,
          never whether it is listed.

	Query Parameters:

    visibility: Optional. Narrows results to one partition. (PUBLIC, PRIVATE) (default: unset, meaning everything the caller may see). PRIVATE requires authentication; UNLISTED is rejected with 400.
    type: Optional. Supports filtering results by type (NETWORK, SHORTCUT, FOLDER) (default: unset, meaning all)
	start: Optional. Starting index for pagination (default: 0)
	size: Optional. Number of results per page (default: 100)
	Response:

	200 OK: FileSearchResult with matching files
	400 Bad Request: Invalid query parameters
