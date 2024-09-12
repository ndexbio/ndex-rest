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
package org.ndexbio.rest.services;

import java.io.InputStream;
import java.util.List;
import java.util.Properties;

import jakarta.servlet.http.HttpServletRequest;

import org.easymock.EasyMock;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.NewUser;
import org.ndexbio.model.object.User;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.models.dao.postgresql.UserDAO;


public abstract class TestNdexService
{
//    protected static FramedGraphFactory _graphFactory = null;
 //   protected static FramedGraph<OrientBaseGraph> _orientDbGraph = null;
    
    protected static final HttpServletRequest _mockRequest = EasyMock.createMock(HttpServletRequest.class);
    protected static final Properties _testProperties = new Properties();

    
    
//    @BeforeClass
//    public static void initializeTests() throws Exception
//    {
//        final InputStream propertiesStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("ndex.properties");
//        _testProperties.load(propertiesStream);
//
//        try
//        {
//   /*         _graphFactory = new FramedGraphFactory(new GremlinGroovyModule(),
//                new TypedGraphModuleBuilder()
//                    .withClass(IGroup.class)
//                    .withClass(IUser.class)
//                    .withClass(IGroupMembership.class)
//                    .withClass(INetworkMembership.class)
//                    .withClass(IGroupInvitationRequest.class)
//                    .withClass(IJoinGroupRequest.class)
//                    .withClass(INetworkAccessRequest.class)
//                    .withClass(IBaseTerm.class)
//                    .withClass(IFunctionTerm.class)
//                    .build());
//    */
//            _ndexDatabase = ODatabaseDocumentPool.global().acquire("remote:localhost/ndex", "admin", "admin");
// //           _orientDbGraph = _graphFactory.create((OrientBaseGraph)new OrientGraph(_ndexDatabase));
//            NdexSchemaManager.INSTANCE.init(_ndexDatabase);
//        }
//        catch (Exception e)
//        {
//            Assert.fail("Failed to initialize database. Cause: " + e.getMessage());
//            e.printStackTrace();
//        }
//    }
//
//    @AfterClass
//    public static void cleanUp()
//    {
// //       _graphFactory = null;
//        _ndexDatabase.close();
//  //      _orientDbGraph = null;
//    }

    
    
//    @After
//    public void resetLoggedInUser()
//    {
//        EasyMock.reset(_mockRequest);
//    }
//
//    @Before
//    public void setLoggedInUser() throws NdexException {
//  /*
//    	final NdexDatabase database = new NdexDatabase();
//    	final ODatabaseDocumentTx  localConnection = database.getAConnection();
//    	localConnection.begin();
//    	final UserDAO dao = new UserDAO(localConnection, new OrientGraph(localConnection));
//    	final User loggedInUser;
//
//    	final SimpleUserQuery search = new SimpleUserQuery();
//    	search.setSearchString("dexter");
//
//    	final List<User> users = dao.findUsers(search, 0, 5);
//
//    	if(!users.get(0).getAccountName().equals("dexterpratt")) {
//
//	    	final NewUser newUser = new NewUser();
//	    	newUser.setEmailAddress("dexterpratt@ndexbio.org");
//	        newUser.setPassword("insecure");
//	        newUser.setAccountName("dexterpratt");
//	        newUser.setFirstName("Dexter");
//	        newUser.setLastName("Pratt");
//	        newUser.setDescription("Apart from my work at the Cytoscape Consortium building NDEx, I collect networks around some of my favorite biomolecules, such as FOX03, RBL2, and MUC1");
//	        newUser.setWebsite("www.triptychjs.com");
//	        newUser.setImage("http://i.imgur.com/09oVvZg.jpg");
//	        loggedInUser = dao.createNewUser(newUser);
//
//    	} else {
//
//    		loggedInUser = users.get(0);
//
//    	}
//
//        EasyMock.expect(_mockRequest.getAttribute("User")).andReturn(loggedInUser)
//            .anyTimes();
//
//        EasyMock.replay(_mockRequest);
//
//        localConnection.commit();
//        localConnection.close();
//        database.close();
//        */
//    }
// /*
//    @Test
//    public void connectionPool() throws NdexException {
//
//    	NdexDatabase database = new NdexDatabase();
//    	final ODatabaseDocumentTx[]  localConnection = new ODatabaseDocumentTx[10]; //= database.getAConnection();  //all DML will be in this connection, in one transaction.
//
//    	try {
//	    	for(int jj=0; jj<10; jj++) {
//	    		database = new NdexDatabase();
//		    	for(int ii=0; ii<10; ii++) {
//		    		localConnection[ii] = database.getAConnection();
//		    	}
//
//		    	for(int ii=0; ii<10; ii++) {
//		    		localConnection[ii].close();
//		    	}
//		    	database.close();
//	    	}
//
//
//    	} catch (Throwable e) {
//    		Assert.fail(e.getMessage());
//    	}
//
//    }
//    */
    

    
    /**************************************************************************
    * Emulates a user logged into the system via the mock HTTP request.
    * 
    * @param loggedInUser
    *            The user to emulate being logged in.
    **************************************************************************/
    protected void setLoggedInUser(final User loggedInUser)
    {
        EasyMock.expect(_mockRequest.getAttribute("User"))
        .andReturn(loggedInUser)
        .anyTimes();

        EasyMock.replay(_mockRequest);
    }
}
