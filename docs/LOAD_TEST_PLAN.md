# 부하 테스트 계획서 (Load Test Plan)

## 📌 목적

**순수 DB 부하 상황에서의 성능 한계 측정**

현재 시스템은 Redis 분산락, Kafka 비동기 처리, DB 인덱스 등 다양한 성능 최적화가 적용되어 있습니다.
이 테스트에서는 **의도적으로 최적화를 제거**하여 순수 데이터베이스의 부하를 측정하고,
최적화의 효과를 정량적으로 분석하는 것을 목표로 합니다.

---

## 🎯 테스트 시나리오

### 1. 인기 상품 조회
- **목적**: 인덱스 없는 Full Table Scan 부하 측정
- **동시 사용자**: 100명
- **요청 횟수**: 각 사용자당 10회
- **측정 지표**: 응답시간, TPS, 데이터베이스 CPU 사용률

### 2. 선착순 쿠폰 발급
- **목적**: 비관적 락만으로 동시성 제어 시 성능 측정
- **동시 사용자**: 100명
- **쿠폰 수량**: 50개 (선착순)
- **측정 지표**: 성공/실패율, 평균 응답시간, 데드락 발생 여부

### 3. 주문 생성
- **목적**: 트랜잭션 경합 상황에서의 DB 부하 측정
- **동시 사용자**: 100명
- **상품 재고**: 제한적으로 설정
- **측정 지표**: 트랜잭션 처리 시간, 락 대기 시간, 재고 차감 정합성

### 4. 결제 처리
- **목적**: 사용자 잔액 차감 시 동시성 제어 성능 측정
- **동시 사용자**: 100명
- **측정 지표**: 잔액 정합성, 처리 시간, 데이터베이스 커넥션 풀 상태

---

## 🔧 테스트 환경 구성

### Profile 기반 전환 방식

비즈니스 로직 코드는 **한 줄도 수정하지 않고**, Spring Profile과 조건부 Bean만으로 전환합니다.

#### 실행 방법
```bash
# 부하 테스트 모드 (최적화 제거)
./gradlew bootRun --args='--spring.profiles.active=load-test'

# 운영 모드 (기본, 최적화 적용)
./gradlew bootRun
```

---

## 📋 제거할 최적화 항목

### 1. Redis 분산락 제거

**현재 상태:**
- `CouponServiceImpl.issueCoupon()` - 쿠폰 발급 시 분산락
- `OrderServiceImpl.createOrderTransaction()` - 주문 생성 시 분산락
- `OrderServiceImpl.cancelOrder()` - 주문 취소 시 분산락
- `PaymentServiceImpl.processPaymentTransaction()` - 결제 시 분산락
- `PaymentServiceImpl.cancelPayment()` - 결제 취소 시 분산락

**변경 방법:**
```yaml
# application-load-test.yml
app:
  lock:
    enabled: false  # 분산락 비활성화
```

**구현:**
- `DistributedLockAspect`에 `@ConditionalOnProperty` 추가
- 비활성화 시 AOP가 동작하지 않아 순수 DB 비관적락만 사용

**파일 위치:**
- `src/main/kotlin/com/hhplus/ecommerce/common/lock/DistributedLockAspect.kt`

---

### 2. Kafka 이벤트 발행 제거

**현재 상태:**
- `CouponEventProducer` - 쿠폰 발급 완료 이벤트
- `OrderEventProducer` - 주문 생성 완료 이벤트 (랭킹 업데이트, 카트 삭제)
- `PaymentEventProducer` - 결제 완료 이벤트 (데이터 플랫폼 전송)

**변경 방법:**
```yaml
# application-load-test.yml
spring:
  kafka:
    enabled: false  # Kafka 비활성화
```

**구현:**
- `KafkaConfig`에 `@ConditionalOnProperty` 추가
- Producer Bean들을 조건부로 생성
- 이미 Service 계층에서 `producer?.let { }` 방식으로 nullable 처리되어 있어 Bean만 생성하지 않으면 됨

**파일 위치:**
- `src/main/kotlin/com/hhplus/ecommerce/common/config/KafkaConfig.kt`
- `src/main/kotlin/com/hhplus/ecommerce/infrastructure/kafka/*EventProducer.kt`

---

### 3. ProductRanking을 DB 기반으로 변경

**현재 상태:**
- Redis ZSet으로 실시간 랭킹 관리
- `ProductRankingServiceImpl` - Redis 기반 구현

**변경 방법:**
새로운 DB 기반 구현체를 Profile로 분리:

```kotlin
// 기존 Redis 구현체
@Service
@Profile("!load-test")
class ProductRankingServiceImpl(...)

// 새로운 DB 구현체
@Service
@Profile("load-test")
class ProductRankingServiceDbImpl(...) : ProductRankingService {
    // Product 테이블의 salesCount로 직접 조회
    override fun getRanking(...) {
        productRepository.findTopBySalesCount(limit)
    }
}
```

**구현 상세:**
- `Product` 엔티티의 `salesCount` 필드 활용
- 주문 생성 시 동기적으로 `salesCount` 증가
- 랭킹 조회는 `ORDER BY sales_count DESC LIMIT n`로 직접 쿼리

