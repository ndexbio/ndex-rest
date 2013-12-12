package org.ndexbio.rest.services;

import org.ndexbio.rest.domain.IUser;
import org.ndexbio.rest.exceptions.NdexException;
import org.ndexbio.rest.helpers.RidConverter;
import org.ndexbio.rest.helpers.Security;
import org.ndexbio.rest.models.NewUser;
import org.ndexbio.rest.models.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.orientechnologies.orient.core.id.ORID;

public class UserServiceTest extends NdexServiceTest
{
	private static final Logger _logger = LoggerFactory.getLogger(UserService.class);
	
    public User createUser(final NewUser newUser) throws IllegalArgumentException, NdexException
    {
        if (newUser == null)
            throw new IllegalArgumentException("The new user is empty.");
        else if (newUser.getUsername() == null || newUser.getUsername().isEmpty())
            throw new IllegalArgumentException("Username is empty.");
        else if (newUser.getPassword() == null || newUser.getPassword().isEmpty())
            throw new IllegalArgumentException("Password is empty.");
        else if (newUser.getEmailAddress() == null || newUser.getEmailAddress().isEmpty())
            throw new IllegalArgumentException("Email address is empty.");
        
        try
        {
            setupDatabase();
            
            final IUser user = _orientDbGraph.addVertex("class:user", IUser.class);
            user.setUsername(newUser.getUsername());
            user.setPassword(Security.hashText(newUser.getPassword()));
            user.setEmailAddress(newUser.getEmailAddress());
            
            _orientDbGraph.getBaseGraph().commit();
            return new User(user);
        }
        catch (Exception e)
        {
            _logger.error("Failed to create a new user: " + newUser.getUsername() + ".", e);
            _orientDbGraph.getBaseGraph().rollback(); 
            throw new NdexException(e.getMessage());
        }
        finally
        {
            teardownDatabase();
        }
    }
    
    
    public void updateUser(final User updatedUser) throws IllegalArgumentException, SecurityException, NdexException
    {

    	final ORID userRid = RidConverter.convertToRid(updatedUser.getId());
        try
        {
            setupDatabase();
            
            final IUser existingUser = _orientDbGraph.getVertex(userRid, IUser.class);

            if (updatedUser.getBackgroundImage() != null && !updatedUser.getBackgroundImage().isEmpty())
                existingUser.setBackgroundImage(updatedUser.getBackgroundImage());
    
            if (updatedUser.getDescription() != null && !updatedUser.getDescription().isEmpty())
                existingUser.setDescription(updatedUser.getDescription());
            
            if (updatedUser.getEmailAddress() != null && !updatedUser.getEmailAddress().isEmpty())
                existingUser.setEmailAddress(updatedUser.getEmailAddress());
            
            if (updatedUser.getFirstName() != null && !updatedUser.getFirstName().isEmpty())
                existingUser.setFirstName(updatedUser.getFirstName());
    
            if (updatedUser.getForegroundImage() != null && !updatedUser.getForegroundImage().isEmpty())
                existingUser.setForegroundImage(updatedUser.getForegroundImage());
    
            if (updatedUser.getLastName() != null && !updatedUser.getLastName().isEmpty())
                existingUser.setLastName(updatedUser.getLastName());
    
            if (updatedUser.getWebsite() != null && !updatedUser.getWebsite().isEmpty())
                existingUser.setWebsite(updatedUser.getWebsite());

            _orientDbGraph.getBaseGraph().commit();
        }
        catch (Exception e)
        {
            _logger.error("Failed to update user: " + updatedUser.getUsername() + ".", e);
            _orientDbGraph.getBaseGraph().rollback(); 
            throw new NdexException("Failed to update your profile.");
        }
        finally
        {
            teardownDatabase();
        }
    }

}
