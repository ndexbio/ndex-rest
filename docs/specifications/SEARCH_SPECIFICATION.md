
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

	NOTE: When search visibility is set to PUBLIC then entities whose visibility is type UNLISTED the entity can be found if the 
          query has an exact match for the UUID. If visibility is PRIVATE and user has access then the UNLISTED
          network can be searched for in the normal manner.

	Query Parameters:

    visibility: Optional. Searches on only public or private data. (PUBLIC, PRIVATE) (default: PUBLIC)
    type: Optional. Supports filtering results by type (NETWORK, SHORTCUT, FOLDER) (default: unset, meaning all)
	start: Optional. Starting index for pagination (default: 0)
	size: Optional. Number of results per page (default: 100)
	Response:

	200 OK: FileSearchResult with matching files
	400 Bad Request: Invalid query parameters
