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
package org.ndexbio.common.models.dao.postgresql;

import org.ndexbio.common.NdexClasses;
import org.ndexbio.model.object.network.Namespace;

import com.orientechnologies.orient.core.record.impl.ODocument;

public class NamespaceIterator extends NetworkElementIterator<Namespace> {

	public NamespaceIterator(Iterable<ODocument> nsDocs) {
		super(nsDocs);
	}

	@Override
	public Namespace next() {
		ODocument doc = this.docs.next();
		if ( doc == null ) return null;
		
		return getNamespace(doc);
	}

    private  Namespace getNamespace(ODocument ns)  {
        Namespace rns = new Namespace();
        rns.setId((long)ns.field(NdexClasses.Element_ID));
        rns.setPrefix((String)ns.field(NdexClasses.ns_P_prefix));
        rns.setUri((String)ns.field(NdexClasses.ns_P_uri));
        
        SingleNetworkDAO.getPropertiesFromDoc(ns, rns);
        
        return rns;
     } 
}
