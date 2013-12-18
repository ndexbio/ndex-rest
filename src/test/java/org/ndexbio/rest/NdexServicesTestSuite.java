package org.ndexbio.rest;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.ndexbio.rest.services.TestFeedbackService;
import org.ndexbio.rest.services.TestGroupService;
import org.ndexbio.rest.services.TestRequestService;
import org.ndexbio.rest.services.TestTaskService;
import org.ndexbio.rest.services.TestUserService;

@RunWith(Suite.class)
@SuiteClasses({ TestFeedbackService.class, TestGroupService.class, TestRequestService.class, TestTaskService.class, TestUserService.class })
public class NdexServicesTestSuite
{
}
