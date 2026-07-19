package org.ndexbio.common.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.rest.Configuration;
import org.ndexbio.server.migration.v3.NFSReIndexer;

/**
 * Command-line database maintenance tool, runnable from the deployed {@code .war} (see
 * {@code DbMigrationTool-CLI.md}). Bootstraps {@link Configuration} and {@link NdexDatabase} exactly
 * like {@code SolrIndexBuilder.main}, then dispatches on {@code args[0]}.
 *
 * <p>Two orthogonal entrypoints, each runnable independently in any order:</p>
 * <ul>
 *   <li>{@code transform-accesskey-shortcuts} — realigns DB state for the access-key-via-folders
 *       change (issue #133). Groups shortcuts that live in a folder with an effective access key by
 *       their target. When a target's keyed-folder shortcuts all sit in exactly ONE keyed folder, the
 *       target is reparented into that folder as a real child and those shortcuts are deleted, so the
 *       folder key reaches it again. A target is skipped when its keyed-folder shortcuts span more than
 *       one keyed folder (can't parent it under all of them), or the target is dangling, cross-owner, or
 *       (folder targets) would create a cycle. Skips are reported.</li>
 *   <li>{@code privatize-folders} — patches any PUBLIC folder whose (direct) children are all private
 *       to {@code visibility=PRIVATE}.</li>
 * </ul>
 *
 * <p>A single {@code --apply} flag controls writes: absent =&gt; dry-run (report only, no writes, no
 * reindex); present =&gt; perform the DB writes, then run a full v3 reindex
 * ({@link NFSReIndexer}, the same routine behind {@code /v3/admin/reindex-v3}). Both entrypoints are
 * idempotent and safe to re-run.</p>
 */
public class DbMigrationTool implements AutoCloseable {

	static final String CMD_TRANSFORM = "transform-accesskey-shortcuts";
	static final String CMD_PRIVATIZE = "privatize-folders";
	static final String APPLY_FLAG = "--apply";

	private final Connection db;

	public DbMigrationTool() throws SQLException {
		this.db = NdexDatabase.getInstance().getConnection();
		this.db.setAutoCommit(false);
	}

	/** Test seam. */
	DbMigrationTool(Connection conn) {
		this.db = conn;
	}

	public static void main(String[] args) throws Exception {
		// Console-only logging for this CLI: point logback at the bundled console-only config BEFORE any
		// class touches logback, so the WAR's logback.xml (RollingFileAppender -> ../logs/ndex.log) is
		// never loaded and no log files are written. Must stay the first statement in main().
		System.setProperty("logback.configurationFile", "logback-cli.xml");

		Configuration configuration = Configuration.createInstance();
		NdexDatabase.createNdexDatabase(configuration.getDBURL(), configuration.getDBUser(),
				configuration.getDBPasswd(), 5);

		if (args.length < 1) {
			usage();
			return;
		}

		String command = args[0];
		boolean apply = false;
		for (int i = 1; i < args.length; i++) {
			if (APPLY_FLAG.equals(args[i]))
				apply = true;
		}

		if (!CMD_TRANSFORM.equals(command) && !CMD_PRIVATIZE.equals(command)) {
			usage();
			return;
		}

		report((apply ? "APPLY" : "DRY-RUN") + " mode. Command: " + command);

		try (DbMigrationTool tool = new DbMigrationTool()) {
			switch (command) {
			case CMD_TRANSFORM:
				tool.transformAccessKeyShortcuts(apply);
				break;
			case CMD_PRIVATIZE:
				tool.privatizeFolders(apply);
				break;
			default:
				usage();
				return;
			}
		}

		if (apply) {
			report("DB migration complete. Kicking off full v3 reindex (networks, folders, shortcuts)...");
			try (NFSReIndexer reIndexer = new NFSReIndexer()) {
				reIndexer.run();
			}
			report("Reindex complete.");
		} else {
			report("Dry-run complete. No changes were made and no reindex was run. "
					+ "Re-run with " + APPLY_FLAG + " to perform the changes.");
		}
	}

	private static void usage() {
		System.err.println("Usage: DbMigrationTool <command> [--apply]");
		System.err.println("  Commands:");
		System.err.println("    " + CMD_TRANSFORM
				+ "   Reparent single-referrer, owner-owned shortcut targets into their access-key folders.");
		System.err.println("    " + CMD_PRIVATIZE
				+ "              Set PUBLIC folders whose children are all private to PRIVATE.");
		System.err.println("  Without --apply the tool runs in dry-run mode (reports intended changes only).");
	}

	// ------------------------------------------------------------------ transform-accesskey-shortcuts

