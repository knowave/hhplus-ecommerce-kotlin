# Coupon API 문서

## 개요
쿠폰 발급, 조회, 사용 관리 API를 제공합니다. 선착순 발급과 동시성 제어를 지원합니다.

## 기본 경로
```
/api/coupons
```

---

## API 목록

### 1. 쿠폰 발급 (선착순)
**선착순으로 쿠폰을 발급받습니다. 1인 1매 제한이 적용됩니다.**

#### 엔드포인트
```
POST /api/coupons/{couponId}/issue
```

#### 경로 파라미터
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| couponId | Long | O | 쿠폰 ID |

#### 요청 본문
```json
{
  "userId": 1
}
```

#### 요청 필드
| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| userId | Long | O | 사용자 ID |

#### 응답 (201 Created)
```json
{
  "userCouponId": 10001,
  "userId": 1,
  "couponId": 3,
  "couponName": "10% 할인 쿠폰",
  "discountRate": 10,
  "status": "AVAILABLE",
  "issuedAt": "2025-10-29T10:30:00",
  "expiresAt": "2025-11-28T23:59:59",
  "remainingQuantity": 99,
  "totalQuantity": 100
}
```

#### 응답 필드
| 필드 | 타입 | 설명 |
|------|------|------|
| userCouponId | Long | 사용자 쿠폰 ID |
| userId | Long | 사용자 ID |
| couponId | Long | 쿠폰 ID |
| couponName | String | 쿠폰명 |
| discountRate | Integer | 할인율 (%) |
| status | String | 쿠폰 상태 (AVAILABLE, USED, EXPIRED) |
| issuedAt | String | 발급 시간 |
| expiresAt | String | 만료 시간 |
| remainingQuantity | Integer | 남은 발급 수량 |
| totalQuantity | Integer | 총 발급 가능 수량 |

#### 비즈니스 규칙
**쿠폰 발급 규칙:**

1. **선착순 발급**
   - `issued_quantity < total_quantity` 조건 체크
   - 동시 발급 요청 시 낙관적 락으로 동시성 제어
   - 발급 시 `coupons.issued_quantity` 자동 증가

2. **중복 발급 방지**
   - 1인 1매 제한
   - `user_coupons` 테이블에서 (user_id, coupon_id) 조합으로 중복 체크
   - 이미 발급받은 경우 에러 반환

3. **발급 시점**
   - 사용자 요청 시 즉시 발급
   - `user_coupons` 레코드 생성 (상태: AVAILABLE)
   - 발급일로부터 30일 후 자동 만료 (기본값)

4. **발급 가능 조건**
   - 쿠폰 발급 기간 내여야 함 (start_date ~ end_date)
   - 발급 수량이 남아있어야 함
   - 이미 발급받지 않았어야 함

#### 에러 응답
| 상태 코드 | 에러 코드 | 설명 |
|----------|----------|------|
| 400 | COUPON_SOLD_OUT | 쿠폰이 모두 소진됨 |
| 400 | COUPON_ALREADY_ISSUED | 이미 발급받은 쿠폰 |
| 400 | COUPON_ISSUE_PERIOD_ENDED | 발급 기간이 종료됨 |
| 400 | COUPON_ISSUE_NOT_STARTED | 발급 기간이 시작되지 않음 |
| 404 | COUPON_NOT_FOUND | 쿠폰을 찾을 수 없음 |
| 404 | USER_NOT_FOUND | 사용자를 찾을 수 없음 |
| 409 | OPTIMISTIC_LOCK_FAILURE | 동시성 충돌 (선착순 경쟁) |

---

### 2. 사용자 보유 쿠폰 목록 조회
**사용자가 보유한 쿠폰 목록을 조회합니다.**

#### 엔드포인트
```
GET /api/users/{userId}/coupons
```

#### 경로 파라미터
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| userId | Long | O | 사용자 ID |

#### 쿼리 파라미터
| 파라미터 | 타입 | 필수 | 설명 | 기본값 |
|---------|------|------|------|-------|
| status | String | X | 쿠폰 상태 필터 | 전체 |

