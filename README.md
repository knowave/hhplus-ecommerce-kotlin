# 항해플러스 이커머스 프로젝트

## 프로젝트 개요

대규모 트래픽 환경에서 안정적으로 동작하는 이커머스 시스템을 구현한 프로젝트입니다. 특히 **동시성 제어**, **재고 관리**, **주문/결제 처리**, **쿠폰 시스템** 등 실무에서 자주 발생하는 복잡한 비즈니스 로직을 다룹니다.

---

## 목차

1. [핵심 기능](#핵심-기능)
2. [기술 스택](#기술-스택)
3. [아키텍처](#아키텍처)
4. [동시성 제어](#동시성-제어)
5. [프로젝트 구조](#프로젝트-구조)
6. [빌드 및 실행](#빌드-및-실행)
7. [테스트](#테스트)

---

## 핵심 기능

### 1. 상품 관리
- 상품 정보 조회 (가격, 재고)
- 재고 실시간 확인
- 인기 상품 통계 (최근 3일, Top 5)

### 2. 주문/결제 시스템
- 장바구니 기능
- 재고 확인 및 차감 (주문 시점 즉시 차감)
- 잔액 기반 결제
- 쿠폰 할인 적용

### 3. 쿠폰 시스템
- 선착순 발급 (한정 수량)
- 쿠폰 유효성 검증
- 사용 이력 관리
- 1인 1매 제한

### 4. 배송 관리
- 주문 완료 시 배송 정보 자동 생성
- 배송 상태 추적 (PENDING → IN_TRANSIT → DELIVERED)
- 택배사 및 송장번호 관리

### 5. 데이터 연동
- 주문 데이터 외부 전송 (Outbox Pattern)
- 실패 시에도 주문은 정상 처리
- 재시도 메커니즘 (최대 3회)

---

## 기술 스택

| 카테고리 | 기술 스택 | 버전 |
|---------|----------|------|
| Language | Kotlin | 1.9.25 |
| Framework | Spring Boot | 3.5.6 |
| Data Storage | In-Memory (MutableMap) | - |
| Concurrency Control | ReentrantLock | - |
| Build Tool | Gradle | 8.x |
| API Documentation | Swagger (SpringDoc OpenAPI) | - |

---

## 아키텍처

이 프로젝트는 **레이어드 아키텍처(Layered Architecture)** 패턴을 기반으로 구현되었습니다.

### 아키텍처 개요

```
┌─────────────────────────────────────────────────────────┐
│                  Presentation Layer                     │
│           (Controllers, Request/Response DTOs)          │
└──────────────────────┬──────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────┐
│                  Application Layer                      │
│      (Services, Command/Result DTOs, LockManager)       │
└──────────────────────┬──────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────┐
│                    Domain Layer                         │
│        (Pure Kotlin Entities, Repository Interfaces)    │
└──────────────────────┬──────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────┐
│                Infrastructure Layer                     │
│         (In-Memory Repository Implementations)          │
└─────────────────────────────────────────────────────────┘
```

### 계층별 역할 및 책임

#### 1. Presentation Layer (표현 계층)
**위치**: `com.hhplus.ecommerce.presentation`

**역할**:
- HTTP 요청/응답 처리
- 클라이언트와의 인터페이스 제공
- 입력 데이터 검증 (Bean Validation)
- Request DTO → Command DTO 변환
- Result DTO → Response DTO 변환

**주요 구성 요소**:
- `Controller`: REST API 엔드포인트 정의
- `Request DTO`: 클라이언트로부터 받은 요청 데이터
- `Response DTO`: 클라이언트에게 반환할 응답 데이터

**예시**:
```kotlin
@RestController
@RequestMapping("/orders")
class OrderController(
    private val orderService: OrderService
) {
    @PostMapping
    fun createOrder(@RequestBody request: CreateOrderRequest): CreateOrderResponse {
        val command = CreateOrderCommand.command(request)
        val result = orderService.createOrder(command)
        return CreateOrderResponse.from(result)
    }
}
```

**특징**:
- Spring MVC의 `@RestController`, `@RequestMapping` 등 웹 프레임워크 어노테이션 사용
- HTTP 프로토콜과 밀접하게 연관된 계층
- 비즈니스 로직을 포함하지 않음
- Swagger를 통한 API 문서 자동 생성

---

#### 2. Application Layer (응용 계층)
**위치**: `com.hhplus.ecommerce.application`

**역할**:
- 비즈니스 유스케이스 구현
- 동시성 제어 (`LockManager` 활용)
- 여러 도메인 객체 간 협력 조율
- 도메인 로직 호출 및 결과 조합

**주요 구성 요소**:
- `Service Interface`: 비즈니스 유스케이스 정의
- `ServiceImpl`: 유스케이스 구현체
- `Command DTO`: 서비스 계층 입력 데이터
- `Result DTO`: 서비스 계층 출력 데이터

**예시**:
```kotlin
@Service
class OrderServiceImpl(
    private val orderRepository: OrderRepository,
    private val productService: ProductService,
    private val lockManager: LockManager
) : OrderService {

    override fun createOrder(command: CreateOrderCommand): CreateOrderResult {
        // 1. 재고 확인 및 차감 (Lock 사용)
        val products = validateAndGetProducts(command.items)
        deductStock(command.items, products)

        // 2. 주문 생성 (도메인 로직 활용)
        val orderId = orderRepository.generateId()
        val order = Order(...)

        // 3. 저장
        orderRepository.save(order)

        return CreateOrderResult.from(order)
    }

    private fun deductStock(items: List<OrderItemCommand>, products: Map<Long, Product>) {
        val productIds = items.map { it.productId }

        // ReentrantLock을 사용한 동시성 제어
        lockManager.executeWithProductLocks(productIds) {
            items.forEach { item ->
                val product = products[item.productId]!!
                product.deductStock(item.quantity)  // 도메인 메서드 호출
                productService.updateProduct(product)
            }
        }
    }
}
```

**특징**:
- 도메인 모델을 활용하여 비즈니스 로직 구현
- **DB 트랜잭션 대신 ReentrantLock으로 동시성 제어**
- 계층 간 데이터 변환 (Command/Result DTO 사용)
- 여러 Repository와 Service를 조합하여 복잡한 유스케이스 구현

---

#### 3. Domain Layer (도메인 계층)
**위치**: `com.hhplus.ecommerce.domain`

**역할**:
- 핵심 비즈니스 규칙 및 로직 구현
- 순수 Kotlin 도메인 모델 정의
- Repository 인터페이스 정의
- 프레임워크에 독립적인 비즈니스 로직 작성

**주요 구성 요소**:
- `Entity (data class)`: 순수 Kotlin 비즈니스 객체
- `Repository Interface`: 도메인 객체 저장소 인터페이스
- 도메인 로직 (엔티티 내부 메서드)

**예시**:
```kotlin
// 순수 Kotlin data class로 정의된 도메인 엔티티
data class Order(
    val id: Long,
    val userId: Long,
    val orderNumber: String,
    val items: List<OrderItem>,
    val totalAmount: Long,
    val discountAmount: Long,
    val finalAmount: Long,
    var status: OrderStatus,
    val createdAt: LocalDateTime,
    var updatedAt: LocalDateTime
) {
    // 도메인 비즈니스 규칙 검증
    init {
        require(totalAmount >= 0) { "Total amount must be non-negative" }
        require(finalAmount == totalAmount - discountAmount) {
            "Final amount must equal total amount minus discount amount"
        }
        require(items.isNotEmpty()) { "Order must have at least one item" }
    }

    // 비즈니스 로직: 결제 완료 처리
    fun markAsPaid() {
        require(status == OrderStatus.PENDING) {
            "Only PENDING orders can be marked as PAID"
        }
        status = OrderStatus.PAID
        updatedAt = LocalDateTime.now()
    }

    // 비즈니스 로직: 주문 취소
    fun cancel() {
        require(status == OrderStatus.PENDING) {
            "Only PENDING orders can be cancelled"
        }
        status = OrderStatus.CANCELLED
        updatedAt = LocalDateTime.now()
    }
}

// Repository Interface (구현체는 Infrastructure 계층에 위치)
interface OrderRepository {
    fun save(order: Order): Order
    fun findById(id: Long): Order?
    fun findByUserId(userId: Long): List<Order>
    fun generateId(): Long
    fun generateOrderNumber(orderId: Long): String
}
```

**특징**:
- **프레임워크 독립적**: JPA, Hibernate 등 어떤 영속성 프레임워크에도 의존하지 않음
- **순수 Kotlin**: `data class`를 활용한 불변성 및 간결한 코드
- **비즈니스 규칙의 중심**: 도메인 로직이 엔티티 내부에 캡슐화됨
- **테스트 용이성**: 외부 의존성 없이 도메인 로직만 단위 테스트 가능
- **데이터베이스 독립적**: 현재는 In-Memory, 향후 MySQL, PostgreSQL 등 어떤 DB로도 전환 가능

---

#### 4. Infrastructure Layer (인프라 계층)
**위치**: `com.hhplus.ecommerce.infrastructure`

**역할**:
- 도메인 계층의 Repository 인터페이스 구현
- 데이터 영속성 처리 (현재는 In-Memory)
- 기술적 세부사항 처리

**주요 구성 요소**:
- `RepositoryImpl`: Repository 인터페이스 구현체
- `MutableMap`: 인메모리 데이터 저장소

**예시**:
```kotlin
@Repository
class OrderRepositoryImpl : OrderRepository {

    // ID 자동 생성을 위한 카운터
    private var nextOrderId: Long = 1001L

    // 인메모리 데이터 저장소: orderId -> Order
    private val orders: MutableMap<Long, Order> = mutableMapOf()

    override fun findById(orderId: Long): Order? {
        return orders[orderId]
    }

    override fun findByUserId(userId: Long): List<Order> {
        return orders.values
            .filter { it.userId == userId }
            .sortedByDescending { it.createdAt }
    }

    override fun save(order: Order): Order {
        orders[order.id] = order
        return order
    }

    override fun generateId(): Long {
        return nextOrderId++
    }

    override fun generateOrderNumber(orderId: Long): String {
        val dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        return "ORD-$dateStr-$orderId"
    }
}
```

**현재 구현 특징**:
- **In-Memory 저장소**: `MutableMap`을 사용하여 메모리에 데이터 저장
- **Mock 데이터 제공**: 초기 테스트 데이터를 미리 생성하여 제공
- **ID 자동 생성**: 카운터를 사용한 간단한 ID 생성 전략
- **순수 Kotlin 컬렉션 활용**: `filter`, `sortedByDescending` 등을 활용한 데이터 조회

**In-Memory 방식의 장점**:
1. **빠른 개발**: DB 설정 없이 즉시 개발 시작 가능
2. **테스트 용이성**: 별도의 테스트 DB 설정 불필요
3. **로컬 실행 간편**: Docker, MySQL 설치 없이 바로 실행 가능
4. **도메인 로직 집중**: 인프라 설정에 시간을 쓰지 않고 비즈니스 로직에 집중

**향후 확장**:
- JPA/Hibernate를 사용한 영속성 관리로 전환 가능
- Repository 구현체만 교체하면 도메인 계층은 변경 불필요
- 인터페이스 기반 설계로 쉬운 전환 보장

---

### 계층 간 DTO 분리 전략

이 프로젝트는 **각 계층별로 독립적인 DTO**를 사용하여 계층 간 결합도를 낮추고 유지보수성을 향상시켰습니다.

#### DTO 분리 구조

```
Presentation Layer
    ├─ Request DTO  (클라이언트 → 서버)
    └─ Response DTO (서버 → 클라이언트)
           │
           ▼ 변환 (Companion Object 활용)
Application Layer
    ├─ Command DTO  (입력 데이터)
    └─ Result DTO   (출력 데이터)
           │
           ▼ 사용
Domain Layer
    └─ Entity       (순수 도메인 모델)
```

#### DTO 계층별 역할

| 계층 | DTO 타입 | 역할 | 예시 |
|-----|---------|-----|------|
| Presentation | Request DTO | 클라이언트 요청 데이터 수신 | `CreateOrderRequest` |
| Presentation | Response DTO | 클라이언트 응답 데이터 반환 | `CreateOrderResponse` |
| Application | Command DTO | 서비스 계층 입력 데이터 | `CreateOrderCommand` |
| Application | Result DTO | 서비스 계층 출력 데이터 | `CreateOrderResult` |
| Domain | Entity | 비즈니스 도메인 모델 | `Order` (data class) |

#### 계층 간 변환 흐름

```kotlin
// 1. Presentation → Application (Request → Command)
val command = CreateOrderCommand.command(request)

// 2. Application에서 비즈니스 로직 처리
val result = orderService.createOrder(command)

// 3. Application → Presentation (Result → Response)
val response = CreateOrderResponse.from(result)
```

#### 분리의 장점

1. **계층 독립성**: 각 계층의 변경이 다른 계층에 영향을 최소화
2. **유연성**: API 스펙 변경 시 Presentation DTO만 수정
3. **명확한 책임**: 각 DTO가 해당 계층의 관심사만 표현
4. **테스트 용이성**: 각 계층을 독립적으로 테스트 가능
5. **보안**: 도메인 엔티티의 내부 구조를 외부에 노출하지 않음

---

### 레이어드 아키텍처의 핵심 원칙

1. **단방향 의존성**: 상위 계층 → 하위 계층으로만 의존
   ```
   Presentation → Application → Domain ← Infrastructure
   ```

2. **계층별 책임 분리**: 각 계층은 명확한 단일 책임을 가짐

3. **도메인 중심 설계**: 비즈니스 로직은 도메인 계층에 집중

4. **인터페이스를 통한 결합도 완화**: Repository는 인터페이스로 정의하고 Infrastructure에서 구현

5. **DTO를 통한 계층 간 데이터 전달**: 엔티티를 직접 노출하지 않음

6. **프레임워크 독립성**: 도메인 계층은 어떤 프레임워크에도 의존하지 않음

---

## 동시성 제어

이 프로젝트는 **In-Memory 저장소**를 사용하므로 DB 트랜잭션(`@Transactional`)을 사용하지 않습니다. 대신 **`ReentrantLock`을 활용한 애플리케이션 레벨 동시성 제어**를 구현했습니다.

### LockManager 아키텍처

```
┌────────────────────────────────────────────────────────┐
│                    LockManager                         │
│                                                        │
│  ┌──────────────────────────────────────────────┐      │
│  │   ConcurrentHashMap<Long, ReentrantLock>     │      │
│  │                                              │      │
│  │   - userLocks:    userId → Lock              │      │
│  │   - productLocks: productId → Lock           │      │
│  │   - couponLocks:  couponId → Lock            │      │
│  └──────────────────────────────────────────────┘      │
│                                                        │
│  Lock 획득 순서 (데드락 방지):                              │
│  1. Coupon Lock                                        │
│  2. Product Lock(s) - 정렬된 순서                         │
│  3. User Lock                                          │
└────────────────────────────────────────────────────────┘
```

### 주요 특징

#### 1. 도메인별 독립적인 Lock 관리

각 도메인(User, Product, Coupon)별로 독립적인 Lock을 관리하여 세밀한 동시성 제어를 제공합니다.

```kotlin
@Component
class LockManager {
    // 도메인별 Lock 저장소
    private val userLocks = ConcurrentHashMap<Long, ReentrantLock>()
    private val productLocks = ConcurrentHashMap<Long, ReentrantLock>()
    private val couponLocks = ConcurrentHashMap<Long, ReentrantLock>()

    fun <T> executeWithUserLock(userId: Long, action: () -> T): T {
        val lock = userLocks.computeIfAbsent(userId) { ReentrantLock() }
        lock.lock()
        try {
            return action()
        } finally {
            lock.unlock()
        }
    }
}
```

#### 2. 데드락 방지 전략

**단일 리소스 Lock 순서**:
1. Coupon Lock
2. Product Lock
3. User Lock

**다중 상품 Lock 전략**:
- `productId`를 **정렬**하여 항상 동일한 순서로 Lock 획득
- 두 스레드가 서로 다른 순서로 Lock을 획득하는 것을 방지

```kotlin
fun <T> executeWithProductLocks(productIds: List<Long>, action: () -> T): T {
    // 데드락 방지: productId를 정렬하여 항상 동일한 순서로 Lock 획득
    val sortedProductIds = productIds.distinct().sorted()
    val locks = sortedProductIds.map { productId ->
        productLocks.computeIfAbsent(productId) { ReentrantLock() }
    }

    // 순차적으로 Lock 획득
    locks.forEach { it.lock() }
    try {
        return action()
    } finally {
        // 역순으로 Lock 해제 (LIFO)
        locks.reversed().forEach { it.unlock() }
    }
}
```

#### 3. 실제 사용 예시

**재고 차감 시 동시성 제어**:
```kotlin
private fun deductStock(items: List<OrderItemCommand>, products: Map<Long, Product>) {
    val productIds = items.map { it.productId }

    // 여러 상품에 대한 Lock을 안전하게 획득
    lockManager.executeWithProductLocks(productIds) {
        items.forEach { item ->
            val product = products[item.productId]!!
            product.deductStock(item.quantity)  // 도메인 메서드 호출
            productService.updateProduct(product)
        }
    }
}
```

**사용자 잔액 차감 시 동시성 제어**:
```kotlin
fun chargeBalance(userId: Long, amount: Long): User {
    return lockManager.executeWithUserLock(userId) {
        val user = findUserById(userId)
        user.charge(amount)  // 도메인 메서드 호출
        userRepository.save(user)
    }
}
```

**쿠폰 발급 시 동시성 제어 (선착순)**:
```kotlin
fun issueCoupon(couponId: Long, userId: Long): UserCoupon {
    return lockManager.executeWithCouponLock(couponId) {
        val coupon = findCouponById(couponId)

        // 재고 확인 및 차감
        coupon.decreaseStock()
        updateCoupon(coupon)

        // 사용자 쿠폰 발급
        val userCoupon = UserCoupon(...)
        saveUserCoupon(userCoupon)
    }
}
```

### 동시성 제어의 핵심 원칙

1. **세밀한 단위**: 전체 시스템이 아닌 리소스(userId, productId 등) 단위로 Lock
2. **일관된 순서**: 데드락 방지를 위한 Lock 획득 순서 엄수
3. **최소 범위**: Lock을 보호하는 코드 블록을 최소화하여 성능 향상
4. **도메인 로직 활용**: Lock 내부에서 도메인 엔티티의 비즈니스 메서드 호출

---

## 프로젝트 구조

```
src/main/kotlin/com/hhplus/ecommerce
├── presentation              # Presentation Layer
│   ├── cart
│   │   ├── CartController.kt
│   │   └── dto               # Request/Response DTOs
│   ├── coupon
│   ├── order
│   ├── payment
│   ├── product
│   ├── shipping
│   └── user
│
├── application               # Application Layer
│   ├── cart
│   │   ├── CartService.kt
│   │   ├── CartServiceImpl.kt
│   │   └── dto               # Command/Result DTOs
│   ├── coupon
│   ├── order
│   ├── payment
│   ├── product
│   ├── shipping
│   └── user
│
├── domain                    # Domain Layer (순수 Kotlin)
│   ├── cart
│   │   ├── entity
│   │   │   └── CartItem.kt         # data class
│   │   └── CartRepository.kt        # interface
│   ├── coupon
│   │   ├── entity
│   │   │   ├── Coupon.kt           # data class (재고 관리 로직)
│   │   │   └── UserCoupon.kt       # data class (사용/복원 로직)
│   │   └── CouponRepository.kt
│   ├── order
│   │   ├── entity
│   │   │   ├── Order.kt            # data class (상태 관리 로직)
│   │   │   └── OrderItem.kt
│   │   └── OrderRepository.kt
│   ├── product
│   │   ├── entity
│   │   │   ├── Product.kt          # data class (재고 차감/복구 로직)
│   │   │   └── ProductCategory.kt
│   │   └── ProductRepository.kt
│   ├── user
│   │   ├── entity
│   │   │   └── User.kt             # data class (잔액 관리 로직)
│   │   └── UserRepository.kt
│   └── ...
│
├── infrastructure            # Infrastructure Layer (In-Memory)
│   ├── cart
│   │   └── CartRepositoryImpl.kt    # MutableMap 기반
│   ├── coupon
│   │   └── CouponRepositoryImpl.kt
│   ├── order
│   │   └── OrderRepositoryImpl.kt
│   ├── product
│   │   └── ProductRepositoryImpl.kt # Mock 데이터 포함
│   ├── user
│   │   └── UserRepositoryImpl.kt    # Mock 데이터 포함
│   └── ...
│
└── common                    # 공통 모듈
    ├── exception             # 커스텀 예외
    │   ├── OrderNotFoundException.kt
    │   ├── InsufficientStockException.kt
    │   └── ...
    └── lock                  # 동시성 제어
        └── LockManager.kt    # ReentrantLock 기반 Lock 관리
```

---

## 빌드 및 실행

### 프로젝트 빌드

```bash
# 프로젝트 빌드
./gradlew build

# 클린 빌드
./gradlew clean build

# 컴파일만 수행
./gradlew compileKotlin
```

### 애플리케이션 실행

```bash
# Spring Boot 애플리케이션 실행
./gradlew bootRun
```

애플리케이션이 시작되면 다음 URL에서 API 문서를 확인할 수 있습니다:
- Swagger UI: `http://localhost:8080/api/swagger-ui.html`
- API Docs: `http://localhost:8080/api/api-docs`

---

## 테스트

### 테스트 실행

```bash
# 전체 테스트 실행
./gradlew test

# 특정 클래스 테스트
./gradlew test --tests "com.hhplus.ecommerce.application.order.OrderServiceUnitTest"

# 특정 메서드 테스트
./gradlew test --tests "com.hhplus.ecommerce.application.order.OrderServiceUnitTest.createOrder"

# 상세 로그와 함께 테스트 실행
./gradlew test --info
```

### 테스트 구조

- **Unit Test**: 각 계층별 단위 테스트 (Mocking 활용)
- **Integration Test**: 여러 계층을 통합하여 테스트
- **목표 커버리지**: 70% 이상

```
src/test/kotlin/com/hhplus/ecommerce
├── application                          # Application Layer 테스트
│   ├── cart
│   │   ├── CartServiceUnitTest.kt       # 단위 테스트 (Mocking)
│   │   └── CartServiceIntegrationTest.kt # 통합 테스트
│   ├── coupon
│   │   ├── CouponServiceUnitTest.kt
│   │   └── CouponServiceIntegrationTest.kt
│   ├── order
│   │   ├── OrderServiceUnitTest.kt
│   │   └── OrderServiceIntegrationTest.kt
│   └── ...
│
└── infrastructure                        # Infrastructure Layer 테스트
    ├── cart
    │   └── CartRepositoryIntegrationTest.kt
    ├── coupon
    │   └── CouponRepositoryIntegrationTest.kt
    └── ...
```

---

## 주요 구현 특징

### 1. 순수 도메인 모델 (Pure Domain Model)

JPA나 다른 프레임워크에 의존하지 않는 순수 Kotlin `data class`로 도메인 모델을 구현:

```kotlin
data class Product(
    val id: Long,
    val name: String,
    val price: Long,
    var stock: Int,
    val category: ProductCategory
) {
    // 도메인 비즈니스 로직
    fun deductStock(quantity: Int) {
        if (stock < quantity) {
            throw InsufficientStockException(...)
        }
        stock -= quantity
    }

    fun restoreStock(quantity: Int) {
        stock += quantity
    }
}
```

**장점**:
- 프레임워크 독립적
- 테스트 용이
- 비즈니스 로직이 명확하게 드러남
- 향후 다양한 영속성 기술로 전환 가능

### 2. In-Memory Repository

`MutableMap`을 활용한 간단하고 빠른 데이터 저장소:

```kotlin
@Repository
class ProductRepositoryImpl : ProductRepository {
    private val products: MutableMap<Long, Product> = mutableMapOf(...)

    override fun findById(productId: Long): Product? {
        return products[productId]
    }

    override fun save(product: Product): Product {
        products[product.id] = product
        return product
    }
}
```

**장점**:
- DB 설정 불필요
- 빠른 로컬 개발
- 간단한 테스트 환경

### 3. ReentrantLock 기반 동시성 제어

DB 트랜잭션 없이 애플리케이션 레벨에서 동시성 제어:

```kotlin
lockManager.executeWithProductLocks(productIds) {
    // 동시성이 보장되는 영역
    products.forEach { product ->
        product.deductStock(quantity)
    }
}
```

**장점**:
- In-Memory 환경에 최적화
- 세밀한 Lock 단위 제어
- 데드락 방지 전략 직접 구현

### 4. 계층 간 명확한 DTO 분리

각 계층마다 독립적인 DTO를 사용하여 변경에 유연한 구조:

- **Presentation**: `CreateOrderRequest` / `CreateOrderResponse`
- **Application**: `CreateOrderCommand` / `CreateOrderResult`
- **Domain**: `Order` (entity)