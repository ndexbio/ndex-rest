package org.ndexbio.common.solr;

import org.apache.solr.common.SolrInputDocument;
import org.ndexbio.common.NdexClasses;
import org.ndexbio.common.util.Util;
import org.ndexbio.cx2.aspect.element.core.CxNetworkAttribute;
import org.ndexbio.cx2.aspect.element.core.CxNode;
import org.ndexbio.cx2.aspect.element.core.DeclarationEntry;
import org.ndexbio.cxio.aspects.datamodels.ATTRIBUTE_DATA_TYPE;
import org.ndexbio.cxio.aspects.datamodels.NetworkAttributesElement;
import org.ndexbio.model.cx.FunctionTermElement;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.model.tools.TermUtilities;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

public class GlobalNetworkIndexManager extends NFSIndexManager<NetworkSummaryWrapper> {
    private static final String USER_READ= "userRead";
    private static final String USER_EDIT = "userEdit";

    // user required indexing fields. hardcoded for now. Will turn them into configurable list in 1.4.
    public static final Set<String> otherAttributes =
            new HashSet<>(Arrays.asList("objectCategory", "organism",
                    "platform",
                    "graphPropertiesHash",
                    "networkType",
                    "disease",
                    "tissue",
                    "rightsHolder",
                    "author",
                    "createdAt",
                    "methods",
                    "subnetworkType","subnetworkFilter","graphHash","rights", "labels"));

    // holds the mapping between node ID and member attributes.
    // members will be added to the represent field as additional lists.
    private Map<Long, Set<String>> nodeMembers;

    public GlobalNetworkIndexManager(VisibilityType visibilityType){
        super(visibilityType);
        nodeMembers = new TreeMap<>();
    }

    @Override
    protected SolrInputDocument setupIndexDocument(NetworkSummaryWrapper summaryWrapper) {
        doc = new SolrInputDocument();
        NetworkSummary summary = summaryWrapper.getNetworkSummary();
        doc.addField(UUID,  summary.getExternalId().toString() );
        doc.addField(EDGE_COUNT, summary.getEdgeCount());
        doc.addField(NODE_COUNT, summary.getNodeCount());
        doc.addField(ENTITY_TYPE, FileType.NETWORK.toString());

        if ( summary.getName() !=null && summary.getName().length()>1) {
            doc.addField(NAME, summary.getName());
        }

        if (summary.getDescription() !=null && summary.getDescription().length()>1) {
            doc.addField(DESC, summary.getDescription());
        }

        if ( summary.getVersion() !=null && summary.getVersion().length()>1) {
            doc.addField(VERSION, summary.getVersion());
        }

        doc.addField(CREATION_TIME, summary.getCreationTime());
        doc.addField(MODIFICATION_TIME, summary.getModificationTime());

        doc.addField(NDEX_SCORE, Util.getNdexScoreFromSummary(summary));

        // network summary already has owner field?
        doc.addField(USER_ADMIN, summaryWrapper.getOwnerUserName());
        //doc.setDocumentBoost(documentBoost);;
        if( summaryWrapper.getUserReads() != null) {
            for(String userName : summaryWrapper.getUserReads()) {
                doc.addField(USER_READ, userName);
            }
        }

        if ( summaryWrapper.getUserEdits() !=null) {
            for ( String userName: summaryWrapper.getUserEdits()) {
                doc.addField(USER_EDIT, userName);
            }
        }

        if (visibilityType.equals(VisibilityType.PRIVATE)){
            if( summaryWrapper.getUserReads() != null) {
                addKeyWithValues(doc, USER_READ, summaryWrapper.getUserReads());
            }
            if ( summaryWrapper.getUserEdits() !=null) {
                addKeyWithValues(doc, USER_EDIT, summaryWrapper.getUserEdits());
            }

            if (summaryWrapper.getOwnerUserName() != null && !summaryWrapper.getOwnerUserName().isBlank()) {
                doc.addField(OWNER_FIELD, summaryWrapper.getOwnerUserName());
            }
            doc.addField(VISIBILITY, VisibilityType.PRIVATE);

        }
        return doc;
    }


    @Override
    public void postCommit(){
        nodeMembers = new TreeMap<>();
    }


    public void addCX2NodeToIndex(CxNode node, Map<String, Map.Entry<String, DeclarationEntry>> attributeNameMapping)  {

        Map<String,Object> nodeAttrs = node.getAttributes();
        Object nodeName = nodeAttrs.get(CxNode.NAME);
        if ( nodeName != null) {
            doc.addField(NODE_NAME, nodeName);
        }
        Object represents= nodeAttrs.get(CxNode.REPRESENTS);
        if ( represents != null) {
            for (String indexableString : getIndexableString((String)represents)) {
                doc.addField(REPRESENTS, indexableString);
            }
        }

        if ( attributeNameMapping.get(SingleNetworkSolrIdxManager.ALIAS)!=null) {

            for (String v : SingleNetworkSolrIdxManager.getSplitableTerms(SingleNetworkSolrIdxManager.ALIAS, node,
                    attributeNameMapping) ){
                doc.addField(ALIASES, v);
            }

        }

        String nodeType = SingleNetworkSolrIdxManager.getSingleIndexableTermFromNode(SingleNetworkSolrIdxManager.TYPE,
                node, attributeNameMapping);

        if ( nodeType != null && (nodeType.equalsIgnoreCase(SingleNetworkSolrIdxManager.PROTEINFAMILY) ||
                nodeType.equalsIgnoreCase(SingleNetworkSolrIdxManager.COMPLEX) )) {
            List<String> memberGenes = SingleNetworkSolrIdxManager.getSplitableTerms (SingleNetworkSolrIdxManager.MEMBER,
                    node, attributeNameMapping);
            for ( String memberIdStr : memberGenes) {
                for ( String indexableString : getIndexableString(memberIdStr) ){
                    doc.addField(REPRESENTS, indexableString);
                }
            }
        }

    }


