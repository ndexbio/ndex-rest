package org.ndexbio.rest.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class TestDownloadService {

    private DownloadService service;
    private static final String NETWORK_ID = "f93f402c-86d4-11e7-a10d-0ac135e8bacf";
    private static final String CACHE_KEY  = "session123:download:" + NETWORK_ID;
    private static final Path   SOURCE     = Paths.get("/data/" + NETWORK_ID + "/network.cx2");

    @BeforeEach
    void setUp() {
        service = new DownloadService();
    }

    @Test
    void initSession_createsSessionWithCorrectTotalChunks() {
        long chunkSize = 5L * 1024 * 1024;
        long fileSize  = 3L * chunkSize + 1;  // 3 full chunks + 1 byte → 4 chunks
        DownloadService.DownloadSessionState state =
                service.initSession(CACHE_KEY, NETWORK_ID, SOURCE, fileSize, chunkSize);
        assertEquals(4, state.totalChunks());
        assertEquals(0, state.lastServedChunk());
        assertEquals(NETWORK_ID, state.networkId());
        assertEquals(SOURCE, state.sourcePath());
        assertEquals(fileSize, state.fileSize());
        assertEquals(chunkSize, state.chunkSize());
    }

    @Test
    void initSession_exactlyOneChunk_whenFileSizeEqualsChunkSize() {
        long chunkSize = 5L * 1024 * 1024;
        DownloadService.DownloadSessionState state =
                service.initSession(CACHE_KEY, NETWORK_ID, SOURCE, chunkSize, chunkSize);
        assertEquals(1, state.totalChunks());
    }

    @Test
    void initSession_oneChunk_whenFileSizeIsZero() {
        DownloadService.DownloadSessionState state =
                service.initSession(CACHE_KEY, NETWORK_ID, SOURCE, 0, 5L * 1024 * 1024);
        assertEquals(1, state.totalChunks());
    }

    @Test
    void initSession_replacesStaleSessions_silently() {
        service.initSession(CACHE_KEY, NETWORK_ID, SOURCE, 100L, 50L);
        // Replace without exception — no file cleanup needed (no temp files)
        DownloadService.DownloadSessionState fresh =
                service.initSession(CACHE_KEY, NETWORK_ID, SOURCE, 200L, 50L);
        assertEquals(200L, fresh.fileSize());
        // Old session is gone; the new one is returned
        assertEquals(fresh, service.getSession(CACHE_KEY));
    }

    @Test
    void recordChunkServed_returnsFalseForNonFinalChunk() {
        service.initSession(CACHE_KEY, NETWORK_ID, SOURCE, 300L, 100L);  // 3 chunks
        assertFalse(service.recordChunkServed(CACHE_KEY, 1));
        assertFalse(service.recordChunkServed(CACHE_KEY, 2));
    }

    @Test
    void recordChunkServed_returnsTrueForFinalChunk() {
        service.initSession(CACHE_KEY, NETWORK_ID, SOURCE, 100L, 100L);  // 1 chunk
        assertTrue(service.recordChunkServed(CACHE_KEY, 1));
    }

    @Test
    void recordChunkServed_advancesLastServedChunk() {
        service.initSession(CACHE_KEY, NETWORK_ID, SOURCE, 300L, 100L);  // 3 chunks
        assertEquals(0, service.getSession(CACHE_KEY).lastServedChunk());
        service.recordChunkServed(CACHE_KEY, 1);
        assertEquals(1, service.getSession(CACHE_KEY).lastServedChunk());
        service.recordChunkServed(CACHE_KEY, 2);
        assertEquals(2, service.getSession(CACHE_KEY).lastServedChunk());
    }

    @Test
    void recordChunkServed_outOfOrderChunk_throwsIllegalArgument() {
        service.initSession(CACHE_KEY, NETWORK_ID, SOURCE, 300L, 100L);  // 3 chunks
        service.recordChunkServed(CACHE_KEY, 1);
        // Skip chunk 2, jump to chunk 3 → should throw
        assertThrows(IllegalArgumentException.class,
                () -> service.recordChunkServed(CACHE_KEY, 3));
    }

    @Test
    void recordChunkServed_noActiveSession_throwsIllegalState() {
        // Call without initSession → should throw
        assertThrows(IllegalStateException.class,
                () -> service.recordChunkServed(CACHE_KEY, 1));
    }

    @Test
    void closeSession_removesSessionEntry() {
        service.initSession(CACHE_KEY, NETWORK_ID, SOURCE, 100L, 50L);
        assertNotNull(service.getSession(CACHE_KEY));
        service.closeSession(CACHE_KEY);
        assertNull(service.getSession(CACHE_KEY));
    }

    @Test
    void closeSession_unknownKey_isNoOp() {
        // Should not throw
        assertDoesNotThrow(() -> service.closeSession("no-such-key"));
    }

    // ── Coverage-gap tests ──────────────────────────────────────────────────────────────

    @Test
    void initSession_fileSmallerThanChunkSize_givesSingleChunk() {
        // File (100 bytes) is smaller than a single chunk (1 MB) → Math.ceil(100/1_000_000) = 1.
        long fileSize  = 100L;
        long chunkSize = 1_000_000L;
        DownloadService.DownloadSessionState state =
                service.initSession(CACHE_KEY, NETWORK_ID, SOURCE, fileSize, chunkSize);
        assertEquals(1, state.totalChunks(),
                "file smaller than chunkSize should yield exactly 1 total chunk");
    }

    @Test
    void recordChunkServed_duplicateChunk_throwsIllegalArgument() {
        // After serving chunk 1 once, serving it again replays an already-seen chunk.
        // lastServedChunk = 1, expected next = 2; receiving 1 again → IllegalArgumentException.
        service.initSession(CACHE_KEY, NETWORK_ID, SOURCE, 300L, 100L);  // 3 chunks
        service.recordChunkServed(CACHE_KEY, 1);  // first call: OK
        assertThrows(IllegalArgumentException.class,
                () -> service.recordChunkServed(CACHE_KEY, 1),
                "replaying an already-served chunk must throw IllegalArgumentException");
    }
}
