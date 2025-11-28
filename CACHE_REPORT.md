# STEP12 - Redis 캐싱 전략 보고서

## 1. 개요

### 1.1 캐싱이란?

캐싱은 자주 접근하는 데이터를 빠른 저장소(메모리)에 임시 저장하여 응답 시간을 단축하고 DB 부하를 줄이는 기법입니다.

### 1.2 캐싱 적용 대상 선정 기준

| 기준            | 설명                             |
| --------------- | -------------------------------- |
| **조회 빈도**   | 자주 조회되는 데이터             |
| **변경 빈도**   | 변경이 적은 데이터               |
| **데이터 크기** | 너무 크지 않은 데이터            |
| **실시간성**    | 실시간 정합성이 덜 중요한 데이터 |

---

## 2. 캐싱 구간 분석

### 2.1 이커머스 시스템 데이터 분류

| 데이터             | 조회 빈도 | 변경 빈도   | 실시간성  | 캐싱 적합성 |
| ------------------ | --------- | ----------- | --------- | ----------- |
| **상품 정보**      | 매우 높음 | 낮음        | 중간      | ⭐⭐⭐⭐⭐  |
| **인기 상품**      | 높음      | 낮음 (집계) | 낮음      | ⭐⭐⭐⭐⭐  |
| **쿠폰 메타 정보** | 중간      | 낮음        | 중간      | ⭐⭐⭐⭐    |
| 사용자 잔액        | 중간      | 높음        | 높음      | ⭐          |
| 주문 정보          | 낮음      | 높음        | 높음      | ⭐          |
| 재고               | 높음      | 매우 높음   | 매우 높음 | ❌          |

### 2.2 캐싱 적용 대상

#### ✅ 적용 대상

1. **상품 정보 (`products`)**

   - 조회 빈도: 상품 상세 페이지, 주문 시 상품 정보 조회 등
   - 변경 빈도: 관리자가 상품 정보 수정 시에만 변경
   - TTL: 10분

2. **인기 상품 목록 (`topProducts`)**

   - 조회 빈도: 메인 페이지, 카테고리 페이지 등
   - 변경 빈도: 판매량 기반 집계 데이터로 실시간 변경 불필요
   - TTL: 3분

3. **쿠폰 메타 정보 (`coupons`)**
   - 조회 빈도: 쿠폰 목록 조회, 쿠폰 상세 조회
   - 변경 빈도: 쿠폰 생성/수정 시에만 변경
   - TTL: 10분

#### ❌ 미적용 대상

1. **사용자 잔액**: 결제 시 정확한 잔액 필요 (실시간성 높음)
2. **재고**: 동시성 제어 필요, 캐시 정합성 문제 발생 가능
3. **주문 정보**: 사용자별 개인 데이터, 캐시 효율 낮음

---

## 3. 캐싱 전략 설계

### 3.1 캐시 아키텍처

```
┌─────────────────────────────────────────────────────────────────┐
│                        Client Request                            │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Application Layer                           │
│                                                                  │
│   @Cacheable("products")          @CacheEvict("products")       │
│   findProductById(id)             updateProduct(product)         │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
                                │
                    ┌───────────┴───────────┐
                    ▼                       ▼
        ┌─────────────────┐       ┌─────────────────┐
        │   Redis Cache   │       │    Database     │
        │                 │       │                 │
        │  products:uuid  │       │   Product 테이블 │
        │  topProducts:*  │       │                 │
        │  coupons:uuid   │       │                 │
        └─────────────────┘       └─────────────────┘
```

### 3.2 캐시 전략 패턴

#### Cache-Aside (Look-Aside) 패턴

```
조회 시:
1. 캐시 확인 → Hit: 캐시 데이터 반환
2. 캐시 Miss → DB 조회 → 캐시 저장 → 데이터 반환

갱신 시:
1. DB 업데이트
2. 캐시 무효화 (Evict)
```

```kotlin
// 조회: @Cacheable - 캐시 있으면 반환, 없으면 DB 조회 후 캐싱
@Cacheable(value = ["products"], key = "#id")
override fun findProductById(id: UUID): Product {
    return productRepository.findById(id)
        .orElseThrow { ProductNotFoundException(id) }
}

// 갱신: @CacheEvict - 캐시 무효화
@CacheEvict(value = ["products"], key = "#product.id")
override fun updateProduct(product: Product): Product {
    return productRepository.save(product)
}
```

---

## 4. 구현 상세

### 4.1 Redis 캐시 설정

