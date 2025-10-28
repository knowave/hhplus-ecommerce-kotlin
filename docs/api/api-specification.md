# API 명세서

## 기본 정보
- **Base URL**: `http://localhost:8080/api`
- **Content-Type**: `application/json`
- **Character Encoding**: `UTF-8`

## 응답 형식

### 성공 응답
```json
{
  "data": { ... },
  "message": "Success",
  "timestamp": "2025-01-01T00:00:00"
}
```

### 에러 응답
```json
{
  "code": "ERROR_CODE",
  "message": "Error message",
  "data": { ... },
  "timestamp": "2025-01-01T00:00:00"
}
```

---

## 1. 사용자 관리 API

### 1.1 사용자 생성
사용자를 생성합니다.

**Endpoint**: `POST /api/users`

**Request Body**:
```json
{
  "balance": 1000000
}
```

**Response** (201 Created):
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "balance": 1000000
}
```

**Error Responses**:
- `400 Bad Request`: 잘못된 요청 (balance < 0)

---

### 1.2 잔액 충전
사용자의 잔액을 충전합니다.

**Endpoint**: `PATCH /api/users/{userId}/balance/charge`

**Path Parameters**:
| Name | Type | Description |
|------|------|-------------|
| userId | String | 사용자 ID (UUID) |

**Request Body**:
```json
{
  "amount": 50000
}
```

**Response** (200 OK):
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "balance": 1050000
}
```

**Error Responses**:
- `400 Bad Request`: 충전 금액이 0 이하
- `404 Not Found`: 사용자를 찾을 수 없음

---

### 1.3 잔액 조회
사용자의 현재 잔액을 조회합니다.

**Endpoint**: `GET /api/users/{userId}/balance`

**Path Parameters**:
| Name | Type | Description |
|------|------|-------------|
| userId | String | 사용자 ID (UUID) |

