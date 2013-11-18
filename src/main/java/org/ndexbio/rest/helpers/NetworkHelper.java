package org.ndexbio.rest.helpers;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.ndexbio.rest.domain.XEdge;
import org.ndexbio.rest.domain.XNameSpace;
import org.ndexbio.rest.domain.XNetwork;
import org.ndexbio.rest.domain.XNode;
import org.ndexbio.rest.domain.XTerm;
import com.orientechnologies.orient.core.id.ORID;

public class NetworkHelper
{
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    
    
    /**************************************************************************
    * Loads all nodes referenced by the edges.
    *
    * @param edges The edges.
    * @return A collection of nodes.
    **************************************************************************/
    public static Collection<XNode> loadNodeDependencies(Collection<XEdge> edges)
    {
        Set<XNode> edgeNodes = new HashSet<XNode>();
        for (XEdge edge : edges)
        {
            for (XNode xNode : edge.getSubject())
                edgeNodes.add(xNode);

            for (XNode xNode : edge.getObject())
                edgeNodes.add(xNode);
        }
        
        return edgeNodes;
    }

    /**************************************************************************
    * Loads all terms referenced by the nodes and edges.
    *
    * @param nodes The nodes.
    * @param edges The edges.
    **************************************************************************/
    public static Collection<XTerm> loadTermDependencies(Collection<XNode> nodes, Collection<XEdge> edges)
    {
        Set<XTerm> terms = new HashSet<XTerm>();

        for (XNode node : nodes)
        {
            for (XTerm term : node.getRepresents())
                terms.add(term);
        }

        for (XEdge edge : edges)
        {
            for (XTerm term : edge.getPredicate())
                terms.add(term);
        }

        return terms;
    }

    /**************************************************************************
    * Serializes an edge.
    * 
    * @param edge           The edge.
    * @param serializedEdge The JSON-serialized edge.
    **************************************************************************/
    public static void serializeEdge(XEdge edge, ObjectNode serializedEdge)
    {
        serializedEdge.put("Id", RidConverter.convertToJid((ORID)edge.asVertex().getId()));

        Iterator<XTerm> edgePredicates = edge.getPredicate().iterator();
        if (edgePredicates.hasNext())
        {
            XTerm xPredicate = edgePredicates.next();
            serializedEdge.put("Predicate", xPredicate.getJdexId());
        }

        final Iterator<XNode> edgeSubjects = edge.getSubject().iterator();
        final XNode xSubject = edgeSubjects.next();
        serializedEdge.put("Subject", xSubject.getJdexId());

        final Iterator<XNode> edgeObjects = edge.getObject().iterator();
        final XNode xObject = edgeObjects.next();
        serializedEdge.put("Object", xObject.getJdexId());
    }

    /**************************************************************************
    * Serializes the edges.
    * 
    * @param network         The network.
    * @param serializedEdges The JSON-serialized edges.
    **************************************************************************/
    public static void serializeEdges(XNetwork network, ArrayNode serializedEdges)
    {
        Iterable<XEdge> networkEdges = network.getNdexEdges();
        for (XEdge networkEdge : networkEdges)
        {
            ObjectNode serializedEdge = OBJECT_MAPPER.createObjectNode();
            serializeEdge(networkEdge, serializedEdge);
            serializedEdges.add(serializedEdge);
        }
    }

    /**************************************************************************
    * Serializes the namespaces.
    * 
    * @param network              The network.
    * @param serializedNamespaces The JSON-serialized namespaces.
    **************************************************************************/
    public static void serializeNamespaces(XNetwork network, ObjectNode serializedNamespaces)
    {
        Iterable<XNameSpace> xNameSpaces = network.getNamespaces();
        for (XNameSpace xNameSpace : xNameSpaces)
        {
            ObjectNode serializedNamespace = OBJECT_MAPPER.createObjectNode();
            serializedNamespace.put("Prefix", xNameSpace.getPrefix());
            serializedNamespace.put("Id", RidConverter.convertToJid((ORID)xNameSpace.asVertex().getId()));
            serializedNamespace.put("Uri", xNameSpace.getUri());
            
            serializedNamespaces.put(xNameSpace.getJdexId(), serializedNamespace);
        }
    }

    /**************************************************************************
    * Serializes a node.
    * 
    * @param node           The node.
    * @param serializedNode The JSON-serialized node.
    **************************************************************************/
    public static void serializeNode(XNode node, ObjectNode serializedNode)
    {
        serializedNode.put("Name", node.getName());
        serializedNode.put("Id", RidConverter.convertToJid((ORID)node.asVertex().getId()));

        final Iterator<XTerm> nodeTerms = node.getRepresents().iterator();
        if (nodeTerms.hasNext())
        {
            XTerm nodeTerm = nodeTerms.next();
            serializedNode.put("Represents", nodeTerm.getJdexId());
        }
    }

    /**************************************************************************
    * Serializes the nodes.
    * 
    * @param nodes           The nodes.
    * @param serializedNodes The JSON-serialized nodes.
    **************************************************************************/
    public static void serializeNodes(Iterable<XNode> nodes, ObjectNode serializedNodes)
    {
        for (XNode node : nodes)
        {
            ObjectNode serializedNode = OBJECT_MAPPER.createObjectNode();
            serializeNode(node, serializedNode);
            serializedNodes.put(node.getJdexId(), serializedNode);
        }
    }

    /**************************************************************************
    * Serializes the terms.
    * 
    * @param terms           The terms.
    * @param serializedTerms The JSON-serialized terms.
    **************************************************************************/
    public static void serializeTerms(Iterable<XTerm> terms, ObjectNode serializedTerms)
    {
        for (XTerm term : terms)
        {
            final ObjectNode serializedTerm = OBJECT_MAPPER.createObjectNode();
            serializedTerms.put(term.getJdexId(), serializedTerm);

            serializedTerm.put("Name", term.getName());
            serializedTerm.put("Id", RidConverter.convertToJid((ORID)term.asVertex().getId()));
        }
    }
}
