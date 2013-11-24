package org.ndexbio.rest.gremlin;

import com.orientechnologies.orient.core.db.record.OIdentifiable;

/**
 * @author Andrey Lomakin <a href="mailto:lomakin.andrey@gmail.com">Andrey Lomakin</a>
 * @since 11/14/13
 */
public class SearchSpec {
    private OIdentifiable[] startingTerms;
    private String[] startingTermStrings;
    private RepresentationCriteria representationCriterion;
    private SearchType searchType;
    private OIdentifiable[] includedPredicates;
    private OIdentifiable[] excludedPredicates;

    public int getSearchDepth() {
        return searchDepth;
    }

    public void setSearchDepth(int searchDepth) {
        this.searchDepth = searchDepth;
    }

    private int searchDepth;

    public OIdentifiable[] getStartingTerms() {
        return startingTerms;
    }

    public void setStartingTerms(OIdentifiable[] startingTerms) {
        this.startingTerms = startingTerms;
    }

    public String[] getStartingTermStrings() {
        return startingTermStrings;
    }

    public void setStartingTermStrings(String[] startingTermStrings) {
        this.startingTermStrings = startingTermStrings;
    }

    public RepresentationCriteria getRepresentationCriterion() {
        return representationCriterion;
    }

    public void setRepresentationCriterion(RepresentationCriteria representationCriterion) {
        this.representationCriterion = representationCriterion;
    }

    public SearchType getSearchType() {
        return searchType;
    }

    public void setSearchType(SearchType searchType) {
        this.searchType = searchType;
    }

    public OIdentifiable[] getIncludedPredicates() {
        return includedPredicates;
    }

    public void setIncludedPredicates(OIdentifiable[] includedPredicates) {
        this.includedPredicates = includedPredicates;
    }

    public OIdentifiable[] getExcludedPredicates() {
        return excludedPredicates;
    }

    public void setExcludedPredicates(OIdentifiable[] excludedPredicates) {
        this.excludedPredicates = excludedPredicates;
    }
}
