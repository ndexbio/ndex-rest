package org.ndexbio.common.solr;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.NamedList;
import org.ndexbio.common.models.dao.SearchScope;
import org.ndexbio.common.models.dao.DAOFactory;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.model.exceptions.BadRequestException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.User;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.model.tools.SearchUtilities;
import org.ndexbio.rest.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class NFSIndexManager<T> implements AutoCloseable {
    protected final Logger logger = LoggerFactory.getLogger(this.getClass().getSimpleName());


    /**
     * The single core holding every folder, shortcut and network document, whatever its visibility.
     *
     * <p>This replaced a {@code public-nfs}/{@code private-nfs} pair. The split made a correct ranked
     * result set impossible: each core computed relevance from its own corpus statistics, so scores from
     * one were never comparable with scores from the other, and a caller wanting both had to query twice
     * and sort the union of two unrelated number ranges. Visibility is now a field on the document and a
     * clause in the filter — see {@link #buildPermissionFilter} and {@link #buildPartitionFilter}.</p>
     */
    public static final String nfsCoreName = "ndex-nfs";

    protected final SolrClientWrapper solrClientWrapper;
    protected final String solrUrl ;

    protected SolrInputDocument doc ;

    public static final String UUID = "uuid";
    public static final String NAME = "name";
    public static final String ENTITY_TYPE = "entityType";
    public static final String DESC = "description";
    public static final String VERSION = "version";
    public static final String NODE_NAME = "nodeName";
    public static final String USER_ADMIN = "owner";
    protected static final String PARENT_UUID = "parentUuid";
    protected static final String TARGET_UUID = "targetUuid"; // for shortcuts
    public static final String REPRESENTS = "represents";
    public static final String ALIASES = "alias";
    public static final String MODIFICATION_TIME = "modificationTime";
    public static final String VISIBILITY = "visibility";

    public static final String TARGET_TYPE = "targetType";

    public static final String EDGE_COUNT = "edgeCount";

    public static final String NODE_COUNT = "nodeCount";
    public static final String CREATION_TIME = "creationTime";
    public static final String NDEX_SCORE = "ndexScore";

    protected static final int DEFAULT_MAX_SEARCH_RESULTS = 100000;

    private final int max_search_results;

    /**
     * Create index document for subclass input - must be implemented by subclasses
     */
    protected abstract SolrInputDocument setupIndexDocument(T inputData, VisibilityType visibilityType);

    /**
     * Get query fields with weights - must be implemented by subclasses
     */
    protected abstract String getQueryFields();

    /**
     * Public wrapper function for setupIndexDocument that doesn't expose inner SolrInputDocument
     *
     * <p>No access list is written. Permissions used to be copied onto the document at index time, which
     * meant every share, move or revoke silently went stale until that item happened to be re-indexed.
     * They are now resolved from the database on each query — see {@code SearchScope} — so the index
     * carries structure only.</p>
     */
    public void prepareIndexDocument(T inputData, VisibilityType visibilityType){
        setupIndexDocument(inputData, visibilityType);
        doc.addField(VISIBILITY, visibilityType.name());
    }


    public NFSIndexManager(SolrClientWrapper solrClientWrapper){
        this(solrClientWrapper, DEFAULT_MAX_SEARCH_RESULTS);
    }
    public NFSIndexManager(SolrClientWrapper solrClientWrapper, int maxDefaultSearchResults){
        solrUrl = Configuration.getInstance().getSolrURL();
        doc = new SolrInputDocument();
        this.solrClientWrapper = solrClientWrapper;
        this.max_search_results = maxDefaultSearchResults;

    }

    /**
     * Creates core on Solr if needed. If core exists, nothing is done
     *
     * @throws SolrServerException
     * @throws IOException
     * @throws NdexException
     */
    public void createCoreIfNeeded() throws SolrServerException, IOException, NdexException {
        solrClientWrapper.createCoreIfNeeded(nfsCoreName);
    }

    /**
     *
     * @param inputData - Object to be mapped to document
     */
    public void createIndex(T inputData, VisibilityType visibilityType){
        prepareIndexDocument(inputData, visibilityType);
        try {
            commit();
        } catch(SolrServerException sse){
            logger.error("Unable to commit document: " + sse.getMessage(), sse);
            //throw new RuntimeException("Failed to commit to Solr", sse); // ADD THIS
        } catch(IOException io){
            logger.error("Unable to commit document: " + io.getMessage(), io);
            //throw new RuntimeException("Failed to commit to Solr", io); // ADD THIS
        }
    }

    public void commit() throws SolrServerException, IOException {
        commit(nfsCoreName);
    }
    public void commit(String coreName) throws SolrServerException, IOException {
        if ( !doc.isEmpty()) {
            /*
            logger.info("Committing doc to core [{}]:", coreName);
            for (String fieldName : doc.getFieldNames()) {
                logger.info("  {} = {}", fieldName, doc.getFieldValues(fieldName));
            }

             */
            Collection<SolrInputDocument> docs = new ArrayList<>(1);
            docs.add(doc);
            solrClientWrapper.commit(coreName, docs);
        } else {
            //logger.info("Empty doc, committing to core [{}] with no additions", coreName);
            solrClientWrapper.commit(coreName, null);
        }
        doc = new SolrInputDocument();
        postCommit();
    }
    public void delete(String uuid) throws SolrServerException, IOException {

        solrClientWrapper.delete(nfsCoreName, uuid, false);
    }


    /**
     * Stub function called after commit() that can be overridden by subclasses to define
     * any extra actions needed after a commit
     *
     */
    protected void postCommit(){

    }
    /**
     * Base search method for all entity types (Networks, Folders, Shortcuts)
     *
     * @param searchTerms The search query (use "*:*" for all)
     * @param userAccount The authenticated user (null for anonymous)
     * @param limit Max results to return
     * @param offset Starting position for pagination
     * @param ownedBy Filter by owner username (null for no filter)
     * @param permission Filter by permission level (null for all accessible items)
     * @param visibilityType Optional narrowing to one visibility partition; null searches everything the
     *                       caller may see. See {@link #buildPartitionFilter}.
     * @param scope Folder-propagation reach resolved for this request; {@link SearchScope#EMPTY} when the
     *              caller is anonymous or holds no grants.
     * @return SolrDocumentList containing matching documents
     */
    public SolrDocumentList search(
            String searchTerms,
            String userAccount,
            VisibilityType visibilityType,
            int limit,
            int offset,
            String ownedBy,
            Permissions permission,
            SearchScope scope) throws NdexException {

        SolrQuery solrQuery = new SolrQuery();

        // Build the permission filter
        String permissionFilter = buildPermissionFilter(userAccount, permission, scope);

        // Build the owner filter
        String ownerFilter = "";
        if (ownedBy != null) {
            ownerFilter = " AND (" + USER_ADMIN + ":\"" + escapeForFilter(ownedBy) + "\")";
        }

        // Combine filters
        String resultFilter = "(" + permissionFilter + ")" + ownerFilter
                + buildPartitionFilter(visibilityType);

        // Set up the query
        configureQuery(solrQuery, searchTerms, resultFilter, limit, offset);
        //logger.info("QUERY {} FILTER {}", solrQuery.toQueryString(), solrQuery.getFilterQueries());

        // Execute search
        try {
            QueryResponse rsp = solrClientWrapper.query(nfsCoreName, solrQuery);
            return rsp.getResults();
        } catch (BaseHttpSolrClient.RemoteSolrException e) {
            throw convertException(e, nfsCoreName);
        }
        catch (Exception e){
            throw new NdexException("Error accessing Solr: " + e.getMessage());
        }
    }

    /**
     * Search with entity type filter.
     *
     * @param includeShortcuts when true, also returns SHORTCUT docs whose targetType
     * matches entityType (for callers that resolve results per entity type, e.g.
     * v3 NFSSearchProvider). When false, only docs of the exact entityType are
     * returned (for callers that assume a single type, e.g. v2 findNetworks).
     */
    public SolrDocumentList searchByType(
            String searchTerms,
            String userAccount,
            VisibilityType visibilityType,
            int limit,
            int offset,
            String ownedBy,
            Permissions permission,
            String entityType,
            boolean includeShortcuts,
            SearchScope scope) throws NdexException {

        String typeFilter;
        if ("SHORTCUT".equalsIgnoreCase(entityType)) {
            typeFilter = " AND (" + ENTITY_TYPE + ":\"SHORTCUT\")";
        } else if (includeShortcuts) {
            typeFilter = " AND ((" + ENTITY_TYPE + ":\"" + entityType + "\") OR " +
                    "(" + ENTITY_TYPE + ":\"SHORTCUT\" AND " + TARGET_TYPE + ":\"" + entityType + "\"))";
        } else {
            typeFilter = " AND (" + ENTITY_TYPE + ":\"" + entityType + "\")";
        }

        SolrQuery solrQuery = new SolrQuery();
        String permissionFilter = buildPermissionFilter(userAccount, permission, scope);
        String ownerFilter = ownedBy != null ? " AND (" + USER_ADMIN + ":\"" + escapeForFilter(ownedBy) + "\")" : "";
        String resultFilter = "(" + permissionFilter + ")" + ownerFilter + typeFilter
                + buildPartitionFilter(visibilityType);

        configureQuery(solrQuery, searchTerms, resultFilter, limit, offset);
        //logger.info("QUERY {} FILTER {}", solrQuery.toQueryString(), solrQuery.getFilterQueries());

        try {
            QueryResponse rsp = solrClientWrapper.query(nfsCoreName, solrQuery);
            return rsp.getResults();
        } catch (BaseHttpSolrClient.RemoteSolrException e) {
            throw convertException(e, nfsCoreName);
        } catch (Exception e) {
            throw new NdexException("Error accessing Solr: " + e.getMessage());
        }
    }

    /**
     * The access-control filter: every reason this caller may see a document, OR-ed together.
     *
     * <p>Effective permission is the <em>most permissive</em> of everything that applies, so this filter
     * only ever adds reasons — nothing here subtracts. It reads no permission state from the index: the
     * reachable ids arrive in {@code scope}, resolved from the database for this request, so a share or a
     * revoke takes effect on the very next search.</p>
     *
     * <p><b>Two placements are load-bearing and look like tidying.</b></p>
     *
     * <p>{@code owner} sits <em>outside</em> the PRIVATE arm, unpinned to any visibility. It has to cover
     * owned PUBLIC, owned UNLISTED <em>and</em> owned PRIVATE, because an owner has always been able to
     * find their own UNLISTED files. Pinning it inside the PRIVATE arm silently drops them.</p>
     *
     * <p>The {@code {!terms}} group stays <em>pinned to</em> {@code visibility:PRIVATE}. That pin is the
     * whole UNLISTED rule: an UNLISTED file is "only searchable by the owner", which is a listing rule
     * rather than an access rule — a grant changes who can <em>open</em> an item, never whether it is
     * <em>listed</em>. Unpinned, a folder grant surfaces someone else's UNLISTED file to every grantee on
     * an ancestor folder.</p>
     *
     * <p>The anonymous and public arm is the <em>positive</em> {@code visibility:PUBLIC}. It was once
     * {@code (*:* NOT visibility:UNLISTED)}, which was correct only because it ran against a core holding
     * nothing but PUBLIC and UNLISTED documents. Against one core that negative form matches every
     * PRIVATE document too.</p>
     *
     * <p>Every arm is parenthesised because callers append {@code " AND (...)"} to whatever this
     * returns.</p>
     */
    protected String buildPermissionFilter(String userAccount, Permissions permission, SearchScope scope) {

        if (userAccount == null) {
            // Anonymous: public documents are the whole of their access.
            return "(" + VISIBILITY + ":" + VisibilityType.PUBLIC.name() + ")";
        }

        String userAccountStr = "\"" + escapeForFilter(userAccount) + "\"";
        String ownerClause = "(" + USER_ADMIN + ":" + userAccountStr + ")";

        // A folder grant never confers ownership, so ADMIN is ownership alone and ignores the scope.
        if (permission == Permissions.ADMIN) {
            return ownerClause;
        }
        if (permission != null && permission != Permissions.READ && permission != Permissions.WRITE) {
            // MEMBER / GROUPADMIN reach no more than an anonymous caller does.
            return "(" + VISIBILITY + ":" + VisibilityType.PUBLIC.name() + ")";
        }

        StringBuilder filter = new StringBuilder();
        if (permission != Permissions.WRITE) {
            // A WRITE search is asking what the caller may edit, which public visibility never grants.
            filter.append("(").append(VISIBILITY).append(":").append(VisibilityType.PUBLIC.name())
                  .append(") OR ");
        }
        filter.append(ownerClause);

        String termsGroup = buildTermsGroup(permission, scope);
        if (termsGroup != null) {
            filter.append(" OR (").append(VISIBILITY).append(":").append(VisibilityType.PRIVATE.name())
                  .append(" AND (").append(termsGroup).append("))");
        }

        return filter.toString();
    }

    /**
     * Narrows the result set to one visibility partition, or to nothing at all when {@code visibilityType}
     * is null.
     *
     * <p><b>{@code PUBLIC} narrows on {@code PUBLIC OR UNLISTED}, not on the literal field value.</b> The
     * parameter has never meant "documents whose visibility field equals PUBLIC" — it selected the
     * {@code public-nfs} core, and that core physically held both. Its filter then admitted every PUBLIC
     * document plus the caller's own, and the caller's own documents in that core included their UNLISTED
     * ones. Narrowing on the literal value instead quietly drops the caller's own unlisted files.</p>
     *
     * <p>This is deliberately separate from {@link #buildPermissionFilter}: that one is the security
     * boundary and answers who the caller is, this one answers which slice they asked for. Folding the
     * two together is what makes the UNLISTED rule easy to get wrong.</p>
     */
    protected String buildPartitionFilter(VisibilityType visibilityType) {
        if (visibilityType == null) {
            return "";
        }
        if (visibilityType == VisibilityType.PRIVATE) {
            return " AND (" + VISIBILITY + ":" + VisibilityType.PRIVATE.name() + ")";
        }
        return " AND (" + VISIBILITY + ":(" + VisibilityType.PUBLIC.name()
                + " OR " + VisibilityType.UNLISTED.name() + "))";
    }

    /**
     * The OR-ed {!terms} clauses for everything {@code scope} makes reachable, or null when it makes
     * nothing reachable.
     *
     * <p>Null rather than an empty string, because the caller must omit the whole PRIVATE arm in that
     * case: {@code visibility:PRIVATE AND ()} is a parse error, and an empty terms list would be a wasted
     * clause on every query by a user with no grants.</p>
     *
     * <p><b>The clause differs by document type</b>, and conflating them is the easy mistake: a network is
     * reached through its <em>parent's</em> id, a folder through its <em>own</em>. Each clause is pinned to
     * an {@code entityType} for that reason — folder and shortcut documents also carry {@code parentUuid},
     * so an unpinned parent clause would admit any shortcut sitting in a granted folder and quietly bypass
     * the target-reachability half of the shortcut conjunction.</p>
     */
    protected String buildTermsGroup(Permissions permission, SearchScope scope) {
        if (scope == null) {
            scope = SearchScope.EMPTY;
        }

        StringBuilder group = new StringBuilder();
        // A network inherits from the folder it sits in; the granted set already includes descendants.
        appendTermsClause(group, FileType.NETWORK, PARENT_UUID, scope.grantedFolderIds());
        // A folder is reached by its own id.
        appendTermsClause(group, FileType.FOLDER, UUID, scope.grantedFolderIds());
        // Networks with no granted folder above them: a direct grant, or a same-owner shortcut reference.
        appendTermsClause(group, FileType.NETWORK, UUID, scope.reachableNetworkIds());

        // Shortcuts are read-only aliases — permission cannot be set on one — so they take no part in a
        // WRITE search. The set is a read-level conjunction and would over-admit if reused here.
        if (permission == null || permission == Permissions.READ) {
            appendTermsClause(group, FileType.SHORTCUT, UUID, scope.readableShortcutIds());
        }

        return group.length() == 0 ? null : group.toString();
    }

    /**
     * ORs {@code (entityType:TYPE AND {!terms f=field v='id,id,…'})} onto {@code filter}, or appends
     * nothing when {@code ids} is empty.
     *
     * <p>{@code {!terms}} is used rather than a chain of ORs because it is not subject to
     * {@code maxBooleanClauses}, so a user holding a very large subtree cannot make the query fail. It is
     * embedded with an inline {@code v=} local param, which is how the standard parser accepts a nested
     * query inside a boolean expression. No {@code method} is specified: the default works against
     * {@code indexed="true"} fields, and the alternatives that would need {@code docValues} would force a
     * schema change on existing deployments.</p>
     *
     * <p>The empty case must skip the clause entirely — an empty terms list matches nothing, which as one
     * arm of an OR is merely useless, but it is a wasted clause on every anonymous-adjacent query.</p>
     *
     * <p>Values are {@link java.util.UUID} rendered to text, so they cannot contain a separator or a
     * quote; no escaping is required and none is performed.</p>
     */
    private void appendTermsClause(StringBuilder filter, FileType entityType, String field,
                                   Set<java.util.UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        StringJoiner values = new StringJoiner(",");
        for (java.util.UUID id : ids) {
            values.add(id.toString());
        }
        // The separator is conditional because this now builds a standalone group that starts empty —
        // an unconditional " OR " would leave a leading operator on the first clause.
        if (filter.length() > 0) {
            filter.append(" OR ");
        }
        filter.append("(").append(ENTITY_TYPE).append(":\"").append(entityType).append("\"")
              .append(" AND {!terms f=").append(field).append(" v='").append(values).append("'})");
    }

    /**
     * Configure the Solr query - can be overridden by subclasses for entity-specific needs
     */
    protected void configureQuery(SolrQuery solrQuery, String searchTerms,
                                  String resultFilter, int limit, int offset) {

        // Default sorting
        if (searchTerms.equalsIgnoreCase("*:*")) {
            solrQuery.setSort(MODIFICATION_TIME, SolrQuery.ORDER.desc);
        }

        // Set the query with default fields
        solrQuery.setQuery(preprocessSearchTerms(searchTerms)).setFields(UUID, USER_ADMIN, NAME, ENTITY_TYPE);

        // Set query type and fields to search
        solrQuery.set("defType", "edismax");
        solrQuery.set("qf", getQueryFields());

        // Optional multiplicative boost (e.g. to demote edgeless networks)
        String boostFunction = getBoostFunction();
        if (boostFunction != null) {
            solrQuery.set("boost", boostFunction);
        }

        // Pagination
        if (offset >= 0) {
            solrQuery.setStart(offset);
        }
        if (limit > 0) {
            solrQuery.setRows(limit);
        } else {
            solrQuery.setRows(max_search_results);
        }

        // Apply filters
        solrQuery.setFilterQueries(resultFilter);
    }

    /**
     * Optional edismax multiplicative boost function applied to the query score.
     * Returns null by default (no boost). Subclasses override to demote or promote
     * documents (e.g. GlobalNetworkIndexManager demotes edgeless networks).
     */
    protected String getBoostFunction() {
        return null;
    }

    /**
     * Builds a multiplicative boost function that demotes edgeless documents
     * (edgeCount == 0) by the given penalty while leaving all other documents
     * unchanged. Documents lacking an edgeCount field default to 1 (no penalty),
     * so non-network types are never affected.
     *
     * @param penalty the multiplier applied to edgeless documents (e.g. 0.01)
     */
    protected String edgePenaltyBoost(double penalty) {
        return "map(def(" + EDGE_COUNT + ",1),0,0," + penalty + ",1)";
    }

    /**
     * Preprocess search terms - can be overridden for entity-specific handling
     */
    protected String preprocessSearchTerms(String searchTerms) {
        if (searchTerms.equalsIgnoreCase("*:*")) {
            return searchTerms;
        }
        return SearchUtilities.preprocessSearchTerm(searchTerms);
    }


    protected static NdexException convertException(BaseHttpSolrClient.RemoteSolrException e, String core_name) {
        if (e.code() == 400) {
            String err = e.getMessage();
            Pattern p = Pattern.compile("Error from server at .*/" + core_name +": (.*)");
            Matcher m = p.matcher(e.getMessage());
            if ( m.matches()) {
                err = m.group(1);
            }
            return new BadRequestException(err);
        }
        return new NdexException("Error from NDEx Solr server: " + e.getMessage());
    }

    /**
     * Escapes a value for safe interpolation inside a double-quoted Solr/Lucene
     * phrase (e.g. {@code owner:"<value>"} in a filter query). Only the backslash
     * and double-quote characters can terminate or alter a quoted phrase, so those
     * are the only characters escaped. This prevents a value such as
     * {@code zzz") OR (*:*) OR (owner:"zzz} from breaking out of the phrase and
     * injecting boolean clauses into an access-control filter query. The backslash
     * is escaped first so a value's own backslashes are not confused with the
     * escaping added for the double-quotes.
     *
     * <p>For values that contain none of these characters (typical account names
     * and UUIDs) the input is returned unchanged, so existing queries are unaffected.
     *
     * @param value the raw value to place inside a quoted phrase (may be null)
     * @return the escaped value, or null if the input was null
     */
    protected static String escapeForFilter(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public void close () {
        solrClientWrapper.close();
    }

}
