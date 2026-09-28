package com.devcordeiro.insight_service.service;

import com.devcordeiro.insight_service.dto.InsightDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.dao.QueryTimeoutException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InsightCacheTest {

    private static final String CACHE = "saving-tips";

    private ConcurrentMapCacheManager cacheManager;
    private InsightCache insightCache;

    @BeforeEach
    void setUp() {
        cacheManager = new ConcurrentMapCacheManager(CACHE);
        insightCache = new InsightCache(cacheManager);
    }

    @Test
    void aSecondRequestForTheSameUserIsServedFromTheCacheWithoutCallingOllama() {
        AtomicInteger calls = new AtomicInteger();

        InsightDto first = insightCache.get(CACHE, 10L, () -> answer(10L, calls));
        InsightDto second = insightCache.get(CACHE, 10L, () -> answer(10L, calls));

        assertThat(second).isEqualTo(first);
        assertThat(calls).hasValue(1);
    }

    @Test
    void differentUsersAreCachedSeparately() {
        AtomicInteger calls = new AtomicInteger();

        insightCache.get(CACHE, 10L, () -> answer(10L, calls));
        InsightDto other = insightCache.get(CACHE, 11L, () -> answer(11L, calls));

        assertThat(other.userId()).isEqualTo(11L);
        assertThat(calls).hasValue(2);
    }

    @Test
    void concurrentRequestsForTheSameUserWaitForASingleOllamaCall() throws Exception {
        int requests = 8;
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch allStarted = new CountDownLatch(requests);
        CountDownLatch releaseOllama = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        try {
            List<Future<InsightDto>> results = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                results.add(pool.submit(() -> {
                    allStarted.countDown();
                    return insightCache.get(CACHE, 10L, () -> {
                        await(releaseOllama);
                        return answer(10L, calls);
                    });
                }));
            }
            // Every request is in flight before the slow Ollama call is allowed to finish
            assertThat(allStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(100);
            releaseOllama.countDown();

            for (Future<InsightDto> result : results) {
                assertThat(result.get(5, TimeUnit.SECONDS).tips()).isEqualTo("dicas 1");
            }
            assertThat(calls).hasValue(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aFailedOllamaCallIsNotCachedSoTheNextRequestTriesAgain() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> insightCache.get(CACHE, 10L, () -> {
            throw new IllegalStateException("ollama is down");
        })).isInstanceOf(IllegalStateException.class).hasMessage("ollama is down");

        InsightDto retried = insightCache.get(CACHE, 10L, () -> answer(10L, calls));
        assertThat(retried.tips()).isEqualTo("dicas 1");
    }

    @Test
    void whenRedisFailsTheRequestStillGetsAnAnswerFromOllama() {
        Cache broken = mock(Cache.class);
        when(broken.getName()).thenReturn(CACHE);
        when(broken.get(any(), any(Class.class))).thenThrow(new QueryTimeoutException("redis timed out"));
        doThrow(new QueryTimeoutException("redis timed out")).when(broken).put(any(), any());
        CacheManager brokenManager = mock(CacheManager.class);
        when(brokenManager.getCache(CACHE)).thenReturn(broken);
        AtomicInteger calls = new AtomicInteger();

        InsightDto insight = new InsightCache(brokenManager).get(CACHE, 10L, () -> answer(10L, calls));

        assertThat(insight.tips()).isEqualTo("dicas 1");
    }

    @Test
    void anAnswerAlreadyInTheCacheIsReturnedAsIs() {
        InsightDto stored = new InsightDto(10L, "do cache", 4.2);
        ((ConcurrentMapCache) cacheManager.getCache(CACHE)).put(10L, stored);

        InsightDto insight = insightCache.get(CACHE, 10L, () -> {
            throw new AssertionError("Ollama must not be called on a hit");
        });

        assertThat(insight).isEqualTo(stored);
    }

    private static InsightDto answer(Long userId, AtomicInteger calls) {
        return new InsightDto(userId, "dicas " + calls.incrementAndGet(), 12.5);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
