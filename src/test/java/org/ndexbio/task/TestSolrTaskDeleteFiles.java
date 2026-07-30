package org.ndexbio.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.Test;
import org.ndexbio.common.models.dao.DeletedFileIds;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.TaskType;

/**
 * Covers the persistence round-trip of the batch Solr delete.
 *
 * <p>This matters because queued-but-unrun tasks are replayed from the DB at startup
 * ({@code NdexHttpServletDispatcher.populateQueuedTasksFromDB} → {@code NdexSystemTask.createSystemTask}),
 * which dispatches on {@link TaskType}. This task shares {@code SYS_SOLR_DELETE_NETWORK} with
 * {@code SolrTaskDeleteNetwork} — {@code TaskType} lives in {@code ndex-object-model}, so adding a value
 * would force a dependency bump — so its id-list attributes are what tell the two apart. If that
 * discrimination broke, a batch delete surviving a restart would silently degrade into a single-network
 * delete of the root folder id and every descendant's index doc would be orphaned.
 */
public class TestSolrTaskDeleteFiles {

	private static final UUID ROOT = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID SUBFOLDER = UUID.fromString("22222222-2222-2222-2222-222222222222");
	private static final UUID NETWORK = UUID.fromString("33333333-3333-3333-3333-333333333333");
	private static final UUID SHORTCUT = UUID.fromString("44444444-4444-4444-4444-444444444444");

	private static DeletedFileIds sampleIds() {
		return new DeletedFileIds(List.of(ROOT, SUBFOLDER), List.of(NETWORK), List.of(SHORTCUT));
	}

	@Test
	public void createTaskRecordsTheRootAsResourceAndTheIdsAsAttributes() {
		Task t = new SolrTaskDeleteFiles(ROOT, sampleIds()).createTask();

		// Task.setResource holds a single value, so it carries the file the user acted on.
		assertEquals(ROOT.toString(), t.getResource());
		assertEquals(TaskType.SYS_SOLR_DELETE_NETWORK, t.getTaskType());
		assertNotNull(t.getAttribute(SolrTaskDeleteFiles.folderIdsAttr));
		assertNotNull(t.getAttribute(SolrTaskDeleteFiles.networkIdsAttr));
		assertNotNull(t.getAttribute(SolrTaskDeleteFiles.shortcutIdsAttr));
	}

	@Test
	public void aPersistedBatchDeleteIsReconstructedAsABatchDelete() throws Exception {
		Task persisted = new SolrTaskDeleteFiles(ROOT, sampleIds()).createTask();

		NdexSystemTask restored = NdexSystemTask.createSystemTask(persisted);

		assertTrue("must not degrade into a single-network delete",
				restored instanceof SolrTaskDeleteFiles);
	}

	@Test
	public void idsSurviveTheJsonbRoundTripAsStrings() {
		// Attributes go to jsonb and come back as a generic Map, so a JSON array arrives as a List of
		// Strings rather than the List<UUID> that was written.
		Task persisted = new SolrTaskDeleteFiles(ROOT, sampleIds()).createTask();
		Task reread = new Task();
		reread.setResource(persisted.getResource());
		reread.setTaskType(persisted.getTaskType());
		reread.setAttribute(SolrTaskDeleteFiles.folderIdsAttr,
				List.of(ROOT.toString(), SUBFOLDER.toString()));
		reread.setAttribute(SolrTaskDeleteFiles.networkIdsAttr, List.of(NETWORK.toString()));
		reread.setAttribute(SolrTaskDeleteFiles.shortcutIdsAttr, List.of(SHORTCUT.toString()));

		assertEquals(List.of(ROOT, SUBFOLDER),
				SolrTaskDeleteFiles.idsFromAttribute(reread.getAttribute(SolrTaskDeleteFiles.folderIdsAttr)));
		assertEquals(List.of(NETWORK),
				SolrTaskDeleteFiles.idsFromAttribute(reread.getAttribute(SolrTaskDeleteFiles.networkIdsAttr)));
		assertEquals(List.of(SHORTCUT),
				SolrTaskDeleteFiles.idsFromAttribute(reread.getAttribute(SolrTaskDeleteFiles.shortcutIdsAttr)));
	}

	@Test
	public void aPersistedSingleNetworkDeleteStillReconstructsAsOne() throws Exception {
		// Rows written before this task existed carry no id-list attributes and must keep taking the
		// original path — the discrimination has to be backward compatible.
		Task legacy = new SolrTaskDeleteNetwork(NETWORK, true, null).createTask();

		NdexSystemTask restored = NdexSystemTask.createSystemTask(legacy);

		assertTrue(restored instanceof SolrTaskDeleteNetwork);
	}

	@Test
	public void anEmptyBatchRoundTripsAsEmpty() {
		// Callers skip enqueuing an empty batch, so this is only about the attribute encoding surviving a
		// list with nothing in it rather than writing something a reconstruction would misread.
		Task t = new SolrTaskDeleteFiles(ROOT, DeletedFileIds.empty()).createTask();

		assertEquals(ROOT.toString(), t.getResource());
		assertTrue(SolrTaskDeleteFiles.idsFromAttribute(t.getAttribute(SolrTaskDeleteFiles.folderIdsAttr))
				.isEmpty());
	}

	@Test
	public void deletedFileIdsIsImmutableAndFlattensAllTypes() {
		DeletedFileIds ids = sampleIds();

		assertEquals(4, ids.size());
		assertEquals(List.of(ROOT, SUBFOLDER, NETWORK, SHORTCUT), ids.all());
		assertTrue(DeletedFileIds.empty().isEmpty());

		// Defensive copies, so a caller cannot mutate what the DAO reported.
		assertSame(ids.folders(), ids.folders());
		org.junit.Assert.assertThrows(UnsupportedOperationException.class,
				() -> ids.folders().add(UUID.randomUUID()));
	}
}
