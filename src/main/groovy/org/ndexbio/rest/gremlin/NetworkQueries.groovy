package org.ndexbio.rest.gremlin

import com.orientechnologies.orient.core.db.record.OIdentifiable
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import com.tinkerpop.blueprints.Direction
import com.tinkerpop.blueprints.Vertex
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph
import com.tinkerpop.blueprints.impls.orient.OrientVertex
import com.tinkerpop.gremlin.groovy.Gremlin

class NetworkQueries {
    static {
        Gremlin.load();
    }

    static NetworkQueries INSTANCE = new NetworkQueries();

    def Set<OrientVertex> searchNeighborhoodByTerm(OrientBaseGraph g, OrientVertex network, SearchSpec searchSpec) {
        def nodes = getRepresentedVertices(g, network, searchSpec.representationCriterion, searchSpec.startingTerms, searchSpec.startingTermStrings);

        def foundInEdges = new HashSet<OrientVertex>();
        def foundOutEdges = new HashSet<OrientVertex>();


        switch (searchSpec.searchType) {
            case SearchType.DOWNSTREAM:
                nodes.as("e").out("s").except(foundInEdges).store(foundInEdges).out("o").loop("e", { it.loops < searchSpec.searchDepth }).iterate();
                break;
            case SearchType.UPSTREAM:
                nodes.as("e").in("o").except(foundOutEdges).store(foundOutEdges).in("s").loop("e", { it.loops < searchSpec.searchDepth }).iterate();
                break;
            case SearchType.BOTH:
                nodes.copySplit(
                        _().as("e1").out("s").except(foundInEdges).store(foundInEdges).out("o").loop("e1", { it.loops < searchSpec.searchDepth }),
                        _().as("e2").in("o").except(foundOutEdges).store(foundOutEdges).in("s").loop("e2", { it.loops < searchSpec.searchDepth })).exhaustMerge().iterate();
                break;
        }

        if (foundInEdges.isEmpty())
            return foundOutEdges;
        if (foundOutEdges.isEmpty())
            return foundInEdges;

        foundInEdges.addAll(foundOutEdges);

        return foundInEdges;
    }

    def filterByPredicates(OrientBaseGraph g, SearchSpec searchSpec) {
        def edges = g.v(searchSpec.includedPredicates).in("p");
        def foundInEdges = new HashSet<OrientVertex>();
        def foundOutEdges = new HashSet<OrientVertex>();

        def excludedPredicates;
        if (searchSpec.excludedPredicates != null)
            excludedPredicates = new HashSet<OIdentifiable>(Arrays.asList(searchSpec.excludedPredicates));
        else
            excludedPredicates = new HashSet<OIdentifiable>();

        switch (searchSpec.searchType) {
            case SearchType.DOWNSTREAM:
                edges.as("e").filter { edgePredicateFilter(it, excludedPredicates) }.except(foundInEdges).store(foundInEdges).out("o").out("s").loop("e", { it.loops < searchSpec.searchDepth }).iterate();
                break;
            case SearchType.UPSTREAM:
                edges.as("e").filter { edgePredicateFilter(it, excludedPredicates) }.except(foundOutEdges).store(foundOutEdges).in("s").in("o").loop("e", { it.loops < searchSpec.searchDepth }).iterate();
                break;
            case SearchType.BOTH:
                edges.copySplit(
                        _().as("e1").filter { edgePredicateFilter(it, excludedPredicates) }.except(foundInEdges).store(foundInEdges).out("o").out("s").loop("e1", { it.loops < searchSpec.searchDepth }),
                        _().as("e2").filter { edgePredicateFilter(it, excludedPredicates) }.except(foundOutEdges).store(foundOutEdges).in("s").in("o").loop("e2", { it.loops < searchSpec.searchDepth })
                ).exhaustMerge().iterate();
                break;

        }

        if (foundInEdges.isEmpty())
            return foundOutEdges;
        if (foundOutEdges.isEmpty())
            return foundInEdges;

        foundInEdges.addAll(foundOutEdges);

        return foundInEdges;
    }

    def getRepresentedVertices(OrientBaseGraph g, OrientVertex network, RepresentationCriteria representationCriterion, OIdentifiable[] startingTerms, String[] startingTermStrings) {
        def nodes;
        switch (representationCriterion) {
            case RepresentationCriteria.STRICT:
                def terms = g.v(startingTerms);
                nodes = terms.filter {
                    termNetworkFilter(it, network)
                }.in("represents");
                break;

            case RepresentationCriteria.FUNCTIONAL:
                def allTerms = [];
                def termFunctions = [];

                for (termString in startingTermStrings)
                    allTerms += g.getRawGraph().query(new OSQLSynchQuery<ODocument>("select from xTerm where name = ?"), termString);

                for (term in startingTerms)
                    termFunctions += g.getRawGraph().query(new OSQLSynchQuery<ODocument>("select from xFunctionTerm where linkParameters containsvalue " + term.identity));


                def functions = new HashSet<OIdentifiable>();
                functions.addAll(allTerms);
                functions.addAll(termFunctions);

                nodes = g.v(functions.toArray()).filter {
                    termNetworkFilter(it, network)
                }.in("represents");

                break;

            case RepresentationCriteria.PERMISSIVE:
                def allTerms = [];
                def termFunctions = [];

                for (termString in startingTermStrings)
                    allTerms += g.getRawGraph().query(new OSQLSynchQuery<ODocument>("select from xTerm where name = ?"), termString);

                for (term in startingTerms)
                    termFunctions += g.getRawGraph().query(new OSQLSynchQuery<ODocument>("select from xFunctionTerm where linkParameters containsvalue " + term.identity));

                def functions = new HashSet<OIdentifiable>();
                functions.addAll(allTerms);
                functions.addAll(termFunctions);

                def Set<OIdentifiable> newFunctions = functions;
                def Set<OIdentifiable> addedFunctions = new HashSet<>();

                while (true) {
                    for (function in newFunctions)
                        addedFunctions += g.getRawGraph().query(new OSQLSynchQuery<ODocument>("select from xFunctionTerm where linkParameters containsvalue " + function.identity));

                    def int functionsPrevSize = functions.size();
                    functions.addAll(addedFunctions);

                    if (functionsPrevSize == functions.size())
                        break;

                    newFunctions = addedFunctions;
                    addedFunctions.clear();
                }

                nodes = g.v(functions.toArray()).filter
                {
                    termNetworkFilter(it, network)

                }.in("represents");
                break;
            default:
                throw new IllegalArgumentException("Unknown value of representation criterion.");
        }

        return nodes;
    }

    private static boolean termNetworkFilter(Vertex term, Vertex network) {
        def Iterable termNetwork = term.getVertices(Direction.IN, "terms");
        def Iterator termNetworkIterator = termNetwork.iterator();
        termNetworkIterator.next().equals(network)
    }

    private static boolean edgePredicateFilter(Vertex edge, Set<OIdentifiable> excludedPredicates) {
        def Iterable predicate = edge.getVertices(Direction.OUT, "p");
        def Iterator predicateIterator = predicate.iterator();
        !excludedPredicates.contains(predicateIterator.next())
    }

}
