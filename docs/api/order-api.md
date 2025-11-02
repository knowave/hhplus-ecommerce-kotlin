# Order API 문서

## 개요
주문 생성, 조회, 취소 API를 제공합니다. 재고 차감, 쿠폰 적용, 결제 프로세스를 포함합니다.

## 기본 경로
```
/api/orders
```

---

## API 목록

### 1. 주문 생성
**장바구니 또는 직접 선택한 상품으로 주문을 생성합니다.**

#### 엔드포인트
```
POST /api/orders
```

#### 요청 본문
```json
{
  "userId": 1,
  "items": [
    {
      "productId": 15,
      "quantity": 2
    },
    {
      "productId": 7,
      "quantity": 1
    }
  ],
  "couponId": 3
}
```

#### 요청 필드
| 필드 | 타입 | 필수 | 설명 | 제약사항 |
|------|------|------|------|---------|
| userId | Long | O | 사용자 ID | 존재하는 사용자여야 함 |
| items | Array | O | 주문 상품 목록 | 최소 1개 이상 |
| items[].productId | Long | O | 상품 ID | 존재하는 상품이어야 함 |
| items[].quantity | Integer | O | 수량 | 1 이상, 재고 이하 |
| couponId | Long | X | 쿠폰 ID | 사용 가능한 쿠폰이어야 함 |

#### 응답 (201 Created)
```json
{
  "orderId": 1001,
  "userId": 1,
  "orderNumber": "ORD-20251029-1001",
  "items": [
    {
      "orderItemId": 1,
      "productId": 15,
      "productName": "무선 이어폰 XYZ",
      "quantity": 2,
      "unitPrice": 150000,
      "subtotal": 300000
    },
    {
      "orderItemId": 2,
      "productId": 7,
      "productName": "운동화 ABC",
      "quantity": 1,
      "unitPrice": 89000,
      "subtotal": 89000
    }
  ],
  "pricing": {
    "totalAmount": 389000,
    "discountAmount": 38900,
    "finalAmount": 350100,
    "appliedCoupon": {
      "couponId": 3,
      "couponName": "10% 할인 쿠폰",
      "discountRate": 10
    }
  },
  "status": "PENDING",
  "createdAt": "2025-10-29T10:30:00"
}
```

#### 응답 필드
| 필드 | 타입 | 설명 |
|------|------|------|
| orderId | Long | 주문 ID |
| userId | Long | 사용자 ID |
| orderNumber | String | 주문 번호 (고유 식별자) |
| items | Array | 주문 아이템 목록 |
| items[].orderItemId | Long | 주문 아이템 ID |
| items[].productId | Long | 상품 ID |
| items[].productName | String | 상품명 |
| items[].quantity | Integer | 주문 수량 |
| items[].unitPrice | Long | 상품 단가 |
| items[].subtotal | Long | 소계 |
| pricing | Object | 가격 정보 |
| pricing.totalAmount | Long | 총 상품 금액 |
| pricing.discountAmount | Long | 할인 금액 |
| pricing.finalAmount | Long | 최종 결제 금액 |
| pricing.appliedCoupon | Object | 적용된 쿠폰 정보 (없으면 null) |
| status | String | 주문 상태 (PENDING, PAID, CANCELLED) |
| createdAt | String | 주문 생성 시간 |

#### 비즈니스 규칙
**주문 생성 시 처리 순서:**
1. **상품 존재 여부 확인** - 모든 상품이 존재하는지 검증
2. **재고 확인 및 차감** - 모든 상품의 재고가 충분한지 확인 후 즉시 차감 (낙관적 락 사용)
3. **쿠폰 검증 및 사용** - 쿠폰 유효성 검증 후 USED 상태로 변경
4. **금액 계산** - 총 금액, 할인 금액, 최종 금액 계산
5. **주문 생성** - PENDING 상태로 주문 생성

