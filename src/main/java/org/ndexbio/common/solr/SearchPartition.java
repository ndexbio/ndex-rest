package org.ndexbio.common.solr;

import java.util.Optional;

import org.ndexbio.model.object.network.VisibilityType;

/**
 * The slice of the index a search is narrowed to when the caller asks for one.
 *
 * <p>Note what {@code visibility=PUBLIC} selects: {@link #PUBLIC_AND_UNLISTED}. An owner's unlisted
 * files have always come back from that request, so narrowing on the literal field value would drop
 * them.</p>
 *
 * <p>This narrowing is separate from the permission filter. It says which slice the caller asked
 * for; the permission filter says what they are allowed to see. Both must hold.</p>
 */
public enum SearchPartition {

    PUBLIC_AND_UNLISTED(VisibilityType.PUBLIC, VisibilityType.UNLISTED),

    PRIVATE_ONLY(VisibilityType.PRIVATE);

    private final VisibilityType[] visibilities;

    SearchPartition(VisibilityType... visibilities) {
        this.visibilities = visibilities;
    }

    /**
     * The partition a requested visibility selects, or empty when none was requested — in which case
     * the search spans everything the caller may see.
     */
    public static Optional<SearchPartition> requested(VisibilityType requestedVisibility) {
        if (requestedVisibility == null) {
            return Optional.empty();
        }
        return Optional.of(requestedVisibility == VisibilityType.PRIVATE
                ? PRIVATE_ONLY
                : PUBLIC_AND_UNLISTED);
    }

    public SolrClause clause() {
        return visibilities.length == 1
                ? SolrClause.term(NFSIndexManager.VISIBILITY, visibilities[0])
                : SolrClause.anyValueOf(NFSIndexManager.VISIBILITY, (Object[]) visibilities);
    }
}
