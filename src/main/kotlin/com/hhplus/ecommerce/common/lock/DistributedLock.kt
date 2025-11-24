package com.hhplus.ecommerce.common.lock

/**
 * 분산 락 어노테이션
 *
 * Redis 기반 분산 락을 선언적으로 적용하기 위한 어노테이션입니다.
 * SpEL(Spring Expression Language)을 사용하여 동적 락 키를 생성할 수 있습니다.
 *
 * 사용 예시:
 * ```
 * @DistributedLock(
 *     key = "'coupon:issue:' + #couponId",
 *     waitTimeMs = 3000,
 *     leaseTimeMs = 10000,
 *     errorMessage = "쿠폰 발급 요청이 많습니다. 잠시 후 다시 시도해주세요."
 * )
 * fun issueCoupon(couponId: UUID, request: IssueCouponCommand): IssueCouponResult {
 *     // 비즈니스 로직만 작성
 * }
 * ```
 *
 * @property key 락 키 (SpEL 표현식 지원)
 *               - 고정값: "'my-lock-key'"
 *               - 파라미터 참조: "#couponId"
 *               - 조합: "'coupon:issue:' + #couponId"
 *               - 객체 필드: "#request.userId"
 *
 * @property waitTimeMs 락 획득 대기 시간 (밀리초)
 *                      락을 얻을 때까지 대기하는 최대 시간
 *                      기본값: 3000ms (3초)
 *
 * @property leaseTimeMs 락 유지 시간 (밀리초)
 *                       락이 자동으로 해제되는 시간 (데드락 방지)
 *                       기본값: 5000ms (5초)
 *
 * @property errorMessage 락 획득 실패 시 예외 메시지
 *                        기본값: "락 획득에 실패했습니다. 잠시 후 다시 시도해주세요."
 *
 * @property unlockAfterCommit 트랜잭션 커밋 후 락 해제 여부
 *                              true: 트랜잭션 커밋 후 락 해제 (데이터 정합성 보장)
 *                              false: 메서드 종료 시 즉시 락 해제
 *                              기본값: true
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class DistributedLock(
    val key: String,
    val waitTimeMs: Long = 3000,
    val leaseTimeMs: Long = 5000,
    val errorMessage: String = "락 획득에 실패했습니다. 잠시 후 다시 시도해주세요.",
    val unlockAfterCommit: Boolean = true
)