	public void transformAccessKeyShortcuts(boolean apply) throws SQLException {
		report("Scanning shortcuts in folders that have an access key (directly or by inheritance)...");
		Map<UUID, UUID> keyedFolderOwners = foldersWithEffectiveAccessKey(); // folderId -> ownerId
		Map<UUID, TargetGroup> byTarget = groupKeyedShortcutsByTarget();

		int targetsConverted = 0;
		int shortcutsDeleted = 0;
		List<String> skipped = new ArrayList<>();

		for (Map.Entry<UUID, TargetGroup> e : byTarget.entrySet()) {
			UUID target = e.getKey();
			TargetGroup g = e.getValue();

			// Gate: the target's keyed-folder shortcuts must all sit in exactly ONE keyed folder. If they
			// span more than one, the target can't be parented under all of them at once -> skip.
			if (g.folderIds.size() > 1) {
				skipped.add(target + " (" + g.targetType + "): in " + g.folderIds.size() + " keyed folders");
				continue;
			}
			UUID folderId = g.folderIds.iterator().next();
			UUID folderOwner = keyedFolderOwners.get(folderId);

			// dangling: the target must be a live network/folder.
			UUID targetOwner = targetOwnerIfLive(target, g.targetType);
			if (targetOwner == null) {
				skipped.add(target + " (" + g.targetType + "): dangling");
				continue;
			}
			// cross-owner: never relocate another user's item into this folder.
			if (folderOwner == null || !targetOwner.equals(folderOwner)) {
				skipped.add(target + " (" + g.targetType + "): cross-owner");
				continue;
			}
			// folder targets only: guard against creating a cycle.
			if ("FOLDER".equalsIgnoreCase(g.targetType) && wouldCreateCycle(target, folderId)) {
				skipped.add(target + " (" + g.targetType + "): would-create-cycle");
				continue;
			}

			if (apply) {
				reparentTarget(target, g.targetType, folderId);
				for (UUID shortcutId : g.shortcutIds)
					deleteShortcut(shortcutId); // remove all (now-redundant) shortcuts to this target in the folder
				db.commit();
				report("Converted " + g.targetType + " " + target + " -> reparented into folder " + folderId
						+ "; deleted " + g.shortcutIds.size() + " shortcut(s)");
			} else {
				report("Would convert " + g.targetType + " " + target + " -> reparent into folder " + folderId
						+ "; delete " + g.shortcutIds.size() + " shortcut(s)");
			}
			targetsConverted++;
			shortcutsDeleted += g.shortcutIds.size();
		}

		// Detail list first, so the count tally below is always the last thing this command prints.
		report("");
		if (!skipped.isEmpty()) {
			report("Skipped targets (not transformable):");
			for (String s : skipped)
				report("  - " + s);
			report("");
		}
		report("==== " + CMD_TRANSFORM + " summary (" + (apply ? "applied" : "dry-run") + ") ====");
		report((apply ? "Targets converted to real children: " : "Targets that would be converted: ") + targetsConverted);
		report((apply ? "Shortcuts deleted: " : "Shortcuts that would be deleted: ") + shortcutsDeleted);
		report("Targets skipped (not transformable): " + skipped.size());
	}

	/**
	 * All live shortcuts that sit in a folder with an effective access key, grouped by their target.
	 * For each target we track the distinct keyed parent folders holding a shortcut to it (the gate) and
	 * every such shortcut's id (so a transform can delete them all).
	 */
	private Map<UUID, TargetGroup> groupKeyedShortcutsByTarget() throws SQLException {
		String sql = "WITH RECURSIVE keyed_folders AS ("
				+ "  SELECT \"UUID\" FROM folder WHERE access_key_is_on = true AND is_deleted = false"
				+ "  UNION"
				+ "  SELECT f.\"UUID\" FROM folder f JOIN keyed_folders k ON f.parent = k.\"UUID\""
				+ "   WHERE f.is_deleted = false"
				+ ") "
				+ "SELECT s.\"UUID\" AS shortcut_id, s.target, s.target_type, s.parent AS folder_id"
				+ "  FROM shortcut s JOIN keyed_folders kf ON kf.\"UUID\" = s.parent"
				+ " WHERE s.is_deleted = false";
		Map<UUID, TargetGroup> byTarget = new LinkedHashMap<>();
		try (PreparedStatement p = db.prepareStatement(sql); ResultSet rs = p.executeQuery()) {
			while (rs.next()) {
				UUID shortcutId = (UUID) rs.getObject(1);
				UUID target = (UUID) rs.getObject(2);
				String targetType = rs.getString(3);
				UUID folderId = (UUID) rs.getObject(4);
				TargetGroup g = byTarget.computeIfAbsent(target, k -> new TargetGroup(targetType));
				g.shortcutIds.add(shortcutId);
				g.folderIds.add(folderId);
			}
		}
		return byTarget;
	}

