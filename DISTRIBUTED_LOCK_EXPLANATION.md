# 분산락 어노테이션 동작 원리 상세 설명

## 🎯 핵심 개념: Spring AOP 프록시 패턴

어노테이션만으로 분산락이 동작하는 비밀은 **Spring AOP의 프록시(Proxy) 패턴**에 있습니다.

---

## 1. Spring이 애플리케이션을 시작할 때 하는 일

```kotlin
// ❌ Spring이 하는 것이 아닌 것
val couponService = CouponServiceImpl(couponRepository, userCouponRepository)

// ✅ Spring이 실제로 하는 것
val realCouponService = CouponServiceImpl(couponRepository, userCouponRepository)
val proxyCouponService = createProxyFor(realCouponService)  // 프록시 생성!

// 스프링 컨테이너에 등록되는 것은 프록시 객체
container.register("couponService", proxyCouponService)
```

### Spring의 프록시 생성 로직 (의사 코드)

```kotlin
fun createProxyFor(target: CouponServiceImpl): CouponService {
    return object : CouponService {
        override fun issueCoupon(couponId: UUID, request: IssueCouponCommand): IssueCouponResult {
            // 1. @DistributedLock 어노테이션이 있는지 확인
            val annotation = target::issueCoupon.getAnnotation(DistributedLock::class)

            if (annotation != null) {
                // 2. DistributedLockAspect.around() 메서드 호출
                return distributedLockAspect.around(
                    joinPoint = JoinPoint(target, "issueCoupon", [couponId, request]),
                    distributedLock = annotation
                )
            } else {
                // 어노테이션이 없으면 그냥 실행
                return target.issueCoupon(couponId, request)
            }
        }

        // 다른 메서드들도 동일하게 프록시 처리...
    }
}
```

---

## 2. 실제 호출 시 상세 흐름

### 클라이언트 코드가 실행되면:

```kotlin
// 컨트롤러에서
@PostMapping("/coupons/{couponId}/issue")
fun issueCoupon(@PathVariable couponId: UUID, @RequestBody request: IssueCouponCommand) {
    // ⭐ 여기서 호출하는 couponService는 실제로 프록시 객체입니다!
    return couponService.issueCoupon(couponId, request)
}
```

### 실행 흐름 (단계별 상세):

```
1️⃣ couponService.issueCoupon() 호출
   ↓
   [실제로는 프록시의 메서드가 호출됨]

2️⃣ 프록시 객체의 issueCoupon() 실행
   ↓
   프록시: "어라? 이 메서드에 @DistributedLock이 있네?"
   프록시: "DistributedLockAspect.around()를 먼저 실행해야겠다!"

3️⃣ DistributedLockAspect.around() 실행 시작
   ↓
   Aspect: "SpEL 파싱: 'coupon:issue:' + #couponId"
   Aspect: "평가 결과: coupon:issue:12345678-1234-1234-1234-123456789012"
   Aspect: "Redis 락 획득 시도..."
   Aspect: "락 획득 성공! lockValue = uuid-thread-id"

4️⃣ joinPoint.proceed() 호출
   ↓
   [이제야 실제 CouponServiceImpl의 issueCoupon() 실행]
   ↓
   CouponServiceImpl: "비즈니스 로직 실행 중..."
   CouponServiceImpl: "쿠폰 조회, 검증, 발급..."
   CouponServiceImpl: "결과 반환: IssueCouponResult(...)"

5️⃣ Aspect로 다시 돌아옴
   ↓
   Aspect: "비즈니스 로직 성공!"
   Aspect: "unlockAfterCommit=true니까 트랜잭션 커밋 후 락 해제 등록"
   Aspect: "결과 반환"

6️⃣ 프록시가 결과를 클라이언트에게 반환
   ↓
   컨트롤러로 결과 전달
```

---

## 3. 코드로 보는 프록시 동작 원리

### 실제 Spring이 생성하는 프록시 (CGLIB 방식)

```kotlin
// Spring이 런타임에 생성하는 프록시 클래스 (개념적 표현)
class CouponServiceImpl$$EnhancerBySpringCGLIB : CouponServiceImpl {

    private val target: CouponServiceImpl  // 실제 객체
    private val aspectChain: List<Aspect>  // 적용할 Aspect 리스트

    override fun issueCoupon(couponId: UUID, request: IssueCouponCommand): IssueCouponResult {
        // 1. 이 메서드에 적용할 Aspect 찾기
        val applicableAspects = aspectChain.filter {
            it.matches(method = "issueCoupon", annotations = [@DistributedLock])
        }

        // 2. Aspect 체인 실행
        var result: Any? = null
        for (aspect in applicableAspects) {
            result = aspect.invoke {
                // 3. 최종적으로 실제 메서드 호출
                target.issueCoupon(couponId, request)
            }
        }

        return result as IssueCouponResult
    }
}
```

