package org.ndexbio.common.models.dao.postgresql;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.junit.Test;
import org.ndexbio.common.models.dao.FilePermissionResolver;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.object.Permissions;

/**
 * Authorization behaviour of {@link PostgresShortcutDAO} (issue #165).
 *
 * <p>The DAO must not carry a permission rule of its own — it delegates to the injected
 * {@link FilePermissionResolver}. These tests drive the DAO's own branching with a mock resolver; the
 * semantics of the resolver's SQL (the reachability conjunction, the same-owner shortcut seed) are
 * asserted in {@code TestPostgresFilePermissionResolver}, not duplicated here.</p>
 */
public class TestPostgresShortcutDAO {

    private static final UUID SHORTCUT = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID USER = UUID.fromString("55555555-5555-5555-5555-555555555555");

    /** Mocks the existence probe {@code isReadable} runs before consulting the resolver. */
    private static Connection connWhereShortcutExists(boolean exists) throws SQLException {
        Connection conn = createMock(Connection.class);
        PreparedStatement pst = createMock(PreparedStatement.class);
        ResultSet rs = createMock(ResultSet.class);

        expect(conn.prepareStatement(anyString())).andReturn(pst);
        pst.setObject(anyInt(), anyObject());
        expectLastCall().anyTimes();
        expect(pst.executeQuery()).andReturn(rs);
        expect(rs.next()).andReturn(exists);
        rs.close();
        expectLastCall();
        pst.close();
        expectLastCall();
        replay(conn, pst, rs);
        return conn;
    }

    // ── positive: the resolver grants, so the DAO reports readable ──────────────

    @Test
    public void testReadableWhenResolverGrantsRead() throws Exception {
        Connection conn = connWhereShortcutExists(true);
        FilePermissionResolver resolver = createMock(FilePermissionResolver.class);
        expect(resolver.effectiveShortcutPermission(SHORTCUT, USER)).andReturn(Permissions.READ);
        replay(resolver);

        PostgresShortcutDAO dao = new PostgresShortcutDAO(conn);
        dao.setPermissionResolver(resolver);

        assertTrue(dao.isReadable(SHORTCUT, USER));
        verify(resolver);
    }

    @Test
    public void testReadableWhenResolverGrantsWrite() throws Exception {
        Connection conn = connWhereShortcutExists(true);
        FilePermissionResolver resolver = createMock(FilePermissionResolver.class);
        expect(resolver.effectiveShortcutPermission(SHORTCUT, USER)).andReturn(Permissions.WRITE);
        replay(resolver);

        PostgresShortcutDAO dao = new PostgresShortcutDAO(conn);
        dao.setPermissionResolver(resolver);

        assertTrue("write implies read", dao.isReadable(SHORTCUT, USER));
        verify(resolver);
    }

    // ── negative: the resolver denies, so the DAO reports not readable ──────────

    @Test
    public void testNotReadableWhenResolverDenies() throws Exception {
        Connection conn = connWhereShortcutExists(true);
        FilePermissionResolver resolver = createMock(FilePermissionResolver.class);
        expect(resolver.effectiveShortcutPermission(SHORTCUT, USER)).andReturn(null);
        replay(resolver);

        PostgresShortcutDAO dao = new PostgresShortcutDAO(conn);
        dao.setPermissionResolver(resolver);

        assertFalse(dao.isReadable(SHORTCUT, USER));
        verify(resolver);
    }

    /**
     * A shortcut that does not exist is a 404, not a denial — the two are different answers and callers
     * map them to different statuses. Collapsing "absent" into "denied" would turn a missing object into
     * a 401 and tell a caller they lack access to something that is not there.
     *
     * <p>The resolver is a strict mock with no expectations, so it also asserts the resolver is never
     * consulted for a nonexistent shortcut.</p>
     */
    @Test
    public void testMissingShortcutThrowsNotFoundRatherThanReportingDenied() throws Exception {
        Connection conn = connWhereShortcutExists(false);
        FilePermissionResolver resolver = createMock(FilePermissionResolver.class);
        replay(resolver); // no interaction expected

        PostgresShortcutDAO dao = new PostgresShortcutDAO(conn);
        dao.setPermissionResolver(resolver);

        assertThrows(ObjectNotFoundException.class, () -> dao.isReadable(SHORTCUT, USER));
        verify(resolver);
    }

    /** Anonymous callers take the same path; the resolver decides, the DAO does not special-case null. */
    @Test
    public void testAnonymousCallerIsDelegatedNotShortCircuited() throws Exception {
        Connection conn = connWhereShortcutExists(true);
        FilePermissionResolver resolver = createMock(FilePermissionResolver.class);
        expect(resolver.effectiveShortcutPermission(SHORTCUT, null)).andReturn(null);
        replay(resolver);

        PostgresShortcutDAO dao = new PostgresShortcutDAO(conn);
        dao.setPermissionResolver(resolver);

        assertFalse(dao.isReadable(SHORTCUT, null));
        verify(resolver);
    }
}
