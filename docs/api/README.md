# E-Commerce API 문서

## 개요
이커머스 플랫폼의 REST API 명세서입니다. 이 문서는 각 도메인별 API 엔드포인트, 요청/응답 형식, 비즈니스 규칙을 상세히 설명합니다.

## 기술 스택
- **언어**: Kotlin 1.9.25
- **프레임워크**: Spring Boot 3.5.6
- **데이터베이스**: MySQL
- **인증**: JWT

## API 기본 정보

### Base URL
```
http://localhost:8080/api
```

### 공통 헤더
```
Content-Type: application/json
Authorization: Bearer {JWT_TOKEN}
```

### 공통 에러 응답 형식
모든 API는 동일한 에러 응답 형식을 따릅니다:
```json
{
  "timestamp": "2025-10-29T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "code": "SPECIFIC_ERROR_CODE",
  "message": "사용자 친화적인 에러 메시지",
  "path": "/api/endpoint"
}
```

### HTTP 상태 코드
| 상태 코드 | 설명 |
|----------|------|
| 200 | 요청 성공 |
| 201 | 리소스 생성 성공 |
| 204 | 요청 성공 (응답 본문 없음) |
| 400 | 잘못된 요청 (유효성 검증 실패) |
| 403 | 권한 없음 |
| 404 | 리소스를 찾을 수 없음 |
| 409 | 충돌 (동시성 제어 실패) |
| 500 | 서버 내부 오류 |

---

## 도메인별 API 문서

### 1. [User API](./user-api.md)
사용자 관리 및 잔액 관리

**주요 기능:**
- 사용자 정보 조회
- 잔액 조회
- 잔액 충전

**핵심 엔드포인트:**
- `GET /api/users/{userId}` - 사용자 정보 조회
- `GET /api/users/{userId}/balance` - 잔액 조회
- `POST /api/users/{userId}/balance/charge` - 잔액 충전

---

### 2. [Product API](./product-api.md)
상품 조회, 재고 확인, 인기 상품 통계

**주요 기능:**
- 상품 목록 조회 (필터링, 정렬, 페이징)
- 상품 상세 조회
- 인기 상품 조회 (최근 3일 기준 Top 5)
- 재고 확인

**핵심 엔드포인트:**
- `GET /api/products` - 상품 목록 조회
- `GET /api/products/{productId}` - 상품 상세 조회
- `GET /api/products/top` - 인기 상품 Top 5 조회
- `GET /api/products/{productId}/stock` - 재고 확인

---

### 3. [Cart API](./cart-api.md)
장바구니 관리

**주요 기능:**
- 장바구니 조회
- 상품 추가/수량 변경
- 상품 삭제
- 장바구니 비우기

**핵심 엔드포인트:**
- `GET /api/carts/{userId}` - 장바구니 조회
- `POST /api/carts/{userId}/items` - 장바구니에 상품 추가
- `PATCH /api/carts/{userId}/items/{cartItemId}` - 수량 변경
- `DELETE /api/carts/{userId}/items/{cartItemId}` - 상품 삭제

---

### 4. [Order API](./order-api.md)
주문 생성, 조회, 취소

**주요 기능:**
- 주문 생성 (재고 차감, 쿠폰 적용)
- 주문 상세 조회
- 주문 목록 조회
- 주문 취소 (PENDING 상태만 가능)

**핵심 엔드포인트:**
- `POST /api/orders` - 주문 생성
- `GET /api/orders/{orderId}` - 주문 상세 조회
- `GET /api/orders` - 주문 목록 조회
- `POST /api/orders/{orderId}/cancel` - 주문 취소

**주문 상태:**
- `PENDING`: 결제 대기
- `PAID`: 결제 완료
- `CANCELLED`: 주문 취소

---

### 5. [Payment API](./payment-api.md)
결제 처리 및 데이터 전송 관리

**주요 기능:**
- 잔액 기반 결제
- 결제 내역 조회
- 외부 시스템 데이터 전송 (Outbox Pattern)
- 실패한 전송 재시도

**핵심 엔드포인트:**
- `POST /api/orders/{orderId}/payment` - 결제 처리
- `GET /api/payments/{paymentId}` - 결제 정보 조회
- `GET /api/orders/{orderId}/payment` - 주문별 결제 내역 조회
- `GET /api/data-transmissions/{transmissionId}` - 데이터 전송 상태 조회
- `POST /api/data-transmissions/{transmissionId}/retry` - 수동 재시도

**데이터 전송 상태:**
- `PENDING`: 전송 대기
- `SUCCESS`: 전송 성공
- `FAILED`: 전송 실패 (최대 재시도 초과)

---

### 6. [Coupon API](./coupon-api.md)
쿠폰 발급, 조회, 사용 관리

**주요 기능:**
- 선착순 쿠폰 발급 (1인 1매 제한)
- 보유 쿠폰 조회
- 발급 가능한 쿠폰 목록 조회
- 쿠폰 상세 조회

**핵심 엔드포인트:**
- `POST /api/coupons/{couponId}/issue` - 쿠폰 발급
- `GET /api/users/{userId}/coupons` - 보유 쿠폰 목록
- `GET /api/coupons/available` - 발급 가능한 쿠폰 목록
- `GET /api/coupons/{couponId}` - 쿠폰 상세 조회

**쿠폰 상태:**
- `AVAILABLE`: 사용 가능
- `USED`: 사용 완료
- `EXPIRED`: 만료됨

---

## 핵심 비즈니스 플로우

