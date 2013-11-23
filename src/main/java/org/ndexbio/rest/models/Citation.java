package org.ndexbio.rest.models;

import java.util.ArrayList;
import java.util.List;

import org.ndexbio.rest.domain.ICitation;
import org.ndexbio.rest.domain.IEdge;

public class Citation extends NdexObject
{
    private List<String> _contributors;
    private String _identifier;
    private String _jdexId;
    private String _title;
    private String _type;
    private List<String> _edges;  // edge ids   

    
    /**************************************************************************
    * Default constructor.
    **************************************************************************/
    public Citation()
    {
        super();
        
        _contributors = new ArrayList<String>();
    }
    
    /**************************************************************************
    * Populates the class (from the database) and removes circular references.
    * 
    * @param citation The Citation with source data.
    **************************************************************************/
    public Citation(ICitation citation)
    {
        super(citation);
        
        _contributors = new ArrayList<String>();
        _identifier = citation.getIdentifier();
        _jdexId = citation.getJdexId();
        _title = citation.getTitle();
        _type = citation.getType();     
        _contributors = citation.getContributors();
        
        for (IEdge iEdge : citation.getNdexEdges()){
        	_edges.add(iEdge.getJdexId());
        }
    }
    
    public List<String> getContributors()
    {
        return _contributors;
    }
    
    public void setContributors(List<String> contributors)
    {
        _contributors = contributors;
    }
    
    public String getIdentifier()
    {
        return _identifier;
    }
    
    public void setIdentifier(String identifier)
    {
        _jdexId = identifier;
    }
    
    public String getJdexId()
    {
        return _jdexId;
    }
    
    public void setJdexId(String jdexId)
    {
        _jdexId = jdexId;
    }
    
    public String getTitle()
    {
        return _title;
    }
    
    public void setTitle(String title)
    {
        _title = title;
    }
    
    public String getType()
    {
        return _type;
    }
    
    public void setType(String type)
    {
        _type = type;
    }

	public List<String> getEdges() {
		return _edges;
	}

	public void setEdges(List<String> _edges) {
		this._edges = _edges;
	}
    
    
}
