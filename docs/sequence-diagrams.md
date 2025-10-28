# 시퀀스 다이어그램

이 문서는 이커머스 시스템의 주요 기능별 시퀀스 다이어그램을 정의합니다.

## 목차
1. [상품 관리](#1-상품-관리)
2. [주문/결제 시스템](#2-주문결제-시스템)
3. [쿠폰 시스템](#3-쿠폰-시스템)
4. [데이터 연동](#4-데이터-연동)

---

## 1. 상품 관리

### 1.1 상품 목록 조회

```mermaid
sequenceDiagram
    actor User as 사용자
    participant Controller as ProductController
    participant Service as ProductService
    participant Repository as ProductRepository
    participant DB as Database

    User->>Controller: GET /api/products?category=전자제품
    Controller->>Service: getAllProducts(category)
    Service->>Repository: findByCategory(category)
    Repository->>DB: SELECT * FROM products WHERE category = ?
    DB-->>Repository: Product List
    Repository-->>Service: List<Product>
    Service-->>Controller: List<ProductResponseDto>
    Controller-->>User: 200 OK + 상품 목록

    Note over Service,Repository: 카테고리 파라미터가 없으면<br/>전체 상품 조회 (findAll)
```

**핵심 로직**:
- 카테고리 필터링 (선택사항)
- 재고 0인 상품도 표시 (품절 표시)

---

### 1.2 상품 재고 확인

```mermaid
sequenceDiagram
    actor User as 사용자
    participant Controller as ProductController
    participant Service as ProductService
    participant Repository as ProductRepository
    participant DB as Database

    User->>Controller: GET /api/products/{productId}/stock
    Controller->>Service: getProductStock(productId)
    Service->>Repository: findById(productId)
    Repository->>DB: SELECT * FROM products WHERE id = ?

    alt 상품 존재
        DB-->>Repository: Product
        Repository-->>Service: Optional<Product>
        Service->>Service: stock >= 1 ? available : soldout
        Service-->>Controller: ProductStockResponseDto
        Controller-->>User: 200 OK + 재고 정보
    else 상품 없음
        DB-->>Repository: Empty
        Repository-->>Service: Optional.empty()
        Service-->>Controller: ProductNotFoundException
        Controller-->>User: 404 Not Found
    end
```

**핵심 로직**:
- 실시간 재고 조회
- 재고 상태 판단 (available/soldout)

---

### 1.3 인기 상품 조회 (최근 3일, Top 5)

```mermaid
sequenceDiagram
    actor User as 사용자
    participant Controller as ProductController
    participant Service as ProductService
    participant Repository as ProductRepository
    participant DB as Database

    User->>Controller: GET /api/products/top
    Controller->>Service: getTopProducts()
    Service->>Service: fromDate = now() - 3 days
    Service->>Repository: findTopProductsByRecentSales(fromDate)

    Repository->>DB: SELECT p.id, p.name,<br/>SUM(oi.quantity) as sales_count,<br/>SUM(oi.subtotal) as revenue<br/>FROM order_items oi<br/>JOIN products p ON oi.product_id = p.id<br/>JOIN orders o ON oi.order_id = o.id<br/>WHERE o.created_at >= ?<br/>AND o.status = 'PAID'<br/>GROUP BY p.id, p.name<br/>ORDER BY sales_count DESC<br/>LIMIT 5

    DB-->>Repository: List<TopProductInfo>
    Repository-->>Service: List<TopProductInfo>
    Service->>Service: rank 추가 (1~5)
    Service-->>Controller: TopProductsResponseDto
    Controller-->>User: 200 OK + 인기 상품 Top 5
```

**핵심 로직**:
- 최근 3일 데이터만 집계
- PAID 상태 주문만 포함
- 판매량 기준 정렬
- Top 5 제한

---

## 2. 주문/결제 시스템

### 2.1 주문 생성 (정상 흐름)

```mermaid
sequenceDiagram
    actor User as 사용자
    participant Controller as OrderController
    participant Service as OrderService
    participant UserRepo as UserRepository
    participant ProductRepo as ProductRepository
    participant CouponRepo as UserCouponRepository
    participant OrderRepo as OrderRepository
    participant DB as Database

    User->>Controller: POST /api/orders<br/>{userId, items, couponId}
    Controller->>Service: createOrder(request)

    rect rgb(200, 220, 240)
    Note over Service: 1. 사용자 조회
    Service->>UserRepo: findById(userId)
    UserRepo->>DB: SELECT * FROM users WHERE id = ?
    DB-->>UserRepo: User
    UserRepo-->>Service: User
    end

    rect rgb(220, 240, 200)
    Note over Service: 2. 상품 검증 및 재고 차감
    loop 각 주문 아이템
        Service->>ProductRepo: findById(productId)
        ProductRepo->>DB: SELECT * FROM products WHERE id = ?
        DB-->>ProductRepo: Product
        ProductRepo-->>Service: Product

        Service->>Service: 재고 검증 (stock >= quantity)
        Service->>Service: product.decreaseStock(quantity)
        Service->>ProductRepo: save(product)
        ProductRepo->>DB: UPDATE products<br/>SET stock = stock - ?<br/>WHERE id = ?
        DB-->>ProductRepo: Success
        Service->>Service: 금액 계산 (totalAmount += subtotal)
    end
    end

    rect rgb(240, 220, 200)
    Note over Service: 3. 쿠폰 적용 (선택)
    opt couponId 존재
        Service->>CouponRepo: findByUserIdAndCouponIdAndStatus(userId, couponId, AVAILABLE)
        CouponRepo->>DB: SELECT * FROM user_coupons<br/>WHERE user_id = ? AND coupon_id = ?<br/>AND status = 'AVAILABLE'
        DB-->>CouponRepo: UserCoupon
        CouponRepo-->>Service: UserCoupon

        Service->>Service: userCoupon.use()
        Service->>Service: 할인 계산 (discountAmount)
        Service->>CouponRepo: save(userCoupon)
        CouponRepo->>DB: UPDATE user_coupons<br/>SET status = 'USED', used_at = NOW()<br/>WHERE id = ?
    end
    end

    rect rgb(240, 200, 220)
    Note over Service: 4. 최종 금액 계산 및 잔액 검증
    Service->>Service: finalAmount = totalAmount - discountAmount
    Service->>Service: 잔액 검증 (balance >= finalAmount)
    end

    rect rgb(200, 240, 220)
    Note over Service: 5. 주문 생성 및 저장
    Service->>Service: Order 생성 (status = PENDING)
    Service->>Service: OrderItem 생성 및 추가
    Service->>OrderRepo: save(order)
    OrderRepo->>DB: INSERT INTO orders ...
    DB-->>OrderRepo: Order
    OrderRepo->>DB: INSERT INTO order_items ...
    OrderRepo-->>Service: Saved Order
    end

    Service-->>Controller: CreateOrderResponseDto
    Controller-->>User: 201 Created + 주문 정보

    Note over User,DB: 주문 상태: PENDING<br/>재고: 차감 완료<br/>쿠폰: USED<br/>잔액: 아직 차감 안됨
```

**핵심 단계**:
1. 사용자 조회
2. 상품 검증 및 재고 차감 (즉시)
3. 쿠폰 적용 및 사용 처리
4. 최종 금액 계산 및 잔액 검증
5. 주문 생성 (PENDING 상태)

---

### 2.2 주문 생성 (재고 부족 예외)

```mermaid
sequenceDiagram
    actor User as 사용자
    participant Controller as OrderController
    participant Service as OrderService
    participant ProductRepo as ProductRepository
    participant DB as Database

    User->>Controller: POST /api/orders<br/>{userId, items: [{productId, quantity: 10}]}
    Controller->>Service: createOrder(request)

    Service->>ProductRepo: findById(productId)
    ProductRepo->>DB: SELECT * FROM products WHERE id = ?
    DB-->>ProductRepo: Product (stock = 5)
    ProductRepo-->>Service: Product

    Service->>Service: 재고 검증<br/>stock(5) < quantity(10)

    rect rgb(255, 200, 200)
    Note over Service: 재고 부족!
    Service->>Service: throw InsufficientStockException
    Service-->>Controller: InsufficientStockException
    Controller-->>User: 400 Bad Request<br/>{code: "P002",<br/>message: "Insufficient stock.<br/>Requested: 10, Available: 5"}
    end
```

**에러 처리**:
- 재고 부족 시 주문 생성 거부
- 명확한 에러 메시지 (요청 수량 vs 가능 수량)

---

### 2.3 주문 생성 (잔액 부족 예외)

```mermaid
sequenceDiagram
    actor User as 사용자
    participant Controller as OrderController
    participant Service as OrderService
    participant UserRepo as UserRepository
    participant ProductRepo as ProductRepository
    participant DB as Database

    User->>Controller: POST /api/orders
    Controller->>Service: createOrder(request)

    Service->>UserRepo: findById(userId)
    UserRepo->>DB: SELECT * FROM users WHERE id = ?
    DB-->>UserRepo: User (balance = 10,000)
    UserRepo-->>Service: User

    Note over Service: 상품 검증 및 재고 차감 진행...
    Service->>ProductRepo: findById(productId)
    ProductRepo-->>Service: Product

    Service->>Service: 금액 계산<br/>finalAmount = 1,000,000

    rect rgb(255, 200, 200)
    Note over Service: 잔액 부족!
    Service->>Service: balance(10,000) < finalAmount(1,000,000)
    Service->>Service: throw InsufficientBalanceException

    Note over Service: 트랜잭션 롤백<br/>- 재고 복원<br/>- 쿠폰 복원 (사용했다면)

    Service-->>Controller: InsufficientBalanceException
    Controller-->>User: 400 Bad Request<br/>{code: "PAY001",<br/>message: "Insufficient balance.<br/>Required: 1000000, Available: 10000"}
    end
```

**에러 처리**:
- 잔액 부족 시 트랜잭션 롤백
- 재고 및 쿠폰 자동 복원

---

### 2.4 결제 처리 (진행 중)

```mermaid
sequenceDiagram
    actor User as 사용자
    participant Controller as OrderController
    participant Service as PaymentService
    participant OrderRepo as OrderRepository
    participant UserRepo as UserRepository
    participant TransRepo as DataTransmissionRepository
    participant DB as Database

    User->>Controller: POST /api/orders/{orderId}/payment<br/>{userId}
    Controller->>Service: processPayment(orderId, userId)

    rect rgb(200, 220, 240)
    Note over Service: 1. 주문 및 사용자 조회
    Service->>OrderRepo: findById(orderId)
    OrderRepo->>DB: SELECT * FROM orders WHERE id = ?
    DB-->>OrderRepo: Order (status = PENDING)
    OrderRepo-->>Service: Order

    Service->>UserRepo: findById(userId)
    UserRepo->>DB: SELECT * FROM users WHERE id = ?
    DB-->>UserRepo: User
    UserRepo-->>Service: User
    end

    rect rgb(220, 240, 200)
    Note over Service: 2. 잔액 차감
    Service->>Service: user.deductBalance(order.finalAmount)
    Service->>UserRepo: save(user)
    UserRepo->>DB: UPDATE users<br/>SET balance = balance - ?<br/>WHERE id = ?
    DB-->>UserRepo: Success
    end

    rect rgb(240, 220, 200)
    Note over Service: 3. 주문 상태 변경
    Service->>Service: order.markAsPaid()
    Service->>OrderRepo: save(order)
    OrderRepo->>DB: UPDATE orders<br/>SET status = 'PAID', paid_at = NOW()<br/>WHERE id = ?
    DB-->>OrderRepo: Success
    end

    rect rgb(240, 200, 220)
    Note over Service: 4. 데이터 전송 레코드 생성 (Outbox)
    Service->>Service: DataTransmission 생성<br/>(status = PENDING)
    Service->>TransRepo: save(dataTransmission)
    TransRepo->>DB: INSERT INTO data_transmissions<br/>(order_id, payload, status, attempts)<br/>VALUES (?, ?, 'PENDING', 0)
    DB-->>TransRepo: Success
    end

    Service-->>Controller: PaymentResponseDto
    Controller-->>User: 200 OK<br/>{orderId, paidAmount,<br/>remainingBalance, status: "SUCCESS"}

    Note over User,DB: 주문 상태: PAID<br/>잔액: 차감 완료<br/>데이터 전송: 대기 중 (PENDING)
```

**핵심 단계**:
1. 주문 및 사용자 조회
2. 잔액 차감
3. 주문 상태 변경 (PENDING → PAID)
4. 데이터 전송 레코드 생성 (Outbox Pattern)

---

### 2.5 주문 조회

```mermaid
sequenceDiagram
    actor User as 사용자
    participant Controller as OrderController
    participant Service as OrderService
    participant Repository as OrderRepository
    participant DB as Database

    User->>Controller: GET /api/orders/{orderId}
    Controller->>Service: getOrder(orderId)
    Service->>Repository: findById(orderId)
    Repository->>DB: SELECT o.*, oi.*, p.*<br/>FROM orders o<br/>LEFT JOIN order_items oi ON o.id = oi.order_id<br/>LEFT JOIN products p ON oi.product_id = p.id<br/>WHERE o.id = ?

    alt 주문 존재
        DB-->>Repository: Order + OrderItems
        Repository-->>Service: Optional<Order>
        Service-->>Controller: OrderResponseDto
        Controller-->>User: 200 OK + 주문 상세 정보
    else 주문 없음
        DB-->>Repository: Empty
        Repository-->>Service: Optional.empty()
        Service-->>Controller: OrderNotFoundException
        Controller-->>User: 404 Not Found
    end
```

---

### 2.6 사용자별 주문 목록 조회

```mermaid
sequenceDiagram
    actor User as 사용자
    participant Controller as OrderController
    participant Service as OrderService
    participant UserRepo as UserRepository
    participant OrderRepo as OrderRepository
    participant DB as Database

    User->>Controller: GET /api/orders/user/{userId}
    Controller->>Service: getUserOrders(userId)

    Service->>UserRepo: existsById(userId)
    UserRepo->>DB: SELECT EXISTS(SELECT 1 FROM users WHERE id = ?)
    DB-->>UserRepo: true/false

    alt 사용자 존재
        UserRepo-->>Service: true
        Service->>OrderRepo: findByUserId(userId)
        OrderRepo->>DB: SELECT * FROM orders<br/>WHERE user_id = ?<br/>ORDER BY created_at DESC
        DB-->>OrderRepo: List<Order>
        OrderRepo-->>Service: List<Order>
        Service-->>Controller: List<OrderResponseDto>
        Controller-->>User: 200 OK + 주문 목록
    else 사용자 없음
        UserRepo-->>Service: false
        Service-->>Controller: UserNotFoundException
        Controller-->>User: 404 Not Found
    end
```

---

## 3. 쿠폰 시스템

### 3.1 쿠폰 발급 (선착순)

```mermaid
sequenceDiagram
    actor User as 사용자
    participant Controller as CouponController
    participant Service as CouponService
    participant UserRepo as UserRepository
    participant CouponRepo as CouponRepository
    participant UserCouponRepo as UserCouponRepository
    participant DB as Database

    User->>Controller: POST /api/coupons/{couponId}/issue<br/>{userId}
    Controller->>Service: issueCoupon(userId, couponId)

    rect rgb(200, 220, 240)
    Note over Service: 1. 사용자 및 쿠폰 조회
    Service->>UserRepo: findById(userId)
    UserRepo->>DB: SELECT * FROM users WHERE id = ?
    DB-->>UserRepo: User
    UserRepo-->>Service: User

    Service->>CouponRepo: findByIdWithOptimisticLock(couponId)
    CouponRepo->>DB: SELECT * FROM coupons<br/>WHERE id = ? FOR UPDATE
    DB-->>CouponRepo: Coupon
    CouponRepo-->>Service: Coupon
    end

    rect rgb(220, 240, 200)
    Note over Service: 2. 유효성 검증
    Service->>Service: coupon.isValid()<br/>(현재 시간이 start_date ~ end_date 사이)

    alt 유효하지 않음
        Service-->>Controller: InvalidCouponException
        Controller-->>User: 400 Bad Request<br/>쿠폰이 유효하지 않습니다
    end
    end

    rect rgb(240, 220, 200)
    Note over Service: 3. 중복 발급 체크
    Service->>UserCouponRepo: existsByUserIdAndCouponId(userId, couponId)
    UserCouponRepo->>DB: SELECT EXISTS(<br/>SELECT 1 FROM user_coupons<br/>WHERE user_id = ? AND coupon_id = ?)
    DB-->>UserCouponRepo: boolean

    alt 이미 발급받음
        UserCouponRepo-->>Service: true
        Service-->>Controller: CouponAlreadyIssuedException
        Controller-->>User: 400 Bad Request<br/>이미 발급받은 쿠폰입니다
    end
    end

    rect rgb(240, 200, 220)
    Note over Service: 4. 쿠폰 발급 (낙관적 락)
    Service->>Service: coupon.issue()<br/>(issued_quantity++, version++)
    Service->>CouponRepo: save(coupon)
    CouponRepo->>DB: UPDATE coupons<br/>SET issued_quantity = issued_quantity + 1,<br/>version = version + 1<br/>WHERE id = ? AND version = ?<br/>AND issued_quantity < total_quantity

    alt 동시성 충돌 or 수량 초과
        DB-->>CouponRepo: 0 rows affected
        CouponRepo-->>Service: OptimisticLockException or CouponSoldOutException
        Service-->>Controller: Exception
        Controller-->>User: 400 Bad Request or 409 Conflict<br/>쿠폰이 매진되었습니다
    else 발급 성공
        DB-->>CouponRepo: Success
    end
    end

    rect rgb(200, 240, 220)
    Note over Service: 5. UserCoupon 레코드 생성
    Service->>Service: UserCoupon 생성<br/>(status = AVAILABLE, expiresAt = now + 30일)
    Service->>UserCouponRepo: save(userCoupon)
    UserCouponRepo->>DB: INSERT INTO user_coupons<br/>(user_id, coupon_id, status, issued_at, expires_at)<br/>VALUES (?, ?, 'AVAILABLE', NOW(), ?)
    DB-->>UserCouponRepo: Success
    end

    Service-->>Controller: CouponIssueResponseDto
    Controller-->>User: 201 Created<br/>{userCouponId, couponName,<br/>discountRate, expiresAt,<br/>remainingQuantity}

    Note over User,DB: 쿠폰 발급 완료<br/>issued_quantity 증가<br/>남은 수량 감소
```

**핵심 로직**:
- 낙관적 락으로 동시성 제어 (`version` 필드)
- `issued_quantity < total_quantity` 조건 체크
- 중복 발급 방지 (`user_id`, `coupon_id` 조합)
- 발급 후 30일 자동 만료

---

### 3.2 보유 쿠폰 조회

```mermaid
sequenceDiagram
    actor User as 사용자
    participant Controller as CouponController
    participant Service as CouponService
    participant Repository as UserCouponRepository
    participant DB as Database

    User->>Controller: GET /api/users/{userId}/coupons
    Controller->>Service: getUserCoupons(userId)
    Service->>Repository: findAllCouponsForUser(userId)
    Repository->>DB: SELECT uc.id, c.name, c.discount_rate,<br/>uc.status, uc.expires_at<br/>FROM user_coupons uc<br/>JOIN coupons c ON uc.coupon_id = c.id<br/>WHERE uc.user_id = ?<br/>ORDER BY uc.expires_at ASC

    DB-->>Repository: List<UserCouponResponseDto>
    Repository-->>Service: List<UserCouponResponseDto>
    Service-->>Controller: List<UserCouponResponseDto>
    Controller-->>User: 200 OK + 쿠폰 목록

    Note over User,DB: 만료일 가까운 순으로 정렬<br/>상태별 표시 (AVAILABLE/USED/EXPIRED)
```

---

### 3.3 쿠폰 목록 조회

```mermaid
sequenceDiagram
    actor User as 사용자
    participant Controller as CouponController
    participant Service as CouponService
    participant Repository as CouponRepository
    participant DB as Database

    User->>Controller: GET /api/coupons
    Controller->>Service: getAllCoupons()
    Service->>Service: currentTime = now()
    Service->>Repository: findByStartDateBeforeAndEndDateAfter(currentTime, currentTime)
    Repository->>DB: SELECT * FROM coupons<br/>WHERE start_date <= ?<br/>AND end_date >= ?

    DB-->>Repository: List<Coupon>
    Repository-->>Service: List<Coupon>
    Service-->>Controller: List<CouponResponseDto>
    Controller-->>User: 200 OK + 현재 발급 가능한 쿠폰 목록

    Note over User,DB: 현재 유효기간 내의 쿠폰만 조회
```

---

## 4. 데이터 연동

### 4.1 데이터 전송 (Outbox Pattern)

```mermaid
sequenceDiagram
    participant Scheduler as 배치 스케줄러
    participant Service as DataTransmissionService
    participant Repository as DataTransmissionRepository
    participant ExternalAPI as 외부 시스템
    participant DB as Database

    Note over Scheduler: 주기적 실행 (예: 1분마다)

    Scheduler->>Service: processPendingTransmissions()

    rect rgb(200, 220, 240)
    Note over Service: 1. 전송 대상 조회
    Service->>Repository: findByStatusIn([PENDING, FAILED])<br/>AND attempts < 3
    Repository->>DB: SELECT * FROM data_transmissions<br/>WHERE status IN ('PENDING', 'FAILED')<br/>AND attempts < 3<br/>ORDER BY created_at ASC
    DB-->>Repository: List<DataTransmission>
    Repository-->>Service: List<DataTransmission>
    end

    loop 각 전송 레코드
        rect rgb(220, 240, 200)
        Note over Service: 2. 외부 시스템 전송 시도
        Service->>Service: attempts++
        Service->>ExternalAPI: POST /api/orders<br/>{payload}

        alt 전송 성공
            ExternalAPI-->>Service: 200 OK
            Service->>Service: transmission.markAsSuccess()
            Service->>Repository: save(transmission)
            Repository->>DB: UPDATE data_transmissions<br/>SET status = 'SUCCESS',<br/>sent_at = NOW()<br/>WHERE id = ?
            DB-->>Repository: Success

        else 전송 실패
            ExternalAPI-->>Service: 500 Error or Timeout
            Service->>Service: transmission.markAsFailed()
            Service->>Repository: save(transmission)
            Repository->>DB: UPDATE data_transmissions<br/>SET status = 'FAILED',<br/>attempts = attempts + 1<br/>WHERE id = ?
            DB-->>Repository: Success

            alt attempts >= 3
                Note over Service: 최대 재시도 횟수 초과
                Service->>Service: sendAlert(관리자)
                Note over Service: 알림 발송<br/>(이메일, 슬랙 등)
            else attempts < 3
                Note over Service: 다음 스케줄에서 재시도<br/>재시도 간격:<br/>1차: 1분 후<br/>2차: 5분 후<br/>3차: 15분 후
            end
        end
        end
    end

    Service-->>Scheduler: 처리 완료
```

**핵심 로직**:
- Outbox Pattern으로 트랜잭션과 분리
- 최대 3회 재시도
- 지수 백오프 (1분, 5분, 15분)
- 실패 시 알림 발송

---

### 4.2 데이터 전송 재시도 로직

```mermaid
flowchart TD
    A[배치 스케줄러 실행] --> B[전송 대상 조회<br/>status IN PENDING, FAILED<br/>AND attempts < 3]
    B --> C{레코드 존재?}
    C -->|NO| D[종료]
    C -->|YES| E[외부 시스템 전송]

    E --> F{전송 성공?}
    F -->|YES| G[status = SUCCESS<br/>sent_at = NOW]
    F -->|NO| H[status = FAILED<br/>attempts++]

    H --> I{attempts >= 3?}
    I -->|YES| J[관리자 알림 발송]
    I -->|NO| K[다음 스케줄에서 재시도]

    G --> L[다음 레코드]
    J --> L
    K --> L
    L --> C

    style G fill:#51cf66
    style J fill:#ff6b6b
    style K fill:#ffd43b
```

**재시도 간격**:
| 시도 | 실패 후 대기 시간 | 설명 |
|------|------------------|------|
| 1차 | 1분 | 일시적 장애 대응 |
| 2차 | 5분 | 시스템 복구 대기 |
| 3차 | 15분 | 최종 재시도 |
| 실패 | - | 관리자 개입 필요 |

---

## 5. 주요 예외 처리 시나리오

### 5.1 동시성 충돌 (쿠폰 발급)

```mermaid
sequenceDiagram
    participant User1 as 사용자1
    participant User2 as 사용자2
    participant Service as CouponService
    participant DB as Database

    Note over DB: 쿠폰 남은 수량: 1개<br/>version: 5

    par 동시 발급 시도
        User1->>Service: issueCoupon(user1, coupon)
        User2->>Service: issueCoupon(user2, coupon)
    end

    Service->>DB: SELECT * FROM coupons<br/>WHERE id = ? AND version = 5
    DB-->>Service: Coupon (version = 5)

    Service->>DB: SELECT * FROM coupons<br/>WHERE id = ? AND version = 5
    DB-->>Service: Coupon (version = 5)

    Service->>DB: UPDATE coupons<br/>SET issued_quantity = 1, version = 6<br/>WHERE id = ? AND version = 5

    alt 사용자1 먼저 커밋
        DB-->>Service: Success (사용자1)
        Service-->>User1: 발급 성공

        Service->>DB: UPDATE coupons<br/>SET issued_quantity = 2, version = 6<br/>WHERE id = ? AND version = 5
        DB-->>Service: 0 rows affected (버전 불일치)
        Service-->>User2: 409 Conflict<br/>OptimisticLockException
    end

    Note over DB: 최종 상태:<br/>issued_quantity = 1<br/>version = 6<br/>사용자1만 발급 성공
```

---

## 참고 문서
- [요구사항 분석](./requirements-analysis.md)
- [데이터베이스 다이어그램](./database-diagram.md)
- [API 명세서](./api/api-specification.md)