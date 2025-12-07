# STEP11 - Redis 분산락 구현 보고서

## 1. 개요

### 1.1 분산락이란?

분산락(Distributed Lock)은 **다중 서버 환경**에서 공유 자원에 대한 동시 접근을 제어하는 메커니즘입니다. 단일 서버에서는 `synchronized`나 `ReentrantLock`으로 해결할 수 있지만, 여러 서버가 동일한 자원에 접근할 때는 **외부 저장소(Redis)**를 이용한 분산락이 필요합니다.

### 1.2 도입 배경

이커머스 시스템에서 다음과 같은 동시성 문제가 발생할 수 있습니다:

| 시나리오         | 문제 상황                          | 결과      |
| ---------------- | ---------------------------------- | --------- |
| 선착순 쿠폰 발급 | 100개 한정 쿠폰에 1000명 동시 요청 | 초과 발급 |
| 주문 생성        | 같은 사용자가 중복 클릭            | 중복 주문 |
| 결제 처리        | 같은 주문에 동시 결제 요청         | 중복 결제 |
| 재고 차감        | 10개 재고에 100명 동시 주문        | 음수 재고 |

---

## 2. 분산락 구현

### 2.1 아키텍처

```
┌─────────────────────────────────────────────────────────────────┐
│                        Client Request                            │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│              DistributedLockAspect (AOP - HIGHEST_PRECEDENCE)    │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ 1. SpEL 파싱 → 동적 락 키 생성                           │    │
│  │ 2. Redis SET NX (락 획득 시도)                           │    │
│  │ 3. 락 획득 실패 시 LockAcquisitionFailedException       │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
                                │ 락 획득 성공
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│               @Transactional (트랜잭션 시작)                     │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                   Service Layer                          │    │
│  │  • 비관적 락으로 데이터 조회 (SELECT FOR UPDATE)          │    │
│  │  • 비즈니스 로직 실행                                    │    │
│  │  • 데이터 저장                                           │    │
│  └─────────────────────────────────────────────────────────┘    │
│               @Transactional (트랜잭션 커밋)                     │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│              TransactionSynchronization (커밋 후)                │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ Redis 분산락 해제 (unlockAfterCommit = true)             │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 핵심 컴포넌트

#### 2.2.1 @DistributedLock 어노테이션

```kotlin
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class DistributedLock(
    val key: String,                    // 락 키 (SpEL 지원)
    val waitTimeMs: Long = 3000,        // 락 대기 시간 (ms)
    val leaseTimeMs: Long = 5000,       // 락 유지 시간 (ms, 데드락 방지)
    val errorMessage: String = "...",   // 실패 메시지
    val unlockAfterCommit: Boolean = true  // 트랜잭션 커밋 후 해제
)
```

**SpEL 표현식 지원**:
| 표현식 유형 | 예시 | 결과 |
|-----------|------|------|
| 고정값 | `"'my-lock-key'"` | `my-lock-key` |
| 파라미터 | `"#couponId"` | `실제 couponId 값` |
| 조합 | `"'coupon:issue:' + #couponId"` | `coupon:issue:uuid값` |
| 객체 필드 | `"#request.userId"` | `실제 userId 값` |

#### 2.2.2 DistributedLockAspect

```kotlin
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)  // 트랜잭션보다 먼저 실행
class DistributedLockAspect(
    private val redisDistributedLock: RedisDistributedLock
) {
    @Around("@annotation(distributedLock)")
    fun around(joinPoint: ProceedingJoinPoint, distributedLock: DistributedLock): Any? {
        // 1. SpEL 파싱하여 동적 락 키 생성
        val lockKey = parseLockKey(distributedLock.key, joinPoint)

        // 2. 락 획득 시도
        val lockValue = redisDistributedLock.tryLock(lockKey, ...)
            ?: throw LockAcquisitionFailedException(distributedLock.errorMessage)

        try {
            // 3. 비즈니스 로직 실행
            val result = joinPoint.proceed()

            // 4. 트랜잭션 커밋 후 락 해제
            if (distributedLock.unlockAfterCommit) {
                redisDistributedLock.unlockAfterCommit(lockKey, lockValue)
            }
            return result
        } catch (e: Exception) {
            // 5. 예외 시 즉시 락 해제
            redisDistributedLock.unlock(lockKey, lockValue)
            throw e
        }
    }
}
```

**`@Order(Ordered.HIGHEST_PRECEDENCE)` 적용 이유**:

분산락 Aspect가 트랜잭션 Aspect보다 먼저 실행되어야 합니다:

```
✅ 올바른 순서 (분산락 → 트랜잭션):
┌─ 분산락 획득 ─────────────────────────────────┐
│  ┌─ 트랜잭션 시작 ─────────────────────┐      │
│  │  비즈니스 로직 실행                  │      │
│  │  DB 변경                            │      │
│  └─ 트랜잭션 커밋 ─────────────────────┘      │
└─ 분산락 해제 (커밋 후) ───────────────────────┘

