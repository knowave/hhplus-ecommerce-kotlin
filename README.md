# 항해플러스 이커머스 프로젝트

## 프로젝트 개요

대규모 트래픽 환경에서 안정적으로 동작하는 이커머스 시스템을 구현합니다. 특히 **동시성 제어**, **재고 관리**, **주문/결제 처리**, **쿠폰 시스템** 등 실무에서 자주 발생하는 복잡한 비즈니스 로직을 다룹니다.

### 핵심 기능

1. **상품 관리**
   - 상품 정보 조회 (가격, 재고)
   - 재고 실시간 확인
   - 인기 상품 통계 (최근 3일, Top 5)

2. **주문/결제 시스템**
   - 장바구니 기능
   - 재고 확인 및 차감 (주문 시점 즉시 차감)
   - 잔액 기반 결제
   - 쿠폰 할인 적용

3. **쿠폰 시스템**
   - 선착순 발급 (한정 수량)
   - 쿠폰 유효성 검증
   - 사용 이력 관리
   - 1인 1매 제한

4. **배송 관리**
   - 주문 완료 시 배송 정보 자동 생성
   - 배송 상태 추적 (PENDING → IN_TRANSIT → DELIVERED)
   - 택배사 및 송장번호 관리

5. **데이터 연동**
   - 주문 데이터 외부 전송 (Outbox Pattern)
   - 실패 시에도 주문은 정상 처리
   - 재시도 메커니즘 (최대 3회)

---

## 기술 스택

| 카테고리 | 기술 스택 | 버전 |
|---------|----------|------|
| Language | Kotlin | 1.9.25 |
| Framework | Spring Boot | 3.5.6 |
| ORM | Spring Data JPA / Hibernate | - |
| Database | MySQL | 8.0+ |
| Authentication | JWT | - |
| Build Tool | Gradle | 8.x |
| Documentation | Spring REST Docs | - |

---

## 주요 설계 결정사항

### 1. 재고 차감 시점: 주문 생성 시 즉시 차감

**결정 내용**: 결제 완료 시점이 아닌 **주문 생성 시점**에 재고를 즉시 차감합니다.

**이유**:
- 동시 다발적인 주문 요청 시 재고 초과 판매를 방지하기 위함
- 결제 대기 중인 재고를 예약하여 다른 사용자가 구매하지 못하도록 보장
- 결제 실패 시 보상 트랜잭션으로 재고를 복원

**트레이드오프**:
- 장점: 재고 초과 판매 방지, 선착순 보장
- 단점: 결제 미완료 시 재고가 일시적으로 묶일 수 있음 (이를 위해 일정 시간 후 자동 취소 로직 필요)

### 2. 동시성 제어: 낙관적 락 (Optimistic Lock)

**결정 내용**: 재고 차감과 쿠폰 발급에 **낙관적 락(@Version)**을 사용합니다.

**이유**:
- 비관적 락(Pessimistic Lock)보다 높은 성능 제공
- 대부분의 트랜잭션이 충돌 없이 완료될 것으로 예상
- 충돌 발생 시 재시도 로직으로 처리 (최대 3회)

**구현 방식**:
```kotlin
@Entity
class Product(
    // ...
    @Version
    var version: Long = 0
)
```

**트레이드오프**:
- 장점: 높은 처리량(throughput), 데드락 가능성 낮음
- 단점: 충돌 시 재시도 로직 필요, 높은 경쟁 상황에서는 실패율 증가 가능

### 3. 데이터 전송: Outbox Pattern

**결정 내용**: 주문 완료 데이터를 외부 시스템으로 전송할 때 **Outbox Pattern**을 사용합니다.

**이유**:
- 결제 완료와 데이터 전송을 원자적(atomic)으로 처리하여 데이터 정합성 보장
- 외부 시스템 장애 시에도 주문 처리는 정상 진행
- 비동기 재시도 메커니즘으로 최종 일관성(eventual consistency) 보장

**구현 방식**:
1. 결제 완료 트랜잭션 내에서 `data_transmissions` 테이블에 레코드 생성 (status: PENDING)
2. 별도 스케줄러가 PENDING 상태 레코드를 조회하여 외부 시스템으로 전송 시도
3. 실패 시 재시도 (1분 → 5분 → 15분 간격, 최대 3회)
4. 3회 실패 시 관리자 알림 발송

**트레이드오프**:
- 장점: 높은 안정성, 외부 시스템 장애에도 주문 처리 가능
- 단점: 실시간 전송이 아닌 최종 일관성 모델, 추가 테이블 필요

### 4. 주문 상태 관리

**상태 전이 흐름**:
```
PENDING (주문 생성) → PAID (결제 완료)
                   ↘ CANCELLED (취소)
```

