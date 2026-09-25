package org.ndexbio.common.solr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * One Solr filter-query expression, composed rather than concatenated.
 *
 * <p>Every operand is parenthesised as it is combined, so nesting cannot change meaning by
 * precedence, and untrusted values reach the query only through {@link #phrase}, which escapes
 * them.</p>
 */
public final class SolrClause {

    /** Matches no document. What an empty {@link #anyOf} means, stated rather than left empty. */
    private static final String NOTHING = "(*:* AND NOT *:*)";

    private final String expression;

    private SolrClause(String expression) {
        this.expression = expression;
    }

    /**
     * {@code field:value} for a value this code controls, such as an enum name.
     *
     * <p>The value is written literally. Use {@link #phrase} for anything a caller supplied.</p>
     */
    public static SolrClause term(String field, Object value) {
        return new SolrClause(field + ":" + value);
    }

    /** {@code field:"value"} for a caller-supplied value, escaped so it cannot end the phrase. */
    public static SolrClause phrase(String field, String value) {
        return new SolrClause(field + ":\"" + escape(value) + "\"");
    }

    /** {@code field:(a OR b)} — one field, several controlled values. */
    public static SolrClause anyValueOf(String field, Object... values) {
        StringJoiner alternatives = new StringJoiner(" OR ");
        for (Object value : values) {
            alternatives.add(String.valueOf(value));
        }
        return new SolrClause(field + ":(" + alternatives + ")");
    }

    /**
     * {@code {!terms f=field v='a,b'}} — membership in a set of ids.
     *
     * <p>Chosen over a chain of ORs because it is exempt from {@code maxBooleanClauses}, so a caller
     * holding a very large subtree cannot make the query fail. Ids render as text that cannot contain
     * a separator or a quote, so no escaping applies.</p>
     */
    public static SolrClause terms(String field, Collection<UUID> ids) {
        StringJoiner values = new StringJoiner(",");
        for (UUID id : ids) {
            values.add(id.toString());
        }
        return new SolrClause("{!terms f=" + field + " v='" + values + "'}");
    }

    /** Any one of these admits a document. Empty means nothing is admitted. */
    public static SolrClause anyOf(Collection<SolrClause> clauses) {
        return join(clauses, " OR ", NOTHING);
    }

    /** Every one of these must hold. Empty means no restriction. */
    public static SolrClause allOf(Collection<SolrClause> clauses) {
        return join(clauses, " AND ", "*:*");
    }

    public static SolrClause allOf(SolrClause... clauses) {
        return allOf(List.of(clauses));
    }

    private static SolrClause join(Collection<SolrClause> clauses, String operator, String whenEmpty) {
        if (clauses.isEmpty()) {
            return new SolrClause(whenEmpty);
        }
        if (clauses.size() == 1) {
            return clauses.iterator().next();
        }
        StringJoiner joined = new StringJoiner(operator);
        for (SolrClause clause : clauses) {
            joined.add("(" + clause.expression + ")");
        }
        return new SolrClause(joined.toString());
    }

    /** This clause as a filter query, parenthesised so a caller may append its own conditions. */
    public String asFilterQuery() {
        return "(" + expression + ")";
    }

    /** This clause joined onto an existing filter query with AND. */
    public String appendedTo(String filterQuery) {
        return filterQuery + " AND " + asFilterQuery();
    }

    @Override
    public String toString() {
        return expression;
    }

    /**
     * Escapes a value for use inside a double-quoted phrase.
     *
     * <p>Only the backslash and the double-quote can end or alter a quoted phrase, so those are the
     * only characters escaped. This is what stops a value such as
     * {@code zzz") OR (*:*) OR (owner:"zzz} from breaking out of its phrase and injecting boolean
     * clauses into an access-control filter. The backslash goes first so a value's own backslashes
     * are not confused with the escaping added for the quotes.</p>
     */
    static String escape(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Collects clauses, skipping the ones that do not apply. */
    static final class Builder {
        private final List<SolrClause> clauses = new ArrayList<>();

        Builder add(SolrClause clause) {
            if (clause != null) {
                clauses.add(clause);
            }
            return this;
        }

        List<SolrClause> clauses() {
            return clauses;
        }

        boolean isEmpty() {
            return clauses.isEmpty();
        }
    }
}
