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

import java.util.AbstractMap;
import java.util.Hashtable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;

import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.NewUser;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.helpers.Security;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

public class LDAPAuthenticator {
	
	static Logger logger = Logger.getLogger(LDAPAuthenticator.class.getName());
	
	private final static int CACHE_SIZE = 100;
	
	private final static String PROP_LDAP_URL = "PROP_LDAP_URL";
	private final static String AD_SEARCH_BASE= "AD_SEARCH_BASE";
	private final static String AD_NDEX_GROUP_NAME="AD_NDEX";
	private final static String AD_AUTH_USE_CACHE="AD_AUTH_USE_CACHE";
	private final static String JAVA_KEYSTORE="KEYSTORE_PATH";
	private final static String AD_USE_SSL="AD_USE_SSL";
	private final static String AD_TRACE_MODE="AD_TRACE_MODE";
	private final static String JAVA_KEYSTORE_PASSWD= "JAVA_KEYSTORE_PASSWD";
    private final static String AD_CTX_PRINCIPLE = "AD_CTX_PRINCIPLE"; 
    private final static String AD_CTX_PRINCIPLE2 = "AD_CTX_PRINCIPLE2";
    protected final static String AD_SEARCH_FILTER = "AD_SEARCH_FILTER";
    protected final static String userNamePattern = "%%USER_NAME%%";
    
	private String ldapAdServer;
	protected String ldapSearchBase;
	private String ldapNDExGroup;
	protected Hashtable <String,Object> env ;
	private Pattern pattern ;
	private boolean useCache = false;
	protected String ctxPrinciplePattern ;
	protected String ctxPrinciplePattern2 ;
	protected String searchFilterPattern ;
	
	
	public static final String AD_DELEGATED_ACCOUNT="AD_DELEGATED_ACCOUNT";
	private static final String AD_DELEGATED_ACCOUNT_PASSWORD="AD_DELEGATED_ACCOUNT_PASSWORD";
//	private static final String AD_DELEGATED_ACCOUNT_USER_NAME_PATTERN="AD_DELEGATED_ACCOUNT_USER_NAME_PATTERN";
	
	private String delegatedUserName ;
	private String delegatedUserPassword;

	
	//key is the combination of 
	protected LoadingCache<java.util.Map.Entry<String,String>, Boolean>  userCredentials;
	
	public LDAPAuthenticator (Configuration config) throws NdexException {
		
		ldapAdServer = config.getRequiredProperty(PROP_LDAP_URL);
		
		ldapSearchBase=config.getRequiredProperty(AD_SEARCH_BASE);
		
		ldapNDExGroup = config.getProperty(AD_NDEX_GROUP_NAME);
       
		ctxPrinciplePattern = config.getRequiredProperty(AD_CTX_PRINCIPLE);

		if ( ctxPrinciplePattern.indexOf(userNamePattern) == -1) 
			throw new NdexException ("Pattern "+ userNamePattern + " not found in configuration property "
					+ AD_CTX_PRINCIPLE + ".");
	    
		searchFilterPattern = config.getRequiredProperty(AD_SEARCH_FILTER);

		ctxPrinciplePattern2 = config.getProperty(AD_CTX_PRINCIPLE2);

		if ( ctxPrinciplePattern2 != null && ctxPrinciplePattern2.indexOf(userNamePattern) == -1)
			throw new NdexException ("Pattern "+ userNamePattern + " not found in configuration property "
					 + AD_CTX_PRINCIPLE2 + ".");

		if ( searchFilterPattern.indexOf(userNamePattern) == -1) 
			throw new NdexException ("Pattern "+ userNamePattern + " not found in configuration property "
					+ AD_SEARCH_FILTER + ".");

		delegatedUserName = config.getProperty(AD_DELEGATED_ACCOUNT);
		if ( delegatedUserName !=null)
  		   delegatedUserPassword = config.getRequiredProperty(AD_DELEGATED_ACCOUNT_PASSWORD);

        env = new Hashtable<>();

		env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, ldapAdServer);
       
       env.put("java.naming.ldap.attributes.binary", "objectSID");
       