**파일 위치:**
- `src/main/kotlin/com/hhplus/ecommerce/application/product/ProductRankingServiceDbImpl.kt` (신규)
- `src/main/kotlin/com/hhplus/ecommerce/application/product/ProductRankingServiceImpl.kt` (수정)

---

### 4. Product 인덱스 제거

**현재 상태:**
```kotlin
@Table(
    name = "product",
    indexes = [
        Index(name = "idx_product_category", columnList = "category"),
        Index(name = "idx_product_category_sales", columnList = "category, sales_count DESC"),
        Index(name = "idx_product_category_price", columnList = "category, price"),
        Index(name = "idx_product_stock", columnList = "stock"),
        Index(name = "idx_product_sales_count", columnList = "sales_count DESC")  // 랭킹 조회용
    ]
)
```

**변경 방법:**
Flyway 마이그레이션 스크립트로 인덱스 제거/복구

```sql
-- V999__drop_product_indexes_for_load_test.sql
DROP INDEX IF EXISTS idx_product_category ON product;
DROP INDEX IF EXISTS idx_product_category_sales ON product;
DROP INDEX IF EXISTS idx_product_category_price ON product;
DROP INDEX IF EXISTS idx_product_stock ON product;
DROP INDEX IF EXISTS idx_product_sales_count ON product;
```

```sql
-- V1000__restore_product_indexes.sql
CREATE INDEX idx_product_category ON product (category);
CREATE INDEX idx_product_category_sales ON product (category, sales_count DESC);
CREATE INDEX idx_product_category_price ON product (category, price);
CREATE INDEX idx_product_stock ON product (stock);
CREATE INDEX idx_product_sales_count ON product (sales_count DESC);
```

**파일 위치:**
- `src/main/resources/db/migration/V999__drop_product_indexes_for_load_test.sql` (신규)
- `src/main/resources/db/migration/V1000__restore_product_indexes.sql` (신규)

**실행 방법:**
```bash
# 인덱스 제거
./gradlew flywayMigrate

# 테스트 완료 후 인덱스 복구
./gradlew flywayMigrate
```

---

## 🛠 구현 체크리스트

### 1. 설정 파일 작성
- [ ] `application-load-test.yml` 생성
  - Redis 분산락 비활성화 설정
  - Kafka 비활성화 설정
  - Database 로깅 활성화

### 2. 조건부 Bean 구성
- [ ] `DistributedLockAspect`에 `@ConditionalOnProperty` 추가
- [ ] `KafkaConfig`에 `@ConditionalOnProperty` 추가
- [ ] Kafka Producer Bean들에 조건 추가

### 3. DB 기반 랭킹 서비스 구현
- [ ] `ProductRankingServiceDbImpl` 작성
- [ ] `ProductRankingServiceImpl`에 `@Profile("!load-test")` 추가
- [ ] `ProductRankingServiceDbImpl`에 `@Profile("load-test")` 추가

### 4. 데이터베이스 마이그레이션
- [ ] 인덱스 제거 스크립트 작성 (V999)
- [ ] 인덱스 복구 스크립트 작성 (V1000)

### 5. K6 테스트 스크립트 작성
- [ ] 인기 상품 조회 시나리오
- [ ] 선착순 쿠폰 발급 시나리오
- [ ] 주문 생성 시나리오
- [ ] 결제 처리 시나리오

---

## 📊 K6 테스트 스크립트 구조

### 디렉토리 구조
```
k6/
├── scenarios/
│   ├── product-ranking.js       # 인기 상품 조회
│   ├── coupon-issue.js          # 선착순 쿠폰 발급
│   ├── order-create.js          # 주문 생성
│   └── payment-process.js       # 결제 처리
├── config/
│   └── load-test-config.js      # 공통 설정
└── run-all.js                   # 전체 시나리오 실행
```

### 테스트 설정 예시
```javascript
export const options = {
  scenarios: {
    product_ranking: {
      executor: 'constant-vus',
      vus: 100,              // 100명 동시 사용자
      duration: '30s',
    },
    coupon_issue: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 100 },  // 10초에 걸쳐 100명까지 증가
        { duration: '30s', target: 100 },  // 30초 동안 100명 유지
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<2000'],  // 95%의 요청이 2초 이내
    http_req_failed: ['rate<0.1'],      // 실패율 10% 이하
  },
};
```

---

## 🎯 측정 지표

### 1. 응답 시간
- 평균 응답 시간 (avg)
- 중앙값 (p50)
- 95 백분위수 (p95)
- 99 백분위수 (p99)
- 최대 응답 시간 (max)

### 2. 처리량
- TPS (Transactions Per Second)
- 성공 요청 수
- 실패 요청 수
- 실패율 (%)

### 3. 데이터베이스
- CPU 사용률
- 메모리 사용률
- 커넥션 풀 사용률
- 락 대기 시간
- 데드락 발생 횟수
- 슬로우 쿼리 로그

### 4. 정합성 검증
- 쿠폰 발급 수량 정합성 (50개 초과 발급 여부)
- 재고 차감 정합성 (음수 재고 발생 여부)
- 잔액 정합성 (음수 잔액 발생 여부)