---

## 4. DistributedLockAspect의 @Around가 하는 일

### @Around의 동작 원리

```kotlin
@Around("@annotation(distributedLock)")
fun around(joinPoint: ProceedingJoinPoint, distributedLock: DistributedLock): Any? {

    // 🔹 BEFORE: 실제 메서드 실행 전
    val lockKey = parseLockKey(distributedLock.key, joinPoint)
    val lockValue = redisDistributedLock.tryLock(lockKey, ...)
        ?: throw LockAcquisitionFailedException(...)

    try {
        // 🔸 PROCEED: 실제 메서드 실행
        val result = joinPoint.proceed()  // ← 여기서 실제 비즈니스 로직 실행!

        // 🔹 AFTER: 실제 메서드 실행 후
        if (distributedLock.unlockAfterCommit) {
            redisDistributedLock.unlockAfterCommit(lockKey, lockValue)
        } else {
            redisDistributedLock.unlock(lockKey, lockValue)
        }

        return result

    } catch (e: Exception) {
        // 🔹 AFTER THROWING: 예외 발생 시
        redisDistributedLock.unlock(lockKey, lockValue)
        throw e
    }
}
```

### joinPoint.proceed()의 의미

```kotlin
// joinPoint는 다음 정보를 포함:
joinPoint = {
    target: CouponServiceImpl 객체,
    method: "issueCoupon",
    args: [couponId, request],
    signature: MethodSignature
}

// proceed()는 실제로 다음을 실행:
joinPoint.proceed() ≈ target.issueCoupon(couponId, request)
```

---

## 5. SpEL 파싱 원리

### SpEL 표현식이 평가되는 과정

```kotlin
// 1. 어노테이션에 작성한 SpEL
@DistributedLock(key = "'coupon:issue:' + #couponId")

// 2. 런타임에 파싱 및 평가
fun parseLockKey(keyExpression: String, joinPoint: ProceedingJoinPoint): String {
    // 메서드 파라미터 추출
    val parameterNames = ["couponId", "request"]
    val args = [UUID("12345678-..."), IssueCouponCommand(...)]

    // SpEL 컨텍스트 생성
    val context = StandardEvaluationContext()
    context.setVariable("couponId", UUID("12345678-..."))
    context.setVariable("request", IssueCouponCommand(...))

    // SpEL 표현식 파싱
    val expression = spelParser.parseExpression("'coupon:issue:' + #couponId")

    // 평가 (실제 값으로 치환)
    val result = expression.getValue(context, String::class.java)
    // result = "coupon:issue:12345678-1234-1234-1234-123456789012"

    return result
}
```

### SpEL 표현식 예시와 결과

| SpEL 표현식 | 파라미터 | 평가 결과 |
|------------|---------|----------|
| `'coupon:issue:' + #couponId` | `couponId=123` | `"coupon:issue:123"` |
| `#request.userId` | `request={userId: 456}` | `"456"` |
| `'order:' + #userId + ':' + #orderId` | `userId=1, orderId=999` | `"order:1:999"` |
| `#userId.toString()` | `userId=UUID(...)` | `"12345678-..."` |

---

## 6. 왜 이것이 가능한가?

### Spring AOP의 세 가지 핵심 기술

#### 1️⃣ **프록시 패턴 (Proxy Pattern)**
```
실제 객체를 감싸는 래퍼(Wrapper) 객체를 만들어서
메서드 호출을 가로채고 추가 로직을 실행합니다.
```

#### 2️⃣ **런타임 바이트코드 생성 (CGLIB/JDK Dynamic Proxy)**
```
Spring은 애플리케이션 시작 시 바이트코드를 동적으로 생성하여
프록시 클래스를 만듭니다.

CGLIB: 클래스를 상속받아 프록시 생성 (일반적)
JDK Proxy: 인터페이스를 구현하여 프록시 생성
```

#### 3️⃣ **리플렉션 (Reflection)**
```
런타임에 클래스의 메타데이터(어노테이션, 메서드 시그니처 등)를
읽고 조작할 수 있습니다.

예: method.getAnnotation(DistributedLock::class)
```

---

## 7. 다른 Spring 기능도 같은 원리

이 프록시 패턴은 Spring의 많은 기능에서 사용됩니다:

