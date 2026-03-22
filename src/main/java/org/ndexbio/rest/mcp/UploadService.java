package org.ndexbio.rest.mcp;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.TimeUnit;

/**
 * Accumulates chunked CX2 uploads. Each upload session is identified by a
 * cache key ({Mcp-Session-Id}:{networkUUID}) and stores incoming string chunks
 * as sequential appends to a temp file. When all chunks arrive, the caller
 * retrieves the assembled file path via {@link #takeCompletedUpload}.
 *
 * <p>Lifecycle is determined by the caller — McpServletContextListener creates
 * one instance and threads it to UpdateNetworkTool via McpToolRegistry.
 *
 * <p>Abandoned uploads (LLM stops mid-transfer) are cleaned up automatically
 * when the 2-minute TTL expires.
 */
public class UploadService {

    static final class ChunkAccumulatorState {
        final String networkId;
        final int    totalChunks;
        volatile int lastReceivedChunk;
        final Path   tempFilePath;

        ChunkAccumulatorState(String networkId, int totalChunks, int lastReceivedChunk, Path tempFilePath) {
            this.networkId         = networkId;
            this.totalChunks       = totalChunks;
            this.lastReceivedChunk = lastReceivedChunk;
            this.tempFilePath      = tempFilePath;
        }

        String networkId()         { return networkId; }
        int    totalChunks()       { return totalChunks; }
        int    lastReceivedChunk() { return lastReceivedChunk; }
        Path   tempFilePath()      { return tempFilePath; }
    }

    private static final int EXPIRY_MINUTES = 2;

    private final Cache<String, ChunkAccumulatorState> cache = CacheBuilder.newBuilder()
            .expireAfterAccess(EXPIRY_MINUTES, TimeUnit.MINUTES)
            .removalListener((com.google.common.cache.RemovalNotification<String,
                              ChunkAccumulatorState> n) -> {
                // Delete temp file on expiry or replacement only — NOT on explicit remove
                // (takeCompletedUpload uses explicit remove; caller is still using the file).
                if (n.getCause() != RemovalCause.EXPLICIT && n.getValue() != null) {
                    try { Files.deleteIfExists(n.getValue().tempFilePath()); }
                    catch (IOException ignored) {}
                }
            })
            .build();

    public UploadService() {}

    /**
     * Write a chunk to the temp file for this upload session.
     * On chunk 1 a new temp file is created (and any stale session is cleaned up).
     * Subsequent chunks are appended by mutating state in-place — no cache.put() is
     * called, so the removalListener is not triggered for mid-session updates.
     *
     * @return true if this was the final chunk (chunkNumber == totalChunks)
     * @throws IllegalStateException     if chunkNumber &gt; 1 and no active session exists
     * @throws IllegalArgumentException  if arguments are invalid or out of expected sequence
     * @throws IOException               if the temp file cannot be written
     */
    public boolean writeChunk(String cacheKey, int chunkNumber, int totalChunks,
                              String networkId, String chunkData) throws IOException {
        // Guard: reject invalid chunk/total values before touching the cache
        if (totalChunks < 1)
            throw new IllegalArgumentException("totalChunks must be >= 1, got: " + totalChunks);
        if (chunkNumber < 1 || chunkNumber > totalChunks)
            throw new IllegalArgumentException(
                "chunkNumber " + chunkNumber + " is out of range [1.." + totalChunks + "].");

        if (chunkNumber == 1) {
            Path tempFile = Files.createTempFile("ndex_cx2_", ".json");
            Files.writeString(tempFile, chunkData, StandardCharsets.UTF_8);
            // Replacing an existing entry fires removalListener(REPLACED) → old file deleted.
            // This is intentional: chunk 1 always starts a fresh session.
            cache.put(cacheKey, new ChunkAccumulatorState(networkId, totalChunks, 1, tempFile));
        } else {
            // getIfPresent also resets the expireAfterAccess TTL for this session.
            ChunkAccumulatorState state = cache.getIfPresent(cacheKey);
            if (state == null)
                throw new IllegalStateException(
                    "No active upload session for key '" + cacheKey + "'. " +
                    "Session may have expired (TTL: " + EXPIRY_MINUTES + " min). Restart from chunk 1.");
            if (chunkNumber != state.lastReceivedChunk() + 1)
                throw new IllegalArgumentException(
                    "Out-of-order chunk: expected chunk " + (state.lastReceivedChunk() + 1) +
                    " but received chunk " + chunkNumber + ".");
            if (totalChunks != state.totalChunks())
                throw new IllegalArgumentException(
                    "totalChunks mismatch: session expects " + state.totalChunks() +
                    " but this call declared " + totalChunks + ".");
            Files.writeString(state.tempFilePath(), chunkData,
                              StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            // Mutate in-place — avoids cache.put() which would trigger REPLACED and delete the file.
            state.lastReceivedChunk = chunkNumber;
        }
        return chunkNumber == totalChunks;
    }

    /**
     * Atomically retrieve and remove the assembled temp file path for a completed upload.
     * Returns {@code null} if no session exists for the key.
     *
     * <p>The removalListener will NOT delete the file on this call (cause = EXPLICIT);
     * the caller is responsible for deleting the file after use.
     */
    public Path takeCompletedUpload(String cacheKey) {
        ChunkAccumulatorState state = cache.asMap().remove(cacheKey);
        return state != null ? state.tempFilePath() : null;
    }
}
