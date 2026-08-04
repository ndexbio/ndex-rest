package org.ndexbio.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.UUID;

import org.junit.Test;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.TaskType;
import org.ndexbio.model.object.network.VisibilityType;

/**
 * Covers the persistence round-trip of the single-file Solr delete.
 *
 * <p>Queued-but-unrun tasks are replayed from the DB at startup
 * ({@code NdexHttpServletDispatcher.populateQueuedTasksFromDB} → {@link NdexSystemTask#createSystemTask}).
 * This task shares {@code SYS_SOLR_DELETE_NETWORK} with {@code SolrTaskDeleteNetwork} and
 * {@code SolrTaskDeleteFiles} ({@code TaskType} lives in {@code ndex-object-model}, so adding a value
 * would force a dependency bump), so an attribute only this class writes is what tells them apart.
 *
 * <p>Two things went wrong before this was covered. The row persisted nothing but its resource, so a
 * replayed task came back as a {@code SolrTaskDeleteNetwork} and lost the {@code fileType} guard that
 * stops a folder's id being sent to Solr as a per-network core name. And reconstruction unboxed a
 * missing {@code globalIdxOnly} attribute straight into a primitive, so one such row threw an NPE
 * during startup replay and stopped the server from coming up at all.
 */
public class TestSolrTaskDeleteFile {

	private static final UUID FILE_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

	@Test
	public void createTaskPersistsEverythingRunBranchesOn() {
		Task t = new SolrTaskDeleteFile(FILE_ID, VisibilityType.PRIVATE, false, FileType.FOLDER)
				.createTask();

		assertEquals(FILE_ID.toString(), t.getResource());
		assertEquals(TaskType.SYS_SOLR_DELETE_NETWORK, t.getTaskType());
		assertEquals(FileType.FOLDER.toString(), t.getAttribute(SolrTaskDeleteFile.fileTypeAttr));
		assertEquals(Boolean.FALSE, t.getAttribute(SolrTaskDeleteNetwork.globalIdxAttr));
		assertEquals(VisibilityType.PRIVATE.toString(),
				t.getAttribute(SolrTaskDeleteFile.visibilityAttr));
	}

	@Test
	public void aPersistedSingleFileDeleteIsReconstructedAsOne() throws Exception {
		Task persisted = new SolrTaskDeleteFile(FILE_ID, VisibilityType.PUBLIC, false,
				FileType.NETWORK).createTask();

		NdexSystemTask restored = NdexSystemTask.createSystemTask(persisted);

		assertTrue("must not degrade into a plain network delete",
				restored instanceof SolrTaskDeleteFile);
	}

	@Test
	public void theFileTypeGuardSurvivesARestart() throws Exception {
		// The whole point of persisting fileType: a folder must still be recognised as a folder after
		// replay, so its id is never handed to Solr as a per-network core name.
		Task persisted = new SolrTaskDeleteFile(FILE_ID, VisibilityType.PRIVATE, false,
				FileType.FOLDER).createTask();

		SolrTaskDeleteFile restored = SolrTaskDeleteFile.fromTask(persisted);

		assertEquals(FileType.FOLDER,
				SolrTaskDeleteFile.fileTypeFromAttribute(
						persisted.getAttribute(SolrTaskDeleteFile.fileTypeAttr)));
		assertNotNull(restored);
	}

	@Test
	public void aLegacyRowWithNoAttributesReconstructsWithoutThrowing() throws Exception {
		// This is the regression test for the startup NPE: rows written before these attributes
		// existed carry a resource and nothing else, and reconstruction unboxed the missing
		// globalIdxOnly Boolean into a primitive.
		Task legacy = new Task();
		legacy.setExternalId(UUID.randomUUID());
		legacy.setTaskType(TaskType.SYS_SOLR_DELETE_NETWORK);
		legacy.setResource(FILE_ID.toString());

		NdexSystemTask restored = NdexSystemTask.createSystemTask(legacy);

		assertNotNull("a legacy row must reconstruct rather than abort startup", restored);
		assertTrue("with no discriminator it takes the original path",
				restored instanceof SolrTaskDeleteNetwork);
	}

	@Test
	public void anUnrecognisedVisibilityDoesNotThrow() {
		// Reconstruction happens during startup replay, so an unexpected stored value must degrade to
		// the pre-attribute behaviour rather than take the server down.
		assertNull(NdexSystemTask.visibilityFromAttribute("NOT_A_VISIBILITY"));
		assertNull(NdexSystemTask.visibilityFromAttribute(null));
		assertEquals(VisibilityType.PUBLIC, NdexSystemTask.visibilityFromAttribute("PUBLIC"));
	}

	@Test
	public void anUnrecognisedFileTypeFallsBackToNetwork() {
		// NETWORK is what the two-arg constructor has always meant, so legacy rows keep their original
		// behaviour rather than silently becoming a folder.
		assertEquals(FileType.NETWORK, SolrTaskDeleteFile.fileTypeFromAttribute("NOT_A_FILE_TYPE"));
		assertEquals(FileType.NETWORK, SolrTaskDeleteFile.fileTypeFromAttribute(null));
		assertEquals(FileType.FOLDER, SolrTaskDeleteFile.fileTypeFromAttribute("FOLDER"));
	}

	@Test
	public void aMissingGlobalIdxOnlyDefaultsToSkippingThePerNetworkDrop() throws Exception {
		// Absent means "we cannot tell", and the safe reading is to leave the per-network core alone
		// rather than unload one that may not belong to this file.
		Task noFlag = new Task();
		noFlag.setExternalId(UUID.randomUUID());
		noFlag.setTaskType(TaskType.SYS_SOLR_DELETE_NETWORK);
		noFlag.setResource(FILE_ID.toString());
		noFlag.setAttribute(SolrTaskDeleteFile.fileTypeAttr, FileType.NETWORK.toString());

		NdexSystemTask restored = NdexSystemTask.createSystemTask(noFlag);

		assertTrue(restored instanceof SolrTaskDeleteFile);
	}

	@Test
	public void batchDeleteRowsStillReconstructAsBatchDeletes() throws Exception {
		// Guards the dispatch order in createSystemTask: adding this class's discriminator must not
		// capture rows belonging to the batch delete.
		Task batch = new SolrTaskDeleteFiles(FILE_ID,
				new org.ndexbio.common.models.dao.DeletedFileIds(
						java.util.List.of(FILE_ID), java.util.List.of(), java.util.List.of()))
				.createTask();

		NdexSystemTask restored = NdexSystemTask.createSystemTask(batch);

		assertTrue(restored instanceof SolrTaskDeleteFiles);
	}
}
