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
 * distinct-keyed-parent-folder gate and, importantly, that a dry-run performs NO writes
 * (no executeUpdate / commit).
 *
 * <p>Query order in {@code transformAccessKeyShortcuts}: (1) folders with an effective key,
 * (2) keyed-folder shortcuts grouped by target, then per eligible target (3) the target-owner lookup.</p>
 */
public class TestDbMigrationTool {

    /**
     * One eligible target: its single keyed-folder shortcut sits in one keyed folder, owner matches.
     * In DRY-RUN the tool must read the state but NOT reparent, delete, or commit anything.
     */
    @Test
    public void testTransformDryRunPerformsNoWrites() throws SQLException {
        UUID folderId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID shortcutId = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        Connection conn = createMock(Connection.class);
        PreparedStatement pstFolders = createMock(PreparedStatement.class);
        PreparedStatement pstGroup = createMock(PreparedStatement.class);
        PreparedStatement pstOwner = createMock(PreparedStatement.class);
        ResultSet rsFolders = createMock(ResultSet.class);
        ResultSet rsGroup = createMock(ResultSet.class);
        ResultSet rsOwner = createMock(ResultSet.class);

        // Order: keyed folders, keyed-folder shortcuts, target owner.
        expect(conn.prepareStatement(anyString()))
                .andReturn(pstFolders).andReturn(pstGroup).andReturn(pstOwner);
        anyPst(pstFolders);
        anyPst(pstGroup);
        anyPst(pstOwner);

        // foldersWithEffectiveAccessKey(): one keyed folder owned by ownerId
        expect(pstFolders.executeQuery()).andReturn(rsFolders);
        expect(rsFolders.next()).andReturn(true).andReturn(false);
        expect(rsFolders.getObject(1)).andReturn(folderId);
        expect(rsFolders.getObject(2)).andReturn(ownerId);
        rsFolders.close();
        expectLastCall();

        // groupKeyedShortcutsByTarget(): one shortcut to target, in that one folder
        expect(pstGroup.executeQuery()).andReturn(rsGroup);
        expect(rsGroup.next()).andReturn(true).andReturn(false);
        expect(rsGroup.getObject(1)).andReturn(shortcutId);
        expect(rsGroup.getObject(2)).andReturn(target);
        expect(rsGroup.getString(3)).andReturn("NETWORK");
        expect(rsGroup.getObject(4)).andReturn(folderId);
        rsGroup.close();
        expectLastCall();

        // targetOwnerIfLive(): live, owned by ownerId (matches folder owner -> eligible)
        expect(pstOwner.executeQuery()).andReturn(rsOwner);
        expect(rsOwner.next()).andReturn(true);
        expect(rsOwner.getObject(1)).andReturn(ownerId);
        rsOwner.close();
        expectLastCall();

        replay(conn, pstFolders, pstGroup, pstOwner, rsFolders, rsGroup, rsOwner);

        DbMigrationTool tool = new DbMigrationTool(conn);
        tool.transformAccessKeyShortcuts(false); // dry-run

        // No executeUpdate / commit expected; the default mock would fail if the tool wrote anything.
        verify(conn, pstFolders, pstGroup, pstOwner, rsFolders, rsGroup, rsOwner);
    }

    /**
     * A target whose keyed-folder shortcuts span TWO distinct keyed folders is skipped — even in apply
     * mode the tool must not resolve the owner or write anything.
     */
    @Test
    public void testTransformSkipsTargetSpanningMultipleFolders() throws SQLException {
        UUID folder1 = UUID.randomUUID();
        UUID folder2 = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        UUID sc1 = UUID.randomUUID();
        UUID sc2 = UUID.randomUUID();

        Connection conn = createMock(Connection.class);
        PreparedStatement pstFolders = createMock(PreparedStatement.class);
        PreparedStatement pstGroup = createMock(PreparedStatement.class);
        ResultSet rsFolders = createMock(ResultSet.class);
        ResultSet rsGroup = createMock(ResultSet.class);

        // Only two queries run: the gate skips before any target-owner lookup or write.
        expect(conn.prepareStatement(anyString())).andReturn(pstFolders).andReturn(pstGroup);
        anyPst(pstFolders);
        anyPst(pstGroup);

        expect(pstFolders.executeQuery()).andReturn(rsFolders);
        expect(rsFolders.next()).andReturn(true).andReturn(true).andReturn(false);
        expect(rsFolders.getObject(1)).andReturn(folder1).andReturn(folder2);
        expect(rsFolders.getObject(2)).andReturn(ownerId).andReturn(ownerId);
        rsFolders.close();
        expectLastCall();

        // Same target referenced from two different keyed folders -> spans >1 folder -> skip
        expect(pstGroup.executeQuery()).andReturn(rsGroup);
        expect(rsGroup.next()).andReturn(true).andReturn(true).andReturn(false);
        expect(rsGroup.getObject(1)).andReturn(sc1).andReturn(sc2);
        expect(rsGroup.getObject(2)).andReturn(target).andReturn(target);
        expect(rsGroup.getString(3)).andReturn("NETWORK").andReturn("NETWORK");
        expect(rsGroup.getObject(4)).andReturn(folder1).andReturn(folder2);
        rsGroup.close();
        expectLastCall();

        replay(conn, pstFolders, pstGroup, rsFolders, rsGroup);

        DbMigrationTool tool = new DbMigrationTool(conn);
        tool.transformAccessKeyShortcuts(true); // even in apply mode, a multi-folder target is skipped

        verify(conn, pstFolders, pstGroup, rsFolders, rsGroup);
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
