package org.ndexbio.rest.services;

import static org.junit.Assert.*;

import javax.servlet.http.HttpServletRequest;

import org.junit.AfterClass;
import org.junit.Test;
import org.ndexbio.common.exceptions.NdexException;
import org.ndexbio.model.object.SimpleNetworkQuery;
import org.easymock.EasyMock;

public class NetworkAServiceTest  extends TestNdexService{

    private static final NetworkAService _networkService = new NetworkAService(_mockRequest);

	@Test
	public void test() throws IllegalArgumentException, NdexException {
		SimpleNetworkQuery s = new SimpleNetworkQuery();
		s.setSearchString("ca");
		s.setAccountName("");
		try {
			assertTrue(_networkService.searchNetwork(s, 0, 1).size() == 1);
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		
	}

}