❌ 잘못된 순서 (트랜잭션 → 분산락):
┌─ 트랜잭션 시작 ─────────────────────────────────┐
│  ┌─ 분산락 획득 ─────────────────────┐         │
│  │  비즈니스 로직 실행                 │         │
│  └─ 분산락 해제 ─────────────────────┘  ← 락 해제
│                                                 │
└─ 트랜잭션 커밋 ─────────────────────────────────┘  ← 커밋 전에 다른 요청 접근!
```

#### 2.2.3 RedisDistributedLock

```kotlin
@Component
class RedisDistributedLock(
    private val redisTemplate: RedisTemplate<String, String>
) {
    // Lua 스크립트: 본인 락만 해제 (원자적 연산)
    private val unlockScript = DefaultRedisScript<Long>().apply {
        setScriptText("""
            if redis.call("get", KEYS[1]) == ARGV[1] then
                return redis.call("del", KEYS[1])
            else
                return 0
            end
        """)
    }

    fun tryLock(lockKey: String, waitTimeMs: Long, leaseTimeMs: Long): String? {
        val lockValue = "${UUID.randomUUID()}-${Thread.currentThread().id}"
        val endTime = System.currentTimeMillis() + waitTimeMs

        // Spin Lock 방식으로 대기
        while (System.currentTimeMillis() < endTime) {
            val acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, Duration.ofMillis(leaseTimeMs))

            if (acquired == true) return lockValue
            TimeUnit.MILLISECONDS.sleep(50)  // 50ms 간격 재시도
        }
        return null  // 대기 시간 초과
    }

    fun unlockAfterCommit(lockKey: String, lockValue: String) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        unlock(lockKey, lockValue)
                    }
                    override fun afterCompletion(status: Int) {
                        if (status == STATUS_ROLLED_BACK) {
                            unlock(lockKey, lockValue)
                        }
                    }
                }
            )
        } else {
            unlock(lockKey, lockValue)
        }
    }
}
```

---

## 3. 적용 현황: 락 키와 범위 설계

### 3.1 CouponService - 선착순 쿠폰 발급

| 항목             | 값                                                         |
| ---------------- | ---------------------------------------------------------- |
| **락 키**        | `coupon:issue:{couponId}`                                  |
| **키 선정 이유** | 같은 쿠폰에 대한 발급 요청만 직렬화, 다른 쿠폰은 병렬 처리 |
| **락 범위**      | `issueCoupon()` 메서드 전체                                |
| **waitTimeMs**   | 3,000ms (선착순은 빠른 실패가 중요)                        |
| **leaseTimeMs**  | 10,000ms                                                   |

```kotlin
@DistributedLock(
    key = "'coupon:issue:' + #couponId",
    waitTimeMs = 3000,
    leaseTimeMs = 10000,
    errorMessage = "쿠폰 발급 요청이 많습니다. 잠시 후 다시 시도해주세요.",
    unlockAfterCommit = true
)
@Transactional
override fun issueCoupon(couponId: UUID, request: IssueCouponCommand): IssueCouponResult {
    // 비관적 락으로 쿠폰 조회 (DB 레벨 2차 방어)
    val coupon = couponRepository.findByIdWithLock(couponId)

    // 중복 발급 검증
    // 재고 검증
    // 발급 처리
}
```

**왜 이 키인가?**

- `userId` 기반: 같은 사용자의 중복 요청만 막음 → 다른 사용자 요청은 동시 처리되어 재고 초과 가능
- `couponId` 기반: 같은 쿠폰에 대한 모든 요청 직렬화 → 재고 정합성 보장 ✅

### 3.2 OrderService - 주문 생성/취소

#### 주문 생성

| 항목             | 값                                                         |
| ---------------- | ---------------------------------------------------------- |
| **락 키**        | `order:create:{userId}`                                    |
| **키 선정 이유** | 같은 사용자의 중복 주문 방지, 다른 사용자 주문은 병렬 처리 |
| **락 범위**      | `createOrderTransaction()` 메서드                          |
| **waitTimeMs**   | 5,000ms                                                    |
| **leaseTimeMs**  | 15,000ms (재고 확인, 쿠폰 적용 등 복잡한 로직)             |

```kotlin
@DistributedLock(
    key = "'order:create:' + #userId",
    waitTimeMs = 5000,
    leaseTimeMs = 15000,
    errorMessage = "주문 처리 중입니다. 잠시 후 다시 시도해주세요.",
    unlockAfterCommit = true
)
@Transactional
internal fun createOrderTransaction(request: CreateOrderCommand, userId: UUID): OrderCreationData {
    // 상품 비관적 락 (재고 정합성)
    val productIds = request.items.map { it.productId }.distinct().sorted()
    val products = productService.findAllByIdWithLock(productIds)

    // 재고 확인 및 차감
    // 쿠폰 적용
    // 주문 생성
}
```

**왜 이 키인가?**

- 같은 사용자가 버튼 더블클릭 시 중복 주문 방지
- 다른 사용자의 주문은 독립적으로 처리 가능

#### 주문 취소

| 항목             | 값                              |
| ---------------- | ------------------------------- |
| **락 키**        | `order:cancel:{orderId}`        |
| **키 선정 이유** | 같은 주문에 대한 중복 취소 방지 |
| **락 범위**      | `cancelOrder()` 메서드          |

```kotlin
@DistributedLock(
    key = "'order:cancel:' + #orderId",
    waitTimeMs = 5000,
    leaseTimeMs = 15000,
    errorMessage = "주문 취소 처리 중입니다. 잠시 후 다시 시도해주세요.",
    unlockAfterCommit = true
)
@Transactional
override fun cancelOrder(orderId: UUID, request: CancelOrderCommand): CancelOrderResult
```

### 3.3 PaymentService - 결제 처리/취소

#### 결제 처리

| 항목             | 값                              |
| ---------------- | ------------------------------- |
| **락 키**        | `payment:process:{orderId}`     |
| **키 선정 이유** | 같은 주문에 대한 중복 결제 방지 |
| **락 범위**      | `processPayment()` 메서드       |
| **waitTimeMs**   | 5,000ms                         |
| **leaseTimeMs**  | 15,000ms                        |

```kotlin
@DistributedLock(
    key = "'payment:process:' + #orderId",
    waitTimeMs = 5000,
    leaseTimeMs = 15000,
    errorMessage = "결제 처리 중입니다. 잠시 후 다시 시도해주세요.",
    unlockAfterCommit = true
)
@Transactional
override fun processPayment(orderId: UUID, request: ProcessPaymentCommand): ProcessPaymentResult {
    // 비관적 락으로 주문 조회 (DB 레벨 2차 방어)
    val order = orderService.getOrderWithLock(orderId)

    // 주문 상태 검증 (PENDING만 결제 가능)
    // 잔액 차감
    // 결제 처리
    // 배송 정보 생성
}
```

**왜 `orderId` 기반인가?**

- `userId` 기반: 같은 사용자의 다른 주문 결제도 직렬화됨 → 불필요한 대기
- `orderId` 기반: 같은 주문에 대한 중복 결제만 방지 ✅

#### 결제 취소

| 항목             | 값                              |
| ---------------- | ------------------------------- |
| **락 키**        | `payment:cancel:{paymentId}`    |
| **키 선정 이유** | 같은 결제에 대한 중복 취소 방지 |
| **락 범위**      | `cancelPayment()` 메서드        |

```kotlin
@DistributedLock(
    key = "'payment:cancel:' + #paymentId",
    waitTimeMs = 5000,
    leaseTimeMs = 15000,
    errorMessage = "결제 취소 처리 중입니다. 잠시 후 다시 시도해주세요.",
    unlockAfterCommit = true
)
@Transactional
override fun cancelPayment(paymentId: UUID, request: CancelPaymentCommand): CancelPaymentResult
```

---

## 4. 락 키/범위 설계 요약

| 기능      | 락 키                        | 범위                   | waitTime | leaseTime |
| --------- | ---------------------------- | ---------------------- | -------- | --------- |
| 쿠폰 발급 | `coupon:issue:{couponId}`    | issueCoupon            | 3s       | 10s       |
| 주문 생성 | `order:create:{userId}`      | createOrderTransaction | 5s       | 15s       |
| 주문 취소 | `order:cancel:{orderId}`     | cancelOrder            | 5s       | 15s       |
| 결제 처리 | `payment:process:{orderId}`  | processPayment         | 5s       | 15s       |
| 결제 취소 | `payment:cancel:{paymentId}` | cancelPayment          | 5s       | 15s       |

---

## 5. 통합 테스트

### 5.1 테스트 환경 구성

```kotlin
@DataJpaTest
@ComponentScan(basePackages = ["com.hhplus.ecommerce"])
@TestPropertySource(properties = [
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379"
])
@Import(
    EmbeddedRedisConfig::class,
    TestRedisConfig::class
)
class PaymentServiceIntegrationTest
```

### 5.2 쿠폰 발급 동시성 테스트

**시나리오**: 100개 한정 쿠폰에 200명이 동시 발급 요청

```kotlin
it("100개의 쿠폰을 200명이 동시에 발급받을 때 Redis 분산락으로 순차 처리된다") {
    // given - 100개 한정 쿠폰 생성
    val couponId = executeInNewTransaction {
        val coupon = Coupon(
            name = "선착순 100명 쿠폰",
            totalQuantity = 100,
            issuedQuantity = 0,
            // ...
        )
        couponRepository.save(coupon).id!!
    }

    // given - 200명의 사용자 생성
    val userIds = (1..200).map {
        executeInNewTransaction { userService.createUser(...).id!! }
    }

    // when - 200명이 동시에 쿠폰 발급 시도
    val executorService = Executors.newFixedThreadPool(200)
    val latch = CountDownLatch(200)
    val successCount = AtomicInteger(0)
    val soldOutCount = AtomicInteger(0)

    userIds.forEach { userId ->
        executorService.submit {
            latch.countDown()
            latch.await()  // 모든 스레드가 동시에 시작
            try {
                executeInNewTransaction {
                    couponService.issueCoupon(couponId, IssueCouponCommand(userId))
                }
                successCount.incrementAndGet()
            } catch (e: CouponSoldOutException) {
                soldOutCount.incrementAndGet()
            }
        }
    }

    // then - 정확히 100개만 발급됨
    successCount.get() shouldBe 100
    soldOutCount.get() shouldBe 100

    val actualIssuedCount = couponRepository.findById(couponId).get().issuedQuantity
    actualIssuedCount shouldBe 100
}
```

### 5.3 결제 중복 방지 테스트

**시나리오**: 같은 주문에 5번 동시 결제 요청

```kotlin
it("동일한 주문에 대해 동시에 5번 중복 결제 요청 시 1번만 성공해야 한다") {
    // given - 주문 생성
    val order = executeInNewTransaction {
        orderService.createOrder(CreateOrderCommand(
            userId = testUserId,
            items = listOf(OrderItemCommand(productId, 1)),
            couponId = null
        ))
    }

    // when - 5개 스레드에서 동시에 같은 주문 결제 시도
    val executorService = Executors.newFixedThreadPool(5)
    val latch = CountDownLatch(5)
    val successCount = AtomicInteger(0)
    val duplicatePaymentCount = AtomicInteger(0)

    repeat(5) {
        executorService.submit {
            latch.countDown()
            latch.await()
            try {
                executeInNewTransaction {
                    paymentService.processPayment(order.orderId, ProcessPaymentCommand(testUserId))
                }
                successCount.incrementAndGet()
            } catch (e: AlreadyPaidException) {
                duplicatePaymentCount.incrementAndGet()
            } catch (e: InvalidOrderStatusException) {
                duplicatePaymentCount.incrementAndGet()
            }
        }
    }

    // then - 오직 1개만 성공, 4개는 중복 결제 예외
    successCount.get() shouldBe 1
    duplicatePaymentCount.get() shouldBe 4
}
```

### 5.4 테스트 결과

```
CouponServiceIntegrationTest
├── 쿠폰 발급
│   ├── ✅ 사용자에게 쿠폰을 발급할 수 있다
│   ├── ✅ 쿠폰 발급 후 잔여 수량이 감소한다
│   └── ✅ 동일한 사용자가 같은 쿠폰을 중복 발급받을 수 없다
└── 동시성 테스트 - Redis 분산락
    └── ✅ 100개의 쿠폰을 200명이 동시에 발급받을 때 Redis 분산락으로 순차 처리된다

