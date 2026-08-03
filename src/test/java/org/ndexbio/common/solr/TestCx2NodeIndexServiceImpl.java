package org.ndexbio.common.solr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.ndexbio.model.exceptions.NdexException;

/**
 * Unit tests for {@link Cx2NodeIndexServiceImpl}.
 *
 * <p>The reason this class exists: an unreadable aspect directory used to be indistinguishable
 * from a network with nothing to index, so a reindex running as the wrong OS user produced empty
 * Solr cores while reporting success. Every "cannot read" path below must now throw rather than
 * return an empty map, and say so in one line naming the path and the user.
 */
@RunWith(JUnit4.class)
public class TestCx2NodeIndexServiceImpl {

	private static final String NETWORK_ID = "ec25aeeb-fb51-11ef-b81d-005056ae3c32";

	/** Declares name via the short alias "n", as CX2 writes it for most networks. */
	private static final String DECL_ALIAS_FORM =
			"[{\"nodes\":{\"name\":{\"a\":\"n\",\"d\":\"string\"}}}]";

	/** Declares name with no alias, the shape WikiPathways networks use. */
	private static final String DECL_PLAIN_FORM =
			"[{\"nodes\":{\"name\":{\"d\":\"string\"}}}]";

	@Rule
	public TemporaryFolder tmpFolder = new TemporaryFolder();

	/** Anything we strip permissions from, so teardown can still delete the temp tree. */
	private final List<File> permissionsToRestore = new ArrayList<>();

	@After
	public void restorePermissions() {
		for (File f : permissionsToRestore) {
			f.setReadable(true);
			f.setExecutable(true);
			f.setWritable(true);
		}
		permissionsToRestore.clear();
	}

	// ------------------------------------------------------------------ helpers

	private File aspectDirPath() {
		return new File(tmpFolder.getRoot(), "data/" + NETWORK_ID + "/aspects_cx2");
	}

	private File aspectDir() throws IOException {
		File dir = aspectDirPath();
		Files.createDirectories(dir.toPath());
		return dir;
	}

	private void write(File dir, String fileName, String content) throws IOException {
		Files.write(new File(dir, fileName).toPath(), content.getBytes(StandardCharsets.UTF_8));
	}

	private Cx2NodeIndexService service() {
		return new Cx2NodeIndexServiceImpl(tmpFolder.getRoot().getAbsolutePath());
	}

	private void denyAccess(File f) {
		permissionsToRestore.add(f);
		// Removing only the read bit on a directory still allows access by exact path; the
		// traversal bit has to go too, which is what mode drwxr-x--- denies another user.
		assertTrue("could not clear read permission on " + f, f.setReadable(false));
		if (f.isDirectory()) {
			assertTrue("could not clear execute permission on " + f, f.setExecutable(false));
		}
	}

	private String expectNdexException(Cx2NodeIndexService svc) throws IOException {
		try {
			svc.loadNodeIndexEntries(NETWORK_ID);
			fail("expected NdexException for an unreadable node index source");
			return null;
		} catch (NdexException e) {
			assertNotNull("exception must carry a message", e.getMessage());
			return e.getMessage();
		}
	}

	/** Permission-based cases are meaningless as root, which bypasses the checks entirely. */
	private void assumeNotRoot() {
		Assume.assumeFalse("test requires a non-root user",
				"root".equals(System.getProperty("user.name")));
	}

	// ------------------------------------------- unreadable / absent: must throw

	/**
	 * The message deliberately states one known fact - this path could not be read, by this user -
	 * without probing the filesystem to guess why. Introspecting to produce a nicer error is how
	 * an error path acquires its own failure modes, and the earlier attempt at it reported a
	 * permission problem as missing data.
	 */
	private void assertNotAccessible(String msg, File declFile) {
		assertTrue("should say the path is not accessible, got: " + msg,
				msg.contains("is not accessible"));
		assertTrue("should name the declarations file, got: " + msg,
				msg.contains(declFile.getAbsolutePath()));
		assertTrue("should name the OS user, got: " + msg,
				msg.contains(System.getProperty("user.name")));
		assertTrue("should name the network, got: " + msg, msg.contains(NETWORK_ID));
	}

	@Test
	public void unreadableAspectDirectoryThrows() throws Exception {
		assumeNotRoot();
		File dir = aspectDir();
		write(dir, "attributeDeclarations", DECL_ALIAS_FORM);
		write(dir, "nodes", "[{\"id\":0,\"v\":{\"n\":\"FBXL13\"}}]");
		denyAccess(dir);

		assertNotAccessible(expectNdexException(service()), new File(dir, "attributeDeclarations"));
	}

	@Test
	public void missingAspectDirectoryThrows() throws Exception {
		// no directory created at all
		assertNotAccessible(expectNdexException(service()),
				new File(aspectDirPath(), "attributeDeclarations"));
	}

	@Test
	public void unreadableDeclarationFileThrows() throws Exception {
		assumeNotRoot();
		File dir = aspectDir();
		write(dir, "attributeDeclarations", DECL_ALIAS_FORM);
		write(dir, "nodes", "[{\"id\":0,\"v\":{\"n\":\"FBXL13\"}}]");
		File declFile = new File(dir, "attributeDeclarations");
		denyAccess(declFile);

		assertNotAccessible(expectNdexException(service()), declFile);
	}

	@Test
	public void missingDeclarationFileThrows() throws Exception {
		File dir = aspectDir();
		write(dir, "nodes", "[{\"id\":0,\"v\":{\"n\":\"FBXL13\"}}]");

		assertNotAccessible(expectNdexException(service()), new File(dir, "attributeDeclarations"));
	}

