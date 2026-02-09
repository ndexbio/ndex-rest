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
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.NamedList;
import org.ndexbio.model.exceptions.BadRequestException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.model.tools.SearchUtilities;
import org.ndexbio.rest.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class NFSIndexManager<T> implements AutoCloseable {
    protected final Logger logger = LoggerFactory.getLogger(this.getClass().getSimpleName());


    protected static final String privateCoreName = "private-nfs";
    protected static final String publicCoreName = "public-nfs";

    protected final VisibilityType visibilityType;

    protected final String coreName;
    protected final HttpSolrClient client;
    private final HttpSolrClient adminClient;
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

    public static final String EDGE_COUNT = "edgeCount";

    public static final String NODE_COUNT = "nodeCount";
    public static final String CREATION_TIME = "creationTime";
    public static final String NDEX_SCORE = "ndexScore";

    protected static final String USER_READ = "userRead";
    protected static final String USER_EDIT = "userEdit";

    /**
     * Create index document for subclass input - must be implemented by subclasses
     */
    protected abstract SolrInputDocument setupIndexDocument(T inputData);

    /**
     * Get query fields with weights - must be implemented by subclasses
     */
    protected abstract String getQueryFields();

    /**
     * Public wrapper function for setupIndexDocument that doesn't expose inner SolrInputDocument
     */
    public void prepareIndexDocument(T  inputData,
                                     Collection<String> userReads,
                                     Collection<String> userEdits){
        setupIndexDocument(inputData);
        if (visibilityType.equals(VisibilityType.PUBLIC)){
            if(userReads != null) {
                addKeyWithValues(doc, USER_READ, userReads);
            }
            if ( userEdits !=null) {
                addKeyWithValues(doc, USER_EDIT, userEdits);
            }
        }
    }


    public NFSIndexManager(VisibilityType visibilityType){
        this.visibilityType = visibilityType;
        if (visibilityType.equals(VisibilityType.PUBLIC) || visibilityType.equals(VisibilityType.UNLISTED)){
            coreName = publicCoreName;
        }
        else {
            coreName = privateCoreName;
        }

        solrUrl = Configuration.getInstance().getSolrURL();
        doc = new SolrInputDocument();
        adminClient = new HttpSolrClient.Builder(solrUrl).build();
        client  = new HttpSolrClient.Builder(solrUrl + "/" + coreName).build();

    }
    /**
     * Creates core on Solr if needed. If core exists, nothing is done
     *
     * @throws SolrServerException
     * @throws IOException
     * @throws NdexException
     */
    public void createCoreIfNeeded() throws SolrServerException, IOException, NdexException {

        CoreAdminResponse foo = CoreAdminRequest.getStatus(coreName,adminClient);
        if (foo.getStatus() != 0 ) {
            throw new NdexException ("Failed to get status of solrIndex for " + coreName + ". Error: " + foo.getResponseHeader().toString());
        }
        NamedList<Object> bar = foo.getResponse();

        NamedList<Object> st = (NamedList<Object>)bar.get("status");

        NamedList<Object> core = (NamedList<Object>)st.get(coreName);
        if ( core.size() == 0 ) {
            logger.debug("Solr core " + coreName + " doesn't exist. Creating it now ....");

            CoreAdminRequest.Create creator = new CoreAdminRequest.Create();
            creator.setCoreName(coreName);
            creator.setConfigSet(coreName);
            foo = creator.process(adminClient);
            if ( foo.getStatus() != 0 ) {
                throw new NdexException ("Failed to create solrIndex for " + coreName + ". Error: " + foo.getResponseHeader().toString());
            }
            logger.debug("Done.");
        }
        else {
            logger.debug("Found core "+ coreName + " in Solr.");
        }

    }

    /**
     *
     * @param inputData - Object to be mapped to document
     */

    public void createIndex(T inputData, Collection<String> userReads, Collection<String> userEdits){
        prepareIndexDocument(inputData, userReads, userEdits);
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
        if ( !doc.isEmpty()) {
            Collection<SolrInputDocument> docs = new ArrayList<>(1);
            docs.add(doc);
            client.add(docs);
            client.commit(false,true,true);
            docs.clear();
        } else
            client.commit(false,true,true);
        doc = new SolrInputDocument();
        postCommit();

    }

    public void delete(String uuid) throws SolrServerException, IOException {
        client.deleteById(uuid);
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
            int limit,
            int offset,
            String ownedBy,
            Permissions permission) throws IOException, SolrServerException, NdexException {

        SolrQuery solrQuery = new SolrQuery();

        // Build the permission filter
        String permissionFilter = buildPermissionFilter(userAccount, permission);

        // Build the owner filter
        String ownerFilter = "";
        if (ownedBy != null) {
            ownerFilter = " AND (" + USER_ADMIN + ":\"" + ownedBy + "\")";
        }

        // Combine filters
         String resultFilter = "(" + permissionFilter + ")" + ownerFilter;

        // Set up the query
        configureQuery(solrQuery, searchTerms, resultFilter, limit, offset);

        // Execute search
        try {
            QueryResponse rsp = client.query(solrQuery, SolrRequest.METHOD.POST);
            return rsp.getResults();
        } catch (BaseHttpSolrClient.RemoteSolrException e) {
            throw convertException(e, coreName);
        }
    }

    /**
     * Search with entity type filter
     */
    public SolrDocumentList searchByType(
            String searchTerms,
            String userAccount,
            int limit,
            int offset,
            String ownedBy,
            Permissions permission,
            String entityType) throws IOException, SolrServerException, NdexException {

        // Add entity type filter
        String typeFilter = " AND (" + ENTITY_TYPE + ":\"" + entityType + "\")";

        SolrQuery solrQuery = new SolrQuery();
        String permissionFilter = buildPermissionFilter(userAccount, permission);
        String ownerFilter = ownedBy != null ? " AND (" + USER_ADMIN + ":\"" + ownedBy + "\")" : "";
        String resultFilter = "(" + permissionFilter + ")" + ownerFilter + typeFilter;

        configureQuery(solrQuery, searchTerms, resultFilter, limit, offset);

        try {
            QueryResponse rsp = client.query(solrQuery, SolrRequest.METHOD.POST);
            return rsp.getResults();
        } catch (BaseHttpSolrClient.RemoteSolrException e) {
            throw convertException(e, coreName);
        }
    }

    /**
     * Builds the Solr filter query for permissions based on user and visibility
     */
    protected String buildPermissionFilter(String userAccount, Permissions permission) {
        // For PUBLIC/UNLISTED cores
        if (coreName.equals(publicCoreName)) {
            return buildPublicCorePermissionFilter(userAccount, permission);
        }
        // For PRIVATE core
        else {
            return buildPrivateCorePermissionFilter(userAccount, permission);
        }
    }

    /**
     * Permission filter for public-nfs core (PUBLIC and UNLISTED items)
     * Anonymous users see everything. Authenticated users see everything for READ,
     * WRITE/ADMIN filters down to items they have those permissions on.
     */
    protected String buildPublicCorePermissionFilter(String userAccount, Permissions permission) {
        if (userAccount == null) {
            // Anonymous users can see everything in the public core
            // (client-side will filter UNLISTED)
            return "*:*";
        }

        String userAccountStr = "\"" + userAccount + "\"";

        if (permission == null) {
            // All accessible items - everything in public core
            return "*:*";
        } else if (permission == Permissions.READ) {
            // Items they can read - everything in public core
            return "*:*";
        } else if (permission == Permissions.WRITE) {
            // Items they can write
            return "(" + USER_ADMIN + ":" + userAccountStr + ") OR " +
                    "(" + USER_EDIT + ":" + userAccountStr + ")";
        } else if (permission == Permissions.ADMIN) {
            // Items they own
            return USER_ADMIN + ":" + userAccountStr;
        }

        return "*:*";
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
        solrQuery.setQuery(preprocessSearchTerms(searchTerms)).setFields(UUID);

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
            solrQuery.setRows(100000);
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
        try {
            client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



}
