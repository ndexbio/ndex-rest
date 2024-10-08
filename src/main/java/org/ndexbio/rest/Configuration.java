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
package org.ndexbio.rest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.ndexbio.common.importexport.ImporterExporterEntry;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.rest.helpers.Security;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.qos.logback.classic.Level;


public class Configuration
{
	public static final String NDEX_EXPORTER_TIMEOUT = "NdexExporterTimeout";
	
    public static final String UPLOADED_NETWORKS_PATH_PROPERTY = "Uploaded-Networks-Path";
    
    private static final String PROP_USE_AD_AUTHENTICATION = "USE_AD_AUTHENTICATION";
    private static final String SOLR_URL = "SolrURL";
    
    private static Configuration INSTANCE = null;
    private static final Logger _logger = LoggerFactory.getLogger(Configuration.class);
    private Properties _configurationProperties;
    
	private String dbURL;
	private static final String dbUserPropName 	   = "NdexDBUsername";
	private static final String dbPasswordPropName = "NdexDBDBPassword";
	
	public static final String ndexConfigFilePropName = "ndexConfigurationPath";
	
	public static final String networkPostEdgeLimit = "NETWORK_POST_ELEMENT_LIMIT";
	private static final String defaultSolrURL = "http://localhost:8983/solr";
	
	private static final String NDEX_KEY = "NDEX_KEY";
	
	private static final String DOI_CREATOR="DOI_CREATOR";
	
	private static final String DEFAULT_NDEX_EXPORTER_TIMEOUT_VAL="600";
	private String solrURL;
	private String ndexSystemUser ;
	private String ndexSystemUserPassword;
	private String ndexRoot;
	
	private String hostURI ;
	private String restAPIPrefix ;

	private String networkStorePath;
	
	private String ndexNetworkCachePath;
   
	private boolean useADAuthentication ;
	
	//private long serverElementLimit;
	
	private String statsDBLink;
	
	private Map<String,ImporterExporterEntry> impExpTable;
		
	// variables for DOI creation
	//private String DOICreator;
		
	private SecretKeySpec secretKey;
	
	private static byte[] key;
	
	private String DOIPrefix;
	private String ezidUser;
	private String ezidpswd;

	// Possible values for Log-Level are:
    // trace, debug, info, warn, error, all, off
    // If no Log-Level config parameter specified in /opt/ndex/conf/ndex.properties, or the value is 
	// invalid/unrecognized, we set loglevel to 'info'.
	// Please see http://logback.qos.ch/manual/architecture.html for description of log levels	
	private Level logLevel;
    
	/**
	 * This constructor should only be used for unit tests. To 
	 * get an instance of this object please use {@link Configuration#getInstance() }
	 * 
	 * @deprecated Should only be used for unit tests
	 */
	@Deprecated
	public Configuration(){
		INSTANCE = this;
	}
	
	/**
	 * This should only be used for unit tests.
	 * To 
	 * get an instance of this object please use 
	 * {@link Configuration#getInstance() }
	 * @deprecated Should only be used for unit tests
	 * @param config 
	 */
	@Deprecated
	public static void setInstance(Configuration config){
		INSTANCE = config;
	}
	
