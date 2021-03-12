package org.ndexbio.common.util;

import static org.junit.Assert.*;

import java.util.TreeSet;

import org.junit.Test;

public class UtilTest {

	@Test
	public void testGenerateRandomId() {
		TreeSet<Long> r = Util.generateRandomId(10, 10);
		assertEquals(r.size(), 10);
		System.out.println(r);
		
		r = Util.generateRandomId(6, 10);
		assertEquals(r.size(), 6);
		assertTrue(r.first().longValue()>=0);
		assertTrue(r.last().longValue()<10);
		System.out.println(r);
	}

}
