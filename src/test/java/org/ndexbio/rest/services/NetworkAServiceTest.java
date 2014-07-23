package org.ndexbio.rest.services;

import static org.junit.Assert.*;

import javax.servlet.http.HttpServletRequest;

import org.junit.AfterClass;
import org.junit.Test;
import org.ndexbio.common.exceptions.NdexException;
import org.ndexbio.model.object.SimpleNetworkQuery;
import org.easymock.EasyMock;

public class NetworkAServiceTest {

    private static final NetworkAService _networkService = new NetworkAService(EasyMock.createMock(HttpServletRequest.class));

	@Test
	public void test() throws IllegalArgumentException, NdexException {
		SimpleNetworkQuery s = new SimpleNetworkQuery();
		s.setSearchString("*");
	     _networkService.searchNetwork(s, 1, 1);
		
	}

}