**재고 차감 정책:**
- 주문 생성 시점에 즉시 재고 차감 (예약)
- 낙관적 락을 사용하여 동시성 제어
- 재고 부족 시 주문 생성 자체를 거부

**쿠폰 사용 조건:**
- 쿠폰 상태가 AVAILABLE이어야 함
- 쿠폰이 만료되지 않았어야 함 (expires_at 체크)
- 쿠폰 사용 기간 내여야 함 (start_date ~ end_date)
- 한 주문에 하나의 쿠폰만 사용 가능

**금액 계산 공식:**
```kotlin
totalAmount = sum(item.unitPrice * item.quantity)
discountAmount = floor(totalAmount * coupon.discountRate / 100)
finalAmount = totalAmount - discountAmount
```

#### 에러 응답
| 상태 코드 | 에러 코드 | 설명 |
|----------|----------|------|
| 400 | INVALID_ORDER_ITEMS | 주문 상품 목록이 비어있음 |
| 400 | INSUFFICIENT_STOCK | 재고 부족 |
| 400 | COUPON_NOT_AVAILABLE | 쿠폰을 사용할 수 없음 |
| 400 | COUPON_EXPIRED | 쿠폰이 만료됨 |
| 400 | INSUFFICIENT_BALANCE | 잔액 부족 (결제 시) |
| 404 | PRODUCT_NOT_FOUND | 상품을 찾을 수 없음 |
| 404 | USER_NOT_FOUND | 사용자를 찾을 수 없음 |
| 404 | COUPON_NOT_FOUND | 쿠폰을 찾을 수 없음 |
| 409 | OPTIMISTIC_LOCK_FAILURE | 동시성 충돌 (재고 차감 실패) |

---

### 2. 주문 상세 조회
**특정 주문의 상세 정보를 조회합니다.**

#### 엔드포인트
```
GET /api/orders/{orderId}
```

#### 경로 파라미터
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| orderId | Long | O | 주문 ID |

#### 쿼리 파라미터
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| userId | Long | O | 사용자 ID (권한 확인용) |

#### 응답 (200 OK)
```json
{
  "orderId": 1001,
  "userId": 1,
  "orderNumber": "ORD-20251029-1001",
  "items": [
    {
      "orderItemId": 1,
      "productId": 15,
      "productName": "무선 이어폰 XYZ",
      "quantity": 2,
      "unitPrice": 150000,
      "subtotal": 300000
    }
  ],
  "pricing": {
    "totalAmount": 389000,
    "discountAmount": 38900,
    "finalAmount": 350100,
    "appliedCoupon": {
      "couponId": 3,
      "couponName": "10% 할인 쿠폰",
      "discountRate": 10
    }
  },
  "status": "PAID",
  "payment": {
    "paidAmount": 350100,
    "paidAt": "2025-10-29T10:31:00"
  },
  "createdAt": "2025-10-29T10:30:00",
  "updatedAt": "2025-10-29T10:31:00"
}
```

#### 에러 응답
| 상태 코드 | 에러 코드 | 설명 |
|----------|----------|------|
| 404 | ORDER_NOT_FOUND | 주문을 찾을 수 없음 |
| 403 | FORBIDDEN | 다른 사용자의 주문 |

---

### 3. 사용자 주문 목록 조회
**특정 사용자의 주문 목록을 조회합니다.**

#### 엔드포인트
```
GET /api/orders
```

#### 쿼리 파라미터
| 파라미터 | 타입 | 필수 | 설명 | 기본값 |
|---------|------|------|------|-------|
| userId | Long | O | 사용자 ID | - |
| status | String | X | 주문 상태 필터 | 전체 |
| page | Integer | X | 페이지 번호 | 0 |
| size | Integer | X | 페이지 크기 | 20 |

