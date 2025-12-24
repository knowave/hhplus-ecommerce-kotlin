package com.hhplus.ecommerce.common.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.lettuce.core.resource.DefaultClientResources
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer

/**
 * Redis 연결 및 템플릿 설정
 *
 * 최적화 포인트:
 * - Lettuce 클라이언트 리소스 설정 (IO 스레드 풀)
 * - RedisTemplate 직렬화 방식 최적화 (Jackson)
 *
 * 참고: 캐시 설정은 CacheConfig에서 관리
 */
@Configuration
@ConditionalOnProperty(
    name = ["spring.data.redis.host"],
    matchIfMissing = false
)
class RedisConfig {

    /**
     * Lettuce 클라이언트 리소스 설정
     * - IO 스레드 풀 최적화
     * - 재연결 정책 설정
     */
    @Bean(destroyMethod = "shutdown")
    fun lettuceClientResources(): DefaultClientResources {
        return DefaultClientResources.builder()
            .ioThreadPoolSize(4)           // IO 스레드 수
            .computationThreadPoolSize(4)  // 계산 스레드 수
            .build()
    }

    /**
     * JSON 직렬화용 ObjectMapper
     */
    @Bean
    fun redisObjectMapper(): ObjectMapper {
        return ObjectMapper().apply {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    /**
     * RedisTemplate 설정
     * - String 키 + JSON 값 직렬화
     */
    @Bean
    @ConditionalOnBean(RedisConnectionFactory::class)
    fun redisTemplate(
        connectionFactory: RedisConnectionFactory,
        redisObjectMapper: ObjectMapper
    ): RedisTemplate<String, Any> {
        return RedisTemplate<String, Any>().apply {
            setConnectionFactory(connectionFactory)
            keySerializer = StringRedisSerializer()
            valueSerializer = GenericJackson2JsonRedisSerializer(redisObjectMapper)
            hashKeySerializer = StringRedisSerializer()
            hashValueSerializer = GenericJackson2JsonRedisSerializer(redisObjectMapper)
            afterPropertiesSet()
        }
    }

    /**
     * StringRedisTemplate (분산락 등에서 사용)
     */
    @Bean
    @ConditionalOnBean(RedisConnectionFactory::class)
    fun stringRedisTemplate(connectionFactory: RedisConnectionFactory): StringRedisTemplate {
        return StringRedisTemplate(connectionFactory)
    }
}

