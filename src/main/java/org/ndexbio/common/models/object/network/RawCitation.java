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

import java.util.List;

import org.ndexbio.model.exceptions.NdexException;

@Deprecated
public class RawCitation implements Comparable <RawCitation>{
	
	private String title;
	private List<String> contributors;
	private String idType;   // pubmed or DOI etc...
	private String identifier;
	
	public RawCitation (String title, String idType, String identifier, List<String> contributors) throws NdexException {
		this.title = title;
		this.setContributors(contributors);
		this.setIdType(idType);
		this.setIdentifier(identifier);
		if ( title == null && identifier == null)
			throw new NdexException ("Invalid Citation object: title and identifier are both null.");
	}
	
	 

	@Override
	public int compareTo(RawCitation o) {
        if ( identifier == null ) {
        	if (o.getIdentifier() != null)
        		return -1;
        } else {
            if (o.getIdentifier() == null) return 1;
        
            int c = identifier.compareTo(o.getIdentifier());
            if (c !=0) return c;
        }
        
        if ( idType == null ) {
        	 if ( o.getIdType() != null)
        		return -1;
        } else {
            if ( o.getIdType() == null) return 1;
        
	   	    int c =idType.compareTo(o.getIdType());
		    if ( c != 0 ) return c;
		    if ( identifier != null )
		    	return 0;
        }

        if (title == null) {
        	if ( o.getTitle() != null) return -1;
        } else {
        	if ( o.getTitle() == null) return 1;
    		int c = title.compareTo(o.getTitle());
    		if ( c!=0) return c;
        }

		return 0;
	}


	@Override
	public int hashCode() {
		if (identifier !=null)
			return this.identifier.hashCode();
		  return title.hashCode();
		
	}
	
	@Override
	public boolean equals (Object o) {
		if (o instanceof RawCitation)
			return compareTo((RawCitation)o) == 0;
		return false;
	}

	public String getTitle() {
		return title;
	}


/*
	public void setTitle(String title) {
		this.title = title;
	}

*/

	public List<String> getContributors() {
		return contributors;
	}



	public void setContributors(List<String> contributors) {
		this.contributors = contributors;
	}



	public String getIdType() {
		return idType;
	}



	public void setIdType(String idType) {
		this.idType = idType;
	}



	public String getIdentifier() {
		return identifier;
	}



	public void setIdentifier(String identifier) {
		this.identifier = identifier;
	}

}