	private Map<UUID, UUID> foldersWithEffectiveAccessKey() throws SQLException {
		// Every folder whose own key is on, plus all of their descendants (which inherit the key).
		String sql = "WITH RECURSIVE keyed AS ("
				+ "  SELECT \"UUID\", owneruuid FROM folder WHERE access_key_is_on = true AND is_deleted = false"
				+ "  UNION"
				+ "  SELECT f.\"UUID\", f.owneruuid FROM folder f JOIN keyed k ON f.parent = k.\"UUID\""
				+ "   WHERE f.is_deleted = false"
				+ ") SELECT \"UUID\", owneruuid FROM keyed";
		Map<UUID, UUID> result = new LinkedHashMap<>();
		try (PreparedStatement p = db.prepareStatement(sql); ResultSet rs = p.executeQuery()) {
			while (rs.next()) {
				result.put((UUID) rs.getObject(1), (UUID) rs.getObject(2));
			}
		}
		return result;
	}

	private List<ShortcutChild> shortcutChildren(UUID folderId) throws SQLException {
		String sql = "SELECT \"UUID\", target, target_type FROM shortcut WHERE parent = ? AND is_deleted = false";
		List<ShortcutChild> result = new ArrayList<>();
		try (PreparedStatement p = db.prepareStatement(sql)) {
			p.setObject(1, folderId);
			try (ResultSet rs = p.executeQuery()) {
				while (rs.next()) {
					result.add(new ShortcutChild((UUID) rs.getObject(1), (UUID) rs.getObject(2), rs.getString(3)));
				}
			}
		}
		return result;
	}

	/** @return the live target's owner uuid, or null if the target is missing/deleted (dangling). */
	private UUID targetOwnerIfLive(UUID target, String targetType) throws SQLException {
		String table = "FOLDER".equalsIgnoreCase(targetType) ? "folder" : "network";
		String sql = "SELECT owneruuid FROM " + table + " WHERE \"UUID\" = ? AND is_deleted = false";
		try (PreparedStatement p = db.prepareStatement(sql)) {
			p.setObject(1, target);
			try (ResultSet rs = p.executeQuery()) {
				return rs.next() ? (UUID) rs.getObject(1) : null;
			}
		}
	}

	/** True if reparenting {@code targetFolder} under {@code newParent} would create a cycle,
	 * i.e. {@code newParent} is {@code targetFolder} itself or a descendant of it. */
	private boolean wouldCreateCycle(UUID targetFolder, UUID newParent) throws SQLException {
		String sql = "WITH RECURSIVE sub AS ("
				+ "  SELECT \"UUID\" FROM folder WHERE \"UUID\" = ?"
				+ "  UNION"
				+ "  SELECT f.\"UUID\" FROM folder f JOIN sub s ON f.parent = s.\"UUID\" WHERE f.is_deleted = false"
				+ ") SELECT 1 FROM sub WHERE \"UUID\" = ? LIMIT 1";
		try (PreparedStatement p = db.prepareStatement(sql)) {
			p.setObject(1, targetFolder);
			p.setObject(2, newParent);
			try (ResultSet rs = p.executeQuery()) {
				return rs.next();
			}
		}
	}

	private void reparentTarget(UUID target, String targetType, UUID newParent) throws SQLException {
		String table = "FOLDER".equalsIgnoreCase(targetType) ? "folder" : "network";
		String sql = "UPDATE " + table + " SET parent = ?, modification_time = current_timestamp WHERE \"UUID\" = ?";
		try (PreparedStatement p = db.prepareStatement(sql)) {
			p.setObject(1, newParent);
			p.setObject(2, target);
			p.executeUpdate();
		}
	}

	private void deleteShortcut(UUID shortcutId) throws SQLException {
		try (PreparedStatement p = db.prepareStatement("DELETE FROM shortcut WHERE \"UUID\" = ?")) {
			p.setObject(1, shortcutId);
			p.executeUpdate();
		}
	}

	// ---------------------------------------------------------------------------- privatize-folders

