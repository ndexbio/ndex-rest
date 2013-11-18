package org.ndexbio.rest.actions.networks;

import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.server.network.protocol.http.OHttpRequest;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.frames.FramedGraph;
import com.tinkerpop.frames.VertexFrame;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.ndexbio.rest.actions.NdexAction;
import org.ndexbio.rest.domain.XBaseTerm;
import org.ndexbio.rest.domain.XCitation;
import org.ndexbio.rest.domain.XEdge;
import org.ndexbio.rest.domain.XFunctionTerm;
import org.ndexbio.rest.domain.XNameSpace;
import org.ndexbio.rest.domain.XNetwork;
import org.ndexbio.rest.domain.XNode;
import org.ndexbio.rest.domain.XSupport;
import org.ndexbio.rest.domain.XTerm;
import org.ndexbio.rest.domain.XUser;
import org.ndexbio.rest.exceptions.JdexParsingException;
import org.ndexbio.rest.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.helpers.RidConverter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/******************************************************************************
* HTTP PUT /networks
******************************************************************************/
public class PutNetwork extends NdexAction<PutNetwork.PutNetworkContext>
{
    public static final class PutNetworkContext implements NdexAction.Context
    {
        private String userId;
        private JsonNode network;
        private XNetwork xNetwork;
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    
    
    @Override
    public String[] getNames()
    {
        return new String[] { "PUT|networks/*" };
    }


    
    @Override
    protected void action(PutNetworkContext requestContext, FramedGraph<OrientBaseGraph> orientDbGraph)
    {
        final XUser owningUser = orientDbGraph.getVertex(RidConverter.convertToRid(requestContext.userId), XUser.class);
        if (owningUser == null)
            throw new ObjectNotFoundException("User", requestContext.userId);

        final XNetwork newNetwork = orientDbGraph.addVertex("class:xNetwork", XNetwork.class);
        owningUser.addOwnsNetwork(newNetwork);

        if (requestContext.network.get("Format") != null)
            newNetwork.setFormat(requestContext.network.get("Format").asText());

        final HashMap<String, VertexFrame> networkIndex = new HashMap<String, VertexFrame>();
        createNamespaces(newNetwork, requestContext.network, orientDbGraph, networkIndex);
        createTerms(newNetwork, requestContext.network, orientDbGraph, networkIndex);
        createProperties(newNetwork, requestContext.network);
        createNodes(newNetwork, requestContext.network, orientDbGraph, networkIndex);
        createSupports(newNetwork, requestContext.network, orientDbGraph, networkIndex);
        createCitations(newNetwork, requestContext.network, orientDbGraph, networkIndex);
        createEdges(newNetwork, requestContext.network, orientDbGraph, networkIndex);

        requestContext.xNetwork = newNetwork;
    }

    @Override
    protected String getDescription()
    {
        return "Creates a network.";
    }

    @Override
    protected PutNetwork.PutNetworkContext parseRequest(OHttpRequest httpRequest) throws IOException
    {
        final JsonNode rootNode = OBJECT_MAPPER.readTree(httpRequest.content);
        PutNetworkContext requestContext = new PutNetworkContext();
        requestContext.userId = rootNode.get("accountid").asText();
        requestContext.network = rootNode.get("network");

        return requestContext;
    }

    @Override
    protected Object serializeResult(PutNetwork.PutNetworkContext requestContext)
    {
        final ObjectNode result = OBJECT_MAPPER.createObjectNode();
        result.put("Id", RidConverter.convertToJid((ORID)requestContext.xNetwork.asVertex().getId()));
        result.put("Owner", requestContext.userId);

        return result;
    }



    /**************************************************************************
    * Converts the serialized [citation] contributors to a list of strings.
    * 
    * @param contributors The contributors.
    **************************************************************************/
    private List<String> asStringList(JsonNode contributors)
    {
        List<String> result = new ArrayList<String>();
        for (JsonNode contributor : contributors)
            result.add(contributor.asText());

        return result;
    }

    /**************************************************************************
    * Creates citations in a new network.
    * 
    * @param newNetwork        The new network.
    * @param serializedNetwork The JSON-serialized network.
    * @param orientDbGraph     The OrientDB graph model.
    * @param networkIndex      The network index.
    **************************************************************************/
    private void createCitations(XNetwork newNetwork, JsonNode serializedNetwork, FramedGraph<OrientBaseGraph> orientDbGraph, HashMap<String, VertexFrame> networkIndex)
    {
        JsonNode serializedCitations = serializedNetwork.get("Citations");
        Iterator<String> citations = serializedCitations.getFieldNames();
        while (citations.hasNext())
        {
            String citationIndex = citations.next();
            JsonNode serializedCitation = serializedCitations.get(citationIndex);

            XCitation newCitation = orientDbGraph.addVertex("class:xCitation", XCitation.class);
            if (serializedCitation.get("Identifier") != null)
                newCitation.setIdentifier(serializedCitation.get("Identifier").asText());

            if (serializedCitation.get("Type") != null)
                newCitation.setType(serializedCitation.get("Type").asText());

            if (serializedCitation.get("Title") != null)
                newCitation.setTitle(serializedCitation.get("Title").asText());

            newCitation.setContributors(asStringList(serializedCitation.get("Contributors")));

            newCitation.setJdexId(citationIndex);

            newNetwork.addCitations(newCitation);

            networkIndex.put(citationIndex, newCitation);
        }
    }

