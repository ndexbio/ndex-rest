package org.ndexbio.rest.mcp;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Token-based upload authorization service. Issues single-use, 2-minute tokens that
 * UploadPreSignedServlet validates before accepting a CX2 multipart POST. JVM singleton;
 * both RequestNetworkUploadTool and UploadPreSignedServlet share the same instance.
 */
public class UploadService {

    private static final UploadService INSTANCE = new UploadService();

    public static UploadService getInstance() { return INSTANCE; }

    UploadService() {
        purger.scheduleAtFixedRate(this::purgeExpired, 2, 2, TimeUnit.MINUTES);
    }

    final ConcurrentHashMap<String, UploadFileRequest> tokenCache = new ConcurrentHashMap<>();
    volatile boolean stopped = false;
    private final ScheduledExecutorService purger = Executors.newSingleThreadScheduledExecutor();

    public String createToken(UploadFileRequest req) {
        String token = UUID.randomUUID().toString();
        tokenCache.put(token, req);
        return token;
    }

    public UploadFileRequest resolveToken(String token) {
        if (token == null) return null;
        UploadFileRequest req = tokenCache.remove(token);
        if (req == null) return null;
        if (System.currentTimeMillis() - req.createTime() > 120_000L) return null;
        return req;
    }

    public void stop() {
        stopped = true;
        purger.shutdownNow();
    }

    void purgeExpired() {
        long cutoff = System.currentTimeMillis() - 120_000L;
        for (Map.Entry<String, UploadFileRequest> entry : tokenCache.entrySet()) {
            if (stopped) return;
            if (entry.getValue().createTime() < cutoff) tokenCache.remove(entry.getKey());
        }
    }

    void clearForTest() {
        tokenCache.clear();
    }
}
