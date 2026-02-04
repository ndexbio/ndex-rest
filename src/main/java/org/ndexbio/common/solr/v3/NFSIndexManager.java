package org.ndexbio.common.solr.v3;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.NamedList;
import org.ndexbio.model.exceptions.BadRequestException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.network.VisibilityType;
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
    protected final String solrUrl ;

    protected SolrInputDocument doc ;

    public static final String UUID = "uuid";
    public static final String NAME = "name";
    public static final String ENTITY_TYPE = "entityType";
    public static final String DESC = "description";
    public static final String VERSION = "version";
    public static final String NODE_NAME = "nodeName";
    public static final String USER_ADMIN = "owner";

    public static final String REPRESENTS = "represents";
    public static final String ALIASES = "alias";
    public static final String MODIFICATION_TIME = "modificationTime";
    public static final String VISIBILITY = "visibility";

    public static final String EDGE_COUNT = "edgeCount";

    public static final String NODE_COUNT = "nodeCount";
    public static final String CREATION_TIME = "creationTime";
    public static final String NDEX_SCORE = "ndexScore";

    protected static final String OWNER_FIELD = "owner";


    protected abstract SolrInputDocument setupIndexDocument(T inputData);


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
        client = new HttpSolrClient.Builder(solrUrl).build();
        client.setBaseURL(solrUrl + "/" + coreName);

    }
    /**
     * Creates core on Solr if needed. If core exists, nothing is done
     *
     * @throws SolrServerException
     * @throws IOException
     * @throws NdexException
     */
    public void createCoreIfNeeded() throws SolrServerException, IOException, NdexException {

        CoreAdminResponse foo = CoreAdminRequest.getStatus(coreName,client);
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
            foo = creator.process(client);
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

    public void createIndex(T inputData){
        setupIndexDocument(inputData);
        try {
            commitDocument();
        } catch(SolrServerException sse){
            logger.error("Unable to commit document: " + sse.getMessage(), sse);
        } catch(IOException io){
            logger.error("Unable to commit document: " + io.getMessage(), io);
        }
    }

    protected void commitDocument() throws SolrServerException, IOException {
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
