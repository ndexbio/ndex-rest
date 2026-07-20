package org.ndexbio.common.models.dao.postgresql;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import org.junit.Test;
import org.ndexbio.common.models.dao.AccessKeyResolver;

public class TestPostgresFolderDAO {

    @Test
    public void testAccessKeyIsValidDelegatesToResolver() throws SQLException {
        Connection mockConn = createMock(Connection.class);
        AccessKeyResolver resolver = createMock(AccessKeyResolver.class);
        UUID folder = UUID.randomUUID();
        expect(resolver.isFolderKeyValid(folder, "k")).andReturn(true);
        expect(resolver.isFolderKeyValid(folder, "bad")).andReturn(false);
        replay(mockConn, resolver);

        PostgresFolderDAO dao = new PostgresFolderDAO(mockConn);
        dao.setAccessKeyResolver(resolver);
        assertTrue(dao.accessKeyIsValid(folder, "k"));
        assertFalse(dao.accessKeyIsValid(folder, "bad"));

        verify(resolver);
    }
}
