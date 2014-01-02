package org.ndexbio.rest.services;

import java.util.Collection;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.ndexbio.common.exceptions.DuplicateObjectException;
import org.ndexbio.common.exceptions.NdexException;
import org.ndexbio.common.exceptions.ObjectNotFoundException;
import org.ndexbio.common.models.object.NewUser;
import org.ndexbio.common.models.object.SearchParameters;
import org.ndexbio.common.models.object.User;
import org.ndexbio.common.helpers.IdConverter;
import com.orientechnologies.orient.core.id.ORID;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestUserService extends TestNdexService
{
    private static final UserService _userService = new UserService(_mockRequest);

    
    
    @Test
    public void addNetworkToWorkSurface()
    {
        Assert.assertTrue(putNetworkOnWorkSurface());
    }

    @Test(expected = IllegalArgumentException.class)
    public void addNetworkToWorkSurfaceInvalid() throws IllegalArgumentException, ObjectNotFoundException, NdexException
    {
        _userService.addNetworkToWorkSurface("");
    }

    @Test
    public void authenticateUser()
    {
        try
        {
            final User authenticatedUser = _userService.authenticateUser("dexterpratt", "insecure");
            Assert.assertNotNull(authenticatedUser);
            Assert.assertEquals(authenticatedUser.getUsername(), "dexterpratt");
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
    }

    @Test(expected = SecurityException.class)
    public void authenticateUserInvalid() throws SecurityException, NdexException
    {
        _userService.authenticateUser("dexterpratt", "notsecure");
    }

    @Test(expected = SecurityException.class)
    public void authenticateUserInvalidUsername() throws SecurityException, NdexException
    {
        _userService.authenticateUser("", "insecure");
    }

    @Test(expected = SecurityException.class)
    public void authenticateUserInvalidPassword() throws SecurityException, NdexException
    {
        _userService.authenticateUser("dexterpratt", "");
    }

    @Test
    public void changePassword()
    {
        try
        {
            _userService.changePassword("not-secure");
            
            User authenticatedUser = _userService.authenticateUser("dexterpratt", "not-secure");
            Assert.assertNotNull(authenticatedUser);
            
            _userService.changePassword("insecure");
            authenticatedUser = _userService.authenticateUser("dexterpratt", "insecure");
            Assert.assertNotNull(authenticatedUser);
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void changePasswordInvalid() throws IllegalArgumentException, NdexException
    {
        _userService.changePassword("");
    }

    @Test
    public void createUser()
    {
        Assert.assertTrue(createNewUser());
    }

    @Test(expected = IllegalArgumentException.class)
    public void createUserInvalid() throws IllegalArgumentException, NdexException
    {
        _userService.createUser(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void createUserInvalidUsername() throws IllegalArgumentException, NdexException
    {
        final NewUser newUser = new NewUser();
        newUser.setEmailAddress("support@ndexbio.org");
        newUser.setPassword("probably-insecure");
        
        _userService.createUser(newUser);
    }

    @Test(expected = IllegalArgumentException.class)
    public void createUserInvalidPassword() throws IllegalArgumentException, NdexException
    {
        final NewUser newUser = new NewUser();
        newUser.setEmailAddress("support@ndexbio.org");
        newUser.setUsername("Support");
        
        _userService.createUser(newUser);
    }

    @Test(expected = IllegalArgumentException.class)
    public void createUserInvalidEmail() throws IllegalArgumentException, NdexException
    {
        final NewUser newUser = new NewUser();
        newUser.setPassword("probably-insecure");
        newUser.setUsername("Support");
        
        _userService.createUser(newUser);
    }

    @Test
    public void deleteNetworkFromWorkSurface()
    {
        Assert.assertTrue(putNetworkOnWorkSurface());
        Assert.assertTrue(removeNetworkFromWorkSurface());
    }

    @Test(expected = IllegalArgumentException.class)
    public void deleteNetworkFromWorkSurfaceInvalid() throws IllegalArgumentException, NdexException
    {
        _userService.deleteNetworkFromWorkSurface("");
    }

    @Test
    public void deleteUser()
    {
        Assert.assertTrue(createNewUser());
        Assert.assertTrue(deleteTargetUser());
    }

    @Test
    public void emailNewPassword()
    {
        try
        {
            Assert.assertTrue(createNewUser());
            
            _userService.emailNewPassword("Support");
            
            Assert.assertTrue(deleteTargetUser());
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void emailNewPasswordInvalid() throws IllegalArgumentException, NdexException
    {
        _userService.emailNewPassword("");
    }

    @Test
    public void findUsers()
    {
        final SearchParameters searchParameters = new SearchParameters();
        searchParameters.setSearchString("dexter");
        searchParameters.setSkip(0);
        searchParameters.setTop(25);
        
        try
        {
            final Collection<User> usersFound = _userService.findUsers(searchParameters);
            Assert.assertNotNull(usersFound);
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void findUsersInvalid() throws IllegalArgumentException, NdexException
    {
        _userService.findUsers(null);
    }

    @Test
    public void getUserById()
    {
        try
        {
            final ORID testUserRid = getRid("dexterpratt");
            final User testUser = _userService.getUser(IdConverter.toJid(testUserRid));
            Assert.assertNotNull(testUser);
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
    }

    @Test
    public void getUserByUsername()
    {
        try
        {
            final User testUser = _userService.getUser("dexterpratt");
            Assert.assertNotNull(testUser);
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void getUserInvalid() throws IllegalArgumentException, NdexException
    {
        _userService.getUser("");
    }

    @Test
    public void updateUser()
    {
        try
        {
            Assert.assertTrue(createNewUser());
            
            EasyMock.reset(_mockRequest);
            User loggedInUser = getUser("Support");
            setLoggedInUser(loggedInUser);

            loggedInUser.setEmailAddress("updated-support@ndexbio.org");
            _userService.updateUser(loggedInUser);
            Assert.assertEquals(_userService.getUser(loggedInUser.getId()).getEmailAddress(), loggedInUser.getEmailAddress());

            Assert.assertTrue(deleteTargetUser());
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
    }

    @Test(expected = SecurityException.class)
    public void updateUserInvalidUser() throws IllegalArgumentException, SecurityException, NdexException
    {
        Assert.assertTrue(createNewUser());
        
        _userService.updateUser(_userService.getUser("Support"));
        
        Assert.assertTrue(deleteTargetUser());
    }

    @Test(expected = IllegalArgumentException.class)
    public void updateUserInvalid() throws IllegalArgumentException, SecurityException, NdexException
    {
        _userService.updateUser(null);
    }
    
    
    
    private boolean createNewUser()
    {
        try
        {
            final NewUser newUser = new NewUser();
            newUser.setEmailAddress("support@ndexbio.org");
            newUser.setPassword("probably-insecure");
            newUser.setUsername("Support");
            
            final User createdUser = _userService.createUser(newUser);
            Assert.assertNotNull(createdUser);
            return true;
        }
        catch (DuplicateObjectException doe)
        {
            return true;
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
        
        return false;
    }
    
    private boolean deleteTargetUser()
    {
        try
        {
            EasyMock.reset(_mockRequest);
            User loggedInUser = getUser("Support");
            setLoggedInUser(loggedInUser);

            _userService.deleteUser();
            Assert.assertNull(_userService.getUser(loggedInUser.getId()));

            EasyMock.reset(_mockRequest);
            loggedInUser = getUser("dexterpratt");
            setLoggedInUser(loggedInUser);
            
            return true;
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
        
        return false;
    }
    
    private boolean putNetworkOnWorkSurface()
    {
        try
        {
            final ORID testNetworkRid = getRid("REACTOME TEST");
            _userService.addNetworkToWorkSurface(IdConverter.toJid(testNetworkRid));
            
            final User testUser = _userService.getUser("dexterpratt");
            Assert.assertEquals(testUser.getWorkSurface().size(), 1);
            return true;
        }
        catch (DuplicateObjectException doe)
        {
            return true;
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
        
        return false;
    }
    
    private boolean removeNetworkFromWorkSurface()
    {
        try
        {
            final ORID testNetworkRid = getRid("REACTOME TEST");
            _userService.deleteNetworkFromWorkSurface(IdConverter.toJid(testNetworkRid));
            
            final User testUser = _userService.getUser("dexterpratt");
            Assert.assertEquals(testUser.getWorkSurface().size(), 0);
            
            return true;
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
        
        return false;
    }
}
