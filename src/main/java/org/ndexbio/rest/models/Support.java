package org.ndexbio.rest.models;

import java.util.ArrayList;
import java.util.List;

import org.ndexbio.rest.domain.IEdge;
import org.ndexbio.rest.domain.ISupport;

public class Support extends NdexObject
{
    private String _jdexId;
    private String _text;
    private List<String> _edges;  // edge ids
    private Citation _citation;
    
    
    
    /**************************************************************************
    * Default constructor.
    **************************************************************************/
    public Support()
    {
        super();
    }
    
    /**************************************************************************
    * Populates the class (from the database) and removes circular references.
    * 
    * @param support The Support with source data.
    **************************************************************************/
    public Support(ISupport support)
    {
        super(support);
        
        _jdexId = support.getJdexId();
        _text = support.getText();
        _citation = new Citation(support.getCitation());
        _edges = new ArrayList<String>();
        for (IEdge iEdge : support.getNdexEdges()){
        	_edges.add(iEdge.getJdexId());
        }
    }
       
    public String getJdexId()
    {
        return _jdexId;
    }
    
    public void setJdexId(String jdexId)
    {
        _jdexId = jdexId;
    }
    
    public String getText()
    {
        return _text;
    }
    
    public void setText(String text)
    {
        _text = text;
    }

	public List<String> getEdges() {
		return _edges;
	}

	public void set_networks(List<String> edges) {
		_edges = edges;
	}

	public Citation getCitation() {
		return _citation;
	}

	public void setCitation(Citation citation) {
		_citation = citation;
	}
    
    
}