	@Test
	public void theFailureMessageIsASingleLineWithNoIntrospection() throws Exception {
		// no directory at all - the reporting path must not walk the filesystem to explain itself
		String msg = expectNdexException(service());
		assertFalse("must not claim to know whether the data is missing, got: " + msg,
				msg.contains("missing"));
		assertFalse("must not guess at an ancestor directory, got: " + msg,
				msg.contains("denied access at"));
		assertEquals("the message must stay one line", 1, msg.split("\n").length);
	}

	@Test
	public void missingNodesFilePropagatesRatherThanReturningEmpty() throws Exception {
		File dir = aspectDir();
		write(dir, "attributeDeclarations", DECL_ALIAS_FORM);
		// deliberately no "nodes" file

		try {
			service().loadNodeIndexEntries(NETWORK_ID);
			fail("a missing nodes aspect must not be reported as an empty index");
		} catch (IOException expected) {
			assertNotNull(expected);
		}
	}

	// ------------------------------------- readable but declaration-free: empty, no throw

	@Test
	public void emptyDeclarationArrayYieldsEmptyMapWithoutThrowing() throws Exception {
		File dir = aspectDir();
		write(dir, "attributeDeclarations", "[]");
		write(dir, "nodes", "[{\"id\":0,\"v\":{\"n\":\"FBXL13\"}}]");

		assertTrue(service().loadNodeIndexEntries(NETWORK_ID).isEmpty());
	}

	@Test
	public void declarationsWithoutNodesAspectYieldEmptyMapWithoutThrowing() throws Exception {
		File dir = aspectDir();
		write(dir, "attributeDeclarations", "[{\"edges\":{\"interaction\":{\"d\":\"string\"}}}]");
		write(dir, "nodes", "[{\"id\":0,\"v\":{\"n\":\"FBXL13\"}}]");

		assertTrue(service().loadNodeIndexEntries(NETWORK_ID).isEmpty());
	}

	@Test
	public void emptyNodeAttributeDeclarationsYieldEmptyMapWithoutThrowing() throws Exception {
		File dir = aspectDir();
		write(dir, "attributeDeclarations", "[{\"nodes\":{}}]");
		write(dir, "nodes", "[{\"id\":0,\"v\":{\"n\":\"FBXL13\"}}]");

		assertTrue(service().loadNodeIndexEntries(NETWORK_ID).isEmpty());
	}

	// ------------------------------------------------------------ happy paths

	@Test
	public void resolvesNodeNameThroughDeclaredAlias() throws Exception {
		File dir = aspectDir();
		write(dir, "attributeDeclarations", DECL_ALIAS_FORM);
		write(dir, "nodes",
				"[{\"id\":0,\"v\":{\"n\":\"FBXL13\"}},{\"id\":1,\"v\":{\"n\":\"ASB2\"}}]");

		Map<Long, NodeIndexEntry> entries = service().loadNodeIndexEntries(NETWORK_ID);

		assertEquals(2, entries.size());
		assertEquals("FBXL13", entries.get(0L).getName());
		assertEquals("ASB2", entries.get(1L).getName());
	}

	@Test
	public void resolvesNodeNameFromFullAttributeNameWhenNoAliasDeclared() throws Exception {
		File dir = aspectDir();
		write(dir, "attributeDeclarations", DECL_PLAIN_FORM);
		write(dir, "nodes", "[{\"id\":7,\"v\":{\"name\":\"CDK2\"}}]");

		Map<Long, NodeIndexEntry> entries = service().loadNodeIndexEntries(NETWORK_ID);

		assertEquals(1, entries.size());
		assertEquals("CDK2", entries.get(7L).getName());
	}

	@Test
	public void nodesWithoutAnIndexableAttributeAreSkipped() throws Exception {
		File dir = aspectDir();
		write(dir, "attributeDeclarations", DECL_ALIAS_FORM);
		// second node carries no attributes at all, as layout-only nodes do
		write(dir, "nodes", "[{\"id\":0,\"v\":{\"n\":\"FBXL13\"}},{\"id\":1}]");

		Map<Long, NodeIndexEntry> entries = service().loadNodeIndexEntries(NETWORK_ID);

		assertEquals("only the named node should be indexed", 1, entries.size());
		assertEquals("FBXL13", entries.get(0L).getName());
	}

	@Test
	public void carriesRepresentsAliasAndProteinFamilyMembers() throws Exception {
		File dir = aspectDir();
		write(dir, "attributeDeclarations",
				"[{\"nodes\":{"
						+ "\"name\":{\"a\":\"n\",\"d\":\"string\"},"
						+ "\"represents\":{\"a\":\"r\",\"d\":\"string\"},"
						+ "\"alias\":{\"d\":\"list_of_string\"},"
						+ "\"type\":{\"d\":\"string\"},"
						+ "\"member\":{\"d\":\"list_of_string\"}}}]");
		write(dir, "nodes",
				"[{\"id\":0,\"v\":{\"n\":\"FAMILY1\",\"r\":\"PROT1\","
						+ "\"alias\":[\"ALIAS1\"],\"type\":\"proteinfamily\","
						+ "\"member\":[\"MEM1\",\"MEM2\"]}}]");

		Map<Long, NodeIndexEntry> entries = service().loadNodeIndexEntries(NETWORK_ID);

		assertEquals(1, entries.size());
		NodeIndexEntry e = entries.get(0L);
		assertEquals("FAMILY1", e.getName());
		assertTrue("represents should carry the declared value: " + e.getRepresents(),
				e.getRepresents().contains("PROT1"));
		assertTrue("proteinfamily members fold into represents: " + e.getRepresents(),
				e.getRepresents().contains("MEM1") && e.getRepresents().contains("MEM2"));
		assertTrue("alias should be carried: " + e.getAliases(),
				e.getAliases().contains("ALIAS1"));
	}
}
