package org.ndexbio.rest.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.ndexbio.rest.domain.IBaseTerm;
import org.ndexbio.rest.domain.ICitation;
import org.ndexbio.rest.domain.IEdge;
import org.ndexbio.rest.domain.IFunctionTerm;
import org.ndexbio.rest.domain.INamespace;
import org.ndexbio.rest.domain.INetwork;
import org.ndexbio.rest.domain.INode;
import org.ndexbio.rest.domain.IRequest;
import org.ndexbio.rest.domain.ISupport;
import org.ndexbio.rest.domain.ITerm;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public class Network extends NdexObject
{
    private Map<String, Citation> _citations;
    private String _copyright;
    private String _description;
    private int _edgeCount;
    private Map<String, Edge> _edges;
    private String _format;
    private Map<String, Namespace> _namespaces;
    private int _nodeCount;
    private Map<String, Node> _nodes;
    private List<Request> _requests;
    private String _source;
    private Map<String, Support> _supports;
    private Map<String, Term> _terms;
    private String _title;
    private String _version;



    /**************************************************************************
    * Default constructor.
    **************************************************************************/
    public Network()
    {
        super();
        
        _edgeCount = 0;
        _nodeCount = 0;
        initMaps();
    }

    /**************************************************************************
    * Populates the class (from the database) and removes circular references.
    * Doesn't load Edges, Nodes, or Terms.
    * 
    * @param network The Network with source data.
    **************************************************************************/
    public Network(INetwork network)
    {
        this(network, false);
    }

    /**************************************************************************
    * Populates the class (from the database) and removes circular references.
    * 
    * @param network        The Network with source data.
    * @param loadEverything True to load Edges, Nodes, and Terms, false to
    *                       exclude them.
    **************************************************************************/
    public Network(INetwork network, boolean loadEverything)
    {
        super(network);

        _copyright = network.getCopyright();
        _description = network.getDescription();
        _edgeCount = network.getNdexEdgeCount();
        _format = network.getFormat();
        _nodeCount = network.getNdexNodeCount();
        _source = network.getSource();
        _title = network.getTitle();
        _version = network.getVersion();
        this.initMaps();
        
        for (IRequest request : network.getRequests())
            _requests.add(new Request(request));

        if (loadEverything)
        {
            for (IEdge edge : network.getNdexEdges())
                _edges.put(edge.getJdexId(), new Edge(edge));

            for (INode node : network.getNdexNodes())
                _nodes.put(node.getJdexId(), new Node(node));

            for (ITerm term : network.getTerms())
            {
                if (term instanceof IBaseTerm)
                    _terms.put(term.getJdexId(), new BaseTerm((IBaseTerm)term));
                else if (term instanceof IFunctionTerm)
                    _terms.put(term.getJdexId(), new FunctionTerm((IFunctionTerm)term));
            }

            for (ICitation citation : network.getCitations())
                _citations.put(citation.getJdexId(), new Citation(citation));

            for (INamespace namespace : network.getNamespaces())
                _namespaces.put(namespace.getJdexId(), new Namespace(namespace));

            for (ISupport support : network.getSupports())
                _supports.put(support.getJdexId(), new Support(support));
        }
    }

    

    public Map<String, Citation> getCitations()
    {
        return _citations;
    }

    public void setCitations(Map<String, Citation> citations)
    {
        this._citations = citations;
    }

    public String getCopyright()
    {
        return _copyright;
    }
    
    public void setCopyright(String copyright)
    {
        _copyright = copyright;
    }

    public String getDescription()
    {
        return _description;
    }
    
    public void setDescription(String description)
    {
        _description = description;
    }
    
    public int getEdgeCount()
    {
        return _edgeCount;
    }

    public void setEdgeCount(int edgeCount)
    {
        _edgeCount = edgeCount;
    }

    public Map<String, Edge> getEdges()
    {
        return _edges;
    }

    public void setEdges(Map<String, Edge> edges)
    {
        this._edges = edges;
    }

    public String getFormat()
    {
        return _format;
    }

    public void setFormat(String format)
    {
        this._format = format;
    }

    public Map<String, Namespace> getNamespaces()
    {
        return _namespaces;
    }

    public void setNamespaces(Map<String, Namespace> namespaces)
    {
        this._namespaces = namespaces;
    }

    public int getNodeCount()
    {
        return _nodeCount;
    }

    public void setNodeCount(int nodeCount)
    {
        _nodeCount = nodeCount;
    }

    public Map<String, Node> getNodes()
    {
        return _nodes;
    }

    public void setNodes(Map<String, Node> nodes)
    {
        this._nodes = nodes;
    }
    
    public List<Request> getRequests()
    {
        return _requests;
    }
    
    public void setRequests(List<Request> requests)
    {
        _requests = requests;
    }

    public String getSource()
    {
        return _source;
    }
    
    public void setSource(String source)
    {
        _source = source;
    }

    public Map<String, Support> getSupports()
    {
        return _supports;
    }

    public void setSupports(Map<String, Support> supports)
    {
        this._supports = supports;
    }

    public Map<String, Term> getTerms()
    {
        return _terms;
    }

    public void setTerms(Map<String, Term> terms)
    {
        this._terms = terms;
    }
    
    public String getTitle()
    {
        return _title;
    }
    
    public void setTitle(String title)
    {
        _title = title;
    }

    public String getVersion()
    {
        return _version;
    }
    
    public void setVersion(String version)
    {
        _version = version;
    }

    

    /**************************************************************************
    * Initializes the maps. 
    **************************************************************************/
    private void initMaps()
    {
        _citations = new HashMap<String, Citation>();
        _edges = new HashMap<String, Edge>();
        _namespaces = new HashMap<String, Namespace>();
        _nodes = new HashMap<String, Node>();
        _requests = new ArrayList<Request>();
        _supports = new HashMap<String, Support>();
        _terms = new HashMap<String, Term>();
    }
}