    /**************************************************************************
    * Default constructor. Made private to prevent instantiation. 
     * @throws NamingException 
     * @throws NdexException 
     * @throws IOException 
     * @throws FileNotFoundException 
     * @throws NoSuchAlgorithmException 
    **************************************************************************/
    private Configuration(final String configPath) throws NamingException, NdexException, FileNotFoundException, IOException
    {
   //     try {
    		String configFilePath = configPath;
        	if ( configFilePath == null) {
        		InitialContext ic = new InitialContext();
        	    configFilePath = (String) ic.lookup("java:comp/env/ndexConfigurationPath"); 
        	}    
        	
        	if ( configFilePath == null) {
        		_logger.error("ndexConfigurationPath is not defined in environement.");
        		throw new NdexException("ndexConfigurationPath is not defined in environement.");
        	} 
        	
        	_logger.info("Loading ndex configuration from " + configFilePath);
        	
        	_configurationProperties = new Properties();
        
        	try (FileReader reader = new FileReader(configFilePath)) {
        		_configurationProperties.load(reader);
        	}
            
            dbURL 	= getRequiredProperty("NdexDBURL");
            solrURL = getProperty(SOLR_URL);
            if ( solrURL == null)
            	solrURL = defaultSolrURL;
            
            this.ndexSystemUser = getRequiredProperty("NdexSystemUser");
            this.ndexSystemUserPassword = getRequiredProperty("NdexSystemUserPassword");

            this.ndexRoot = getRequiredProperty("NdexRoot");
            hostURI = getRequiredProperty("HostURI");

            this.networkStorePath = this.ndexRoot + "/data/";
            
			File file = new File (this.networkStorePath );
			if (!file.exists()) {
				if ( ! file.mkdirs()) {
					throw new NdexException ("Server failed to create data store on path " + this.networkStorePath);
				}
			}
			
			
		/*	String edgeLimit = getProperty(Configuration.networkPostEdgeLimit);
			if ( edgeLimit != null ) {
				try {
					serverElementLimit = Long.parseLong(edgeLimit);
				} catch( NumberFormatException e) {
					_logger.warn("[Invalid value in server property {}. Error: {}]", Configuration.networkPostEdgeLimit, e.getMessage());
			//		props.put("ServerPostEdgeLimit", "-1");  //defaultPostEdgeLimit);
				}
			} else 
				serverElementLimit = -1;*/
			
			statsDBLink = getProperty("STATS_DB_LINK");
            
			restAPIPrefix = getProperty("RESTAPIPrefix");
			if ( restAPIPrefix == null) {
				restAPIPrefix = "/v2";
			}
            setLogLevel();
                        
            // get AD authentication flag
            String useAd = getProperty(PROP_USE_AD_AUTHENTICATION);
            if (useAd != null && Boolean.parseBoolean(useAd)) {
            	setUseADAuthentication(true);
            } else {
            	setUseADAuthentication(false);
            }
            
			String userDefaultStorageQuota = getProperty("USER_STORAGE_LIMIT");
			if ( userDefaultStorageQuota != null) {
				Float limit = Float.valueOf(userDefaultStorageQuota);
					UserDAO.default_disk_quota = limit.floatValue() > 0 ? (limit.intValue() * 1000000000l) : -1;
			}
            
			String ndexKey = getProperty(NDEX_KEY);
			if ( ndexKey!=null) 
				prepareSecreteKey(ndexKey);
			else 
				_logger.info("NDEX_KEY not found in properties file." );
			
			String DOICreator = getProperty(DOI_CREATOR);

			if (DOICreator!=null) {
				String authStr;
				try {
					authStr = Security.decrypt(DOICreator, this.secretKey);
				} catch (InvalidKeyException | IllegalBlockSizeException | BadPaddingException | NoSuchAlgorithmException
					| NoSuchPaddingException | NdexException e) {
					throw new NdexException ("Failed to decode " + DOI_CREATOR +".");
				}
				int idx = authStr.indexOf(":");
	        
				if ( idx == -1) 
					throw new NdexException("Malformed authorization value in "+ DOI_CREATOR + " property.");
        
				ezidUser = authStr.substring(0, idx);
				ezidpswd = authStr.substring(idx+1);
			}
			
			DOIPrefix = getProperty("DOI_PREFIX");
			
            // initialize the importer exporter table
            this.impExpTable = new HashMap<>();
            String impExpConfigFile = this.ndexRoot + "/conf/ndex_importer_exporter.json";
	        try (FileInputStream i = new FileInputStream(impExpConfigFile)) {

		      Iterator<ImporterExporterEntry> it = new ObjectMapper().readerFor(ImporterExporterEntry.class).readValues(i);
		      
			while (it.hasNext()) {
				ImporterExporterEntry entry = it.next();
				entry.setDirectoryName(this.ndexRoot + "/importer_exporter/" + entry.getDirectoryName());
				List<String> cmdList = entry.getImporterCmd();
				if (cmdList != null && !cmdList.isEmpty()) {
					String cmd = cmdList.get(0);
					if (!cmd.startsWith("/")) {
						cmd = entry.getDirectoryName() + "/" + cmd;
						cmdList.set(0, cmd);
					}
				}

				cmdList = entry.getExporterCmd();
				if (cmdList != null && !cmdList.isEmpty()) {
					String cmd = cmdList.get(0);
					if (!cmd.startsWith("/")) {
						cmd = entry.getDirectoryName() + "/" + cmd;
						cmdList.set(0, cmd);
					}
				}

				impExpTable.put(entry.getName(), entry);
			}
    		} catch (FileNotFoundException nfe) {
    			_logger.warn("Importer/Exporter configuration not found at \"" + impExpConfigFile + "\". No import export function will be supported in this server."
    					+ "\nError: " + nfe.getMessage()  );
    		}
   /*     } 
        catch (Exception e)
        {
            _logger.error("Failed to load the configuration file.", e);
            throw new NdexException ("Failed to load the configuration file. " + e.getMessage());
        } */
    }
    