#### 응답 (200 OK)
```json
{
  "orders": [
    {
      "orderId": 1001,
      "orderNumber": "ORD-20251029-1001",
      "totalAmount": 389000,
      "discountAmount": 38900,
      "finalAmount": 350100,
      "status": "PAID",
      "itemCount": 2,
      "createdAt": "2025-10-29T10:30:00"
    }
  ],
  "pagination": {
    "currentPage": 0,
    "totalPages": 3,
    "totalElements": 45,
    "size": 20,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

#### 주문 상태 필터 옵션
- `PENDING`: 결제 대기
- `PAID`: 결제 완료
- `CANCELLED`: 주문 취소

---

### 4. 주문 취소
**PENDING 상태의 주문을 취소합니다.**

#### 엔드포인트
```
POST /api/orders/{orderId}/cancel
```

#### 경로 파라미터
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| orderId | Long | O | 주문 ID |

#### 요청 본문
```json
{
  "userId": 1,
  "reason": "고객 변심"
}
```

#### 요청 필드
| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| userId | Long | O | 사용자 ID |
| reason | String | X | 취소 사유 |

#### 응답 (200 OK)
```json
{
  "orderId": 1001,
  "status": "CANCELLED",
  "cancelledAt": "2025-10-29T10:35:00",
  "refund": {
    "restoredStock": [
      {
        "productId": 15,
        "quantity": 2
      }
    ],
    "restoredCoupon": {
      "couponId": 3,
      "status": "AVAILABLE"
    }
  }
}
```

#### 비즈니스 규칙
**주문 취소 시 보상 처리:**
1. **재고 복원** - 차감된 재고를 원래대로 복원
2. **쿠폰 복원** - USED → AVAILABLE로 상태 변경 (만료되지 않은 경우)
3. **주문 상태 변경** - CANCELLED로 변경

**취소 가능 조건:**
- PENDING 상태의 주문만 취소 가능
- PAID 상태 주문은 별도 환불 프로세스 필요 (현재 미지원)

#### 에러 응답
| 상태 코드 | 에러 코드 | 설명 |
|----------|----------|------|
| 400 | CANNOT_CANCEL_ORDER | 취소할 수 없는 주문 상태 |
| 404 | ORDER_NOT_FOUND | 주문을 찾을 수 없음 |
| 403 | FORBIDDEN | 다른 사용자의 주문 |

---

## 주문 상태 전이도
```
PENDING (결제 대기)
   ├─→ PAID (결제 완료) ← 결제 성공
   └─→ CANCELLED (주문 취소) ← 결제 실패 또는 사용자 취소
```

## 공통 에러 응답 형식
```json
{
  "timestamp": "2025-10-29T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "code": "INSUFFICIENT_STOCK",
  "message": "상품 [무선 이어폰 XYZ]의 재고가 부족합니다. (요청: 5개, 재고: 3개)",
  "path": "/api/orders"
}
```

## 주요 에러 코드 목록
| 에러 코드 | HTTP 상태 | 설명 |
|----------|----------|------|
| ORDER_NOT_FOUND | 404 | 주문을 찾을 수 없음 |
| PRODUCT_NOT_FOUND | 404 | 상품을 찾을 수 없음 |
| USER_NOT_FOUND | 404 | 사용자를 찾을 수 없음 |
| COUPON_NOT_FOUND | 404 | 쿠폰을 찾을 수 없음 |
| INVALID_ORDER_ITEMS | 400 | 주문 상품 목록이 비어있음 |
| INSUFFICIENT_STOCK | 400 | 재고 부족 |
| COUPON_NOT_AVAILABLE | 400 | 쿠폰 사용 불가 |
| COUPON_EXPIRED | 400 | 쿠폰 만료 |
| INSUFFICIENT_BALANCE | 400 | 잔액 부족 |
| CANNOT_CANCEL_ORDER | 400 | 취소 불가능한 주문 상태 |
| OPTIMISTIC_LOCK_FAILURE | 409 | 동시성 충돌 |
| FORBIDDEN | 403 | 권한 없음 |