#### 응답 (200 OK)
```json
{
  "userId": 1,
  "coupons": [
    {
      "userCouponId": 10001,
      "couponId": 3,
      "couponName": "10% 할인 쿠폰",
      "discountRate": 10,
      "status": "AVAILABLE",
      "issuedAt": "2025-10-29T10:30:00",
      "expiresAt": "2025-11-28T23:59:59",
      "isExpired": false,
      "daysRemaining": 30
    },
    {
      "userCouponId": 10002,
      "couponId": 5,
      "couponName": "20% 할인 쿠폰",
      "discountRate": 20,
      "status": "USED",
      "issuedAt": "2025-10-20T14:00:00",
      "expiresAt": "2025-11-19T23:59:59",
      "usedAt": "2025-10-25T09:30:00",
      "isExpired": false,
      "daysRemaining": 21
    },
    {
      "userCouponId": 10003,
      "couponId": 7,
      "couponName": "30% 할인 쿠폰",
      "discountRate": 30,
      "status": "EXPIRED",
      "issuedAt": "2025-09-20T10:00:00",
      "expiresAt": "2025-10-20T23:59:59",
      "isExpired": true,
      "daysRemaining": 0
    }
  ],
  "summary": {
    "totalCount": 3,
    "availableCount": 1,
    "usedCount": 1,
    "expiredCount": 1
  }
}
```

#### 쿠폰 상태 필터 옵션
- `AVAILABLE`: 사용 가능
- `USED`: 사용 완료
- `EXPIRED`: 만료됨

---

### 3. 발급 가능한 쿠폰 목록 조회
**현재 발급 가능한 쿠폰 목록을 조회합니다.**

#### 엔드포인트
```
GET /api/coupons/available
```

#### 쿼리 파라미터
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| userId | Long | X | 사용자 ID (이미 발급받은 쿠폰 제외) |

#### 응답 (200 OK)
```json
{
  "coupons": [
    {
      "couponId": 3,
      "couponName": "10% 할인 쿠폰",
      "description": "모든 상품 10% 할인",
      "discountRate": 10,
      "totalQuantity": 1000,
      "issuedQuantity": 650,
      "remainingQuantity": 350,
      "issuePeriod": {
        "startDate": "2025-10-01T00:00:00",
        "endDate": "2025-10-31T23:59:59"
      },
      "validityDays": 30,
      "isUserIssued": false
    },
    {
      "couponId": 5,
      "couponName": "20% 할인 쿠폰",
      "description": "전자제품 20% 할인",
      "discountRate": 20,
      "totalQuantity": 500,
      "issuedQuantity": 480,
      "remainingQuantity": 20,
      "issuePeriod": {
        "startDate": "2025-10-15T00:00:00",
        "endDate": "2025-11-15T23:59:59"
      },
      "validityDays": 30,
      "isUserIssued": true
    }
  ]
}
```

#### 응답 필드
| 필드 | 타입 | 설명 |
|------|------|------|
| couponId | Long | 쿠폰 ID |
| couponName | String | 쿠폰명 |
| description | String | 쿠폰 설명 |
| discountRate | Integer | 할인율 (%) |
| totalQuantity | Integer | 총 발급 가능 수량 |
| issuedQuantity | Integer | 현재까지 발급된 수량 |
| remainingQuantity | Integer | 남은 발급 수량 |
| issuePeriod | Object | 발급 기간 |
| validityDays | Integer | 유효 기간 (일) |
| isUserIssued | Boolean | 사용자 발급 여부 (userId 제공 시) |

---

### 4. 쿠폰 상세 조회
**특정 쿠폰의 상세 정보를 조회합니다.**

#### 엔드포인트
```
GET /api/coupons/{couponId}
```

#### 경로 파라미터
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| couponId | Long | O | 쿠폰 ID |

#### 응답 (200 OK)
```json
{
  "couponId": 3,
  "couponName": "10% 할인 쿠폰",
  "description": "모든 상품 10% 할인",
  "discountRate": 10,
  "totalQuantity": 1000,
  "issuedQuantity": 650,
  "remainingQuantity": 350,
  "issuePeriod": {
    "startDate": "2025-10-01T00:00:00",
    "endDate": "2025-10-31T23:59:59"
  },
  "validityDays": 30,
  "isAvailable": true,
  "createdAt": "2025-09-25T00:00:00"
}
```

#### 에러 응답
| 상태 코드 | 에러 코드 | 설명 |
|----------|----------|------|
| 404 | COUPON_NOT_FOUND | 쿠폰을 찾을 수 없음 |

---

### 5. 사용자 쿠폰 상세 조회
**사용자가 보유한 특정 쿠폰의 상세 정보를 조회합니다.**

#### 엔드포인트
```
GET /api/users/{userId}/coupons/{userCouponId}
```

