/**
 * Copyright (c) 2013, 2016, The Regents of the University of California, The Cytoscape Consortium
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */
package org.ndexbio.common.models.object.network;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


@JsonIgnoreProperties(ignoreUnknown = true)
@Deprecated
public class RawNamespace implements Comparable<RawNamespace> 
{
    private String _prefix;
    private String _uri;
    
    /**************************************************************************
    * Populates the class (from the database) and removes circular references.
    * 
    * @param namespace The Namespace with source data.
    **************************************************************************/
    public RawNamespace (/*UUID networkID, */String prefix, String URI)
    {
        
        this._prefix = prefix;
        this._uri = URI;
     //   this.setNetworkID(networkID);
    }
    
    public String getPrefix()
    {
        return _prefix;
    }
    
    public String getURI()
    {
        return this._uri;
    }
    
	@Override
	public int compareTo(RawNamespace arg0) {
	
		if ( _uri == null ) {
			if (arg0.getURI() == null) {
				return _prefix.compareTo(arg0.getPrefix());
			}
			return -1;
		}
		if ( arg0.getURI() == null) return 1;
		
		int c = _uri.compareTo(arg0.getURI());
		if ( c != 0)
			return c;
		
		if ( this._prefix == null ) {
			if ( arg0.getPrefix() == null )
				return 0;
			
			return -1;
		}
		
		if ( arg0.getPrefix() == null )
				return 1;
		
		
		String str2 = arg0.getPrefix();
		c = _prefix.compareTo ( str2);
		return c;
	}
    
    @Override
	public int hashCode() {
    	if (_uri !=null)
    		return this._uri.hashCode();
    	return _prefix.hashCode();
    }
    
    @Override
	public boolean equals ( Object arg0) {
    	if (arg0 instanceof RawNamespace)
    		return compareTo((RawNamespace)arg0)==0;
    	return false;
    }

}