	public void privatizeFolders(boolean apply) throws SQLException {
		report("Scanning PUBLIC folders for all-private children...");
		List<UUID> publicFolders = new ArrayList<>();
		try (PreparedStatement p = db.prepareStatement(
				"SELECT \"UUID\" FROM folder WHERE visibility = 'PUBLIC' AND is_deleted = false");
				ResultSet rs = p.executeQuery()) {
			while (rs.next())
				publicFolders.add((UUID) rs.getObject(1));
		}

		List<UUID> patched = new ArrayList<>();
		int leftPublicExposed = 0; // has children but at least one is non-private -> genuine blocker
		int emptyPublicSkipped = 0; // no children -> not a candidate
		for (UUID folderId : publicFolders) {
			ChildVisibilitySummary vis = childVisibility(folderId);
			if (!vis.hasChild) {
				emptyPublicSkipped++;
				continue; // empty folder -> leave it PUBLIC
			}
			if (vis.anyNonPrivate) {
				leftPublicExposed++;
				continue; // exposes a non-private child -> leave it PUBLIC
			}
			if (apply) {
				try (PreparedStatement p = db.prepareStatement(
						"UPDATE folder SET visibility = 'PRIVATE', modification_time = current_timestamp WHERE \"UUID\" = ?")) {
					p.setObject(1, folderId);
					p.executeUpdate();
				}
				db.commit();
				report("Patched folder " + folderId + " PUBLIC -> PRIVATE (all children private)");
			} else {
				report("Would patch folder " + folderId + " PUBLIC -> PRIVATE (all children private)");
			}
			patched.add(folderId);
		}

		// Detail list first, so the count tally below is always the last thing this command prints.
		report("");
		if (!patched.isEmpty()) {
			report(apply ? "Folders patched to PRIVATE:" : "Folders that would be patched to PRIVATE:");
			for (UUID id : patched)
				report("  - " + id);
			report("");
		}
		// Invariant: patched.size() + leftPublicExposed + emptyPublicSkipped == publicFolders.size()
		report("==== " + CMD_PRIVATIZE + " summary (" + (apply ? "applied" : "dry-run") + ") ====");
		report((apply ? "Folders patched to PRIVATE: " : "Folders that would be patched to PRIVATE: ")
				+ patched.size());
		report((apply ? "Folders left PUBLIC (exposes a non-private child): "
				: "Folders that would be left PUBLIC (exposes a non-private child): ") + leftPublicExposed);
		report("Empty PUBLIC folders skipped (no children): " + emptyPublicSkipped);
	}

	/** Examines a folder's direct children (networks, subfolders, and shortcut targets). */
	private ChildVisibilitySummary childVisibility(UUID folderId) throws SQLException {
		ChildVisibilitySummary s = new ChildVisibilitySummary();

		// direct network + subfolder children
		for (String table : new String[] { "network", "folder" }) {
			String sql = "SELECT visibility FROM " + table + " WHERE parent = ? AND is_deleted = false";
			try (PreparedStatement p = db.prepareStatement(sql)) {
				p.setObject(1, folderId);
				try (ResultSet rs = p.executeQuery()) {
					while (rs.next()) {
						s.hasChild = true;
						if (!"PRIVATE".equalsIgnoreCase(rs.getString(1)))
							s.anyNonPrivate = true;
					}
				}
			}
			if (s.anyNonPrivate)
				return s;
		}

		// shortcut children: resolve to the target's visibility (dangling target counts as private)
		for (ShortcutChild sc : shortcutChildren(folderId)) {
			s.hasChild = true;
			String vis = targetVisibilityIfLive(sc.target, sc.targetType);
			if (vis != null && !"PRIVATE".equalsIgnoreCase(vis)) {
				s.anyNonPrivate = true;
				return s;
			}
		}
		return s;
	}

	private String targetVisibilityIfLive(UUID target, String targetType) throws SQLException {
		String table = "FOLDER".equalsIgnoreCase(targetType) ? "folder" : "network";
		String sql = "SELECT visibility FROM " + table + " WHERE \"UUID\" = ? AND is_deleted = false";
		try (PreparedStatement p = db.prepareStatement(sql)) {
			p.setObject(1, target);
			try (ResultSet rs = p.executeQuery()) {
				return rs.next() ? rs.getString(1) : null;
			}
		}
	}

	private static void report(String msg) {
		System.out.println(msg);
	}

	@Override
	public void close() throws SQLException {
		if (db != null)
			db.close();
	}

	private static final class ShortcutChild {
		final UUID shortcutId;
		final UUID target;
		final String targetType;

		ShortcutChild(UUID shortcutId, UUID target, String targetType) {
			this.shortcutId = shortcutId;
			this.target = target;
			this.targetType = targetType;
		}
	}

	private static final class ChildVisibilitySummary {
		boolean hasChild = false;
		boolean anyNonPrivate = false;
	}

	/** Keyed-folder shortcuts for a single target: which distinct keyed folders hold them, and their ids. */
	private static final class TargetGroup {
		final String targetType;
		final List<UUID> shortcutIds = new ArrayList<>();
		final Set<UUID> folderIds = new HashSet<>();

		TargetGroup(String targetType) {
			this.targetType = targetType;
		}
	}
}