```yaml
# application.yml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      lettuce:
        pool:
          max-active: 10
          max-idle: 10
          min-idle: 2
  cache:
    type: redis
    redis:
      time-to-live: 600000 # 기본 TTL: 10분
```

### 4.2 CacheConfig 설정

```kotlin
@Configuration
@EnableCaching
@ConditionalOnProperty(name = ["spring.cache.type"], havingValue = "redis")
class CacheConfig {
    @Bean
    fun cacheManager(redisConnectionFactory: RedisConnectionFactory): CacheManager {
        // Jackson ObjectMapper 설정
        val objectMapper = ObjectMapper().apply {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

            // 타입 정보 포함 (역직렬화 시 올바른 타입 복원)
            val validator = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Any::class.java)
                .build()
            activateDefaultTyping(validator, ObjectMapper.DefaultTyping.NON_FINAL)
        }

        val serializer = GenericJackson2JsonRedisSerializer(objectMapper)

        // 캐시별 개별 설정
        val cacheConfigurations = mapOf(
            // 상품 정보 캐시 (TTL: 10분)
            "products" to RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(StringRedisSerializer())
                .serializeValuesWith(serializer),

            // 인기 상품 캐시 (TTL: 3분)
            "topProducts" to RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(3))
                .serializeKeysWith(StringRedisSerializer())
                .serializeValuesWith(serializer),

            // 쿠폰 메타 정보 캐시 (TTL: 10분)
            "coupons" to RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(StringRedisSerializer())
                .serializeValuesWith(serializer)
        )

        return RedisCacheManager.builder(redisConnectionFactory)
            .cacheDefaults(defaultCacheConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .build()
    }
}
```

### 4.3 ProductService 캐싱 적용

```kotlin
@Service
class ProductServiceImpl(
    private val productRepository: ProductJpaRepository
) : ProductService {

    /**
     * 상품 정보 조회 (캐싱 적용)
     *
     * 조회 빈도가 매우 높고 변경 빈도가 낮으므로 10분간 캐싱
     * 캐시 키: products::{상품ID}
     */
    @Cacheable(value = ["products"], key = "#id")
    override fun findProductById(id: UUID): Product {
        return productRepository.findById(id)
            .orElseThrow { ProductNotFoundException(id) }
    }

    /**
     * 인기 상품 조회 (캐싱 적용)
     *
     * 조회 빈도가 높고 실시간성이 덜 중요한 집계 데이터이므로 3분간 캐싱
     * 캐시 키: topProducts::days:{days}:limit:{limit}
     */
    @Cacheable(
        value = ["topProducts"],
        key = "'days:' + #days + ':limit:' + #limit",
        unless = "#result == null"
    )
    override fun getTopProducts(days: Int, limit: Int): TopProductsResult {
        val pageable = PageRequest.of(0, limit)
        val topProducts = productRepository.findTopProducts(pageable)
        // ... DTO 변환
    }

    /**
     * 상품 정보 업데이트 (캐시 무효화)
     *
     * 상품 정보가 변경되면 해당 상품의 캐시를 즉시 삭제
     */
    @CacheEvict(value = ["products"], key = "#product.id")
    override fun updateProduct(product: Product): Product {
        return productRepository.save(product)
    }
}
```

---

## 5. 캐시 키 설계

| 캐시 이름   | 키 패턴                                  | 예시                                             | TTL  |
| ----------- | ---------------------------------------- | ------------------------------------------------ | ---- |
| products    | `products::{id}`                         | `products::550e8400-e29b-41d4-a716-446655440000` | 10분 |
| topProducts | `topProducts::days:{days}:limit:{limit}` | `topProducts::days:3:limit:10`                   | 3분  |
| coupons     | `coupons::{id}`                          | `coupons::550e8400-e29b-41d4-a716-446655440000`  | 10분 |

---

## 6. 성능 개선 분석

### 6.1 캐시 적용 전후 비교 (예상)

#### 상품 조회 API

| 항목           | 캐시 미적용 | 캐시 적용      | 개선율        |
| -------------- | ----------- | -------------- | ------------- |
| 평균 응답 시간 | ~50ms       | ~5ms           | **90% 감소**  |
| DB 쿼리 수     | 1회/요청    | 0회 (캐시 Hit) | **100% 감소** |
| DB 부하        | 높음        | 낮음           | -             |

#### 인기 상품 조회 API

| 항목           | 캐시 미적용      | 캐시 적용       | 개선율        |
| -------------- | ---------------- | --------------- | ------------- |
| 평균 응답 시간 | ~200ms           | ~10ms           | **95% 감소**  |
| DB 쿼리        | 복잡한 집계 쿼리 | 없음 (캐시 Hit) | **100% 감소** |
| CPU 사용량     | 높음 (정렬/집계) | 낮음            | -             |

