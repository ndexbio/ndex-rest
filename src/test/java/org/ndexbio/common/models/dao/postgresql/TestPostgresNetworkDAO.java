package org.ndexbio.common.models.dao.postgresql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.UUID;

import org.junit.Test;
import static org.easymock.EasyMock.*;

public class TestPostgresNetworkDAO {

    @Test
    public void testSetErrorMessageNull_usesJdbcNull() throws SQLException {
        Connection mockConn = createMock(Connection.class);
        PreparedStatement mockPst = createMock(PreparedStatement.class);

        expect(mockConn.prepareStatement(anyString())).andReturn(mockPst);
        mockPst.setNull(1, Types.VARCHAR);
        expectLastCall();
        mockPst.setObject(eq(2), anyObject());
        expectLastCall();
        expect(mockPst.executeUpdate()).andReturn(1);
        mockConn.commit();
        expectLastCall();
        mockPst.close();
        expectLastCall();
        replay(mockConn, mockPst);

        PostgresNetworkDAO dao = new PostgresNetworkDAO(mockConn);
        dao.setErrorMessage(UUID.randomUUID(), null);

        verify(mockConn, mockPst);
    }

    @Test
    public void testSetErrorMessageValue_usesSetString() throws SQLException {
        Connection mockConn = createMock(Connection.class);
        PreparedStatement mockPst = createMock(PreparedStatement.class);

        expect(mockConn.prepareStatement(anyString())).andReturn(mockPst);
        mockPst.setString(eq(1), eq("some error"));
        expectLastCall();
        mockPst.setObject(eq(2), anyObject());
        expectLastCall();
        expect(mockPst.executeUpdate()).andReturn(1);
        mockConn.commit();
        expectLastCall();
        mockPst.close();
        expectLastCall();
        replay(mockConn, mockPst);

        PostgresNetworkDAO dao = new PostgresNetworkDAO(mockConn);
        dao.setErrorMessage(UUID.randomUUID(), "some error");

        verify(mockConn, mockPst);
    }

    @Test
    public void testSetErrorMessageTruncation() throws SQLException {
        String longMsg = "x".repeat(2001);
        String expectedTruncated = "x".repeat(1996) + "...";

        Connection mockConn = createMock(Connection.class);
        PreparedStatement mockPst = createMock(PreparedStatement.class);

        expect(mockConn.prepareStatement(anyString())).andReturn(mockPst);
        mockPst.setString(eq(1), eq(expectedTruncated));
        expectLastCall();
        mockPst.setObject(eq(2), anyObject());
        expectLastCall();
        expect(mockPst.executeUpdate()).andReturn(1);
        mockConn.commit();
        expectLastCall();
        mockPst.close();
        expectLastCall();
        replay(mockConn, mockPst);

        PostgresNetworkDAO dao = new PostgresNetworkDAO(mockConn);
        dao.setErrorMessage(UUID.randomUUID(), longMsg);

        verify(mockConn, mockPst);
    }
}
