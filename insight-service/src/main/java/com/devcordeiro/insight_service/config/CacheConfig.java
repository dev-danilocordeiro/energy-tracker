package com.devcordeiro.insight_service.config;

import com.devcordeiro.insight_service.dto.InsightDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;

// Enables Boot's cache manager; the caches are used programmatically by InsightCache
@EnableCaching
@Configuration
public class CacheConfig {

    public static final String SAVING_TIPS = "saving-tips";
    public static final String OVERVIEW = "overview";

    @Bean
    RedisCacheManagerBuilderCustomizer insightCaches(@Value("${insight.cache.ttl}") Duration ttl) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                // Keys look like insight:saving-tips::42, so they stay apart from anything
                // else sharing this Redis
                .prefixCacheNameWith("insight:")
                // JSON instead of the default JDK serialization: readable in redis-cli and
                // no Serializable requirement on the DTO
                .serializeValuesWith(SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(InsightDto.class)));

        // Declared up front so they exist, with their metrics, from startup
        return builder -> builder
                .withCacheConfiguration(SAVING_TIPS, config)
                .withCacheConfiguration(OVERVIEW, config);
    }
}
