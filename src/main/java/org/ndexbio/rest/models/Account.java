package org.ndexbio.rest.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.ndexbio.rest.domain.IAccount;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "accountType")
@JsonSubTypes(value = { @Type(value = Group.class, name = "Group"), @Type(value = User.class, name = "User") })
public abstract class Account extends NdexObject
{
    private String _backgroundImage;
    private String _description;
    private String _foregroundImage;
    private String _website;

    
    
    /**************************************************************************
    * Default constructor.
    **************************************************************************/
    public Account()
    {
        super();
    }
    
    /**************************************************************************
    * Populates the class (from the database) and removes circular references.
    * 
    * @param account The Account with source data.
    **************************************************************************/
    public Account(IAccount account)
    {
        super(account);

        this.setCreatedDate(account.getCreatedDate());
        
        _backgroundImage = account.getBackgroundImage();
        _description = account.getDescription();
        _foregroundImage = account.getForegroundImage();
        _website = account.getWebsite();
    }
    
    
    
    public String getBackgroundImage()
    {
        return _backgroundImage;
    }
    
    public void setBackgroundImage(String backgroundImage)
    {
        _backgroundImage = backgroundImage;
    }
    
    public String getDescription()
    {
        return _description;
    }
    
    public void setDescription(String description)
    {
        _description = description;
    }
    
    public String getForegroundImage()
    {
        return _foregroundImage;
    }
    
    public void setForegroundImage(String foregroundImage)
    {
        _foregroundImage = foregroundImage;
    }
    
    public String getWebsite()
    {
        return _website;
    }
    
    public void setWebsite(String website)
    {
        _website = website;
    }
}
