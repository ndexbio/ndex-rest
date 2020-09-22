package org.ndexbio.common.persistence;

import java.util.Map;
import static org.junit.Assert.*;
import org.junit.Test;
import org.ndexbio.model.exceptions.NdexException;

/**
 *
 * @author churas
 */
public class TestAspectElementIdTracker {
	
	@Test
	public void testConstructor(){
		AspectElementIdTracker tracker = new AspectElementIdTracker("foo");
		assertFalse(tracker.hasUndefinedIds());
		assertTrue(tracker.getUndefinedIds().isEmpty());
		assertEquals(0, tracker.getDefinedElementSize());
		assertNull(tracker.checkUndefinedIds());
	}
	
	@Test
	public void testAddingSingleReferenceIdAndNoDefined(){
		AspectElementIdTracker tracker = new AspectElementIdTracker("foo");
		assertFalse(tracker.hasUndefinedIds());
		tracker.addReferenceId(2L, "aspectTwo");
		assertTrue(tracker.hasUndefinedIds());
		Map<Long, String> undefIds = tracker.getUndefinedIds();
		assertEquals(1, undefIds.size());
		assertEquals("aspectTwo", undefIds.get(2L));
		assertEquals(0, tracker.getDefinedElementSize());
		assertEquals("There are 1 missing elements in aspect foo, and these "
				+ "are the element ids that are referenced in other aspects "
				+ "but missing in this aspect: 2 in aspectTwo.",
				tracker.checkUndefinedIds());
	}
	
	@Test
	public void testAddingThreeReferenceIdsAndOneDefined() throws NdexException {
		AspectElementIdTracker tracker = new AspectElementIdTracker("foo");
		assertFalse(tracker.hasUndefinedIds());
		tracker.addReferenceId(1L, "aspectOne");
		tracker.addReferenceId(2L, "aspectTwo");
		tracker.addReferenceId(3L, "aspectThree");
		
		assertTrue(tracker.hasUndefinedIds());
		
		Map<Long, String> undefIds = tracker.getUndefinedIds();
		assertEquals(3, undefIds.size());
		assertEquals("aspectOne", undefIds.get(1L));
		assertEquals("aspectTwo", undefIds.get(2L));
		assertEquals("aspectThree", undefIds.get(3L));
		
		tracker.addDefinedElementId(2L);
		undefIds = tracker.getUndefinedIds();
		assertEquals(2, undefIds.size());
		assertEquals("aspectOne", undefIds.get(1L));
		assertEquals("aspectThree", undefIds.get(3L));

		assertEquals(1, tracker.getDefinedElementSize());
		assertEquals("There are 2 missing elements in aspect foo, and these "
				+ "are the element ids that are referenced in other aspects "
				+ "but missing in this aspect: 1 in aspectOne, 3 in aspectThree.",
				tracker.checkUndefinedIds());
	}
	
	@Test
	public void testAddDuplicateDefinedElementId() throws NdexException {
		AspectElementIdTracker tracker = new AspectElementIdTracker("yo");
		tracker.addDefinedElementId(1L);
		assertFalse(tracker.hasUndefinedIds());
		assertEquals(1, tracker.getDefinedElementSize());
		try {
			tracker.addDefinedElementId(1L);
			fail("Expected NdexException");
		} catch(NdexException ne){
			assertEquals("Duplicate Id 1 found in aspect yo", ne.getMessage());
		}
	}
	
	@Test
	public void testCheckUndefinedIdsWith21UndefinedElements(){
		AspectElementIdTracker tracker = new AspectElementIdTracker("yo");
		StringBuilder sb = new StringBuilder();
		for(long i = 1; i <= 21 ; i++){
			tracker.addReferenceId(i, "x" + Long.toString(i));
			if (i <= 20){
				sb.append(Long.toString(i));
				sb.append(" in x");
				sb.append(Long.toString(i));
				if (i < 20){
					sb.append(", ");
				}
			}
		}
		assertEquals(21, tracker.getUndefinedIds().size());
		assertEquals("There are 21 missing elements in aspect yo, and these "
				+ "are the element ids that are referenced in other aspects "
				+ "but missing in this aspect: " + sb.toString() + ",...",
				tracker.checkUndefinedIds());
	}
}