### 1. 상품 구매 플로우
```
1. 상품 조회 (Product API)
   ↓
2. 장바구니 추가 (Cart API)
   ↓
3. 쿠폰 발급 (Coupon API) - 선택사항
   ↓
4. 주문 생성 (Order API)
   - 재고 즉시 차감
   - 쿠폰 적용 시 USED로 변경
   - 주문 상태: PENDING
   ↓
5. 결제 처리 (Payment API)
   - 잔액 차감
   - 주문 상태: PAID
   - 데이터 전송 레코드 생성 (PENDING)
   ↓
6. 외부 시스템 데이터 전송 (비동기)
   - 성공: SUCCESS
   - 실패: 재시도 (최대 3회)
```

### 2. 주문 취소 플로우
```
1. 주문 취소 요청 (Order API)
   - PENDING 상태만 취소 가능
   ↓
2. 보상 처리
   - 재고 복원
   - 쿠폰 복원 (USED → AVAILABLE)
   ↓
3. 주문 상태: CANCELLED
```

### 3. 결제 실패 시 보상 플로우
```
결제 실패
   ↓
보상 트랜잭션 실행
   ↓
재고 복원 + 쿠폰 복원
   ↓
주문 상태: CANCELLED
```

---

## 동시성 제어

### 낙관적 락 적용 대상
1. **재고 차감** (Product)
   - `@Version` 필드 사용
   - 동시 주문 시 재고 초과 방지

2. **쿠폰 발급** (Coupon)
   - 선착순 발급 시 `issued_quantity` 동시성 제어
   - 발급 수량 초과 방지

### 재시도 정책
- 최대 3회 재시도
- 재시도 간격: 100ms
- 3회 실패 시 에러 응답 (HTTP 409)

---

## 데이터 전송 (Outbox Pattern)

### 개요
결제 완료 시 주문 데이터를 외부 시스템으로 전송합니다. 전송 실패 시에도 주문은 정상 처리되며, 비동기로 재시도합니다.

### 재시도 정책
| 시도 | 재시도 시점 |
|------|----------|
| 1차 실패 | 1분 후 |
| 2차 실패 | 5분 후 |
| 3차 실패 | 15분 후 |
| 3회 실패 | FAILED 상태, 관리자 알림 |

### 멱등성 보장
- `order_id` 기반 중복 체크
- SUCCESS 상태는 재전송하지 않음
- 외부 시스템에서도 `order_id`로 멱등성 보장 필요

---

## 에러 코드 전체 목록

### 4xx 클라이언트 에러
| 에러 코드 | HTTP 상태 | 설명 |
|----------|----------|------|
| USER_NOT_FOUND | 404 | 사용자를 찾을 수 없음 |
| PRODUCT_NOT_FOUND | 404 | 상품을 찾을 수 없음 |
| ORDER_NOT_FOUND | 404 | 주문을 찾을 수 없음 |
| COUPON_NOT_FOUND | 404 | 쿠폰을 찾을 수 없음 |
| CART_ITEM_NOT_FOUND | 404 | 장바구니 아이템을 찾을 수 없음 |
| PAYMENT_NOT_FOUND | 404 | 결제 정보를 찾을 수 없음 |
| INVALID_AMOUNT | 400 | 유효하지 않은 금액 |
| INVALID_QUANTITY | 400 | 유효하지 않은 수량 |
| INSUFFICIENT_BALANCE | 400 | 잔액 부족 |
| INSUFFICIENT_STOCK | 400 | 재고 부족 |
| EXCEED_STOCK | 400 | 재고 초과 |
| EXCEED_MAX_QUANTITY | 400 | 최대 수량 초과 |
| BALANCE_LIMIT_EXCEEDED | 400 | 잔액 한도 초과 |
| COUPON_SOLD_OUT | 400 | 쿠폰 소진 |
| COUPON_ALREADY_ISSUED | 400 | 이미 발급받은 쿠폰 |
| COUPON_NOT_AVAILABLE | 400 | 사용 불가능한 쿠폰 |
| COUPON_EXPIRED | 400 | 만료된 쿠폰 |
| INVALID_ORDER_STATUS | 400 | 유효하지 않은 주문 상태 |
| CANNOT_CANCEL_ORDER | 400 | 취소 불가능한 주문 |
| ALREADY_PAID | 400 | 이미 결제된 주문 |
| FORBIDDEN | 403 | 권한 없음 |
| OPTIMISTIC_LOCK_FAILURE | 409 | 동시성 충돌 |

### 5xx 서버 에러
| 에러 코드 | HTTP 상태 | 설명 |
|----------|----------|------|
| INTERNAL_SERVER_ERROR | 500 | 서버 내부 오류 |
| DATA_TRANSMISSION_FAILED | 500 | 데이터 전송 실패 |

---

## 테스트

### API 테스트 도구
- **Postman**: API 테스트 및 문서화
- **cURL**: 커맨드라인 테스트
- **JUnit**: 통합 테스트

### 주요 테스트 시나리오
1. **동시성 테스트**
   - 동시 주문 시 재고 초과 방지
   - 선착순 쿠폰 발급 정확성

2. **에러 처리 테스트**
   - 재고 부족 시 주문 거부
   - 잔액 부족 시 결제 실패 및 보상 처리
   - 쿠폰 만료 시 사용 거부

3. **보상 트랜잭션 테스트**
   - 결제 실패 시 재고/쿠폰 복원
   - 주문 취소 시 재고/쿠폰 복원

---

## 변경 이력

### v1.0.0 (2025-10-29)
- 초기 API 설계 및 문서 작성
- User, Product, Cart, Order, Payment, Coupon API 구현
- Outbox Pattern 기반 데이터 전송 구현
- 낙관적 락 기반 동시성 제어 구현

---

## 참고 문서
- [비즈니스 정책](../../.claude/docs/BUSINESS_POLICIES.md)
- [요구사항 분석](../requirements-analysis.md)
- [데이터베이스 다이어그램](../database-diagram.md)
- [시퀀스 다이어그램](../sequence-diagrams.md)