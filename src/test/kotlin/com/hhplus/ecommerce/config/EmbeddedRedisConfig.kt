package com.hhplus.ecommerce.config

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.TestConfiguration
import redis.embedded.RedisServer

/**
 * 테스트 환경을 위한 Embedded Redis 설정
 *
 * 통합 테스트에서 실제 Redis 서버 없이 Redis 기능을 테스트할 수 있도록 지원합니다.
 */
@TestConfiguration
class EmbeddedRedisConfig {

    @Value("\${spring.data.redis.port:6379}")
    private val redisPort: Int = 6379

    private var redisServer: RedisServer? = null

    @PostConstruct
    fun startRedis() {
        try {
            redisServer = RedisServer(redisPort)
            redisServer?.start()
            println("Embedded Redis started on port $redisPort")
        } catch (e: Exception) {
            println("Failed to start Embedded Redis: ${e.message}")
            // 이미 실행 중인 Redis가 있을 수 있으므로 무시
        }
    }

    @PreDestroy
    fun stopRedis() {
        try {
            redisServer?.stop()
            println("Embedded Redis stopped")
        } catch (e: Exception) {
            println("Failed to stop Embedded Redis: ${e.message}")
        }
    }
}