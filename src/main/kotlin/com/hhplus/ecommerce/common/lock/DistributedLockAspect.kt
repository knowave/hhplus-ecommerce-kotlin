package com.hhplus.ecommerce.common.lock

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import org.springframework.stereotype.Component

/**
 * 분산 락 AOP Aspect (Circuit Breaker 지원)
 *
 * @DistributedLock 어노테이션이 적용된 메서드에 대해 분산 락을 자동으로 적용합니다.
 *
 * 처리 흐름:
 * 1. SpEL 표현식을 파싱하여 동적 락 키 생성
 * 2. 분산 락 획득 시도 (Redis 우선, 실패 시 DB Fallback)
 * 3. 락 획득 성공 시 비즈니스 로직 실행
 * 4. 정상 처리 또는 예외 발생 시 락 해제
 *    - unlockAfterCommit=true: 트랜잭션 커밋 후 락 해제
 *    - unlockAfterCommit=false: 메서드 종료 시 즉시 락 해제
 *
 * Circuit Breaker 패턴:
 * - Redis 연결 장애 감지 시 자동으로 DB Fallback
 * - Redis 복구 시 자동으로 Redis 분산락으로 전환
 *
 * 조건부 활성화:
 * - app.lock.enabled=false 설정 시 이 Bean이 생성되지 않음 (순수 DB 비관적락만 사용)
 * - 기본값: true (분산락 활성화)
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(
    name = ["app.lock.enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class DistributedLockAspect(
    private val distributedLockService: DistributedLockService
) {
    private val logger = LoggerFactory.getLogger(DistributedLockAspect::class.java)
    private val spelParser = SpelExpressionParser()

    /**
     * @DistributedLock 어노테이션이 적용된 메서드에 Around Advice 적용
     *
     * 주의: 어노테이션 바인딩 방식(@annotation(param))은 Spring AOP 버전에 따라
     * 바인딩 오류가 발생할 수 있어, joinPoint에서 직접 어노테이션을 추출하는 방식 사용
     */
    @Around("@annotation(DistributedLock)")
    fun around(joinPoint: ProceedingJoinPoint): Any? {
        // 메서드에서 @DistributedLock 어노테이션 추출
        val signature = joinPoint.signature as MethodSignature
        val method = signature.method
        val distributedLock = method.getAnnotation(DistributedLock::class.java)
            ?: throw IllegalStateException("@DistributedLock annotation not found")

        // 1. SpEL 파싱하여 동적 락 키 생성
        val lockKey = parseLockKey(
            keyExpression = distributedLock.key,
            joinPoint = joinPoint
        )

        // 2. 락 획득 시도 (Redis 우선, 장애 시 DB Fallback)
        val lockResult = distributedLockService.tryLock(
            lockKey = lockKey,
            waitTimeMs = distributedLock.waitTimeMs,
            leaseTimeMs = distributedLock.leaseTimeMs
        ) ?: throw LockAcquisitionFailedException(distributedLock.errorMessage)

        logger.debug("Lock acquired: key={}, strategy={}", lockKey, lockResult.strategy)

        try {
            // 3. 비즈니스 로직 실행
            val result = joinPoint.proceed()

            // 4. 정상 처리 시 락 해제 (unlockAfterCommit 옵션에 따라)
            if (distributedLock.unlockAfterCommit) {
                // 트랜잭션 커밋 후 락 해제 (데이터 정합성 보장)
                distributedLockService.unlockAfterCommit(lockKey, lockResult)
                logger.debug("Lock will be released after transaction commit: key={}", lockKey)
            } else {
                // 즉시 락 해제
                val unlocked = distributedLockService.unlock(lockKey, lockResult)
                logger.debug("Lock released immediately: key={}, success={}", lockKey, unlocked)
            }

            return result

        } catch (e: Exception) {
            // 5. 예외 발생 시 즉시 락 해제
            val unlocked = distributedLockService.unlock(lockKey, lockResult)
            logger.warn("Lock released due to exception: key={}, success={}, exception={}",
                lockKey, unlocked, e.message)
            throw e
        }
    }

    /**
     * SpEL 표현식을 파싱하여 락 키 생성
     *
     * 지원하는 표현식 예시:
     * - 고정값: "'my-lock-key'" -> "my-lock-key"
     * - 파라미터 참조: "#couponId" -> "실제 couponId 값"
     * - 조합: "'coupon:issue:' + #couponId" -> "coupon:issue:실제값"
     * - 객체 필드: "#request.userId" -> "실제 userId 값"
     *
     * @param keyExpression SpEL 표현식
     * @param joinPoint AOP 조인포인트 (메서드 정보 및 파라미터 포함)
     * @return 파싱된 락 키
     */
    private fun parseLockKey(keyExpression: String, joinPoint: ProceedingJoinPoint): String {
        try {
            // 메서드 시그니처 가져오기
            val signature = joinPoint.signature as MethodSignature
            val parameterNames = signature.parameterNames
            val args = joinPoint.args

            // SpEL 평가 컨텍스트 생성
            val context = StandardEvaluationContext()

            // 파라미터를 컨텍스트에 등록 (파라미터명 -> 파라미터값)
            parameterNames.forEachIndexed { index, paramName ->
                context.setVariable(paramName, args[index])
            }

            // SpEL 표현식 파싱 및 평가
            val expression = spelParser.parseExpression(keyExpression)
            val evaluatedKey = expression.getValue(context, String::class.java)

            return evaluatedKey ?: throw IllegalArgumentException("Lock key evaluated to null")

        } catch (e: Exception) {
            logger.error("Failed to parse lock key expression: {}", keyExpression, e)
            throw IllegalArgumentException("Invalid lock key expression: $keyExpression", e)
        }
    }
}
