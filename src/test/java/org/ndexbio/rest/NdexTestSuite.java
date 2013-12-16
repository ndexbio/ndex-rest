package org.ndexbio.rest;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.ndexbio.rest.services.FeedbackServiceTest;
import org.ndexbio.rest.services.GroupServiceTest;
import org.ndexbio.rest.services.NdexServiceTest;
import org.ndexbio.rest.services.RequestServiceTest;
import org.ndexbio.rest.services.TaskServiceTest;
import org.ndexbio.rest.services.UserServiceTest;

@RunWith(Suite.class)
@SuiteClasses({ CreateTestDatabase.class, NdexServiceTest.class, FeedbackServiceTest.class, GroupServiceTest.class, RequestServiceTest.class, TaskServiceTest.class, UserServiceTest.class })
public class NdexTestSuite
{

}
