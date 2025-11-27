package com.hhplus.ecommerce.common.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import com.hhplus.ecommerce.domain.product.entity.Product
import com.hhplus.ecommerce.application.product.dto.TopProductsResult
import com.hhplus.ecommerce.domain.coupon.entity.Coupon
import java.time.Duration

@Configuration
@EnableCaching
@ConditionalOnProperty(
    name = ["spring.cache.type"],
    havingValue = "redis",
    matchIfMissing = false
)
class CacheConfig {
    @Bean
    fun cacheManager(redisConnectionFactory: RedisConnectionFactory): CacheManager {
        // Jackson ObjectMapper 설정 (Kotlin, JavaTime 지원)
        val objectMapper = ObjectMapper().apply {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }

        // 기본 직렬화 설정 (타입 정보 포함)
        val serializer = GenericJackson2JsonRedisSerializer(objectMapper)

        // 기본 캐시 설정 (TTL 10분)
        val defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer())
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(serializer)
            )

        // 타입별 Serializer 생성
        val productSerializer = Jackson2JsonRedisSerializer(objectMapper, Product::class.java)
        val topProductsSerializer = Jackson2JsonRedisSerializer(objectMapper, TopProductsResult::class.java)
        val couponSerializer = Jackson2JsonRedisSerializer(objectMapper, Coupon::class.java)

        // 캐시별 개별 설정
        val cacheConfigurations = mapOf(
            // 상품 정보 캐시 (TTL: 10분)
            "products" to RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(
                    RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer())
                )
                .serializeValuesWith(
                    RedisSerializationContext.SerializationPair.fromSerializer(productSerializer)
                ),

            // 인기 상품 캐시 (TTL: 3분)
            "topProducts" to RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(3))
                .serializeKeysWith(
                    RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer())
                )
                .serializeValuesWith(
                    RedisSerializationContext.SerializationPair.fromSerializer(topProductsSerializer)
                ),

            // 쿠폰 메타 정보 캐시 (TTL: 10분)
            "coupons" to RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(
                    RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer())
                )
                .serializeValuesWith(
                    RedisSerializationContext.SerializationPair.fromSerializer(couponSerializer)
                )
        )

        return RedisCacheManager.builder(redisConnectionFactory)
            .cacheDefaults(defaultCacheConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .build()
    }
}