	   String valueStr = config.getProperty(AD_TRACE_MODE);
       if ( valueStr != null && Boolean.parseBoolean(valueStr)) {
    //       env.put("com.sun.jndi.ldap.trace.ber", System.err);
       }
       
	   pattern = Pattern.compile("^CN=(.*?[^\\\\]),");

	   String useCacheStr = config.getProperty(AD_AUTH_USE_CACHE);
       if (useCacheStr != null && Boolean.parseBoolean(useCacheStr)) {
       	 useCache = true;
       	 logger.info("Server AD Authentication cache turned on.");
       	 userCredentials = CacheBuilder
				.newBuilder().maximumSize(CACHE_SIZE)
				.expireAfterAccess(10L, TimeUnit.MINUTES)
				.build(new CacheLoader<java.util.Map.Entry<String,String>, Boolean>() {
				   @Override
				   public Boolean load(java.util.Map.Entry<String,String> entry) throws NdexException, ExecutionException {
					   String userName = entry.getKey();
					   String pswd = entry.getValue();
					   Boolean userIsInGrp = userIsInNdexGroup(userName, pswd);
                   	   if ( userIsInGrp.booleanValue() ) {
                   		   userCredentials.put(new AbstractMap.SimpleImmutableEntry<>(userName, pswd), Boolean.TRUE);	
                   		   return Boolean.TRUE;
                   	   }
                   	   throw new UnauthorizedOperationException("User " + userName + " is not in the required group." );
				   }
			    });
       } else {
    	   useCache = false;
       }
       
