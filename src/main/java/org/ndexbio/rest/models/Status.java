package org.ndexbio.rest.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

//TODO: Remove this class, it's unnecessary
//TODO: Refactor KnockoutJS bindings to not use this class
@JsonIgnoreProperties(ignoreUnknown = true)
public class Status extends NdexObject
{
    private String _state;



    /**************************************************************************
    * Default constructor.
    **************************************************************************/
    public Status()
    {
        super();
    }



	public String getState() {
		return _state;
	}



	public void setState(String _state) {
		this._state = _state;
	}
    
    

}
