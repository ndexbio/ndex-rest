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
            ownerFilter = " AND " + SolrClause.phrase(USER_ADMIN, ownedBy).asFilterQuery();
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
        String ownerFilter = ownedBy != null
                ? " AND " + SolrClause.phrase(USER_ADMIN, ownedBy).asFilterQuery() : "";
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
     * Every reason this caller may see a document, OR-ed together.
     *
     * <p>Effective permission is the most permissive of everything that applies, so each rule only
     * adds documents; none subtracts. No permission state is read from the index — the reachable ids
     * arrive in {@code scope}, resolved from the database for this request, so a share or a revoke
     * takes effect on the very next search.</p>
     */
    protected String buildPermissionFilter(String userAccount, Permissions permission, SearchScope scope) {
        SolrClause.Builder reasons = new SolrClause.Builder();
        reasons.add(publicDocumentsAnyoneMaySee(permission));
        reasons.add(documentsThisCallerOwns(userAccount, permission));
        reasons.add(privateDocumentsGrantedToThisCaller(userAccount, permission, scope));
        return SolrClause.anyOf(reasons.clauses()).toString();
    }

    /**
     * Public documents, which every caller may see.
     *
     * <p>Stated positively. The negative form — everything that is not unlisted — would also match
     * every private document, since one core now holds them all.</p>
     *
     * <p>Withheld from a WRITE or ADMIN search: those ask what the caller may change, and public
     * visibility grants no one the right to change anything. That holds for an anonymous caller most of
     * all — holding no permission on anything, the honest answer to what they may change is nothing, so
     * every arm declines and the filter matches no document.</p>
     */
    private SolrClause publicDocumentsAnyoneMaySee(Permissions permission) {
        boolean asksWhatMayBeChanged = permission == Permissions.WRITE || permission == Permissions.ADMIN;
        if (asksWhatMayBeChanged) {
            return null;
        }
        return SolrClause.term(VISIBILITY, VisibilityType.PUBLIC);
    }

    /**
     * Documents this caller owns, whatever their visibility.
     *
     * <p>Deliberately carries no visibility term. An owner has always been able to find their own
     * unlisted files, so restricting this to one visibility would take them away.</p>
     */
    private SolrClause documentsThisCallerOwns(String userAccount, Permissions permission) {
        if (userAccount == null || !grantsAccessToOwnedFiles(permission)) {
            return null;
        }
        return SolrClause.phrase(USER_ADMIN, userAccount);
    }

    /**
     * Private documents this caller reaches through a grant.
     *
     * <p>Pinned to private visibility. A grant decides who may open a file, never whether it is
     * listed, so an unlisted file stays unlisted for a grantee on any folder above it.</p>
     */
    private SolrClause privateDocumentsGrantedToThisCaller(String userAccount, Permissions permission,
                                                           SearchScope scope) {
        if (userAccount == null || !grantsPropagateThroughFolders(permission)) {
            return null;
        }
        SolrClause reachable = documentsReachableThroughGrants(permission, scope);
        if (reachable == null) {
            return null;
        }
        return SolrClause.allOf(SolrClause.term(VISIBILITY, VisibilityType.PRIVATE), reachable);
    }

    /** READ and WRITE act on owned files; so does an unspecified permission. ADMIN is ownership itself. */
    private boolean grantsAccessToOwnedFiles(Permissions permission) {
        return permission == null || permission == Permissions.READ
                || permission == Permissions.WRITE || permission == Permissions.ADMIN;
    }

    /** A folder grant confers read or write, never ownership, so ADMIN looks past it. */
    private boolean grantsPropagateThroughFolders(Permissions permission) {
        return permission == null || permission == Permissions.READ || permission == Permissions.WRITE;
    }

    /**
     * Everything {@code scope} reaches, or null when it reaches nothing.
     *
     * <p>Each clause names the type it applies to, because a network is reached through its parent's
     * id while a folder is reached through its own — and folder and shortcut documents carry a
     * parent id too, so an unnamed clause would admit a shortcut sitting in a granted folder and skip
     * the check on what that shortcut points at.</p>
     */
    private SolrClause documentsReachableThroughGrants(Permissions permission, SearchScope scope) {
        SearchScope reach = scope == null ? SearchScope.EMPTY : scope;
        SolrClause.Builder reachable = new SolrClause.Builder();

        reachable.add(documentsOfType(FileType.NETWORK, PARENT_UUID, reach.grantedFolderIds()));
        reachable.add(documentsOfType(FileType.FOLDER, UUID, reach.grantedFolderIds()));
        reachable.add(documentsOfType(FileType.NETWORK, UUID, reach.reachableNetworkIds()));

        // Permission cannot be set on a shortcut, so a write search has nothing to say about one. The
        // readable set is a read-level conjunction and would over-admit if reused here.
        if (permission == null || permission == Permissions.READ) {
            reachable.add(documentsOfType(FileType.SHORTCUT, UUID, reach.readableShortcutIds()));
        }

        return reachable.isEmpty() ? null : SolrClause.anyOf(reachable.clauses());
    }

    /** Documents of one type whose {@code field} is in {@code ids}, or null when there are none. */
    private SolrClause documentsOfType(FileType entityType, String field, Set<java.util.UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return SolrClause.allOf(SolrClause.phrase(ENTITY_TYPE, entityType.toString()),
                                SolrClause.terms(field, ids));
    }

    /** Narrows a search to the slice the caller asked for, or leaves it spanning everything. */
    protected String buildPartitionFilter(VisibilityType visibilityType) {
        return SearchPartition.requested(visibilityType)
                .map(partition -> " AND " + partition.clause().asFilterQuery())
                .orElse("");
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

    /** Escapes a value for use inside a double-quoted Solr phrase. */
    protected static String escapeForFilter(String value) {
        return SolrClause.escape(value);
    }

    @Override
    public void close () {
        solrClientWrapper.close();
    }

}