	   String configValue = config.getProperty(AD_USE_SSL);
       if (configValue != null && Boolean.parseBoolean(configValue)){
    	   String keystore = config.getProperty(JAVA_KEYSTORE);
    	   if ( keystore == null)
    		   throw new NdexException("Requried property " + JAVA_KEYSTORE + " is not defined in ndex.properties file.");
    	   System.setProperty("javax.net.ssl.trustStore", keystore);
        
    	   String keystorePasswd = config.getProperty(JAVA_KEYSTORE_PASSWD);
    //	   if (keystorePasswd ==null )
    //		   throw new NdexException ("Requried property " + JAVA_KEYSTORE_PASSWD + " is not defined in ndex.properties file.");
    	   if ( keystorePasswd != null )
    	   System.setProperty("javax.net.ssl.trustStorePassword", keystorePasswd);
    //	   System.setProperty("javax.net.debug", "INFO");
    	   
           env.put(Context.SECURITY_PROTOCOL, "ssl");
           logger.info("Server AD authentication using ssl with keystore "+ keystore);
       }

	}

	
	protected Boolean userIsInNdexGroup (String username, String password) throws UnauthorizedOperationException  {
      
 	  //env.put(Context.SECURITY_PRINCIPAL, "NA\\" +username);
	  String searchFilter = null;
	  String searchBase = ldapSearchBase;
	  if ( delegatedUserName != null) {
		  env.put(Context.SECURITY_PRINCIPAL,username);
		  searchBase = username;
		  searchFilter = "(objectClass=user)";
	  } else {
		  env.put(Context.SECURITY_PRINCIPAL, ctxPrinciplePattern.replaceAll(userNamePattern, username));
		  searchFilter = searchFilterPattern.replaceAll(userNamePattern, username);
	  }
	  
	  env.put(Context.SECURITY_CREDENTIALS, password);
      try {
    	  LdapContext ctx;
	  try {
		  ctx = new InitialLdapContext(env,null);
	  } catch (NamingException e) {
		  if (ctxPrinciplePattern2 != null && delegatedUserName == null) {
			env.put(Context.SECURITY_PRINCIPAL, ctxPrinciplePattern2.replaceAll(userNamePattern, username));
			ctx = new InitialLdapContext(env,null);
		  } else {
			throw e;
		  }
	  }
	   
		  SearchControls searchControls = new SearchControls();
		  searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
		  NamingEnumeration<SearchResult> results = ctx.search(searchBase, searchFilter, searchControls);
		  SearchResult searchResult = null;
			if (results.hasMoreElements()) {
				searchResult = results.nextElement();
				Attributes attrs = searchResult.getAttributes();
			//			Attribute uWWID = attrs.get("employeeID");
				if ( ldapNDExGroup != null ) {
					Attribute grp = attrs.get("memberOf");
					NamingEnumeration enu = grp.getAll();
					while ( enu.hasMore()) {
						String obj = (String)enu.next();
						//	        	System.out.println(obj);
						Matcher matcher = pattern.matcher(obj);
						if (matcher.find())
						{
							if ( matcher.group(1).equals(ldapNDExGroup) ) 
								return Boolean.TRUE;
						}
					}
					return Boolean.FALSE;
				}	
				return Boolean.TRUE;
			}
			return Boolean.FALSE;
      } catch (NamingException e) {
    	  throw new UnauthorizedOperationException(e.getMessage());
      }
	}

	
	public User getNewUser (String username, String password) throws UnauthorizedOperationException  {
	      
	 	  //env.put(Context.SECURITY_PRINCIPAL, "NA\\" +username);
	      String searchFilter = null;
		  String searchBase = ldapSearchBase;
		  if (delegatedUserName != null) {
			  String cn = getFullyQualifiedNameByUserId(username);
			  env.put(Context.SECURITY_PRINCIPAL,cn);
			  searchBase = cn;
			  searchFilter = "(objectClass=user)";
		  } else {
			  env.put(Context.SECURITY_PRINCIPAL, ctxPrinciplePattern.replaceAll(userNamePattern, username));
			  searchFilter = searchFilterPattern.replaceAll(userNamePattern, username);
		  }
			
	      env.put(Context.SECURITY_CREDENTIALS, password);
	      try {
	    	  LdapContext ctx;
		  try {
			  ctx = new InitialLdapContext(env,null);
		  } catch (NamingException e) {
			  if (ctxPrinciplePattern2 != null && delegatedUserName == null) {
				env.put(Context.SECURITY_PRINCIPAL, ctxPrinciplePattern2.replaceAll(userNamePattern, username));
				ctx = new InitialLdapContext(env,null);
		  	  } else {
				throw e;
			  }
		  }
	      
	    	  // String searchFilter = "(&(SAMAccountName="+ username + ")(objectClass=user)(objectCategory=person))";
	    	  //String searchFilter = searchFilterPattern.replaceAll(userNamePattern, username);
		  
	    	  SearchControls searchControls = new SearchControls();
	    	  searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
	    	  NamingEnumeration<SearchResult> results = ctx.search(searchBase, searchFilter, searchControls);
		  
	    	  SearchResult searchResult = null;
	    	  if ( results.hasMoreElements()) {
				searchResult = results.nextElement();
				Attributes attrs = searchResult.getAttributes();
//				Attribute uWWID = attrs.get("employeeID");
                User newUser = new User();
				
                newUser.setUserName(username);
    			newUser.setPassword(Security.generateLongPassword());

                
                Attribute attr =attrs.get("givenName");
                if ( attr.size()>0) {
                	newUser.setFirstName(attr.get(0).toString());
                }
                
                attr =attrs.get("sn");
                if ( attr.size()>0) {
                	newUser.setLastName(attr.get(0).toString());
                }
                
                attr =attrs.get("mail");
                if ( attr.size()>0) {
                	newUser.setEmailAddress(attr.get(0).toString());
                }
				
                return newUser;
				
	    	  }
	    	  return null;
	      } catch (NamingException e) {
	    	  throw new UnauthorizedOperationException(e.getMessage());
	      }
		}

	
	
	public boolean authenticateUser(String username, String password) throws UnauthorizedOperationException {
		if ( !useCache) {
			if ( delegatedUserName !=null) {  
				return userIsInNdexGroup(getFullyQualifiedNameByUserId(username),password).booleanValue();
			}
			return userIsInNdexGroup(username,password).booleanValue();
		}
		
		Boolean result;
		try {
			result = userCredentials.get(new AbstractMap.SimpleImmutableEntry<>(username, password));
		} catch (ExecutionException e) {
			throw new UnauthorizedOperationException(e.getMessage());
		}
		return result.booleanValue() ;
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
	    		  return searchResult.getNameInNamespace();
	    	  }
	    	  return null;
	      } catch (NamingException e) {
	    	  throw new UnauthorizedOperationException(e.getMessage());
	      }
	}
	
	
}
 