**Response** (200 OK):
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "balance": 1050000
}
```

**Error Responses**:
- `404 Not Found`: 사용자를 찾을 수 없음

---

## 2. 상품 관리 API

### 2.1 상품 목록 조회
전체 상품 목록 또는 카테고리별 상품 목록을 조회합니다.

**Endpoint**: `GET /api/products`

**Query Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| category | String | No | 카테고리 필터 |

**Response** (200 OK):
```json
[
  {
    "productId": "P001",
    "name": "노트북",
    "price": 890000,
    "stock": 10,
    "category": "전자제품"
  },
  {
    "productId": "P002",
    "name": "키보드",
    "price": 120000,
    "stock": 50,
    "category": "주변기기"
  }
]
```

**Example Requests**:
- 전체 조회: `GET /api/products`
- 카테고리 필터: `GET /api/products?category=전자제품`

---

### 2.2 상품 재고 조회
특정 상품의 실시간 재고를 조회합니다.

**Endpoint**: `GET /api/products/{productId}/stock`

**Path Parameters**:
| Name | Type | Description |
|------|------|-------------|
| productId | String | 상품 ID |

**Response** (200 OK):
```json
{
  "productId": "P001",
  "name": "노트북",
  "stock": 10,
  "isAvailable": true
}
```

**Error Responses**:
- `404 Not Found`: 상품을 찾을 수 없음

---

### 2.3 인기 상품 조회
최근 3일간 판매량 기준 상위 5개 상품을 조회합니다.

**Endpoint**: `GET /api/products/top`

**Response** (200 OK):
```json
{
  "period": "3days",
  "products": [
    {
      "rank": 1,
      "productId": "P001",
      "name": "노트북",
      "salesCount": 150,
      "revenue": 133500000
    },
    {
      "rank": 2,
      "productId": "P002",
      "name": "키보드",
      "salesCount": 320,
      "revenue": 38400000
    }
  ]
}
```

---

## 3. 쿠폰 관리 API

### 3.1 쿠폰 발급
선착순으로 쿠폰을 발급받습니다.

**Endpoint**: `POST /api/coupons/{couponId}/issue`

**Path Parameters**:
| Name | Type | Description |
|------|------|-------------|
| couponId | String | 쿠폰 ID |

**Request Body**:
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response** (201 Created):
```json
{
  "userCouponId": "UC001",
  "couponName": "10% 할인쿠폰",
  "discountRate": 10,
  "expiresAt": "2025-02-01T00:00:00",
  "remainingQuantity": 95
}
```

**Error Responses**:
- `400 Bad Request`: 쿠폰이 매진되었거나 이미 발급받은 쿠폰
- `404 Not Found`: 쿠폰 또는 사용자를 찾을 수 없음
- `409 Conflict`: 동시성 충돌 (낙관적 락 실패)

**Error Examples**:
```json
{
  "code": "C001",
  "message": "Coupon sold out. Coupon id: COUPON_10"
}
```

```json
{
  "code": "C006",
  "message": "User already has this coupon. User id: user1, Coupon id: COUPON_10"
}
```

---

### 3.2 보유 쿠폰 조회
사용자가 보유한 쿠폰 목록을 조회합니다.

**Endpoint**: `GET /api/users/{userId}/coupons`

**Path Parameters**:
| Name | Type | Description |
|------|------|-------------|
| userId | String | 사용자 ID |

**Response** (200 OK):
```json
[
  {
    "userCouponId": "UC001",
    "couponName": "10% 할인쿠폰",
    "discountRate": 10,
    "status": "AVAILABLE",
    "expiresAt": "2025-02-01T00:00:00"
  },
  {
    "userCouponId": "UC002",
    "couponName": "20% 할인쿠폰",
    "discountRate": 20,
    "status": "USED",
    "expiresAt": "2025-01-15T00:00:00"
  }
]
```

**쿠폰 상태**:
- `AVAILABLE`: 사용 가능
- `USED`: 사용 완료
- `EXPIRED`: 만료됨

---

### 3.3 쿠폰 목록 조회
현재 발급 가능한 쿠폰 목록을 조회합니다.

**Endpoint**: `GET /api/coupons`

**Response** (200 OK):
```json
[
  {
    "couponId": "COUPON_10",
    "name": "10% 할인쿠폰",
    "discountRate": 10,
    "totalQuantity": 100,
    "issuedQuantity": 45,
    "startDate": "2025-01-01T00:00:00",
    "endDate": "2025-12-31T23:59:59"
  }
]
```

---

## 4. 주문 관리 API

### 4.1 주문 생성
새로운 주문을 생성합니다.

**Endpoint**: `POST /api/orders`

**Request Body**:
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "items": [
    {
      "productId": "P001",
      "quantity": 2
    },
    {
      "productId": "P002",
      "quantity": 1
    }
  ],
  "couponId": "COUPON_10"
}
```

**Field Descriptions**:
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| userId | String | Yes | 사용자 ID |
| items | Array | Yes | 주문 아이템 목록 (최소 1개) |
| items[].productId | String | Yes | 상품 ID |
| items[].quantity | Integer | Yes | 주문 수량 (1 이상) |
| couponId | String | No | 사용할 쿠폰 ID (선택사항) |

**Response** (201 Created):
```json
{
  "orderId": "ORD001",
  "items": [
    {
      "productId": "P001",
      "name": "노트북",
      "quantity": 2,
      "unitPrice": 890000,
      "subtotal": 1780000
    },
    {
      "productId": "P002",
      "name": "키보드",
      "quantity": 1,
      "unitPrice": 120000,
      "subtotal": 120000
    }
  ],
  "totalAmount": 1900000,
  "discountAmount": 190000,
  "finalAmount": 1710000,
  "status": "PENDING"
}
```

**Error Responses**:
- `400 Bad Request`: 잘못된 요청 (수량 0 이하, 재고 부족, 잔액 부족, 유효하지 않은 쿠폰)
- `404 Not Found`: 사용자 또는 상품을 찾을 수 없음

