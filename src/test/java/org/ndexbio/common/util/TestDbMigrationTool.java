package org.ndexbio.common.util;

import static org.easymock.EasyMock.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.junit.Test;

/**
 * Unit tests for {@link DbMigrationTool} with a fully mocked JDBC connection. These assert the
 * decision logic and, importantly, that a dry-run performs NO writes (no executeUpdate / commit).
 */
public class TestDbMigrationTool {

    /**
     * One access-key folder with a single-referrer, owner-owned NETWORK shortcut, in DRY-RUN:
     * the tool reads the state but must NOT reparent, delete, or commit anything.
     */
    @Test
    public void testTransformDryRunPerformsNoWrites() throws SQLException {
        UUID folderId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID shortcutId = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        Connection conn = createMock(Connection.class);
        PreparedStatement pstKeyed = createMock(PreparedStatement.class);
        PreparedStatement pstShortcuts = createMock(PreparedStatement.class);
        PreparedStatement pstReferrer = createMock(PreparedStatement.class);
        PreparedStatement pstOwner = createMock(PreparedStatement.class);
        ResultSet rsKeyed = createMock(ResultSet.class);
        ResultSet rsShortcuts = createMock(ResultSet.class);
        ResultSet rsReferrer = createMock(ResultSet.class);
        ResultSet rsOwner = createMock(ResultSet.class);

        // Order of prepareStatement calls: keyed folders, shortcut children, referrer count, target owner.
        expect(conn.prepareStatement(anyString()))
                .andReturn(pstKeyed).andReturn(pstShortcuts).andReturn(pstReferrer).andReturn(pstOwner);

        anyPst(pstKeyed);
        anyPst(pstShortcuts);
        anyPst(pstReferrer);
        anyPst(pstOwner);

        expect(pstKeyed.executeQuery()).andReturn(rsKeyed);
        expect(rsKeyed.next()).andReturn(true).andReturn(false);
        expect(rsKeyed.getObject(1)).andReturn(folderId);
        expect(rsKeyed.getObject(2)).andReturn(ownerId);
        rsKeyed.close();
        expectLastCall();

        expect(pstShortcuts.executeQuery()).andReturn(rsShortcuts);
        expect(rsShortcuts.next()).andReturn(true).andReturn(false);
        expect(rsShortcuts.getObject(1)).andReturn(shortcutId);
        expect(rsShortcuts.getObject(2)).andReturn(target);
        expect(rsShortcuts.getString(3)).andReturn("NETWORK");
        rsShortcuts.close();
        expectLastCall();

        expect(pstReferrer.executeQuery()).andReturn(rsReferrer);
        expect(rsReferrer.next()).andReturn(true);
        expect(rsReferrer.getLong(1)).andReturn(1L); // single referrer
        rsReferrer.close();
        expectLastCall();

        expect(pstOwner.executeQuery()).andReturn(rsOwner);
        expect(rsOwner.next()).andReturn(true);
        expect(rsOwner.getObject(1)).andReturn(ownerId); // same owner as folder -> eligible
        rsOwner.close();
        expectLastCall();

        replay(conn, pstKeyed, pstShortcuts, pstReferrer, pstOwner,
                rsKeyed, rsShortcuts, rsReferrer, rsOwner);

        DbMigrationTool tool = new DbMigrationTool(conn);
        tool.transformAccessKeyShortcuts(false); // dry-run

        // No executeUpdate / commit were expected on the mocks; if the tool wrote anything the default
        // mock would have failed. verify() confirms the reads happened.
        verify(conn, pstKeyed, pstShortcuts, pstReferrer, pstOwner,
                rsKeyed, rsShortcuts, rsReferrer, rsOwner);
    }

    /**
     * A target referenced by more than one shortcut is skipped (multi-referrer) — the tool must not
     * look up the target owner or write anything, in either mode.
     */
    @Test
    public void testTransformSkipsMultiReferrerTarget() throws SQLException {
        UUID folderId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID shortcutId = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        Connection conn = createMock(Connection.class);
        PreparedStatement pstKeyed = createMock(PreparedStatement.class);
        PreparedStatement pstShortcuts = createMock(PreparedStatement.class);
        PreparedStatement pstReferrer = createMock(PreparedStatement.class);
        ResultSet rsKeyed = createMock(ResultSet.class);
        ResultSet rsShortcuts = createMock(ResultSet.class);
        ResultSet rsReferrer = createMock(ResultSet.class);

        // Only three queries run: the target-owner lookup is never reached because of the skip.
        expect(conn.prepareStatement(anyString()))
                .andReturn(pstKeyed).andReturn(pstShortcuts).andReturn(pstReferrer);

        anyPst(pstKeyed);
        anyPst(pstShortcuts);
        anyPst(pstReferrer);

        expect(pstKeyed.executeQuery()).andReturn(rsKeyed);
        expect(rsKeyed.next()).andReturn(true).andReturn(false);
        expect(rsKeyed.getObject(1)).andReturn(folderId);
        expect(rsKeyed.getObject(2)).andReturn(ownerId);
        rsKeyed.close();
        expectLastCall();

        expect(pstShortcuts.executeQuery()).andReturn(rsShortcuts);
        expect(rsShortcuts.next()).andReturn(true).andReturn(false);
        expect(rsShortcuts.getObject(1)).andReturn(shortcutId);
        expect(rsShortcuts.getObject(2)).andReturn(target);
        expect(rsShortcuts.getString(3)).andReturn("NETWORK");
        rsShortcuts.close();
        expectLastCall();

        expect(pstReferrer.executeQuery()).andReturn(rsReferrer);
        expect(rsReferrer.next()).andReturn(true);
        expect(rsReferrer.getLong(1)).andReturn(2L); // multi-referrer -> skip
        rsReferrer.close();
        expectLastCall();

        replay(conn, pstKeyed, pstShortcuts, pstReferrer, rsKeyed, rsShortcuts, rsReferrer);

        DbMigrationTool tool = new DbMigrationTool(conn);
        tool.transformAccessKeyShortcuts(true); // even in apply mode, a multi-referrer target is skipped

        verify(conn, pstKeyed, pstShortcuts, pstReferrer, rsKeyed, rsShortcuts, rsReferrer);
    }

    private static void anyPst(PreparedStatement pst) throws SQLException {
        pst.setObject(anyInt(), anyObject());
        expectLastCall().anyTimes();
        pst.setString(anyInt(), anyString());
        expectLastCall().anyTimes();
        pst.close();
        expectLastCall();
    }
}