**정책**:
- PENDING 상태: 재고 차감 완료, 결제 대기 중
- PAID 상태: 결제 완료, 배송 준비
- CANCELLED 상태: 주문 취소, 재고/쿠폰 복원

**보상 트랜잭션**:
- 결제 실패 시: 재고 복원, 쿠폰 복원, 주문 상태 CANCELLED
- 주문 취소 시: 재고 복원, 쿠폰 복원 (PENDING 상태만 가능)

---

## 프로젝트 구조

```
hhplus-ecommerce-kotlin/
├── src/
│   ├── main/
│   │   ├── kotlin/com/hhplus/ecommerce/
│   │   │   ├── domains/              # 도메인별 비즈니스 로직
│   │   │   │   ├── product/          # 상품 도메인
│   │   │   │   ├── order/            # 주문 도메인
│   │   │   │   ├── payment/          # 결제 도메인
│   │   │   │   ├── coupon/           # 쿠폰 도메인
│   │   │   │   ├── cart/             # 장바구니 도메인
│   │   │   │   ├── user/             # 사용자 도메인
│   │   │   │   └── shipping/         # 배송 도메인
│   │   │   ├── common/               # 공통 모듈
│   │   │   │   ├── exception/        # 예외 처리
│   │   │   │   ├── config/           # 설정
│   │   │   │   └── util/             # 유틸리티
│   │   │   └── EcommerceApplication.kt
│   │   └── resources/
│   │       ├── application.yml       # 애플리케이션 설정
│   │       └── application-*.yml     # 환경별 설정
│   └── test/                          # 테스트 코드
├── docs/                              # 설계 문서
│   ├── api/                           # API 명세서
│   ├── requirements-analysis.md       # 요구사항 분석
│   ├── database-diagram.md            # 데이터베이스 다이어그램
│   └── sequence-diagrams.md           # 시퀀스 다이어그램
├── .claude/                           # Claude Code 가이드
│   └── docs/
│       ├── BUSINESS_POLICIES.md       # 비즈니스 정책
│       ├── DEVELOPMENT_GUIDE.md       # 개발 가이드
│       └── PROJECT_GUIDELINES.md      # 프로젝트 가이드라인
├── build.gradle.kts
├── settings.gradle.kts
├── CLAUDE.md                          # Claude Code 프로젝트 가이드
└── README.md                          # 이 파일
```

---

## 데이터베이스 설계

### ERD 개요

프로젝트의 데이터베이스는 다음 7개의 핵심 도메인으로 구성됩니다:

1. **User**: 사용자 정보 및 잔액 관리
2. **Product**: 상품 정보 및 재고 관리 (낙관적 락)
3. **Cart / CartItem**: 장바구니 관리
4. **Order / OrderItem**: 주문 및 주문 상품 관리
5. **Payment**: 결제 정보 관리
6. **Coupon / UserCoupon**: 쿠폰 발급 및 사용 이력 관리
7. **Shipping**: 배송 정보 관리

### 주요 제약 조건

| 테이블 | 제약 조건 | 목적 |
|--------|----------|------|
| Product | `stock >= 0`, `@Version` | 재고 음수 방지, 동시성 제어 |
| Coupon | `issued_quantity <= total_quantity`, `@Version` | 발급 수량 제한, 동시성 제어 |
| UserCoupon | `UNIQUE(user_id, coupon_id)` | 1인 1매 제한 |
| CartItem | `UNIQUE(cart_id, product_id)` | 장바구니 내 중복 상품 방지 |

### 상세 ERD

전체 ERD 및 도메인별 상세 다이어그램은 [데이터베이스 다이어그램 문서](./docs/database-diagram.md)를 참조하세요.

---

## API 명세

### Base URL

```
http://localhost:8080/api
```

### 도메인별 API

| 도메인 | 엔드포인트 예시 | 문서 링크 |
|--------|----------------|----------|
| User | `GET /users/{userId}`, `POST /users/{userId}/balance/charge` | [User API](./docs/api/user-api.md) |
| Product | `GET /products`, `GET /products/top` | [Product API](./docs/api/product-api.md) |
| Cart | `POST /carts/{userId}/items`, `DELETE /carts/{userId}/items/{itemId}` | [Cart API](./docs/api/cart-api.md) |
| Order | `POST /orders`, `POST /orders/{orderId}/cancel` | [Order API](./docs/api/order-api.md) |
| Payment | `POST /orders/{orderId}/payment` | [Payment API](./docs/api/payment-api.md) |
| Coupon | `POST /coupons/{couponId}/issue`, `GET /users/{userId}/coupons` | [Coupon API](./docs/api/coupon-api.md) |
| Shipping | `GET /shippings/{orderId}` | [Shipping API](./docs/api/shipping-api.md) |

### 주요 API 플로우

