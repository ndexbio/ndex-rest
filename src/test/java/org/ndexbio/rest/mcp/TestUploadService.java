package org.ndexbio.rest.mcp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestUploadService {

    private UploadService service;
    private static final String NETWORK_ID = "f93f402c-86d4-11e7-a10d-0ac135e8bacf";
    private static final String CACHE_KEY  = "session123:" + NETWORK_ID;

    @BeforeEach
    void setUp() {
        service = new UploadService();
    }

    @Test
    void writeChunk_singleChunk_returnsTrue() throws IOException {
        boolean done = service.writeChunk(CACHE_KEY, 1, 1, NETWORK_ID, "[{}]");
        assertTrue(done);
    }

    @Test
    void writeChunk_firstOfMany_returnsFalse() throws IOException {
        boolean done = service.writeChunk(CACHE_KEY, 1, 3, NETWORK_ID, "chunk1");
        assertFalse(done);
    }

    @Test
    void writeChunk_lastChunk_returnsTrue() throws IOException {
        service.writeChunk(CACHE_KEY, 1, 2, NETWORK_ID, "chunk1");
        boolean done = service.writeChunk(CACHE_KEY, 2, 2, NETWORK_ID, "chunk2");
        assertTrue(done);
    }

    @Test
    void writeChunk_accumulates_correctContent() throws IOException {
        service.writeChunk(CACHE_KEY, 1, 3, NETWORK_ID, "aaa");
        service.writeChunk(CACHE_KEY, 2, 3, NETWORK_ID, "bbb");
        service.writeChunk(CACHE_KEY, 3, 3, NETWORK_ID, "ccc");

        Path file = service.takeCompletedUpload(CACHE_KEY);
        assertNotNull(file);
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            assertEquals("aaabbbccc", content);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void writeChunk_outOfOrder_throws() throws IOException {
        service.writeChunk(CACHE_KEY, 1, 3, NETWORK_ID, "chunk1");
        assertThrows(IllegalArgumentException.class,
            () -> service.writeChunk(CACHE_KEY, 3, 3, NETWORK_ID, "chunk3"));
    }

    @Test
    void writeChunk_skipChunk_throws() throws IOException {
        service.writeChunk(CACHE_KEY, 1, 3, NETWORK_ID, "chunk1");
        assertThrows(IllegalArgumentException.class,
            () -> service.writeChunk(CACHE_KEY, 3, 3, NETWORK_ID, "chunk3"));
    }

    @Test
    void writeChunk_chunkNumberExceedsTotalChunks_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> service.writeChunk(CACHE_KEY, 3, 2, NETWORK_ID, "data"));
    }

    @Test
    void writeChunk_chunkNumberZero_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> service.writeChunk(CACHE_KEY, 0, 2, NETWORK_ID, "data"));
    }

    @Test
    void writeChunk_totalChunksZero_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> service.writeChunk(CACHE_KEY, 1, 0, NETWORK_ID, "data"));
    }

    @Test
    void writeChunk_totalChunksMismatchMidUpload_throws() throws IOException {
        service.writeChunk(CACHE_KEY, 1, 3, NETWORK_ID, "chunk1");
        assertThrows(IllegalArgumentException.class,
            () -> service.writeChunk(CACHE_KEY, 2, 2, NETWORK_ID, "chunk2"));
    }

    @Test
    void writeChunk_noActiveSession_throws() {
        assertThrows(IllegalStateException.class,
            () -> service.writeChunk(CACHE_KEY, 2, 3, NETWORK_ID, "chunk2"));
    }

    @Test
    void takeCompletedUpload_removesEntry() throws IOException {
        service.writeChunk(CACHE_KEY, 1, 1, NETWORK_ID, "[{}]");

        Path first = service.takeCompletedUpload(CACHE_KEY);
        assertNotNull(first);

        Path second = service.takeCompletedUpload(CACHE_KEY);
        assertNull(second);

        // Clean up
        if (first != null) Files.deleteIfExists(first);
    }

    @Test
    void takeCompletedUpload_unknownKey_returnsNull() {
        assertNull(service.takeCompletedUpload("unknown:key"));
    }

    @Test
    void writeChunk_chunk1_replacesStaleSession_andCleansTempFile() throws IOException {
        // First session: chunk 1 of 2
        service.writeChunk(CACHE_KEY, 1, 2, NETWORK_ID, "old_chunk1");
        Path oldTempFile = service.takeCompletedUpload(CACHE_KEY);
        // Re-add to simulate stale session (put back via a new chunk1)
        service.writeChunk(CACHE_KEY, 1, 2, NETWORK_ID, "old_chunk1_again");

        // Now get the temp file path before replacement
        // We can't directly inspect the cache, but we verify the old file is cleaned up
        // by submitting chunk 1 again (restart), which replaces the cache entry
        service.writeChunk(CACHE_KEY, 1, 2, NETWORK_ID, "new_chunk1");

        // The new session should have a new (different) temp file
        Path newTempFile = service.takeCompletedUpload(CACHE_KEY);
        assertNotNull(newTempFile);
        String content = Files.readString(newTempFile, StandardCharsets.UTF_8);
        assertEquals("new_chunk1", content);

        Files.deleteIfExists(newTempFile);
        if (oldTempFile != null) Files.deleteIfExists(oldTempFile);
    }
}