### 6.2 캐시 효과 시뮬레이션

**가정**:

- 상품 조회 요청: 10,000 req/min
- 캐시 Hit Rate: 95%
- DB 쿼리 시간: 50ms
- 캐시 조회 시간: 5ms

**캐시 미적용 시**:

```
총 응답 시간 = 10,000 × 50ms = 500,000ms = 500초
DB 쿼리 수 = 10,000회
```

**캐시 적용 시**:

```
캐시 Hit: 9,500회 × 5ms = 47,500ms
캐시 Miss: 500회 × 50ms = 25,000ms
총 응답 시간 = 72,500ms = 72.5초
DB 쿼리 수 = 500회
```

**개선 효과**:

- 응답 시간: **85.5% 감소** (500초 → 72.5초)
- DB 쿼리: **95% 감소** (10,000회 → 500회)

---

## 7. 캐시 일관성 관리

### 7.1 캐시 무효화 전략

#### Write-Through with Eviction

```kotlin
// 상품 수정 시 캐시 무효화
@CacheEvict(value = ["products"], key = "#product.id")
override fun updateProduct(product: Product): Product {
    return productRepository.save(product)
}
```

#### 관련 캐시 연쇄 무효화

```kotlin
// 상품 재고 변경 시 인기 상품 캐시도 무효화 필요
@CacheEvict(value = ["products", "topProducts"], allEntries = true)
fun updateProductStock(productId: UUID, quantity: Int) {
    // ...
}
```

### 7.2 TTL 기반 자동 갱신

| 캐시        | TTL  | 이유                             |
| ----------- | ---- | -------------------------------- |
| products    | 10분 | 상품 정보 변경 빈도 낮음, 긴 TTL |
| topProducts | 3분  | 판매량 변동 반영 필요, 짧은 TTL  |
| coupons     | 10분 | 쿠폰 정보 변경 빈도 낮음         |

### 7.3 캐시 스탬피드 방지

**문제**: TTL 만료 시 다수 요청이 동시에 DB 조회

**해결**: 분산락과 조합

```kotlin
@Cacheable(value = ["topProducts"], key = "'days:' + #days + ':limit:' + #limit")
@DistributedLock(key = "'cache:topProducts:' + #days + ':' + #limit", waitTimeMs = 1000)
override fun getTopProducts(days: Int, limit: Int): TopProductsResult {
    // 캐시 Miss 시 분산락으로 단일 요청만 DB 조회
}
```

---

## 8. 모니터링 및 운영

### 8.1 캐시 메트릭

| 메트릭         | 설명                | 목표     |
| -------------- | ------------------- | -------- |
| Hit Rate       | 캐시 적중률         | > 90%    |
| Miss Rate      | 캐시 미적중률       | < 10%    |
| Eviction Count | 캐시 제거 횟수      | 모니터링 |
| Memory Usage   | Redis 메모리 사용량 | < 70%    |

### 8.2 장애 대응

| 상황        | 대응                                              |
| ----------- | ------------------------------------------------- |
| Redis 장애  | 캐시 우회, DB 직접 조회 (Circuit Breaker)         |
| 캐시 오염   | 전체 캐시 무효화 (`@CacheEvict(allEntries=true)`) |
| 메모리 부족 | TTL 단축, LRU 정책 적용                           |

---

## 9. 결론

### 9.1 캐싱 적용 요약

| 캐시        | 대상           | TTL  | 전략                          |
| ----------- | -------------- | ---- | ----------------------------- |
| products    | 상품 정보      | 10분 | Cache-Aside + Evict on Update |
| topProducts | 인기 상품      | 3분  | Cache-Aside + TTL 자동 갱신   |
| coupons     | 쿠폰 메타 정보 | 10분 | Cache-Aside + Evict on Update |

### 9.2 기대 효과

1. **응답 시간 단축**: 캐시 Hit 시 DB 조회 없이 즉시 응답 (90%+ 개선)
2. **DB 부하 감소**: 반복 쿼리 제거로 DB 커넥션 절약
3. **확장성 향상**: 트래픽 증가에도 안정적인 서비스 제공
4. **비용 절감**: DB 인스턴스 스케일업 지연

### 9.3 향후 개선 사항

1. **캐시 워밍**: 서버 시작 시 인기 데이터 미리 캐싱
2. **Local Cache + Redis**: 2-tier 캐시로 네트워크 지연 최소화
3. **캐시 모니터링 대시보드**: Hit Rate, Memory 사용량 실시간 모니터링