### @Transactional의 동작 원리
```kotlin
@Transactional  // ← 이것도 AOP!
fun updateUser(user: User) {
    userRepository.save(user)
}

// 실제 실행:
프록시 -> TransactionInterceptor.invoke {
    트랜잭션 시작
    try {
        실제 메서드 실행
        트랜잭션 커밋
    } catch (e) {
        트랜잭션 롤백
    }
}
```

### @Cacheable의 동작 원리
```kotlin
@Cacheable("products")  // ← 이것도 AOP!
fun getProduct(id: UUID): Product {
    return productRepository.findById(id)
}

// 실제 실행:
프록시 -> CacheInterceptor.invoke {
    캐시에서 조회
    if (캐시에 있음) {
        캐시 값 반환  // 실제 메서드 실행 안 함!
    } else {
        실제 메서드 실행
        결과를 캐시에 저장
        결과 반환
    }
}
```

---

## 8. 디버깅으로 확인하기

### 프록시 객체 확인하는 방법

```kotlin
@Service
class SomeService(
    private val couponService: CouponService
) {
    fun test() {
        // 프록시 객체인지 확인
        println("클래스 이름: ${couponService.javaClass.name}")
        // 출력: CouponServiceImpl$$EnhancerBySpringCGLIB$$12345678

        println("프록시인가? ${AopUtils.isAopProxy(couponService)}")
        // 출력: true

        println("CGLIB 프록시인가? ${AopUtils.isCglibProxy(couponService)}")
        // 출력: true
    }
}
```

### 실행 흐름 로깅

```kotlin
@Aspect
@Component
class DistributedLockAspect(...) {

    @Around("@annotation(distributedLock)")
    fun around(joinPoint: ProceedingJoinPoint, distributedLock: DistributedLock): Any? {

        logger.debug("🔒 [BEFORE] 분산락 획득 시도")
        logger.debug("   - 메서드: ${joinPoint.signature.name}")
        logger.debug("   - 파라미터: ${joinPoint.args.joinToString()}")

        val lockKey = parseLockKey(...)
        logger.debug("   - 락 키: $lockKey")

        val lockValue = redisDistributedLock.tryLock(...)
        logger.debug("   - 락 획득 성공: $lockValue")

        try {
            logger.debug("🚀 [PROCEED] 실제 메서드 실행 시작")
            val result = joinPoint.proceed()
            logger.debug("✅ [AFTER] 메서드 실행 완료")

            return result
        } catch (e: Exception) {
            logger.debug("❌ [EXCEPTION] 예외 발생: ${e.message}")
            throw e
        } finally {
            logger.debug("🔓 [FINALLY] 분산락 해제")
        }
    }
}
```

---

## 9. 주의사항

### ⚠️ 프록시가 동작하지 않는 경우

```kotlin
@Service
class CouponServiceImpl {

    @DistributedLock(...)
    fun issueCoupon() { ... }

    // ❌ 같은 클래스 내부에서 호출하면 프록시를 거치지 않음!
    fun someMethod() {
        this.issueCoupon()  // 프록시 없이 직접 호출됨 → 락 동작 안 함!
    }
}
```

**해결 방법:**
```kotlin
@Service
class CouponServiceImpl(
    private val self: CouponServiceImpl  // 자기 자신을 주입받기
) {
    fun someMethod() {
        self.issueCoupon()  // 프록시를 통해 호출 → 락 동작!
    }
}
```

---

## 10. 정리: 어노테이션만으로 분산락이 동작하는 이유

```
1. Spring은 애플리케이션 시작 시 @Component가 붙은 클래스를 스캔

2. @Aspect가 붙은 DistributedLockAspect를 발견

3. @DistributedLock 어노테이션이 붙은 메서드가 있는 클래스에 대해
   프록시 객체를 생성 (CGLIB 또는 JDK Dynamic Proxy)

4. 프록시 객체가 스프링 컨테이너에 등록됨

5. 다른 빈이 CouponService를 주입받으면 실제로는 프록시 객체를 받음

6. 메서드 호출 시:
   프록시 → Aspect (락 획득) → 실제 객체 → Aspect (락 해제) → 호출자
```

---

## 💡 결론

어노테이션만으로 분산락이 동작하는 것은 **"마법"이 아니라 "공학"**입니다!

- **프록시 패턴**: 실제 객체를 감싸는 래퍼 객체
- **런타임 바이트코드 생성**: 동적으로 프록시 클래스 생성
- **리플렉션**: 어노테이션과 메타데이터 읽기
- **AOP**: 관심사의 분리 (비즈니스 로직 vs 인프라 로직)

이 모든 기술이 조합되어 **"선언적 프로그래밍"**을 가능하게 합니다!
