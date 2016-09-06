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
package org.ndexbio.common.models.object;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

//TODO: This class is inaccurate; it should look like the client-side model
@Deprecated
@JsonIgnoreProperties(ignoreUnknown = true)
public class NetworkQueryParameters
{
    private List<String> _startingTermIds;
    private List<String> _startingTermStrings;
    private String _representationCriterion;
    private String _searchType;
    private List<String> _includedPredicateIds;
    private List<String> _excludedPredicateIds;
    private int _searchDepth;

    
    
    public NetworkQueryParameters()
    {
        super();

        this.initCollections();
    }

    
    
    public List<String> getExcludedPredicateIds()
    {
        return _excludedPredicateIds;
    }

    public void setExcludedPredicateIds(List<String> excludedPredicateIds)
    {
        _excludedPredicateIds = excludedPredicateIds;
    }

    public List<String> getIncludedPredicateIds()
    {
        return _includedPredicateIds;
    }

    public void setIncludedPredicateIds(List<String> includedPredicateIds)
    {
        _includedPredicateIds = includedPredicateIds;
    }

    public String getRepresentationCriterion()
    {
        return _representationCriterion;
    }

    public void setRepresentationCriterion(String representationCriterion)
    {
        _representationCriterion = representationCriterion;
    }

    public int getSearchDepth()
    {
        return _searchDepth;
    }

    public void setSearchDepth(int searchDepth)
    {
        _searchDepth = searchDepth;
    }

    public String getSearchType()
    {
        return _searchType;
    }

    public void setSearchType(String searchType)
    {
        _searchType = searchType;
    }

    public void addStartingTermId(String startingTermId)
    {
        _startingTermIds.add(startingTermId);
    }

    public List<String> getStartingTermIds()
    {
        return _startingTermIds;
    }

    public void setStartingTermIds(List<String> startingTermIds)
    {
        _startingTermIds = startingTermIds;
    }

    public List<String> getStartingTermStrings()
    {
        return _startingTermStrings;
    }

    public void setStartingTermStrings(List<String> startingTermStrings)
    {
        _startingTermStrings = startingTermStrings;
    }

    
    
    /**************************************************************************
    * Initializes the collections. 
    **************************************************************************/
    private void initCollections()
    {
        _startingTermIds = new ArrayList<>();
        _startingTermStrings = new ArrayList<>();
        _includedPredicateIds = new ArrayList<>();
        _excludedPredicateIds = new ArrayList<>();
    }
}
