package org.ndexbio.rest;

import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.easymock.EasyMock;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.ndexbio.rest.domain.IUser;
import org.ndexbio.rest.models.User;
import org.ndexbio.rest.services.TestFeedbackService;
import org.ndexbio.rest.services.TestGroupService;
import org.ndexbio.rest.services.TestRequestService;
import org.ndexbio.rest.services.TestTaskService;
import org.ndexbio.rest.services.TestUserService;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.frames.FramedGraph;

@RunWith(Suite.class)
@SuiteClasses({ TestFeedbackService.class, TestGroupService.class, TestRequestService.class, TestTaskService.class, TestUserService.class })
public class NdexServicesTestSuite
{
    /**************************************************************************
    * Gets the record ID of an object by its name from the database.
    * 
    * @param objectName
    *            The name of the object.
    * @return An ORID object containing the record ID.
    **************************************************************************/
    public static ORID getRid(final String objectName, final ODatabaseDocumentTx ndexDatabase, final FramedGraph<OrientBaseGraph> orientDbGraph) throws IllegalArgumentException
    {
        final List<ODocument> matchingUsers = ndexDatabase.query(new OSQLSynchQuery<Object>("select from User where username = '" + objectName + "'"));
        if (!matchingUsers.isEmpty())
            return (ORID)orientDbGraph.getVertex(matchingUsers.get(0)).getId();
        
        final List<ODocument> matchingGroups = ndexDatabase.query(new OSQLSynchQuery<Object>("select from Group where name = '" + objectName + "'"));
        if (!matchingGroups.isEmpty())
            return (ORID)orientDbGraph.getVertex(matchingGroups.get(0)).getId();

        final List<ODocument> matchingNetworks = ndexDatabase.query(new OSQLSynchQuery<Object>("select from Network where title = '" + objectName + "'"));
        if (!matchingNetworks.isEmpty())
            return (ORID)orientDbGraph.getVertex(matchingNetworks.get(0)).getId();
        
        throw new IllegalArgumentException(objectName + " is not a user, group, or network.");
    }

    /**************************************************************************
    * Queries the database for the user's ID by the username. Necessary to
    * mock the logged in user.
    **************************************************************************/
    public static User getUser(final String username, final ODatabaseDocumentTx ndexDatabase, final FramedGraph<OrientBaseGraph> orientDbGraph)
    {
        final List<ODocument> matchingUsers = ndexDatabase.query(new OSQLSynchQuery<Object>("select from User where username = '" + username + "'"));
        if (!matchingUsers.isEmpty())
            return new User(orientDbGraph.getVertex(matchingUsers.get(0), IUser.class), true);
        else
            return null;
    }
    
    /**************************************************************************
    * Tells EasyMock to return the value of loggedInUser when getAttribute()
    * is called on the mock HTTP request.
    * 
    * @param loggedInUser
    *            The user to emulate being logged in.
    **************************************************************************/
    public static void setLoggedInUser(final User loggedInUser, HttpServletRequest mockRequest)
    {
        EasyMock.expect(mockRequest.getAttribute("User"))
            .andReturn(loggedInUser)
            .anyTimes();

        EasyMock.replay(mockRequest);
    }
}
