package org.ndexbio.rest.helpers;

import java.util.Properties;

public class Configuration
{
    private static final Configuration INSTANCE = new Configuration();
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
            System.out.println(e.getMessage());
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
    **************************************************************************/
    public String getProperty(String propertyName)
    {
        return _servletProperties.getProperty(propertyName);
    }
    
    /**************************************************************************
    * Gets the singleton instance. 
    **************************************************************************/
    public void setProperty(String propertyName, String propertyValue)
    {
        _servletProperties.setProperty(propertyName, propertyValue);
    }
}
