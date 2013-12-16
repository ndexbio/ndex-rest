package org.ndexbio.rest.services;

import javax.servlet.http.HttpServletRequest;
import org.easymock.EasyMock;
import org.junit.Test;

public class TestUserService
{
    private static final HttpServletRequest _mockRequest = EasyMock.createMock(HttpServletRequest.class);
    private static final UserService _userService = new UserService(_mockRequest);

    
    
    @Test
    public void addNetworkToWorkSurface()
    {
    }

    @Test
    public void addNetworkToWorkSurfaceInvalid()
    {
    }

    @Test
    public void authenticateUser()
    {
    }

    @Test
    public void authenticateUserInvalid()
    {
    }

    @Test
    public void changePassword()
    {
    }

    @Test
    public void changePasswordInvalid()
    {
    }

    @Test
    public void createUser()
    {
    }

    @Test
    public void createUserInvalid()
    {
    }

    @Test
    public void deleteNetworkFromWorkSurface()
    {
    }

    @Test
    public void deleteNetworkFromWorkSurfaceInvalid()
    {
    }

    @Test
    public void deleteUser()
    {
    }

    @Test
    public void deleteUserInvalid()
    {
    }

    @Test
    public void emailNewPassword()
    {
    }

    @Test
    public void emailNewPasswordInvalid()
    {
    }

    @Test
    public void findUsers()
    {
    }

    @Test
    public void findUsersInvalid()
    {
    }

    @Test
    public void getUserById()
    {
    }

    @Test
    public void getUserByUsername()
    {
    }

    @Test
    public void getUserInvalid()
    {
    }

    @Test
    public void updateUser()
    {
    }

    @Test
    public void updateUserInvalid()
    {
    }
}
