package org.ndexbio.security;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;

import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.task.Configuration;


@Deprecated
public class DelegatedLDAPAuthenticator extends LDAPAuthenticator {

	public static final String AD_DELEGATED_ACCOUNT="AD_DELEGATED_ACCOUNT";
	private static final String AD_DELEGATED_ACCOUNT_PASSWORD="AD_DELEGATED_ACCOUNT_PASSWORD";
//	private static final String AD_DELEGATED_ACCOUNT_USER_NAME_PATTERN="AD_DELEGATED_ACCOUNT_USER_NAME_PATTERN";
	
	private String delegatedUserName ;
	private String delegatedUserPassword;
//	private String delegatedUserNamePattern;
	
	public DelegatedLDAPAuthenticator(Configuration config)
			throws NdexException {
		super(config);

		delegatedUserName = config.getRequiredProperty(AD_DELEGATED_ACCOUNT);
		delegatedUserPassword = config.getRequiredProperty(AD_DELEGATED_ACCOUNT_PASSWORD);
//		delegatedUserNamePattern = config.getRequiredProperty(AD_DELEGATED_ACCOUNT_USER_NAME_PATTERN);
	}

	
	private String getFullyQualifiedNameByUserId(String userId) throws UnauthorizedOperationException {
		
		  env.put(Context.SECURITY_PRINCIPAL, ctxPrinciplePattern.replaceAll(userNamePattern, delegatedUserName));	
	      env.put(Context.SECURITY_CREDENTIALS, delegatedUserPassword);
	      try {
	    	  LdapContext ctx = new InitialLdapContext(env,null);
		
	    	  String searchFilter = searchFilterPattern.replaceAll(userNamePattern, userId);

	    	  SearchControls searchControls = new SearchControls();
	    	  searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
	    	  NamingEnumeration<SearchResult> results = ctx.search(ldapSearchBase,
	    			  searchFilter, searchControls);

	    	  SearchResult searchResult = null;
	    	  if (results.hasMoreElements()) {
	    		  searchResult = results.nextElement();
	    		  return searchResult.getName();
	    	  }
	    	  return null;
	      } catch (NamingException e) {
	    	  throw new UnauthorizedOperationException(e.getMessage());
	      }
	}
	
	public boolean authenticateUser(String username, String password) throws UnauthorizedOperationException {
		
		String userFullName = getFullyQualifiedNameByUserId(username);
//		if ( !useCache) {
			return userIsInNdexGroup(userFullName,password).booleanValue();
//		}
		
	}
}
