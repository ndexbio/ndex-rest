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

@Deprecated
public class RawEdge implements Comparable<RawEdge> {

	private long subjectId;
	private long predicateId;
	private long objectId;
	
	public RawEdge( long subject, long predicate, long object) {
		this.subjectId 		= subject;
		this.predicateId 	= predicate;
		this.objectId 		= object;
	}
	
	@Override
	public int compareTo(RawEdge o) {
		long c = subjectId - o.getSubjectId();
		if (c>0) return 1;
		if ( c<0 ) return -1;
		
		c = objectId - o.getObjectId();
		if (c>0) return 1;
		if ( c<0 ) return -1;

		c = predicateId - o.getPredicateId();
		if (c>0) return 1;
		if ( c<0 ) return -1;
		
		return 0;
	}

	public long getSubjectId() {
		return subjectId;
	}


	public long getPredicateId() {
		return predicateId;
	}

	public long getObjectId() {
		return objectId;
	}

	
	@Override
	public int hashCode() {
	    final int prime = 31;
	    int result = 1;
	    result = prime * result + (int) (subjectId ^ (subjectId >>> 32));
	    result = prime * result + (int) (predicateId ^ (predicateId >>> 32));
	    result = prime * result + (int) (objectId ^ (objectId >>> 32));
	    return result;
	}
	
	@Override
	public	boolean equals (Object obj) {
		if ( obj instanceof RawEdge) {
			return compareTo((RawEdge)obj) == 0;
		}
		return false;
	}
}
