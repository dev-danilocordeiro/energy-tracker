package com.devcordeiro.insight_service.service;

import com.devcordeiro.insight_service.dto.InsightDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Cache-aside for Ollama answers, per user.
 * <p>
 * Not {@code @Cacheable}: with Spring Data Redis, {@code sync = true} either doesn't lock
 * at all or locks the whole cache, so concurrent misses for one user would each call
 * Ollama, or every user would queue behind one call. Here concurrent misses for the same
 * key wait for a single call, and different users run in parallel. The coordination is
 * per instance; across instances each one may call Ollama once.
 * <p>
 * The cache is an optimisation, never a dependency: if Redis fails, requests go
 * straight to Ollama.
 */
@Slf4j
@Component
public class InsightCache {

    private final CacheManager cacheManager;
    private final ConcurrentMap<String, CompletableFuture<InsightDto>> inFlight = new ConcurrentHashMap<>();

    public InsightCache(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public InsightDto get(String cacheName, Long userId, Supplier<InsightDto> generator) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            throw new IllegalStateException("Cache " + cacheName + " is not configured");
        }

        InsightDto cached = read(cache, userId);
        if (cached != null) {
            return cached;
        }

        String key = cacheName + "::" + userId;
        CompletableFuture<InsightDto> mine = new CompletableFuture<>();
        CompletableFuture<InsightDto> running = inFlight.putIfAbsent(key, mine);
        if (running != null) {
            log.debug("Waiting for the Ollama call already running for {}", key);
            return await(running);
        }

        try {
            InsightDto generated = generator.get();
            write(cache, userId, generated);
            mine.complete(generated);
            return generated;
        } catch (RuntimeException e) {
            // Waiting requests get the same error; nothing is cached, so the next one retries
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(key, mine);
        }
    }

    private static InsightDto await(CompletableFuture<InsightDto> running) {
        try {
            return running.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw e;
        }
    }

    private static InsightDto read(Cache cache, Long userId) {
        try {
            return cache.get(userId, InsightDto.class);
        } catch (RuntimeException e) {
            log.warn("Could not read cache {} for user {}, calling Ollama: {}", cache.getName(), userId, e.getMessage());
            return null;
        }
    }

    private static void write(Cache cache, Long userId, InsightDto insight) {
        try {
            cache.put(userId, insight);
        } catch (RuntimeException e) {
            log.warn("Could not write cache {} for user {}: {}", cache.getName(), userId, e.getMessage());
        }
    }
}
