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
package org.ndexbio.security;

import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.rest.Configuration;


@Deprecated
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