**상품 구매 플로우**:
```
1. 상품 조회 → 2. 장바구니 추가 → 3. (선택) 쿠폰 발급
   → 4. 주문 생성 (재고 차감) → 5. 결제 처리 → 6. 배송 정보 생성
```

전체 API 명세는 [API 문서](./docs/api/README.md)를 참조하세요.

---

## 비즈니스 정책

### 1. 주문/결제 정책

- **주문 생성 시**: 재고 즉시 차감, 쿠폰 USED로 변경, 주문 상태 PENDING
- **결제 성공 시**: 잔액 차감, 주문 상태 PAID, 배송 정보 생성
- **결제 실패 시**: 재고 복원, 쿠폰 복원, 주문 상태 CANCELLED
- **주문 취소**: PENDING 상태만 가능, 재고/쿠폰 복원

### 2. 재고 관리 정책

- **차감 시점**: 주문 생성 시 즉시 차감
- **동시성 제어**: 낙관적 락 사용 (최대 3회 재시도)
- **복원 조건**: 주문 취소 또는 결제 실패 시

### 3. 쿠폰 시스템 정책

- **발급 규칙**: 선착순, 1인 1매 제한
- **상태 관리**: AVAILABLE (사용 가능) → USED (사용 완료) → EXPIRED (만료)
- **유효성 검증**: 만료일, 쿠폰 기간, 발급 수량 체크
- **복원 규칙**: 결제 실패 시 AVAILABLE로 복원 (단, 이미 만료된 경우 제외)

### 4. 데이터 전송 정책 (Outbox Pattern)

- **전송 시점**: 주문 PAID 상태 변경 시 레코드 생성
- **재시도 정책**: 1분 → 5분 → 15분 간격 (지수 백오프)
- **최종 실패**: 3회 실패 시 관리자 알림, 수동 개입 필요
- **멱등성 보장**: `order_id` 기반 중복 체크

상세한 비즈니스 정책은 [비즈니스 정책 문서](./.claude/docs/BUSINESS_POLICIES.md)를 참조하세요.

---

## 빌드 및 실행

### 사전 요구사항

- **JDK**: 17 이상
- **MySQL**: 8.0 이상
- **Gradle**: 8.x (Wrapper 사용 권장)

### 데이터베이스 설정

MySQL에 데이터베이스를 생성하고 `application.yml`에 접속 정보를 설정합니다:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ecommerce
    username: your_username
    password: your_password
  jpa:
    hibernate:
      ddl-auto: create  # 개발 환경: create, 운영 환경: validate
    show-sql: true
```

### 빌드

```bash
# 프로젝트 빌드
./gradlew build

# 빌드 (테스트 제외)
./gradlew build -x test

# 클린 빌드
./gradlew clean build
```

### 실행

```bash
# 애플리케이션 실행
./gradlew bootRun

# 특정 프로파일로 실행
./gradlew bootRun --args='--spring.profiles.active=dev'
```

애플리케이션이 실행되면 `http://localhost:8080`에서 접속할 수 있습니다.

### 테스트

```bash
# 전체 테스트 실행
./gradlew test

# 특정 테스트 클래스 실행
./gradlew test --tests "com.hhplus.ecommerce.domains.order.OrderServiceTest"

# 특정 테스트 메소드 실행
./gradlew test --tests "com.hhplus.ecommerce.domains.order.OrderServiceTest.createOrder"

# 테스트 결과 상세 출력
./gradlew test --info
```

---

## 주요 테스트 시나리오

### 1. 동시성 테스트

- **재고 동시성**: 동일 상품에 대한 동시 주문 시 재고 초과 판매 방지 검증
- **쿠폰 동시성**: 선착순 쿠폰 발급 시 발급 수량 초과 방지 검증

### 2. 비즈니스 로직 테스트

- **재고 부족**: 재고가 부족할 때 주문 생성 거부
- **잔액 부족**: 잔액이 부족할 때 결제 실패 및 보상 트랜잭션 동작
- **쿠폰 만료**: 만료된 쿠폰 사용 시도 시 에러 발생
- **주문 취소**: PENDING 상태 주문 취소 시 재고/쿠폰 정상 복원

### 3. 보상 트랜잭션 테스트

- **결제 실패 시**: 재고 복원, 쿠폰 복원, 주문 상태 CANCELLED
- **주문 취소 시**: 재고 복원, 쿠폰 복원

---

## 개발 가이드

### Mock Server 구현

현재는 개발 단계가 아니므로 Mock Server로 API를 구현합니다:

```kotlin
@RestController
@RequestMapping("/products")
class ProductController {

    @GetMapping("/{productId}")
    fun getProduct(@PathVariable productId: Long): ProductResponse {
        // Mock 데이터 반환
        return ProductResponse(
            id = productId,
            name = "노트북",
            price = 1500000,
            stock = 10
        )
    }
}
```

### 코드 스타일

