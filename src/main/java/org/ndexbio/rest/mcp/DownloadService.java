package org.ndexbio.rest.mcp;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Tracks active CX2 download sessions. Each session is identified by a cache key
 * ({Mcp-Session-Id}:download:{networkUUID}) and records enough state to serve sequential
 * read-only chunks from the source CX2 file.
 *
 * <p>Unlike {@link UploadService}, this service does NOT accumulate any data server-side.
 * The CX2 file already exists at a known path on the server; the tool reads the appropriate
 * byte slice on each call and returns the data to the MCP client (LLM agent). The agent is
 * responsible for accumulating chunks into a temp file and, on completion, moving it to the
 * final {@code file_path} destination.
 *
 * <p>Abandoned download sessions (LLM stops mid-transfer) expire automatically after 2 minutes
 * of inactivity. No cleanup is needed on expiry since no files are managed here.
 */
public class DownloadService {

    public static final class DownloadSessionState {
        final String networkId;
        final Path   sourcePath;
        final long   fileSize;
        final long   chunkSize;
        final int    totalChunks;
        volatile int lastServedChunk;  // starts at 0; incremented by recordChunkServed

        DownloadSessionState(String networkId, Path sourcePath,
                             long fileSize, long chunkSize, int totalChunks) {
            this.networkId       = networkId;
            this.sourcePath      = sourcePath;
            this.fileSize        = fileSize;
            this.chunkSize       = chunkSize;
            this.totalChunks     = totalChunks;
            this.lastServedChunk = 0;
        }

        String networkId()       { return networkId; }
        public Path   sourcePath()      { return sourcePath; }
        public long   fileSize()        { return fileSize; }
        public long   chunkSize()       { return chunkSize; }
        public int    totalChunks()     { return totalChunks; }
        int    lastServedChunk() { return lastServedChunk; }
    }

    private static final int EXPIRY_MINUTES = 2;

    private final Cache<String, DownloadSessionState> cache = CacheBuilder.newBuilder()
            .expireAfterAccess(EXPIRY_MINUTES, TimeUnit.MINUTES)
            .build();  // no removal listener — no files to clean up

    public DownloadService() {}

    /**
     * Create a new download session. If a stale session already exists for the same key it
     * is replaced silently (no cleanup needed — no files are associated with sessions).
     *
     * @param cacheKey  session identifier ({sessionId}:download:{networkId})
     * @param networkId UUID string of the network being downloaded
     * @param sourcePath absolute path to the network.cx2 file on the server
     * @param fileSize  total size of the source file in bytes
     * @param chunkSize maximum bytes per chunk
     * @return the newly created {@link DownloadSessionState}
     */
    public DownloadSessionState initSession(String cacheKey, String networkId,
                                            Path sourcePath, long fileSize, long chunkSize) {
        int totalChunks = fileSize <= 0 ? 1
                : (int) Math.max(1, (long) Math.ceil((double) fileSize / chunkSize));
        DownloadSessionState state = new DownloadSessionState(
                networkId, sourcePath, fileSize, chunkSize, totalChunks);
        cache.put(cacheKey, state);
        return state;
    }

    /**
     * Record that a chunk has been served and validate strict sequential ordering.
     *
     * @param cacheKey    session identifier
     * @param chunkNumber 1-based chunk number just served
     * @return {@code true} if this was the final chunk (chunkNumber == totalChunks)
     * @throws IllegalStateException    if no active session exists for the key
     * @throws IllegalArgumentException if the chunk number is out of sequential order
     */
    public boolean recordChunkServed(String cacheKey, int chunkNumber) {
        DownloadSessionState state = cache.getIfPresent(cacheKey);
        if (state == null)
            throw new IllegalStateException(
                "No active download session for key '" + cacheKey + "'. " +
                "Session may have expired (TTL: " + EXPIRY_MINUTES + " min). Restart from chunk 1.");
        if (chunkNumber != state.lastServedChunk + 1)
            throw new IllegalArgumentException(
                "Out-of-order chunk: expected chunk " + (state.lastServedChunk + 1) +
                " but received chunk " + chunkNumber + ".");
        state.lastServedChunk = chunkNumber;
        return chunkNumber == state.totalChunks;
    }

    /**
     * Look up an active session without removing it.
     *
     * @return the session state, or {@code null} if not found or expired
     */
    public DownloadSessionState getSession(String cacheKey) {
        return cache.getIfPresent(cacheKey);
    }

    /**
     * Explicitly close a completed download session, removing it from the cache.
     * Safe to call multiple times (subsequent calls are no-ops).
     */
    public void closeSession(String cacheKey) {
        cache.asMap().remove(cacheKey);
    }
}