    private void prepareSecreteKey(String myKey) throws NdexException {

        MessageDigest sha = null;
        try {
            key = myKey.getBytes(StandardCharsets.UTF_8);
            sha = MessageDigest.getInstance("SHA-1");
            key = sha.digest(key);
            key = Arrays.copyOf(key, 16);
            secretKey = new SecretKeySpec(key, "AES");
        } catch (NoSuchAlgorithmException e) {
        	throw new NdexException ("Can't find digest algorithm: " + e.getMessage());
        }
    }
    
    /*
     * This method reads Log-Level configuration parameter from /opt/ndex/conf/ndex.properties 
     * and sets the log level.
     * In case Log-Level is not found in ndex.properties or value of Log-Level is invalid/unrecognized,
     * we set log level to 'info'.
     */
    private void setLogLevel() {
        ch.qos.logback.classic.Logger rootLog = 
        		(ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        
    	String result = _configurationProperties.getProperty("Log-Level");

        if (result == null) {
        	// if no Log-Level is specified in /opt/ndex/conf/ndex.properties, we set loglevel to info 
        	this.logLevel = Level.INFO;
        	_logger.info("No 'Log-Level' parameter found in config ndex.properties file. Log level set to 'info'.");
        } else {
        	this.logLevel = Level.toLevel(result.toUpperCase(), Level.INFO);	
        }
    	rootLog.setLevel(this.logLevel);

	}


	public String getRequiredProperty (String propertyName ) throws NdexException {
    	String result = _configurationProperties.getProperty(propertyName);
        if ( result == null) {
        	throw new NdexException ("Required property " + propertyName + " not found in configuration.");
        }
        return result;
    }
    
	public ImporterExporterEntry getImpExpEntry(String name){
		return impExpTable.get(name);
	}
	
	public Collection<ImporterExporterEntry> getImporterExporters () {
		return impExpTable.values();
	}
	
    /**************************************************************************
    * Gets the singleton instance. 
     * @throws NdexException 
     * @throws IOException 
     * @throws NamingException 
     * @throws FileNotFoundException 
    **************************************************************************/
    public static Configuration getInstance() //throws NdexException
    {
    	/*if ( INSTANCE == null)  { 
    		try {
    			INSTANCE = new Configuration();
    		} catch (  NamingException | IOException e) {
    			throw new NdexException ( "Failed to get Configurtion Instance: " + e.getMessage(), e);
    		}
    	} */
        return INSTANCE;
    }

    
    public static Configuration createInstance() throws NdexException
    {
    	if ( INSTANCE == null)  { 
    		try {
    			String configFilePath = System.getenv(ndexConfigFilePropName);
    			if ( configFilePath == null)
    				configFilePath = System.getProperty(ndexConfigFilePropName);
    			if ( configFilePath == null)
    				throw new NdexException(ndexConfigFilePropName + " is not defined. This variable needs to be defined as an environment variable or system property.");
    			INSTANCE = new Configuration(configFilePath);
    		} catch (  NamingException | IOException e) {
    			throw new NdexException ( "Failed to get Configurtion Instance: " + e.getMessage(), e);
    		}
    	} 
        return INSTANCE;
    }
    
    /**
     * Added to enable testing. 
     * @deprecated Dont use this is for testing only
     * @param configFilePath
     * @return Instance of {@link org.ndexbio.rest.Configuration} object
     * @throws NdexException
     * @throws NoSuchAlgorithmException 
     */
	@Deprecated
    protected static Configuration reCreateInstance(final String configFilePath) throws NdexException, NoSuchAlgorithmException{
    	
    	try {
    		INSTANCE = new Configuration(configFilePath);
    	} catch (  NamingException | IOException e) {
    		throw new NdexException ( "Failed to get Configurtion Instance: " + e.getMessage(), e);
    	} 
        return INSTANCE;
    }
    
    /**************************************************************************
    * Gets the value of a property from configuration.
    * 
    * @param propertyName
    *            The property name.
    **************************************************************************/
    public String getProperty(String propertyName)
    {
        return _configurationProperties.getProperty(propertyName);
    }
	
	

      
    
    /**************************************************************************
    * Gets the singleton instance. 
    * 
    * @param propertyName
    *            The property name.
    * @param propertyValue
    *            The property value.
    **************************************************************************/
    public void setProperty(String propertyName, String propertyValue)
    {
        _configurationProperties.setProperty(propertyName, propertyValue);
    }
    
    public String getDBURL () { return dbURL; }
    public String getDBUser() { return _configurationProperties.getProperty(dbUserPropName); }
    public String getDBPasswd () { return _configurationProperties.getProperty(dbPasswordPropName); }
    
	/**
	 * Gets admin email address from NdexSystemUserEmail property
	 * 
	 * @return address or support@ndexbio.org if not set in configuration
	 */
	public String getAdminUserEmail(){
		return _configurationProperties.getProperty("NdexSystemUserEmail", "support@ndexbio.org");
	}
    /**
     * Gets Exporter timeout in seconds from configuration
     * with default set to 600 seconds.
     * @return timeout in seconds as a long
     */
    public long getExporterTimeout() { 
    	try {
    		return Long.parseLong(_configurationProperties.getProperty(NDEX_EXPORTER_TIMEOUT,
    				DEFAULT_NDEX_EXPORTER_TIMEOUT_VAL));
    	} catch(NumberFormatException nfe) {
    		_logger.warn("Unable to convert " + NDEX_EXPORTER_TIMEOUT +
    				     " parameter value to a number", nfe);
    	}
    	return Long.parseLong(DEFAULT_NDEX_EXPORTER_TIMEOUT_VAL);
    }
    public String getSystmUserName() {return this.ndexSystemUser;}
    public String getSystemUserPassword () {return this.ndexSystemUserPassword;}
    public String getNdexRoot()  {return this.ndexRoot;}
    public String getSolrURL() {return this.solrURL; }
    public Level  getLogLevel()  {return this.logLevel;}
    public String getHostURI () { return hostURI; }
    //public long   getServerElementLimit() { return serverElementLimit;}
    public String getStatsDBLink()   {return statsDBLink;}
    public String getRestAPIPrefix() {return restAPIPrefix;}

	public boolean getUseADAuthentication() {
		return useADAuthentication;
	}


	public void setUseADAuthentication(boolean useADAuthentication) {
		this.useADAuthentication = useADAuthentication;
	}
	
	public String getNdexNetworkCachePath() {
		return ndexNetworkCachePath;
	}
	
	//public String getDOICreatorString()  {return DOICreator; }
	
	public SecretKeySpec getSecretKeySpec() { return this.secretKey;}
	
    public String getDOIUser() {return this.ezidUser;}
    public String getDOIPswd() {return this.ezidpswd;}
    public String getDOIPrefix() {return DOIPrefix;}
}