#### 경로 파라미터
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| userId | Long | O | 사용자 ID |
| userCouponId | Long | O | 사용자 쿠폰 ID |

#### 응답 (200 OK)
```json
{
  "userCouponId": 10001,
  "userId": 1,
  "couponId": 3,
  "couponName": "10% 할인 쿠폰",
  "description": "모든 상품 10% 할인",
  "discountRate": 10,
  "status": "AVAILABLE",
  "issuedAt": "2025-10-29T10:30:00",
  "expiresAt": "2025-11-28T23:59:59",
  "usedAt": null,
  "isExpired": false,
  "canUse": true
}
```

#### 응답 필드
| 필드 | 타입 | 설명 |
|------|------|------|
| userCouponId | Long | 사용자 쿠폰 ID |
| userId | Long | 사용자 ID |
| couponId | Long | 쿠폰 ID |
| couponName | String | 쿠폰명 |
| description | String | 쿠폰 설명 |
| discountRate | Integer | 할인율 (%) |
| status | String | 쿠폰 상태 |
| issuedAt | String | 발급 시간 |
| expiresAt | String | 만료 시간 |
| usedAt | String | 사용 시간 (사용 전이면 null) |
| isExpired | Boolean | 만료 여부 |
| canUse | Boolean | 사용 가능 여부 |

#### 에러 응답
| 상태 코드 | 에러 코드 | 설명 |
|----------|----------|------|
| 404 | USER_COUPON_NOT_FOUND | 사용자 쿠폰을 찾을 수 없음 |
| 403 | FORBIDDEN | 다른 사용자의 쿠폰 |

---

## 쿠폰 상태 및 전이

### 쿠폰 상태
```
AVAILABLE (사용 가능)
   ├─→ USED (사용 완료) ← 주문에 사용
   └─→ EXPIRED (만료) ← 유효기간 경과
```

### 상태 전이 조건

**AVAILABLE → USED**
- 주문 생성 시 쿠폰 적용
- 결제 완료 시 확정

**AVAILABLE → EXPIRED**
- expires_at 시간 경과
- 배치 작업으로 만료 처리

**USED → AVAILABLE (복원)**
- 결제 실패 시
- 주문 취소 시
- 단, 이미 만료된 경우 복원하지 않음

## 비즈니스 규칙 요약

### 쿠폰 발급
- 선착순 발급 (낙관적 락 사용)
- 1인 1매 제한
- 발급 기간 내에만 발급 가능
- 발급일로부터 30일 후 자동 만료

### 쿠폰 사용
- AVAILABLE 상태여야 함
- 만료되지 않았어야 함
- 한 주문에 하나의 쿠폰만 사용
- 사용 시 USED 상태로 변경

### 쿠폰 복원
- 결제 실패 또는 주문 취소 시
- USED → AVAILABLE로 복원
- 만료된 쿠폰은 복원하지 않음

### 동시성 제어
- 쿠폰 발급 시 낙관적 락 사용
- `issued_quantity` 증가를 원자적으로 처리
- 동시 발급이 `total_quantity`를 초과하지 않도록 보장

## 공통 에러 응답 형식
```json
{
  "timestamp": "2025-10-29T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "code": "COUPON_SOLD_OUT",
  "message": "쿠폰이 모두 소진되었습니다. (쿠폰: 10% 할인 쿠폰)",
  "path": "/api/coupons/3/issue"
}
```

## 주요 에러 코드 목록
| 에러 코드 | HTTP 상태 | 설명 |
|----------|----------|------|
| COUPON_NOT_FOUND | 404 | 쿠폰을 찾을 수 없음 |
| USER_COUPON_NOT_FOUND | 404 | 사용자 쿠폰을 찾을 수 없음 |
| USER_NOT_FOUND | 404 | 사용자를 찾을 수 없음 |
| COUPON_SOLD_OUT | 400 | 쿠폰이 모두 소진됨 |
| COUPON_ALREADY_ISSUED | 400 | 이미 발급받은 쿠폰 |
| COUPON_ISSUE_PERIOD_ENDED | 400 | 발급 기간 종료 |
| COUPON_ISSUE_NOT_STARTED | 400 | 발급 기간 미시작 |
| COUPON_NOT_AVAILABLE | 400 | 사용 불가능한 쿠폰 |
| COUPON_EXPIRED | 400 | 만료된 쿠폰 |
| OPTIMISTIC_LOCK_FAILURE | 409 | 동시성 충돌 |
| FORBIDDEN | 403 | 권한 없음 |