**Error Examples**:

재고 부족:
```json
{
  "code": "P002",
  "message": "Insufficient stock. Requested: 5, Available: 3",
  "data": {
    "productId": "P001",
    "requested": 5,
    "available": 3
  }
}
```

잔액 부족:
```json
{
  "code": "PAY001",
  "message": "Insufficient balance. Required: 1710000, Available: 500000",
  "data": {
    "required": 1710000,
    "available": 500000
  }
}
```

유효하지 않은 쿠폰:
```json
{
  "code": "C002",
  "message": "Invalid coupon. Reason: No available coupon found for user"
}
```

---

### 4.2 주문 상세 조회
특정 주문의 상세 정보를 조회합니다.

**Endpoint**: `GET /api/orders/{orderId}`

**Path Parameters**:
| Name | Type | Description |
|------|------|-------------|
| orderId | String | 주문 ID |

**Response** (200 OK):
```json
{
  "orderId": "ORD001",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "items": [
    {
      "productId": "P001",
      "name": "노트북",
      "quantity": 2,
      "unitPrice": 890000,
      "subtotal": 1780000
    }
  ],
  "totalAmount": 1780000,
  "discountAmount": 178000,
  "finalAmount": 1602000,
  "status": "PAID",
  "createdAt": "2025-01-01T10:00:00",
  "paidAt": "2025-01-01T10:05:00"
}
```

**주문 상태**:
- `PENDING`: 주문 생성 완료, 결제 대기
- `PAID`: 결제 완료
- `CANCELLED`: 주문 취소

**Error Responses**:
- `404 Not Found`: 주문을 찾을 수 없음

---

### 4.3 사용자별 주문 목록 조회
특정 사용자의 모든 주문 목록을 조회합니다.

**Endpoint**: `GET /api/orders/user/{userId}`

**Path Parameters**:
| Name | Type | Description |
|------|------|-------------|
| userId | String | 사용자 ID |

**Response** (200 OK):
```json
[
  {
    "orderId": "ORD001",
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "items": [
      {
        "productId": "P001",
        "name": "노트북",
        "quantity": 2,
        "unitPrice": 890000,
        "subtotal": 1780000
      }
    ],
    "totalAmount": 1780000,
    "discountAmount": 178000,
    "finalAmount": 1602000,
    "status": "PAID",
    "createdAt": "2025-01-01T10:00:00",
    "paidAt": "2025-01-01T10:05:00"
  },
  {
    "orderId": "ORD002",
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "items": [
      {
        "productId": "P002",
        "name": "키보드",
        "quantity": 1,
        "unitPrice": 120000,
        "subtotal": 120000
      }
    ],
    "totalAmount": 120000,
    "discountAmount": 0,
    "finalAmount": 120000,
    "status": "PENDING",
    "createdAt": "2025-01-02T14:30:00",
    "paidAt": null
  }
]
```

**Error Responses**:
- `404 Not Found`: 사용자를 찾을 수 없음

---

### 4.4 결제 처리 (진행 중)
PENDING 상태의 주문을 결제 처리합니다.

**Endpoint**: `POST /api/orders/{orderId}/payment`

**Path Parameters**:
| Name | Type | Description |
|------|------|-------------|
| orderId | String | 주문 ID |

**Request Body**:
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response** (200 OK):
```json
{
  "orderId": "ORD001",
  "paidAmount": 1602000,
  "remainingBalance": 398000,
  "status": "SUCCESS",
  "dataTransmission": "PENDING"
}
```

**Error Responses**:
- `400 Bad Request`: 이미 결제된 주문 또는 잘못된 상태
- `404 Not Found`: 주문을 찾을 수 없음
- `500 Internal Server Error`: 결제 처리 실패

---

### 4.5 주문 취소 (진행 중)
PENDING 상태의 주문을 취소합니다.

**Endpoint**: `DELETE /api/orders/{orderId}`