    /**************************************************************************
    * Creates edges in a new network.
    * 
    * @param newNetwork        The new network.
    * @param serializedNetwork The JSON-serialized network.
    * @param orientDbGraph     The OrientDB graph model.
    * @param networkIndex      The network index.
    **************************************************************************/
    private void createEdges(XNetwork newNetwork, JsonNode serializedNetwork, FramedGraph<OrientBaseGraph> orientDbGraph, HashMap<String, VertexFrame> networkIndex)
    {
        final JsonNode serializedEdges = serializedNetwork.get("Edges");
        final Iterator<String> edgeProperties = serializedEdges.getFieldNames();

        int edgesCount = 0;
        while (edgeProperties.hasNext())
        {
            final JsonNode edgeJDEx = serializedEdges.get(edgeProperties.next());

            final XEdge xEdge = orientDbGraph.addVertex("class:xEdge", XEdge.class);
            final XNode subject = (XNode)loadFromIndex(networkIndex, edgeJDEx, "s");
            final XNode object = (XNode)loadFromIndex(networkIndex, edgeJDEx, "o");

            xEdge.addSubject(subject);
            xEdge.addObject(object);

            if (loadFromIndex(networkIndex, edgeJDEx, "p") instanceof XTerm)
                xEdge.addPredicate((XTerm)loadFromIndex(networkIndex, edgeJDEx, "p"));

            xEdge.addNetwork(newNetwork);

            newNetwork.addNdexEdge(xEdge);
            edgesCount++;
        }

        newNetwork.setEdgesCount(edgesCount);
    }

    /**************************************************************************
    * Creates nodes in a new network.
    * 
    * @param newNetwork        The new network.
    * @param serializedNetwork The JSON-serialized network.
    * @param orientDbGraph     The OrientDB graph model.
    * @param networkIndex      The network index.
    **************************************************************************/
    private void createNodes(XNetwork newNetwork, JsonNode serializedNetwork, FramedGraph<OrientBaseGraph> orientDbGraph, HashMap<String, VertexFrame> networkIndex)
    {
        JsonNode nodes = serializedNetwork.get("nodes");
        Iterator<String> nodesIterator = nodes.getFieldNames();
        int nodesCount = 0;

        while (nodesIterator.hasNext())
        {
            String index = nodesIterator.next();
            JsonNode node = nodes.get(index);

            XNode xNode = orientDbGraph.addVertex("class:xNode", XNode.class);
            if (node.get("name") != null)
                xNode.setName(node.get("name").asText());

            if (node.get("represents") != null)
                if (loadFromIndex(networkIndex, node, "represents") instanceof XTerm)
                    xNode.addRepresents((XTerm) loadFromIndex(networkIndex, node, "represents"));

            xNode.setJdexId(index);

            newNetwork.addNode(xNode);

            networkIndex.put(index, xNode);
            nodesCount++;
        }

        newNetwork.setNodesCount(nodesCount);
    }

    /**************************************************************************
    * Creates namespaces in a new network.
    * 
    * @param newNetwork        The new network.
    * @param serializedNetwork The JSON-serialized network.
    * @param orientDbGraph     The OrientDB graph model.
    * @param networkIndex      The network index.
    **************************************************************************/
    private void createNamespaces(XNetwork newNetwork, JsonNode serializedNetwork, FramedGraph<OrientBaseGraph> orientDbGraph, HashMap<String, VertexFrame> networkIndex)
    {
        JsonNode namespaces = serializedNetwork.get("namespaces");
        Iterator<String> namespacesIterator = namespaces.getFieldNames();

        while (namespacesIterator.hasNext())
        {
            String index = namespacesIterator.next();
            JsonNode namespace = namespaces.get(index);

            XNameSpace newNamespace = orientDbGraph.addVertex("class:xNameSpace", XNameSpace.class);
            newNamespace.setJdexId(index);
            if (namespace.get("prefix") != null)
                newNamespace.setPrefix(namespace.get("prefix").asText());

            newNamespace.setUri(namespace.get("uri").asText());

            newNetwork.addNameSpace(newNamespace);
            
            networkIndex.put(index, newNamespace);
        }
    }

    /**************************************************************************
    * Creates network properties.
    * 
    * @param newNetwork        The new network.
    * @param serializedNetwork The JSON-serialized network.
    **************************************************************************/
    private void createProperties(XNetwork newNetwork, JsonNode serializedNetwork)
    {
        Map<String, String> propertiesMap = new HashMap<String, String>();
        JsonNode properties = serializedNetwork.get("properties");
        
        Iterator<String> propertiesIterator = properties.getFieldNames();
        while (propertiesIterator.hasNext())
        {
            String index = propertiesIterator.next();
            propertiesMap.put(index, properties.get(index).asText());
        }

        newNetwork.setProperties(propertiesMap);
    }