- **변수명**: camelCase
- **클래스명**: PascalCase
- **타입 지정**: `Any` 타입 사용 금지, 명확한 타입 지정
- **에러 메시지**: 영어로 작성
- **도메인 ID**: `domainId`가 아닌 `id`로 정의

### API 기본 경로

`application.yml`에서 기본 경로를 `/api`로 설정되어 있으므로 컨트롤러에서 `/api`를 중복해서 붙이지 않습니다:

```yaml
spring:
  mvc:
    servlet:
      path: /api
```

---

## 시퀀스 다이어그램

주요 비즈니스 플로우의 시퀀스 다이어그램:

- 주문 생성 플로우
- 결제 처리 플로우
- 쿠폰 발급 플로우
- 주문 취소 플로우

상세 다이어그램은 [시퀀스 다이어그램 문서](./docs/sequence-diagrams.md)를 참조하세요.

---

## 문서

| 문서 | 설명 | 경로 |
|------|------|------|
| 요구사항 분석 | 프로젝트 요구사항 및 핵심 기능 정의 | [requirements-analysis.md](./docs/requirements-analysis.md) |
| 데이터베이스 다이어그램 | ERD 및 테이블 설계 | [database-diagram.md](./docs/database-diagram.md) |
| 시퀀스 다이어그램 | 주요 비즈니스 플로우 | [sequence-diagrams.md](./docs/sequence-diagrams.md) |
| API 명세 | REST API 엔드포인트 상세 | [docs/api/README.md](./docs/api/README.md) |
| 비즈니스 정책 | 핵심 비즈니스 규칙 및 정책 | [.claude/docs/BUSINESS_POLICIES.md](./.claude/docs/BUSINESS_POLICIES.md) |
| 개발 가이드 | 개발 진행 순서 및 컨벤션 | [.claude/docs/DEVELOPMENT_GUIDE.md](./.claude/docs/DEVELOPMENT_GUIDE.md) |

---

## 주요 과제 요구사항 체크리스트

- [x] API 명세 작성
- [x] 데이터베이스 다이어그램 작성
- [x] 시퀀스 다이어그램 작성
- [x] 비즈니스 정책 문서화
- [x] Mock Server 구현
- [ ] 동시성 제어 구현 (낙관적 락)
- [ ] Outbox Pattern 구현
- [ ] 통합 테스트 작성
- [ ] 동시성 테스트 작성

---

## 기술적 고민 및 의사결정

### 1. 재고 차감 시점 선택

**고민**: 재고를 언제 차감할 것인가?
- **옵션 A**: 주문 생성 시 차감
- **옵션 B**: 결제 완료 시 차감

**결정**: 옵션 A 선택

**이유**:
- 동시 주문 시 재고 초과 판매를 원천 차단
- 선착순 보장 (결제 대기 시간이 다른 고객에게 영향 없음)
- 결제 실패는 보상 트랜잭션으로 처리 가능

### 2. 동시성 제어 방식 선택

**고민**: 비관적 락 vs 낙관적 락?

**결정**: 낙관적 락 선택

**이유**:
- 대부분의 트랜잭션이 충돌 없이 완료될 것으로 예상
- 높은 처리량(throughput) 필요
- 읽기 비중이 높은 쇼핑몰 특성상 비관적 락은 성능 저하 우려
- 충돌 시 재시도 로직으로 충분히 대응 가능

### 3. 데이터 전송 패턴 선택

**고민**: 동기 전송 vs Outbox Pattern?

**결정**: Outbox Pattern 선택

**이유**:
- 외부 시스템 장애가 주문 처리에 영향을 주면 안 됨 (고가용성)
- 최종 일관성(eventual consistency)으로 충분
- 재시도 메커니즘으로 전송 보장
- 트랜잭션 원자성 보장 (주문 완료와 전송 레코드 생성을 하나의 트랜잭션으로 처리)

---

## 성능 요구사항

| 기능 | 목표 응답 시간 | 목표 TPS |
|------|--------------|---------|
| 주문 생성 | 1초 이내 | - |
| 결제 처리 | 2초 이내 | - |
| 쿠폰 발급 | 500ms 이내 | 50 TPS 이상 |
| 재고 동시 처리 | - | 100 TPS 이상 |

---

## 향후 개선 사항

1. **Redis 캐싱**: 상품 정보, 재고 조회 성능 개선
2. **분산 락**: Redis 기반 분산 락으로 동시성 제어 강화
3. **이벤트 기반 아키텍처**: Kafka를 활용한 이벤트 드리븐 구조로 확장
4. **API Rate Limiting**: 쿠폰 발급 등 트래픽 집중 API 보호
5. **모니터링**: Prometheus + Grafana를 활용한 메트릭 수집 및 시각화
6. **로깅**: ELK Stack을 활용한 중앙 집중식 로그 관리

---