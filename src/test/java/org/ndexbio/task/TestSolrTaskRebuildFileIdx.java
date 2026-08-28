package org.ndexbio.task;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.UUID;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.ndexbio.common.NdexClasses;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.TestConfigHelper;

/**
 * Covers the two decisions this task makes about a network that failed to load.
 *
 * <p>A network whose CX2 failed validation is left with {@code aspects_cx2/networkAttributes} on disk
 * and {@code aspects_cx2/attributeDeclarations} never written, because the loader throws before it
 * gets there. Reindexing such a network - which any move through
 * {@code BatchService.moveNetworksToFolder} triggers - used to read the declarations file with only
 * the attributes file checked for existence, throw {@code FileNotFoundException}, and then write
 * "Failed to create Index on network..." straight over the CX2 validation message that was the only
 * record of what was actually wrong. That made the folder-listing assertion in the integration test
 * pass or fail on whether the async index task beat the next read.
 */
public class TestSolrTaskRebuildFileIdx {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@BeforeClass
	public static void initConfig() throws Exception {
		TestConfigHelper.initIfNeeded();
	}

	private SolrTaskRebuildFileIdx task(boolean ignoreCxFiles) {
		return new SolrTaskRebuildFileIdx(UUID.randomUUID(), UUID.randomUUID(), "someowner",
				VisibilityType.PRIVATE, FileType.NETWORK, false, ignoreCxFiles);
	}

	// ── which aspect files are usable ────────────────────────────────────────────

	@Test
	public void bothAspectFilesPresentIsTheOnlyReadableCase() throws Exception {
		File attr = tmp.newFile("networkAttributes");
		File decl = tmp.newFile("attributeDeclarations");

		assertTrue(task(false).canReadCx2NetworkAttributes(attr, decl));
	}

	@Test
	public void aFailedLoadLeavesTheAttributesWithoutTheirDeclarations() throws Exception {
		// The regression: networkAttributes written, attributeDeclarations absent. Reading the
		// declarations here is what threw and produced the spurious index error.
		File attr = tmp.newFile("networkAttributes");
		File decl = new File(tmp.getRoot(), "attributeDeclarations");

		assertFalse("a missing attributeDeclarations must divert to the aspect iterator, not be read",
				task(false).canReadCx2NetworkAttributes(attr, decl));
	}

	@Test
	public void declarationsWithoutAttributesAreNotReadEither() throws Exception {
		File attr = new File(tmp.getRoot(), "networkAttributes");
		File decl = tmp.newFile("attributeDeclarations");

		assertFalse(task(false).canReadCx2NetworkAttributes(attr, decl));
	}

	@Test
	public void ignoreCxFilesStillWinsOverBothFilesBeingPresent() throws Exception {
		File attr = tmp.newFile("networkAttributes");
		File decl = tmp.newFile("attributeDeclarations");

		assertFalse(task(true).canReadCx2NetworkAttributes(attr, decl));
	}

	// ── which error message survives ─────────────────────────────────────────────

	@Test
	public void aCx2ValidationMessageIsNeverOverwritten() {
		String validationError = "Attribute name is not declared in attributeDeclarations.";

		assertFalse("a load-time diagnosis outranks an index-time one",
				task(false).shouldRecordIndexError(validationError));
	}

	@Test
	public void anIndexErrorIsRecordedWhenTheRowIsClean() {
		assertTrue(task(false).shouldRecordIndexError(null));
	}

	@Test
	public void anEarlierIndexErrorIsRefreshedRatherThanKept() {
		// Two index failures in a row: the newer cause is the useful one, so this must still write.
		String previous = NdexClasses.NETWORK_INDEX_FAILED_MSG_PREFIX + " Cause: connection refused";

		assertTrue(task(false).shouldRecordIndexError(previous));
	}
}