    /**************************************************************************
    * Creates network supports.
    * 
    * @param newNetwork        The new network.
    * @param serializedNetwork The JSON-serialized network.
    * @param orientDbGraph     The OrientDB graph model.
    * @param networkIndex      The network index.
    **************************************************************************/
    private void createSupports(XNetwork newNetwork, JsonNode serializedNetwork, FramedGraph<OrientBaseGraph> orientDbGraph, HashMap<String, VertexFrame> networkIndex)
    {
        JsonNode supports = serializedNetwork.get("supports");
        Iterator<String> supportsIterator = supports.getFieldNames();

        while (supportsIterator.hasNext())
        {
            String index = supportsIterator.next();
            JsonNode support = supports.get(index);
            XSupport xSupport = orientDbGraph.addVertex("class:xSupport", XSupport.class);

            xSupport.setJdexId(index);
            if (support.get("text") != null)
                xSupport.setText(support.get("text").asText());

            newNetwork.addSupport(xSupport);

            networkIndex.put(index, xSupport);
        }
    }

    /**************************************************************************
    * Creates terms in a new network.
    * 
    * @param newNetwork        The new network.
    * @param serializedNetwork The JSON-serialized network.
    * @param orientDbGraph     The OrientDB graph model.
    * @param networkIndex      The network index.
    **************************************************************************/
    private void createTerms(XNetwork newNetwork, JsonNode serializedNetwork, FramedGraph<OrientBaseGraph> orientDbGraph, HashMap<String, VertexFrame> networkIndex)
    {
        final ArrayList<XFunctionTerm> functions = new ArrayList<XFunctionTerm>();

        JsonNode terms = serializedNetwork.get("terms");
        Iterator<String> termsIterator = terms.getFieldNames();

        while (termsIterator.hasNext())
        {
            String index = termsIterator.next();
            JsonNode term = terms.get(index);

            final XTerm xTerm;
            if (term.get("name") != null)
            {
                xTerm = orientDbGraph.addVertex("class:xBaseTerm", XBaseTerm.class);
                xTerm.setName(term.get("name").asText());

            }
            else if (term.get("termFunction") != null)
            {
                XFunctionTerm xFunctionTerm = orientDbGraph.addVertex("class:xFunctionTerm", XFunctionTerm.class);
                xTerm = xFunctionTerm;

                functions.add(xFunctionTerm);
            }
            else
                continue;

            xTerm.setNamespace((XNameSpace) loadFromIndex(networkIndex, term, "ns"));
            xTerm.setJdexId(index);

            newNetwork.addTerm(xTerm);

            networkIndex.put(index, xTerm);
        }

        postProcessFunctions(functions, terms, networkIndex);
    }

    /**************************************************************************
    * Loads a property from an index.
    * 
    * @param networkIndex   The network index.
    * @param serializedTerm The JSON-serialized term.
    * @param propertyName   The property name.
    **************************************************************************/
    private VertexFrame loadFromIndex(HashMap<String, VertexFrame> networkIndex, JsonNode serializedTerm, String propertyName) throws JdexParsingException
    {
        final JsonNode fieldValue = serializedTerm.get(propertyName);
        if (fieldValue == null)
            return null;

        final String value = fieldValue.asText();
        final VertexFrame result = networkIndex.get(value);
        if (result == null)
            throw new JdexParsingException("Failed to load property: " + propertyName + " from index. (id = " + value + ", referencing object = " + serializedTerm.toString() + ")");

        return result;
    }

    /**************************************************************************
    * Post-processes term functions.
    * 
    * @param functionTerms   The term functions.
    * @param serializedTerms The JSON-serialized terms.
    * @param fieldName       The field name.
    **************************************************************************/
    private void postProcessFunctions(List<XFunctionTerm> functionTerms, JsonNode serializedTerms, HashMap<String, VertexFrame> networkIndex)
    {
        for (XFunctionTerm termFunction : functionTerms)
        {
            final JsonNode serializedTerm = serializedTerms.get(termFunction.getJdexId());
            termFunction.setTermFunction((XTerm) loadFromIndex(networkIndex, serializedTerm, "termFunction"));

            final Map<Integer, String> textParams = new HashMap<Integer, String>();
            final Map<Integer, ORID> linkParams = new HashMap<Integer, ORID>();

            final JsonNode jParameters = serializedTerm.get("parameters");
            final Iterator<String> iterator = jParameters.getFieldNames();
            int paramNumber = 0;

            while (iterator.hasNext())
            {
                final String index = iterator.next();
                final JsonNode jParam = jParameters.get(index);
                
                if (jParam.get("term") != null)
                    linkParams.put(paramNumber, (ORID) loadFromIndex(networkIndex, jParam, "term").asVertex().getId());
                else
                    textParams.put(paramNumber, jParam.asText());

                paramNumber++;
            }

            termFunction.setTextParameters(textParams);
            termFunction.setLinkParameters(linkParams);
        }
    }
}