---

## 📈 예상 결과

### 최적화 제거 전 (운영 환경)
- 인기 상품 조회: ~50ms (인덱스, Redis 캐시)
- 쿠폰 발급: ~100ms (Redis 분산락)
- 주문 생성: ~200ms (Redis 분산락, 비동기 처리)
- 결제 처리: ~150ms (Redis 분산락, 비동기 처리)

### 최적화 제거 후 (부하 테스트 환경)
- 인기 상품 조회: ~500ms 이상 (Full Table Scan)
- 쿠폰 발급: ~500ms 이상 (DB 비관적락 대기)
- 주문 생성: ~1000ms 이상 (락 경합, 동기 처리)
- 결제 처리: ~800ms 이상 (락 경합, 동기 처리)

---

## 🔍 분석 포인트

### 1. Redis 분산락 vs DB 비관적락
- 락 획득 속도 차이
- 락 대기 시간 차이
- 처리량(TPS) 차이

### 2. Kafka 비동기 vs 동기 처리
- 응답 시간 차이
- 트랜잭션 커밋 시간
- 전체 처리 시간

### 3. 인덱스 유무
- 쿼리 실행 시간
- Full Table Scan vs Index Scan
- CPU 사용률

### 4. 병목 지점 식별
- 데이터베이스 락 대기
- 커넥션 풀 고갈
- CPU/메모리 한계

---

## ⚠️ 주의사항

1. **데이터 초기화**
   - 각 테스트 시나리오마다 데이터를 초기 상태로 복구
   - 쿠폰, 재고, 잔액 등을 충분히 설정

2. **격리된 환경**
   - 운영 환경과 분리된 테스트 DB 사용
   - 로컬 또는 테스트 전용 서버에서 실행

3. **모니터링**
   - 데이터베이스 메트릭 실시간 모니터링
   - 슬로우 쿼리 로그 수집
   - 애플리케이션 로그 수집

4. **복구 계획**
   - 테스트 완료 후 인덱스 복구 스크립트 실행
   - Profile을 기본(운영)으로 되돌리기
   - 설정 변경 사항 확인

---

## 📝 테스트 실행 절차

### 1. 사전 준비
```bash
# 1. 테스트 브랜치 생성 (선택사항)
git checkout -b test/load-test-db-only

# 2. 데이터베이스 인덱스 제거
./gradlew flywayMigrate

# 3. 테스트 데이터 준비
# - 쿠폰 50개 생성
# - 상품 재고 충분히 설정
# - 사용자 100명 생성 및 잔액 충전
```

### 2. 애플리케이션 실행
```bash
# 부하 테스트 모드로 실행
./gradlew bootRun --args='--spring.profiles.active=load-test'
```

### 3. K6 테스트 실행
```bash
# 개별 시나리오 테스트
k6 run k6/scenarios/product-ranking.js
k6 run k6/scenarios/coupon-issue.js
k6 run k6/scenarios/order-create.js
k6 run k6/scenarios/payment-process.js

# 전체 시나리오 한번에 실행
k6 run k6/run-all.js
```

### 4. 결과 분석
```bash
# K6 리포트 확인
# 데이터베이스 슬로우 쿼리 분석
# 애플리케이션 로그 분석
```

### 5. 사후 정리
```bash
# 1. 인덱스 복구
./gradlew flywayMigrate

# 2. 애플리케이션 재시작 (운영 모드)
./gradlew bootRun

# 3. 정합성 검증
# - 쿠폰 발급 수량 확인
# - 재고 수량 확인
# - 잔액 확인
```

---

## 📄 보고서 작성 항목

1. **테스트 환경**
   - 하드웨어 스펙
   - 데이터베이스 버전 및 설정
   - JVM 설정

2. **테스트 결과**
   - 각 시나리오별 성능 지표
   - 그래프 및 차트
   - 최적화 전/후 비교

3. **병목 분석**
   - 가장 느린 구간 식별
   - 락 경합 분석
   - 리소스 사용률 분석

4. **개선 방안**
   - 확인된 병목의 해결 방법
   - 최적화 효과 정량화
   - 추가 개선 제안

---

## 🎯 기대 효과

1. **성능 최적화의 정량적 측정**
   - Redis 분산락의 효과를 수치로 확인
   - Kafka 비동기 처리의 효과를 수치로 확인
   - 인덱스의 효과를 수치로 확인

2. **병목 지점 명확화**
   - 실제 성능 한계 파악
   - 우선순위 높은 최적화 대상 식별

3. **아키텍처 검증**
   - 동시성 제어 전략 검증
   - 트랜잭션 격리 수준 검증
   - 비관적 락 사용의 적절성 검증

---

## 📚 참고 문서

- [K6 공식 문서](https://k6.io/docs/)
- [MySQL 락 메커니즘](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking.html)
- [Spring Boot Profiles](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.profiles)
- [JPA Pessimistic Locking](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#jpa.locking)

---

**작성일**: 2025-12-22
**작성자**: Claude Code
**버전**: 1.0