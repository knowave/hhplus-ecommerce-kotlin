package com.hhplus.ecommerce.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

/**
 * 비동기 처리 설정
 *
 * @Async 어노테이션을 사용하는 메서드들의 스레드 풀을 설정합니다.
 */
@Configuration
@EnableAsync
class AsyncConfig {

    /**
     * 기본 비동기 작업용 TaskExecutor
     *
     * 주문 이벤트 처리, 카트 삭제, 랭킹 업데이트 등 비동기 작업에 사용됩니다.
     */
    @Bean(name = ["taskExecutor"])
    fun taskExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 5          // 기본 스레드 수
        executor.maxPoolSize = 10          // 최대 스레드 수
        executor.queueCapacity = 100       // 대기 큐 크기
        executor.setThreadNamePrefix("async-task-")
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.setAwaitTerminationSeconds(30)
        executor.initialize()
        return executor
    }
}

