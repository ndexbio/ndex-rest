package org.ndexbio.rest.helpers;

import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Configuration
{
    private static final Configuration INSTANCE = new Configuration();
    private static final Logger _logger = LoggerFactory.getLogger(Configuration.class);
    private final Properties _servletProperties = new Properties();
    
    
    
    /**************************************************************************
    * Default constructor. Made private to prevent instantiation. 
    **************************************************************************/
    private Configuration()
    {
        try
        {
            _servletProperties.load(this.getClass().getClassLoader().getResourceAsStream("/ndex.properties"));
        }
        catch (Exception e)
        {
            _logger.error("Failed to load the configuration file.", e);
        }
    }
    
    
    
    /**************************************************************************
    * Gets the singleton instance. 
    **************************************************************************/
    public static Configuration getInstance()
    {
        return INSTANCE;
    }

    
    
    /**************************************************************************
    * Gets the singleton instance. 
    * 
    * @param propertyName
    *            The property name.
    **************************************************************************/
    public String getProperty(String propertyName)
    {
        return _servletProperties.getProperty(propertyName);
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
        _servletProperties.setProperty(propertyName, propertyValue);
    }
}
