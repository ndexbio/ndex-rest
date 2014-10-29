package org.ndexbio.rest.services;

import java.util.Collection;




//import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.AfterClass;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Before;
import org.junit.runners.MethodSorters;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.model.object.SimpleUserQuery;
import org.ndexbio.common.exceptions.NdexException;
import org.ndexbio.common.models.dao.orientdb.UserDAO;
import org.ndexbio.model.object.User;
import org.ndexbio.model.object.NewUser;
import org.ndexbio.rest.services.UserService;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.tinkerpop.blueprints.impls.orient.OrientGraph;
import com.tinkerpop.blueprints.impls.orient.OrientGraphNoTx;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestUserService extends TestNdexService {
	
    private static final UserService _userService = new UserService(_mockRequest);
    private static User testUser;
    private static User testUser2;
    
////    @BeforeClass
////    public static void setUpBeforeClass() throws Exception {
////    	NewUser newUser = new NewUser();
////        newUser.setEmailAddress("testUser@ndexbio.org");
////        newUser.setPassword("testUser");
////        newUser.setAccountName("testUser");
////        newUser.setFirstName("testUser");
////        newUser.setLastName("testUser");
////		testUser = _userService.createUser(newUser);
////
////    }
//
//    @AfterClass
//	public static void tearDownAfterClass() throws Exception {
//
//    /*	final NdexDatabase database = new NdexDatabase();
//    	final ODatabaseDocumentTx  localConnection = database.getAConnection();  //all DML will be in this connection, in one transaction.
//    	final UserDAO dao = new UserDAO(localConnection, new OrientGraph(localConnection));
//
//    	dao.deleteUserById(testUser.getExternalId());
//    	localConnection.commit();
//    	if(testUser2!=null) {
//    		dao.deleteUserById(testUser2.getExternalId());
//    		testUser2 = null;
//    		localConnection.commit();
//    	}
//
//
//    	localConnection.close();
//    	database.close(); */
//	}
//
//    @Test
//    public void createUser() {
//    	// no clean up done for creation of user, not a standalone test?
//    	try{
//
//	    	final NewUser newUser = new NewUser();
//	        newUser.setEmailAddress("support3@ndexbio.org");
//	        newUser.setPassword("probably-insecure3");
//	        newUser.setAccountName("Support3");
//	        newUser.setFirstName("foo3");
//	        newUser.setLastName("bar3");
//
//	        testUser2 = _userService.createUser(newUser);
//	        Assert.assertNotNull(testUser2);
//
//    	} catch (Exception e) {
//
//    		Assert.fail(e.getMessage());
//
//    	}
//
//    }
//
//    @Test
//    public void getUserById() {
//
//    	try {
//
//	    	final User user = _userService.getUser(testUser.getExternalId().toString());
//	        Assert.assertNotNull(user);
//
//    	} catch (Exception e) {
//
//    		Assert.fail(e.getMessage());
//
//    	}
//
//    }
//
//    @Test
//    public void getUserByAccountName() {
//
//    	try {
//
//	    	final User user = _userService.getUser(testUser.getAccountName());
//	    	Assert.assertEquals(user.getAccountName(), testUser.getAccountName());
//	        Assert.assertNotNull(user);
//
//    	} catch (Exception e) {
//
//    		Assert.fail(e.getMessage());
//
//    	}
//
//    }
//
//    @Test
//    public void authenticateUser() {
//
//        try {
//
//            final User authenticatedUser = _userService.authenticateUser(testUser.getAccountName(), "testUser");
//            Assert.assertNotNull(authenticatedUser);
//            Assert.assertEquals(authenticatedUser.getAccountName(), testUser.getAccountName());
//            Assert.assertEquals(authenticatedUser.getFirstName(), testUser.getFirstName());
//            Assert.assertEquals(authenticatedUser.getLastName(), testUser.getLastName());
//
//        } catch (Exception e) {
//
//            Assert.fail(e.getMessage());
//            e.printStackTrace();
//
//        }
//    }
//
//    @Test
//    public void deleteUser() {
//
//    	try {
//
//    		_userService.deleteUser();
//
//    	} catch (Exception e) {
//
//    		Assert.fail(e.getMessage());
//
//    	}
//
//    }
//
//   @Test(expected = SecurityException.class)
//    public void authenticateUserInvalid() throws SecurityException, NdexException
//    {
//        _userService.authenticateUser("dexterpratt", "notsecure");
//    }
//
//    @Test(expected = SecurityException.class)
//    public void authenticateUserInvalidUsername() throws SecurityException, NdexException
//    {
//        _userService.authenticateUser("", "insecure");
//    }
//
//    @Test(expected = SecurityException.class)
//    public void authenticateUserInvalidPassword() throws SecurityException, NdexException
//    {
//        _userService.authenticateUser("dexterpratt", "");
//    }
///*
//    @Test
//    public void changePassword()
//    {
//        try
//        {
//            _userService.changePassword("not-secure");
//
//            User authenticatedUser = _userService.authenticateUser("dexterpratt", "not-secure");
//            Assert.assertNotNull(authenticatedUser);
//
//            _userService.changePassword("insecure");
//            authenticatedUser = _userService.authenticateUser("dexterpratt", "insecure");
//            Assert.assertNotNull(authenticatedUser);
//        }
//        catch (Exception e)
//        {
//            Assert.fail(e.getMessage());
//            e.printStackTrace();
//        }
//    }
//
//    @Test(expected = IllegalArgumentException.class)
//    public void changePasswordInvalid() throws IllegalArgumentException, NdexException
//    {
//        _userService.changePassword("");
//    }
//
//    @Test
//    public void createUser()
//    {
//        Assert.assertTrue(createNewUser());
//    }
//
//    @Test(expected = IllegalArgumentException.class)
//    public void createUserInvalid() throws IllegalArgumentException, NdexException
//    {
//        _userService.createUser(null);
//    }
//
//    @Test(expected = IllegalArgumentException.class)
//    public void createUserInvalidUsername() throws IllegalArgumentException, NdexException
//    {
//        final NewUser newUser = new NewUser();
//        newUser.setEmailAddress("support@ndexbio.org");
//        newUser.setPassword("probably-insecure");
//
//        _userService.createUser(newUser);
//    }
//
//    @Test(expected = IllegalArgumentException.class)
//    public void createUserInvalidPassword() throws IllegalArgumentException, NdexException
//    {
//        final NewUser newUser = new NewUser();
//        newUser.setEmailAddress("support@ndexbio.org");
//        newUser.setUsername("Support");
//
//        _userService.createUser(newUser);
//    }
//
//    @Test(expected = IllegalArgumentException.class)
//    public void createUserInvalidEmail() throws IllegalArgumentException, NdexException
//    {
//        final NewUser newUser = new NewUser();
//        newUser.setPassword("probably-insecure");
//        newUser.setUsername("Support");
//
//        _userService.createUser(newUser);
//    }
//
//    @Test
//    public void deleteNetworkFromWorkSurface()
//    {
//        Assert.assertTrue(putNetworkOnWorkSurface());
//        Assert.assertTrue(removeNetworkFromWorkSurface());
//    }
//
//    @Test(expected = IllegalArgumentException.class)
//    public void deleteNetworkFromWorkSurfaceInvalid() throws IllegalArgumentException, NdexException
//    {
//        _userService.deleteNetworkFromWorkSurface("");
//    }
//
//    @Test
//    public void deleteUser()
//    {
//        Assert.assertTrue(createNewUser());
//        Assert.assertTrue(deleteTargetUser());
//    }
//*/
//    /*@Test
//    public void emailNewPassword() {
//
//        try {
//
//            Assert.assertNotNull(_userService.emailNewPassword("Support"));
//
//        } catch (Exception e) {
//
//            Assert.fail(e.getMessage());
//            e.printStackTrace();
//
//        }
//
//    }
//
//    @Test(expected = IllegalArgumentException.class)
//    public void emailNewPasswordInvalid() throws IllegalArgumentException, NdexException {
//
//        _userService.emailNewPassword("");
//
//    }*/
//
//    @Test
//    public void findUsers() {
//
//        final SimpleUserQuery searchParameters = new SimpleUserQuery();
//        searchParameters.setSearchString("test");
//
//        try {
//
//            final Collection<User> usersFound = _userService.findUsers(searchParameters, 0 , 5);
//            Assert.assertNotNull(usersFound);
//
//        } catch (Exception e) {
//
//            Assert.fail(e.getMessage());
//            e.printStackTrace();
//
//        }
//
//    }
//
//    /*@Test(expected = IllegalArgumentException.class)
//    public void findUsersInvalid() throws IllegalArgumentException, NdexException {
//
//    	final SearchParameters searchParameters = new SearchParameters();
//        searchParameters.setSearchString("a");
//        _userService.findUsers( searchParameters, null, 'y');
//
//    }*/
//
//
//    @Test(expected = IllegalArgumentException.class)
//    public void getUserInvalid() throws IllegalArgumentException, NdexException
//    {
//        _userService.getUser(null);
//    }
//
//    @Test
//    public void updateUser()
//    {
//        try
//        {
//        	final User updatedUser = new User();
//            updatedUser.setDescription("change");
//            _userService.updateUser(null, updatedUser);
//            Assert.assertEquals(_userService.getUser("dexterpratt").getDescription(), updatedUser.getDescription());
//            Assert.assertEquals(_userService.getUser("dexterpratt").getWebsite(), "www.triptychjs.com"); // hardwired names should be replaced by a get logged in user method
//        }
//        catch (Exception e)
//        {
//            Assert.fail(e.getMessage());
//            e.printStackTrace();
//        }
//    }
//
//    @Test(expected = IllegalArgumentException.class)
//    public void updateUserInvalid() throws IllegalArgumentException, SecurityException, NdexException
//    {
//        _userService.updateUser(null, null);
//    }
//
//
//   /*
//    private boolean putNetworkOnWorkSurface()
//    {
//        try
//        {
//            final ORID testNetworkRid = getRid("REACTOME TEST");
//            _userService.addNetworkToWorkSurface(IdConverter.toJid(testNetworkRid));
//
//            final User testUser = _userService.getUser("dexterpratt");
//            Assert.assertEquals(testUser.getWorkSurface().size(), 1);
//            return true;
//        }
//        catch (DuplicateObjectException doe)
//        {
//            return true;
//        }
//        catch (Exception e)
//        {
//            Assert.fail(e.getMessage());
//            e.printStackTrace();
//        }
//
//        return false;
//    }
//
//    private boolean removeNetworkFromWorkSurface()
//    {
//        try
//        {
//            final ORID testNetworkRid = getRid("REACTOME TEST");
//            _userService.deleteNetworkFromWorkSurface(IdConverter.toJid(testNetworkRid));
//
//            final User testUser = _userService.getUser("dexterpratt");
//            Assert.assertEquals(testUser.getWorkSurface().size(), 0);
//
//            return true;
//        }
//        catch (Exception e)
//        {
//            Assert.fail(e.getMessage());
//            e.printStackTrace();
//        }
//
//        return false;
//    }  */
}