OrderServiceIntegrationTest
├── 주문 생성 및 조회 통합 시나리오
│   ├── ✅ 사용자가 상품을 주문하고 조회할 수 있다
│   └── ✅ 주문 후 상품 재고가 감소한다
└── 동시성 테스트 - 재고 차감
    └── ✅ 10개 재고 상품을 20명이 동시에 주문하면 정확히 10명만 성공한다

PaymentServiceIntegrationTest
├── 결제 처리 통합 시나리오
│   ├── ✅ 주문 생성 후 결제를 처리할 수 있다
│   ├── ✅ 결제 후 사용자 잔액이 감소한다
│   └── ✅ 이미 결제된 주문은 다시 결제할 수 없다
└── 분산락 동시성 테스트
    ├── ✅ 20명의 사용자가 각각 다른 상품을 주문 후 동시에 결제하면 모두 성공한다
    └── ✅ 동일한 주문에 대해 동시에 5번 중복 결제 요청 시 1번만 성공해야 한다

BUILD SUCCESSFUL - 226 tests passed
```

---

## 6. 이중 락 전략: 분산락 + 비관적 락

### 6.1 왜 이중 락인가?

| 상황       | 분산락만          | 비관적 락만    | 분산락 + 비관적 락    |
| ---------- | ----------------- | -------------- | --------------------- |
| 단일 서버  | ✅                | ✅             | ✅                    |
| 다중 서버  | ✅                | ❌ (서버별 락) | ✅                    |
| Redis 장애 | ❌                | ✅             | ✅ (비관적 락이 백업) |
| 대량 요청  | ✅ (Redis 필터링) | ❌ (DB 부하)   | ✅                    |

### 6.2 구현 예시

```kotlin
// 1차 방어: Redis 분산락
@DistributedLock(key = "'coupon:issue:' + #couponId", ...)
@Transactional
override fun issueCoupon(couponId: UUID, request: IssueCouponCommand) {
    // 2차 방어: DB 비관적 락
    val coupon = couponRepository.findByIdWithLock(couponId)
    // ...
}
```

---

## 7. 결론

### 7.1 구현 요약

| 항목         | 내용                                      |
| ------------ | ----------------------------------------- |
| 락 저장소    | Redis (메모리 기반, 빠른 속도)            |
| 적용 방식    | AOP 기반 선언적 적용 (`@DistributedLock`) |
| 락 키 생성   | SpEL 표현식으로 동적 생성                 |
| 락 해제 시점 | 트랜잭션 커밋 후 (`unlockAfterCommit`)    |
| 백업 전략    | DB 비관적 락 (이중 방어)                  |

### 7.2 적용 효과

1. **중복 처리 방지**: 같은 리소스에 대한 동시 요청 직렬화
2. **데이터 정합성**: 트랜잭션 커밋 후 락 해제로 최신 데이터 보장
3. **DB 부하 감소**: Redis 레벨에서 대량 요청 필터링
4. **코드 가독성**: 어노테이션 기반으로 비즈니스 로직과 동시성 제어 분리
