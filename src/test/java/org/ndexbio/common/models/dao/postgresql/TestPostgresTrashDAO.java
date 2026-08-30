package org.ndexbio.common.models.dao.postgresql;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.easymock.Capture;
import org.easymock.CaptureType;
import org.junit.Test;
import org.ndexbio.common.models.dao.FilePermissionResolver;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.TrashRestoreRequest;

/**
 * Restore-from-trash placement in {@link PostgresTrashDAO} (issue #165).
 *
 * <p>Restoring an item puts it back in its original parent folder only if the caller can write there.
 * That check used to read {@code folder_permission} directly, so a user whose write came from an
 * <em>ancestor</em> folder was told "no access" and their item was silently relocated to their home —
 * disagreeing with what the folder listings said they could do. It now asks the resolver for the
 * effective permission.</p>
 *
 * <p>These drive the public {@code restoreTrashedItems} entry point rather than the private helper, and
 * assert the observable outcome: whether the {@code parent=NULL} statement — the "move it to home"
 * step — is issued.</p>
 */
public class TestPostgresTrashDAO {

    private static final UUID ITEM = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PARENT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID USER = UUID.fromString("33333333-3333-3333-3333-333333333333");

    /** Statement kinds a restore issues, in order. */
    private enum Kind { PARENT_LOOKUP, PARENT_LIVE_CHECK, RESTORE, MOVE_TO_HOME }

    /**
     * Runs one restore and returns every SQL statement issued, so a caller can assert on whether the
     * move-to-home step happened.
     *
     * @param parentIsLive   whether the parent folder row is still present and not trashed
     * @param effective      what the resolver reports for the parent (null when it is never consulted)
     * @param request        which of folders/networks/shortcuts to restore
     */
    private List<String> runRestore(boolean parentIsLive, Permissions effective,
                                    TrashRestoreRequest request, boolean expectResolverCall)
            throws SQLException {
        Connection conn = createMock(Connection.class);
        FilePermissionResolver resolver = createMock(FilePermissionResolver.class);
        Capture<String> sql = newCapture(CaptureType.ALL);

        // 1) parent lookup on the trashed item
        PreparedStatement lookup = createNiceMock(PreparedStatement.class);
        ResultSet lookupRs = createNiceMock(ResultSet.class);
        expect(lookupRs.next()).andReturn(true);
        expect(lookupRs.getObject(1)).andReturn(PARENT);
        expect(lookup.executeQuery()).andReturn(lookupRs);
        replay(lookup, lookupRs);
        expect(conn.prepareStatement(capture(sql))).andReturn(lookup);

        // 2) canRestoreInto: is the parent folder still live?
        PreparedStatement liveCheck = createNiceMock(PreparedStatement.class);
        ResultSet liveRs = createNiceMock(ResultSet.class);
        expect(liveRs.next()).andReturn(parentIsLive);
        expect(liveCheck.executeQuery()).andReturn(liveRs);
        replay(liveCheck, liveRs);
        expect(conn.prepareStatement(capture(sql))).andReturn(liveCheck);

        // 3) the resolver, only reached when the parent is live
        if (expectResolverCall)
            expect(resolver.effectiveFolderPermission(PARENT, USER)).andReturn(effective);

        // 4) the restore update, and 5) optionally the move-to-home update
        PreparedStatement restore = createNiceMock(PreparedStatement.class);
        replay(restore);
        expect(conn.prepareStatement(capture(sql))).andReturn(restore).times(1, 2);

        replay(conn, resolver);

        PostgresTrashDAO dao = new PostgresTrashDAO(conn);
        dao.setPermissionResolver(resolver);
        dao.restoreTrashedItems(USER, request);

        verify(resolver);
        return sql.getValues();
    }

    private static TrashRestoreRequest networkRequest() {
        TrashRestoreRequest r = new TrashRestoreRequest();
        r.setNetworks(Arrays.asList(ITEM));
        return r;
    }

    private static TrashRestoreRequest shortcutRequest() {
        TrashRestoreRequest r = new TrashRestoreRequest();
        r.setShortcuts(Arrays.asList(ITEM));
        return r;
    }

    private static boolean movedToHome(List<String> statements) {
        for (String s : statements)
            if (s.contains("parent=NULL"))
                return true;
        return false;
    }

