package org.ndexbio.rest.models;

import java.util.List;

/**
 * @author Andrey Lomakin <a href="mailto:lomakin.andrey@gmail.com">Andrey Lomakin</a>
 * @since 11/14/13
 */
public class NetworkQueryParameters {
    private List<String> startingTermIds;
    private List<String> startingTermStrings;
    private String representationCriterion;
    private String searchType;
    private List<String> includedPredicateIds;
    private List<String> excludedPredicateIds;
    private int searchDepth;
	public List<String> getStartingTermIds() {
		return startingTermIds;
	}
	public void setStartingTermIds(List<String> startingTermIds) {
		this.startingTermIds = startingTermIds;
	}
	
	public void addStartingTermId(String startingTermId){
		this.startingTermIds.add(startingTermId);
	}
	public List<String> getStartingTermStrings() {
		return startingTermStrings;
	}
	public void setStartingTermStrings(List<String> startingTermStrings) {
		this.startingTermStrings = startingTermStrings;
	}
	public String getRepresentationCriterion() {
		return representationCriterion;
	}
	public void setRepresentationCriterion(String representationCriterion) {
		this.representationCriterion = representationCriterion;
	}
	public String getSearchType() {
		return searchType;
	}
	public void setSearchType(String searchType) {
		this.searchType = searchType;
	}
	public List<String> getIncludedPredicateIds() {
		return includedPredicateIds;
	}
	public void setIncludedPredicateIds(List<String> includedPredicateIds) {
		this.includedPredicateIds = includedPredicateIds;
	}
	public List<String> getExcludedPredicateIds() {
		return excludedPredicateIds;
	}
	public void setExcludedPredicateIds(List<String> excludedPredicateIds) {
		this.excludedPredicateIds = excludedPredicateIds;
	}
	public int getSearchDepth() {
		return searchDepth;
	}
	public void setSearchDepth(int searchDepth) {
		this.searchDepth = searchDepth;
	}
    
 
}
