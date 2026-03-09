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
import org.ndexbio.common.models.dao.DAOFactory;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.model.exceptions.BadRequestException;
import org.ndexbio.model.exceptions.NdexException;
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

    protected static final String USER_READ = "userRead";
    protected static final String USER_EDIT = "userEdit";
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
     */
    public void prepareIndexDocument(T  inputData, VisibilityType visibilityType,
                                     Collection<String> userReads,
                                     Collection<String> userEdits){
        setupIndexDocument(inputData, visibilityType);
        doc.addField(VISIBILITY, visibilityType.name());
        if (visibilityType.equals(VisibilityType.PRIVATE)){
            if(userReads != null) {
                addKeyWithValues(doc, USER_READ, userReads);
            }
            if ( userEdits !=null) {
                addKeyWithValues(doc, USER_EDIT, userEdits);
            }
        }

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
    public void createIndex(T inputData, VisibilityType visibilityType, Collection<String> userReads, Collection<String> userEdits){
        prepareIndexDocument(inputData, visibilityType, userReads, userEdits);
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
            Collection<SolrInputDocument> docs = new ArrayList<>(1);
            docs.add(doc);
            solrClientWrapper.commit(coreName, docs);
        } else {
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
     * @return SolrDocumentList containing matching documents
     */
    public SolrDocumentList search(
            String searchTerms,
            String userAccount,
            VisibilityType visibilityType,
            int limit,
            int offset,
            String ownedBy,
            Permissions permission) throws NdexException {

        SolrQuery solrQuery = new SolrQuery();

        // Build the permission filter
        String permissionFilter = buildPermissionFilter(userAccount, visibilityType,permission);

        // Build the owner filter
        String ownerFilter = "";
        if (ownedBy != null) {
            ownerFilter = " AND (" + USER_ADMIN + ":\"" + ownedBy + "\")";
        }

        // Combine filters
         String resultFilter = "(" + permissionFilter + ")" + ownerFilter;

        // Set up the query
        configureQuery(solrQuery, searchTerms, resultFilter, limit, offset);
        String coreName = getCoreNameFromVisibility(visibilityType);

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
     * Search with entity type filter
     */
    public SolrDocumentList searchByType(
            String searchTerms,
            String userAccount,
            VisibilityType visibilityType,
            int limit,
            int offset,
            String ownedBy,
            Permissions permission,
            String entityType) throws NdexException {

        String typeFilter;
        if ("SHORTCUT".equalsIgnoreCase(entityType)) {
            typeFilter = " AND (" + ENTITY_TYPE + ":\"SHORTCUT\")";
        } else {
            typeFilter = " AND ((" + ENTITY_TYPE + ":\"" + entityType + "\") OR " +
                    "(" + ENTITY_TYPE + ":\"SHORTCUT\" AND " + TARGET_TYPE + ":\"" + entityType + "\"))";
        }

        SolrQuery solrQuery = new SolrQuery();
        String permissionFilter = buildPermissionFilter(userAccount, visibilityType, permission);
        String ownerFilter = ownedBy != null ? " AND (" + USER_ADMIN + ":\"" + ownedBy + "\")" : "";
        String resultFilter = "(" + permissionFilter + ")" + ownerFilter + typeFilter;

        configureQuery(solrQuery, searchTerms, resultFilter, limit, offset);
        String coreName = getCoreNameFromVisibility(visibilityType);

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
                                           Permissions permission) {
        // For PRIVATE cores
        if (visibilityType.equals(VisibilityType.PRIVATE)) {
            return buildPrivateCorePermissionFilter(userAccount, permission);
        }
        // For PUBLIC core
        else {
            return buildPublicCorePermissionFilter(userAccount, permission);
        }
    }

    /**
     * Permission filter for public-nfs core (PUBLIC and UNLISTED items)
     * Anonymous users see everything. Authenticated users see everything for READ,
     * WRITE/ADMIN filters down to items they have those permissions on.
     */
    protected String buildPublicCorePermissionFilter(String userAccount, Permissions permission) {
        String excludeUnlisted = "(*:* NOT " + VISIBILITY + ":UNLISTED)";

        if (userAccount == null) {
            return excludeUnlisted;
        }

        String userAccountStr = "\"" + userAccount + "\"";

        if (permission == null || permission == Permissions.READ) {
            return excludeUnlisted + " OR (" + USER_ADMIN + ":" + userAccountStr + ")";
        } else if (permission == Permissions.WRITE) {
            return "(" + USER_ADMIN + ":" + userAccountStr + ") OR " +
                    "(" + USER_EDIT + ":" + userAccountStr + ")";
        } else if (permission == Permissions.ADMIN) {
            return USER_ADMIN + ":" + userAccountStr;
        }

        return excludeUnlisted;
    }

    /**
     * Permission filter for private-nfs core (PRIVATE items)
     * Anonymous users see nothing. Authenticated users only see items where they're listed in userAdmin, userRead,
     * or userEdit fields, depending on the permission level requested.
     */
    protected String buildPrivateCorePermissionFilter(String userAccount, Permissions permission) {
        if (userAccount == null) {
            // Anonymous users cannot see private items
            return "(*:* AND NOT *:*)"; // Match nothing
        }

        String userAccountStr = "\"" + userAccount + "\"";

        if (permission == null || permission == Permissions.READ) {
            // Items they can access
            return "(" + USER_ADMIN + ":" + userAccountStr + ") OR " +
                    "(" + USER_READ + ":" + userAccountStr + ") OR " +
                    "(" + USER_EDIT + ":" + userAccountStr + ")";
        } else if (permission == Permissions.WRITE) {
            // Items they can write
            return "(" + USER_ADMIN + ":" + userAccountStr + ") OR " +
                    "(" + USER_EDIT + ":" + userAccountStr + ")";
        } else if (permission == Permissions.ADMIN) {
            // Items they own
            return USER_ADMIN + ":" + userAccountStr;
        }

        return "(*:* AND NOT *:*)"; // Match nothing by default
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

    protected static void addKeyWithValues(SolrInputDocument doc, String field, Collection<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                doc.addField(field, value);
            }
        }
    }

    @Override
    public void close () {
        solrClientWrapper.close();
    }
    static String getCoreNameFromVisibility(VisibilityType visibilityType){
        if (visibilityType.equals(VisibilityType.PRIVATE)){
            return privateCoreName;
        }
        return publicCoreName;
    }




}