    // ── positive: effective WRITE keeps the item in its original parent ────────

    /**
     * The case the old direct-row check got wrong. WRITE here may be inherited from an ancestor folder —
     * the resolver is what knows that — and the item must go back where it came from.
     */
    @Test
    public void testRestoreWithEffectiveWriteKeepsItemInOriginalParent() throws SQLException {
        List<String> issued = runRestore(true, Permissions.WRITE, networkRequest(), true);
        assertTrue("the item must be restored", issued.stream().anyMatch(s -> s.contains("is_deleted=false")));
        assertFalse("must not be relocated to home", movedToHome(issued));
    }

    // ── negative: anything short of WRITE relocates to home ───────────────────

    @Test
    public void testRestoreWithOnlyReadRelocatesToHome() throws SQLException {
        assertTrue("read-only on the parent must not place the item there",
                movedToHome(runRestore(true, Permissions.READ, networkRequest(), true)));
    }

    @Test
    public void testRestoreWithNoPermissionRelocatesToHome() throws SQLException {
        assertTrue("no permission on the parent must relocate to home",
                movedToHome(runRestore(true, null, networkRequest(), true)));
    }

    /**
     * A parent that is gone or itself trashed short-circuits: there is nowhere to restore into, so the
     * resolver is never consulted. Asserted by giving the strict resolver mock no expectation.
     */
    @Test
    public void testRestoreWithTrashedParentRelocatesToHomeWithoutConsultingResolver() throws SQLException {
        assertTrue(movedToHome(runRestore(false, null, networkRequest(), false)));
    }

    // ── the shortcut branch specifically ──────────────────────────────────────

    /**
     * The shortcut restore path used to throw. Its inline permission check bound only 4 of the 5
     * placeholders in its SQL, so restoring a shortcut whose parent folder still existed raised a JDBC
     * "no value specified for parameter" error. Consolidating the three copies onto one helper fixed it;
     * this pins the path down so it cannot regress.
     */
    @Test
    public void testShortcutRestoreCompletesAndHonoursEffectiveWrite() throws SQLException {
        List<String> issued = runRestore(true, Permissions.WRITE, shortcutRequest(), true);
        assertTrue("the shortcut must be restored",
                issued.stream().anyMatch(s -> s.contains("UPDATE shortcut SET is_deleted=false")));
        assertFalse("write on the parent means it stays there", movedToHome(issued));
    }

    @Test
    public void testShortcutRestoreWithoutWriteRelocatesToHome() throws SQLException {
        assertTrue(movedToHome(runRestore(true, Permissions.READ, shortcutRequest(), true)));
    }


    // ── trashed networks report certification like every other listing ────────
    //
    // listTrashedItemsOfUser was the one network-bearing listing whose projection never selected
    // certified, so trashed networks silently omitted isCertified while every other listing reported
    // it. Now that the mapper reads the column with a plain rs.getBoolean, dropping it again would
    // report "not certified" for every trashed network instead of failing, so the select list is what
    // has to be pinned. doi is asserted alongside because isCertified carries no meaning without it.
    @Test
    public void testTrashListingSqlSelectsCertifiedAndDoi() throws SQLException {
        Connection conn = createMock(Connection.class);

        Capture<String> sql = newCapture(CaptureType.ALL);
        for (int i = 0; i < 3; i++) {  // folders, networks, shortcuts
            PreparedStatement pst = createNiceMock(PreparedStatement.class);
            ResultSet rs = createNiceMock(ResultSet.class);
            expect(rs.next()).andReturn(false).anyTimes();
            expect(pst.executeQuery()).andReturn(rs).anyTimes();
            replay(pst, rs);
            expect(conn.prepareStatement(capture(sql))).andReturn(pst);
        }
        replay(conn);

        new PostgresTrashDAO(conn).listTrashedItemsOfUser(USER);

        String networkSql = sql.getValues().get(1);  // folders, networks, shortcuts
        assertTrue("trash listing must select certified, or trashed networks report isCertified=false",
                networkSql.contains("certified"));
        assertTrue("trash listing must select ndexdoi — isCertified is meaningless without it",
                networkSql.contains("ndexdoi"));
    }
}
