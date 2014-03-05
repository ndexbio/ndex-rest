package org.ndexbio.rest.services;

import org.easymock.IAnswer;
import org.ndexbio.common.models.object.User;

public class TestUserAnswer implements IAnswer<User> {

	private TestNdexService _testNdexService;
	
	public TestUserAnswer(TestNdexService testNdexService) {
		_testNdexService = testNdexService;
	}

	@Override
	public User answer() throws Throwable {
		return _testNdexService.getUser("dexterpratt");
	}

}
