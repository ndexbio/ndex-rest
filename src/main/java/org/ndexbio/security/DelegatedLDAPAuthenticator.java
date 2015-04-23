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
