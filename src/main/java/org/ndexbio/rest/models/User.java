package org.ndexbio.rest.models;

import java.util.ArrayList;
import java.util.List;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.ndexbio.rest.domain.IGroup;
import org.ndexbio.rest.domain.IGroupMembership;
import org.ndexbio.rest.domain.INetwork;
import org.ndexbio.rest.domain.INetworkMembership;
import org.ndexbio.rest.domain.IRequest;
import org.ndexbio.rest.domain.IUser;
import org.ndexbio.rest.domain.Permissions;

@JsonIgnoreProperties(ignoreUnknown = true)
public class User extends Account
{
    private String _emailAddress;
    private String _firstName;
    private String _lastName;
    private List<Membership> _groupMemberships;
    private List<Membership> _networkMemberships;
    private List<Request> _requests;
    private String _username;
    private List<Network> _workSurface;

    
    
    /**************************************************************************
    * Default constructor.
    **************************************************************************/
    public User()
    {
        super();

        initCollections();
    }
    
    /**************************************************************************
    * Populates the class (from the database) and removes circular references.
    * Doesn't load owned Groups or Networks.
    * 
    * @param user The User with source data.
    **************************************************************************/
    public User(IUser user)
    {
        this(user, false);
    }
    
    /**************************************************************************
    * Populates the class (from the database) and removes circular references.
    * 
    * @param user The User with source data.
    * @param loadEverything True to load owned Groups and Networks, false to
    *                       exclude them.
    **************************************************************************/
    public User(IUser user, boolean loadEverything)
    {
        super(user);

        initCollections();
        
        this.setCreatedDate(user.getCreatedDate());

        _emailAddress = user.getEmailAddress();
        _firstName = user.getFirstName();
        _lastName = user.getLastName();
        _username = user.getUsername();
        
        for (final INetwork onWorkSurface : user.getWorkSurface())
            _workSurface.add(new Network(onWorkSurface));
        
        for (final IRequest request : user.getRequests())
            _requests.add(new Request(request));

        if (loadEverything)
        {
            for (final IGroupMembership membership : user.getGroups())
            {
                _groupMemberships.add(new Membership((IGroup)membership.getGroup(), membership.getPermissions()));
                
                if (membership.getPermissions() == Permissions.ADMIN)
                {
                    for (final IRequest request : membership.getGroup().getRequests())
                        _requests.add(new Request(request));
                }
            }
        
            for (final INetworkMembership membership : user.getNetworks())
            {
                _networkMemberships.add(new Membership((INetwork)membership.getNetwork(), membership.getPermissions()));
                
                if (membership.getPermissions() == Permissions.ADMIN)
                {
                    for (final IRequest request : membership.getNetwork().getRequests())
                        _requests.add(new Request(request));
                }
            }
        }
    }
    
    
    
    public String getEmailAddress()
    {
        return _emailAddress;
    }

    public void setEmailAddress(String emailAddress)
    {
        _emailAddress = emailAddress;
    }

    public String getFirstName()
    {
        return _firstName;
    }

    public void setFirstName(String firstName)
    {
        _firstName = firstName;
    }

    public String getLastName()
    {
        return _lastName;
    }

    public void setLastName(String lastName)
    {
        _lastName = lastName;
    }

    public List<Membership> getGroups()
    {
        return _groupMemberships;
    }

    public void setGroups(List<Membership> groupMemberships)
    {
        _groupMemberships = groupMemberships;
    }

    public List<Membership> getNetworks()
    {
        return _networkMemberships;
    }

    public void setNetworks(List<Membership> networkMemberships)
    {
        _networkMemberships = networkMemberships;
    }
    
    public List<Request> getRequests()
    {
        return _requests;
    }
    
    public void setRequests(List<Request> requests)
    {
        _requests = requests;
    }

    public String getUsername()
    {
        return _username;
    }

    public void setUsername(String username)
    {
        _username = username;
    }

    public List<Network> getWorkSurface()
    {
        return _workSurface;
    }
    
    public void setWorkSurface(List<Network> workSurface)
    {
        _workSurface = workSurface;
    }

    

    /**************************************************************************
    * Initializes the collections. 
    **************************************************************************/
    private void initCollections()
    {
        _groupMemberships = new ArrayList<Membership>();
        _networkMemberships = new ArrayList<Membership>();
        _requests = new ArrayList<Request>();
        _workSurface = new ArrayList<Network>();
    }
}
