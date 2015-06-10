/**
 *   Copyright (c) 2013, 2015
 *  	The Regents of the University of California
 *  	The Cytoscape Consortium
 *
 *   Permission to use, copy, modify, and distribute this software for any
 *   purpose with or without fee is hereby granted, provided that the above
 *   copyright notice and this permission notice appear in all copies.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 *   WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 *   MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 *   ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 *   WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 *   ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 *   OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */
package org.ndexbio.security;

import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.task.Configuration;

public class DelegatedLDAPAuthenticator extends LDAPAuthenticator {

	private static final String AD_DELEGATED_ACCOUNT="AD_DELEGATED_ACCOUNT";
	private static final String AD_DELEGATED_ACCOUNT_PASSWORD="AD_DELEGATED_ACCOUNT_PASSWORD";
	private static final String AD_DELEGATED_ACCOUNT_USER_NAME_PATTERN="AD_DELEGATED_ACCOUNT_USER_NAME_PATTERN";
	
	private String delegatedUserName ;
	private String delegatedUserPassword;
	private String delegatedUserNamePattern;
	
	public DelegatedLDAPAuthenticator(Configuration config)
			throws NdexException {
		super(config);

		delegatedUserName = config.getRequiredProperty(AD_DELEGATED_ACCOUNT);
		delegatedUserPassword = config.getRequiredProperty(AD_DELEGATED_ACCOUNT_PASSWORD);
		delegatedUserNamePattern = config.getRequiredProperty(AD_DELEGATED_ACCOUNT_USER_NAME_PATTERN);
	}
	
	

}
