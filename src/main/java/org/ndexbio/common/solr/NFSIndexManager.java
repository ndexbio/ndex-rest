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


    public static final String privateCoreName = "private-nfs";
    public static final String publicCoreName = "public-nfs";

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

    /** A filter that matches no document, used where a caller is entitled to nothing. */
    protected static final String MATCH_NOTHING = "(*:* AND NOT *:*)";
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
        solrClientWrapper.createCoreIfNeeded(privateCoreName);
        solrClientWrapper.createCoreIfNeeded(publicCoreName);
    }

    /**
     *
     * @param inputData - Object to be mapped to document
     */
    public void createIndex(T inputData, VisibilityType visibilityType){
        prepareIndexDocument(inputData, visibilityType);
        try {
            commit(visibilityType);
        } catch(SolrServerException sse){
            logger.error("Unable to commit document: " + sse.getMessage(), sse);
            //throw new RuntimeException("Failed to commit to Solr", sse); // ADD THIS
        } catch(IOException io){
            logger.error("Unable to commit document: " + io.getMessage(), io);
            //throw new RuntimeException("Failed to commit to Solr", io); // ADD THIS
        }
    }

    public void commit(VisibilityType visibilityType) throws SolrServerException, IOException {
        String coreName = getCoreNameFromVisibility(visibilityType);
        commit(coreName);

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
    public void delete(String uuid, VisibilityType visibilityType) throws SolrServerException, IOException {

        solrClientWrapper.delete(getCoreNameFromVisibility(visibilityType),
                uuid, false);
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
     * @param scope Folder-propagation reach resolved for this request; {@link SearchScope#EMPTY} when the
     *              caller is anonymous or holds no grants. Consulted on the private core only.
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
        String permissionFilter = buildPermissionFilter(userAccount, visibilityType, permission, scope);

        // Build the owner filter
        String ownerFilter = "";
        if (ownedBy != null) {
            ownerFilter = " AND (" + USER_ADMIN + ":\"" + escapeForFilter(ownedBy) + "\")";
        }

        // Combine filters
        String resultFilter = "(" + permissionFilter + ")" + ownerFilter;

        // Set up the query
        configureQuery(solrQuery, searchTerms, resultFilter, limit, offset);
        String coreName = getCoreNameFromVisibility(visibilityType);
        //logger.info("QUERY {} FILTER {}", solrQuery.toQueryString(), solrQuery.getFilterQueries());

        // Execute search
        try {
            QueryResponse rsp = solrClientWrapper.query(coreName, solrQuery);
            return rsp.getResults();
        } catch (BaseHttpSolrClient.RemoteSolrException e) {
            throw convertException(e, coreName);
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
        String permissionFilter = buildPermissionFilter(userAccount, visibilityType, permission, scope);
        String ownerFilter = ownedBy != null ? " AND (" + USER_ADMIN + ":\"" + escapeForFilter(ownedBy) + "\")" : "";
        String resultFilter = "(" + permissionFilter + ")" + ownerFilter + typeFilter;

        configureQuery(solrQuery, searchTerms, resultFilter, limit, offset);
        String coreName = getCoreNameFromVisibility(visibilityType);
        //logger.info("QUERY {} FILTER {}", solrQuery.toQueryString(), solrQuery.getFilterQueries());

        try {
            QueryResponse rsp = solrClientWrapper.query(coreName, solrQuery);
            return rsp.getResults();
        } catch (BaseHttpSolrClient.RemoteSolrException e) {
            throw convertException(e, coreName);
        } catch (Exception e) {
            throw new NdexException("Error accessing Solr: " + e.getMessage());
        }
    }

    /**
     * Builds the Solr filter query for permissions based on user and visibility
     */
    protected String buildPermissionFilter(String userAccount, VisibilityType visibilityType,
                                           Permissions permission, SearchScope scope) {
        // For PUBLIC cores
        if (visibilityType.equals(VisibilityType.PUBLIC)) {
            // Deliberately scope-free: see buildPublicCorePermissionFilter.
            return buildPublicCorePermissionFilter(userAccount, permission);
        }
        // For PRIVATE/UNLISTED core
        else {
            return buildPrivateCorePermissionFilter(userAccount, permission, scope);
        }
    }

    /**
     * Permission filter for public-nfs core (PUBLIC and UNLISTED items).
     * Anonymous users see all public (non-unlisted) items.
     * Authenticated users additionally see unlisted items they own for READ,
     * and are filtered to owned/editable items for WRITE/ADMIN.
     *
     * <p><b>No {@link SearchScope} is accepted here, and none may be added.</b> The specification says an
     * UNLISTED file is "only searchable by the owner", and that is a listing rule rather than an access
     * rule: a grant changes who can <em>open</em> an item, never whether it is <em>listed</em>. OR-ing a
     * folder-propagation clause into this filter would surface UNLISTED items to anyone holding a grant on
     * an ancestor folder — exactly the leak the owner-only test exists to prevent.</p>
     */
    protected String buildPublicCorePermissionFilter(String userAccount, Permissions permission) {
        String excludeUnlisted = "(*:* NOT " + VISIBILITY + ":UNLISTED)";

        if (userAccount == null) {
            return excludeUnlisted;
        }

        String userAccountStr = "\"" + escapeForFilter(userAccount) + "\"";

        if (permission == null || permission == Permissions.READ) {
            return excludeUnlisted + " OR (" + USER_ADMIN + ":" + userAccountStr + ")";
        } else if (permission == Permissions.WRITE) {
            // Ownership only. The former {@code userEdit} arm was written exclusively onto PRIVATE and
            // UNLISTED documents, so on this core its sole effect was to surface UNLISTED files to
            // non-owner editors — the one thing "only searchable by the owner" forbids. Dropping it costs
            // no legitimate result: a genuinely PUBLIC document never carried the field.
            return USER_ADMIN + ":" + userAccountStr;
        } else if (permission == Permissions.ADMIN) {
            return USER_ADMIN + ":" + userAccountStr;
        }

        return excludeUnlisted;
    }
    /**
     * Permission filter for private-nfs core (PRIVATE items).
     *
     * <p>Anonymous users see nothing. For everyone else the filter is a union of independent reasons a
     * document may be visible — ownership, folder propagation, and a direct grant — because effective
     * permission is the <em>most permissive</em> of everything that applies. Nothing here subtracts.</p>
     *
     * <p>No permission state is read from the index. The reachable ids arrive in {@code scope}, resolved
     * from the database for this request, so a share or a revoke takes effect on the very next search.</p>
     *
     * <p><b>The clause differs by document type</b>, and conflating them is the easy mistake: a network is
     * reached through its <em>parent's</em> id, a folder through its <em>own</em>. Each clause is pinned to
     * an {@code entityType} for that reason — folder and shortcut documents also carry {@code parentUuid},
     * so an unpinned parent clause would admit any shortcut sitting in a granted folder and quietly bypass
     * the target-reachability half of the shortcut conjunction.</p>
     */
    protected String buildPrivateCorePermissionFilter(String userAccount, Permissions permission,
                                                      SearchScope scope) {
        if (userAccount == null) {
            // Anonymous users cannot see private items
            return MATCH_NOTHING;
        }

        String userAccountStr = "\"" + escapeForFilter(userAccount) + "\"";

        // A folder grant never confers ownership, so ADMIN is ownership alone and ignores the scope.
        if (permission == Permissions.ADMIN) {
            return USER_ADMIN + ":" + userAccountStr;
        }
        if (permission != null && permission != Permissions.READ && permission != Permissions.WRITE) {
            return MATCH_NOTHING;
        }

        if (scope == null) {
            scope = SearchScope.EMPTY;
        }

        StringBuilder filter = new StringBuilder();
        filter.append("(").append(USER_ADMIN).append(":").append(userAccountStr).append(")");

        // A network inherits from the folder it sits in; the granted set already includes descendants.
        appendTermsClause(filter, FileType.NETWORK, PARENT_UUID, scope.grantedFolderIds());
        // A folder is reached by its own id.
        appendTermsClause(filter, FileType.FOLDER, UUID, scope.grantedFolderIds());
        // Networks with no granted folder above them: a direct grant, or a same-owner shortcut reference.
        appendTermsClause(filter, FileType.NETWORK, UUID, scope.reachableNetworkIds());

        // Shortcuts are read-only aliases — permission cannot be set on one — so they take no part in a
        // WRITE search. The set is a read-level conjunction and would over-admit if reused here.
        if (permission == null || permission == Permissions.READ) {
            appendTermsClause(filter, FileType.SHORTCUT, UUID, scope.readableShortcutIds());
        }

        return filter.toString();
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
        filter.append(" OR (").append(ENTITY_TYPE).append(":\"").append(entityType).append("\"")
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
    static String getCoreNameFromVisibility(VisibilityType visibilityType){
        if (visibilityType.equals(VisibilityType.PUBLIC) || visibilityType.equals(VisibilityType.UNLISTED)){
            return publicCoreName;
        }
        return privateCoreName;
    }




}
