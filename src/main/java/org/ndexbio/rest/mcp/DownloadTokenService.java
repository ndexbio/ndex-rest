package org.ndexbio.rest.mcp;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Token-based download authorization service. Issues single-use, 2-minute tokens that
 * DownloadPreSignedServlet validates before streaming CX2 content. JVM singleton;
 * both RequestNetworkDownloadTool and DownloadPreSignedServlet share the same instance.
 */
public class DownloadTokenService {

    private static final DownloadTokenService INSTANCE = new DownloadTokenService();

    public static DownloadTokenService getInstance() { return INSTANCE; }

    DownloadTokenService() {
        purger.scheduleAtFixedRate(this::purgeExpired, 2, 2, TimeUnit.MINUTES);
    }

    final ConcurrentHashMap<String, DownloadFileRequest> tokenCache = new ConcurrentHashMap<>();
    volatile boolean stopped = false;
    private final ScheduledExecutorService purger = Executors.newSingleThreadScheduledExecutor();

    public String createToken(DownloadFileRequest req) {
        String token = UUID.randomUUID().toString();
        tokenCache.put(token, req);
        return token;
    }

    public DownloadFileRequest resolveToken(String token) {
        if (token == null) return null;
        DownloadFileRequest req = tokenCache.remove(token);
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
        for (Map.Entry<String, DownloadFileRequest> entry : tokenCache.entrySet()) {
            if (stopped) return;
            if (entry.getValue().createTime() < cutoff) tokenCache.remove(entry.getKey());
        }
    }

    void clearForTest() {
        tokenCache.clear();
    }
}