**Path Parameters**:
| Name | Type | Description |
|------|------|-------------|
| orderId | String | 주문 ID |

**Response** (200 OK):
```json
{
  "orderId": "ORD001",
  "status": "CANCELLED",
  "restoredStock": [
    {
      "productId": "P001",
      "quantity": 2
    }
  ],
  "restoredCoupon": {
    "userCouponId": "UC001",
    "status": "AVAILABLE"
  }
}
```

**Error Responses**:
- `400 Bad Request`: PENDING 상태가 아닌 주문은 취소 불가
- `404 Not Found`: 주문을 찾을 수 없음

---

## 5. HTTP 상태 코드

| 상태 코드 | 설명 | 사용 시점 |
|-----------|------|-----------|
| 200 OK | 요청 성공 | GET, PATCH 성공 |
| 201 Created | 리소스 생성 성공 | POST 성공 (주문, 쿠폰 발급 등) |
| 400 Bad Request | 잘못된 요청 | 유효성 검증 실패, 비즈니스 규칙 위반 |
| 404 Not Found | 리소스 없음 | 존재하지 않는 리소스 조회 |
| 409 Conflict | 충돌 발생 | 낙관적 락 실패, 중복 요청 |
| 500 Internal Server Error | 서버 오류 | 예상치 못한 서버 에러 |

---

## 6. 에러 코드

### 상품 관련 (P001-P999)
| 코드 | 메시지 | HTTP 상태 |
|------|--------|-----------|
| P001 | Product not found | 404 |
| P002 | Insufficient stock | 400 |

### 주문 관련 (O001-O999)
| 코드 | 메시지 | HTTP 상태 |
|------|--------|-----------|
| O001 | Invalid quantity | 400 |
| O002 | Order not found | 404 |
| O003 | Order already paid | 400 |

### 결제 관련 (PAY001-PAY999)
| 코드 | 메시지 | HTTP 상태 |
|------|--------|-----------|
| PAY001 | Insufficient balance | 400 |
| PAY002 | Payment failed | 500 |

### 쿠폰 관련 (C001-C999)
| 코드 | 메시지 | HTTP 상태 |
|------|--------|-----------|
| C001 | Coupon sold out | 400 |
| C002 | Invalid coupon | 400 |
| C003 | Expired coupon | 400 |
| C004 | Coupon already used | 400 |
| C005 | Coupon not found | 404 |
| C006 | Coupon already issued to user | 400 |

### 사용자 관련 (U001-U999)
| 코드 | 메시지 | HTTP 상태 |
|------|--------|-----------|
| U001 | User not found | 404 |
| U002 | Email already exists | 409 |
| U003 | Invalid password | 400 |

---

## 7. 비즈니스 규칙 요약

### 금액 계산
```
totalAmount = Σ(unitPrice × quantity)
discountAmount = totalAmount × (discountRate ÷ 100) [소수점 내림]
finalAmount = totalAmount - discountAmount
```

### 쿠폰 적용 조건
- 쿠폰 상태가 AVAILABLE이어야 함
- 현재 시간이 만료일 이전이어야 함
- 한 주문에 하나의 쿠폰만 사용 가능

### 주문 생성 조건
- 모든 상품의 재고 >= 주문 수량
- 사용자 잔액 >= 최종 결제 금액
- 쿠폰 사용 시 쿠폰이 유효해야 함

### 동시성 제어
- 재고 차감: 낙관적 락 (향후 적용)
- 쿠폰 발급: 낙관적 락 (`@Version`)
- 중복 발급 방지: (user_id, coupon_id) 유니크 제약

---

## 8. 개발 중인 기능

### Phase 2
- [ ] 결제 처리 API
- [ ] 주문 취소 API
- [ ] 데이터 전송 (Outbox Pattern)

### Phase 3 (향후 계획)
- [ ] JWT 인증/인가
- [ ] 관리자 API
- [ ] 페이징 및 정렬
- [ ] 통계 API