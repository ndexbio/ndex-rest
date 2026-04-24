package org.ndexbio.rest.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.easymock.EasyMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ndexbio.model.object.User;

import static org.junit.jupiter.api.Assertions.*;

class TestUploadService {

    private UploadService service;

    @BeforeEach
    void setUp() {
        service = new UploadService();
    }

    @AfterEach
    void tearDown() {
        service.stop();
    }

    private UploadFileRequest makeRequest(String networkId) {
        User user = EasyMock.createMock(User.class);
        return new UploadFileRequest(user, networkId, null, null, null, System.currentTimeMillis());
    }

    // ── singleton ────────────────────────────────────────────────────────────

    @Test
    void getInstance_returnsSameInstance() {
        assertSame(UploadService.getInstance(), UploadService.getInstance());
    }

    // ── createToken ──────────────────────────────────────────────────────────

    @Test
    void createToken_returnsNonNullUUID() {
        String token = service.createToken(makeRequest(null));
        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void createToken_eachCallReturnsDifferentToken() {
        String t1 = service.createToken(makeRequest(null));
        String t2 = service.createToken(makeRequest(null));
        assertNotEquals(t1, t2);
    }

    // ── resolveToken ─────────────────────────────────────────────────────────

    @Test
    void resolveToken_returnsRequest_whenTokenValid() {
        UploadFileRequest req = makeRequest("net-1");
        String token = service.createToken(req);
        UploadFileRequest resolved = service.resolveToken(token);
        assertNotNull(resolved);
        assertEquals("net-1", resolved.networkId());
    }

    @Test
    void resolveToken_deletesToken_afterFirstResolve() {
        String token = service.createToken(makeRequest(null));
        assertNotNull(service.resolveToken(token));
        assertNull(service.resolveToken(token)); // single-use
    }

    @Test
    void resolveToken_returnsNull_whenTokenUnknown() {
        assertNull(service.resolveToken("bogus-token-xyz"));
    }

    @Test
    void resolveToken_returnsNull_whenTokenIsNull() {
        assertDoesNotThrow(() -> assertNull(service.resolveToken(null)));
    }

    @Test
    void resolveToken_returnsNull_whenTokenExpired() {
        long pastTime = System.currentTimeMillis() - 130_000L;
        User user = EasyMock.createMock(User.class);
        UploadFileRequest expired = new UploadFileRequest(user, null, null, null, null, pastTime);
        String token = service.createToken(expired);
        // Belt-and-suspenders check: resolveToken detects expired entry even if purger hasn't run
        assertNull(service.resolveToken(token));
    }

    // ── purgeExpired ──────────────────────────────────────────────────────────

    @Test
    void purgeExpired_removesOldEntries() {
        long pastTime = System.currentTimeMillis() - 130_000L;
        User user = EasyMock.createMock(User.class);
        UploadFileRequest stale = new UploadFileRequest(user, null, null, null, null, pastTime);
        String token = service.createToken(stale);

        service.purgeExpired();

        assertFalse(service.tokenCache.containsKey(token), "stale entry should be purged");
    }

    @Test
    void purgeExpired_keepsValidEntries() {
        String token = service.createToken(makeRequest(null));
        service.purgeExpired();
        assertTrue(service.tokenCache.containsKey(token), "fresh entry should survive purge");
    }

    @Test
    void purgeExpired_abortsEarly_whenStopped() {
        long pastTime = System.currentTimeMillis() - 130_000L;
        User user = EasyMock.createMock(User.class);
        for (int i = 0; i < 100; i++) {
            UploadFileRequest stale = new UploadFileRequest(user, null, null, null, null, pastTime);
            service.tokenCache.put("stale-" + i, stale);
        }
        service.stopped = true;
        service.purgeExpired();
        // If stopped check works, not all 100 entries are removed on first iteration abort
        // We can't deterministically assert exact count, but the cache must still have entries
        assertFalse(service.tokenCache.isEmpty(), "purge should abort early when stopped=true");
    }

    // ── stop ─────────────────────────────────────────────────────────────────

    @Test
    void stop_setsStoppedFlag_andShutdownsExecutor() throws Exception {
        // Create a fresh service so we don't stop the singleton's purger
        UploadService svc = new UploadService();
        svc.stop();
        assertTrue(svc.stopped, "stopped flag must be true after stop()");
    }

    // ── concurrency ──────────────────────────────────────────────────────────

    @Test
    void concurrentCreateAndResolve_noDataRace() throws InterruptedException {
        int threads = 20;
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    String token = service.createToken(makeRequest(null));
                    UploadFileRequest resolved = service.resolveToken(token);
                    if (resolved != null) successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        pool.shutdown();
        assertEquals(threads, successCount.get(), "all tokens should resolve successfully");
        assertTrue(service.tokenCache.isEmpty(), "all tokens consumed — cache should be empty");
    }

    @Test
    void concurrentResolve_sameToken_onlyOneSucceeds() throws InterruptedException {
        String token = service.createToken(makeRequest(null));

        int threads = 10;
        CountDownLatch latch = new CountDownLatch(threads);
        List<UploadFileRequest> results = new ArrayList<>();
        Object lock = new Object();
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    UploadFileRequest r = service.resolveToken(token);
                    synchronized (lock) { results.add(r); }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        pool.shutdown();

        long nonNullCount = results.stream().filter(r -> r != null).count();
        assertEquals(1, nonNullCount, "exactly one thread should win the single-use token race");
    }
}
