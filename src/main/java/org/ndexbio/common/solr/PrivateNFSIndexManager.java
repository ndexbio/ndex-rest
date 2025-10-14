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
package org.ndexbio.common.solr;

import java.util.Collection;
import org.apache.solr.common.SolrInputDocument;
import org.ndexbio.model.object.NdexFolder;
import org.ndexbio.model.object.NdexShortcut;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.object.network.VisibilityType;

public class PrivateNFSIndexManager extends PublicNFSIndexManager {


	public static final String CORE_NAME = 
			"private-nfs" ; 

	protected static final String USER_READ_FIELD = "userRead";
	protected static final String USER_EDIT_FIELD = "userEdit";
	protected static final String OWNER_FIELD = "owner";
	
	public PrivateNFSIndexManager(SolrClientWrapper client) {
		super(client);
	}
	
	@Override
	public void createIndexForDocument(NetworkSummary summary, String ownerUserName, Collection<String> userReads,Collection<String> userEdits,
			Collection<String> grpReads, Collection<String> grpEdits) {
		
		if (VisibilityType.PUBLIC.equals(summary.getVisibility())) {
			return;
		}
		SolrInputDocument doc = super.getIndexForDocument(summary, ownerUserName, userReads, userEdits, grpReads, grpEdits);
		
		// @TODO add private information to document for indexing
		if (doc == null) {
			return;
		}
		
		if (ownerUserName != null && !ownerUserName.isBlank()) {
			doc.addField(OWNER_FIELD, ownerUserName);
		}

		addPermissionCollection(doc, USER_READ_FIELD, userReads);
		addPermissionCollection(doc, USER_EDIT_FIELD, userEdits);
		
		super.commitDocument(doc);
	}

	@Override
	public void createIndexForDocument(NdexShortcut shortcut) {
		SolrInputDocument doc = super.getIndexForDocument(shortcut);
		
		// @TODO add private information to document for indexing
		if (shortcut.getOwner() != null && !shortcut.getOwner().isBlank()) {
			doc.addField(OWNER_FIELD, shortcut.getOwner());
		}
		doc.addField(VISIBILITY, VisibilityType.PRIVATE.toString());
		
		super.commitDocument(doc);
	}

	@Override
	public void createIndexForDocument(NdexFolder folder) {
		SolrInputDocument doc = super.getIndexForDocument(folder);
		
		// @TODO add private information to document for indexing
		if (folder.getOwner() != null && !folder.getOwner().isBlank()) {
			doc.addField(OWNER_FIELD, folder.getOwner());
		}
		doc.addField(VISIBILITY, VisibilityType.PRIVATE.toString());
		super.commitDocument(doc);
	}
	
	private static void addPermissionCollection(SolrInputDocument doc, String field, Collection<String> values) {
		if (values == null) {
			return;
		}
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				doc.addField(field, value);
			}
		}
	}
	
}