    public void addFunctionTermToIndex(FunctionTermElement e)  {

        for ( String term : getIndexableStringsFromFunctionTerm(e)) {

            doc.addField(REPRESENTS, term);
        }

    }

    protected static List<String> getIndexableStringsFromFunctionTerm(FunctionTermElement e)  {

        List<String> terms = getIndexableString(e.getFunctionName());
        for (Object arg: e.getArgs()) {
            if (arg instanceof String ) {
                terms.addAll(getIndexableString((String)arg));
            } else if ( arg instanceof FunctionTermElement ){
                terms.addAll(getIndexableStringsFromFunctionTerm((FunctionTermElement)arg));
            }
        }

        return terms;
    }


    public List<String> addCXNetworkAttrToIndex(NetworkAttributesElement e)  {

        List<String> warnings = new ArrayList<>();
        if ( e.getName().equals(NdexClasses.Network_P_name) ) {
            addStringAttrFromAttributeElement(e, NAME, warnings);
        } else if ( e.getName().equals(NdexClasses.Network_P_desc ) ) {
            addStringAttrFromAttributeElement(e, DESC, warnings);
        } else if ( e.getName().equals(NdexClasses.Network_P_version)  ) {
            addStringAttrFromAttributeElement(e, VERSION, warnings);
        } else {
            if ( otherAttributes.contains(e.getName())  ) {
                addStringListgAttribute(e, e.getName(), warnings);
            }
        }

        return warnings;

    }

    public List<String> addCX2NetworkAttrToIndex(CxNetworkAttribute e)  {

        List<String> warnings = new ArrayList<>();
        if ( e.getNetworkName()!= null) {
            doc.addField(NAME, e.getNetworkName());
        } else if ( e.getNetworkDescription() !=null ) {
            doc.addField(DESC, e.getNetworkDescription());
        } else if ( e.getNetworkVersion() !=null) {
            doc.addField(VERSION, e.getNetworkVersion());
        }

        for ( String otherIndexedName: otherAttributes) {
            if ( e.getAttributes().get(otherIndexedName) !=null) {
                addStringOrListgObj(e.getAttributes().get(otherIndexedName), otherIndexedName, warnings);
            }
        }

        return warnings;

    }

    private void addStringAttrFromAttributeElement(NetworkAttributesElement e, String solrFieldName, List<String>  warnings ) {
        if (e.getDataType() == ATTRIBUTE_DATA_TYPE.STRING) {
            if ( e.getValue() !=null && e.getValue().length()>0)
                doc.addField(solrFieldName, e.getValue());
        } else
            warnings.add("Network attribute " + e.getName() + " is not indexed because its data type is not 'string'.");
    }

    private void addStringOrListgObj(Object e, String solrFieldName, List<String>  warnings ) {
        if (e instanceof String) {
            doc.addField(solrFieldName, e);
        } else if (e instanceof List<?>) {
            for ( Object value : ((List<?>)e)) {
                if ( value instanceof String)
                    doc.addField(solrFieldName, value);
                else {
                    warnings.add("Network attribute " + solrFieldName +  " is not indexed because its data type is not 'string' or 'list_of_string'.");
                    break;
                }
            }
        } else
            warnings.add("Network attribute " + solrFieldName + " is not indexed because its data type is not 'string' or 'list_of_string'.");
    }

    private void addStringListgAttribute(NetworkAttributesElement e, String solrFieldName, List<String>  warnings ) {
        if (e.getDataType() == ATTRIBUTE_DATA_TYPE.STRING) {
            if ( e.getValue() !=null && e.getValue().length()>0)
                doc.addField(solrFieldName, e.getValue());
        } else if (e.getDataType() == ATTRIBUTE_DATA_TYPE.LIST_OF_STRING) {
            for ( String value : e.getValues()) {
                if ( value !=null && value.length()>0)
                    doc.addField(solrFieldName, value);
            }
        } else
            warnings.add("Network attribute " + e.getName() + " is not indexed because its data type is not 'string' or 'list_of_string'.");
    }

    protected static List<String> getIndexableString(String termString) {

        // case 1 : termString is a URI
        // example: http://identifiers.org/uniprot/P19838
        // treat the last element in the URI as the identifier and the rest as
        // prefix string. Just to help the future indexing.
        //
        List<String> result = new ArrayList<>(2) ;
        String identifier = null;
        if ( termString.length() > 10 && (termString.substring(0, 7).equalsIgnoreCase("http://") ||
                termString.substring(0, 8).equalsIgnoreCase("https://") )&&
                (!termString.endsWith("/"))) {
            try {
                URI termStringURI = new URI(termString);
                identifier = termStringURI.getFragment();

                if ( identifier == null ) {
                    String path = termStringURI.getPath();
                    if (path != null && path.indexOf("/") != -1) {
                        int pos = termString.lastIndexOf('/');
                        identifier = termString.substring(pos + 1);
                    } else
                        return result; // the string is a URL in the format that we don't want to index it in Solr.
                }
                result.add(identifier);
                return result;

            } catch (URISyntaxException e) {
                // ignore and move on to next case
            }
        }

        String[] termStringComponents = TermUtilities.getNdexQName(termString);
        if (termStringComponents != null && termStringComponents.length == 2) {
            // case 2: termString is of the form (NamespacePrefix:)*Identifier
            //		if ( !termStringComponents[0].contains(" "))
            result.add(termString);
            result.add(termStringComponents[1]);
            return  result;
        }

        // case 3: termString cannot be parsed, use it as the identifier.
        // so leave the prefix as null and return the string
        result.add(termString);
        return result;

    }
}
