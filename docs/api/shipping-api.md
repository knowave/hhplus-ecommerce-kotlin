# Shipping API 문서

## 개요
배송 관리 API를 제공합니다.  
배송 생성, 조회, 상태 변경 기능을 지원하며,  
배송 도착 예정일 및 실제 도착일을 기반으로 상태를 자동 관리합니다.

## 기본 경로
```
/api/shippings
```

---

## API 목록
### 1. 배송 조회 (단건)
**주문에 대한 배송 정보를 조회합니다.**

#### 엔드포인트
```
GET /api/shippings/{orderId}
```

#### 경로 파라미터
| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| orderId | Long | O | 주문 ID |

#### 응답 (200 OK)
```json
{
  "shippingId": 1001,
  "orderId": 5001,
  "carrier": "CJ대한통운",
  "trackingNumber": "123456789012",
  "shippingStartAt": "2025-10-29T14:00:00",
  "estimatedArrivalAt": "2025-11-01T18:00:00",
  "deliveredAt": null,
  "status": "IN_TRANSIT",
  "isDelayed": false,
  "isExpired": false,
  "createdAt": "2025-10-29T13:50:00",
  "updatedAt": "2025-10-29T14:00:00"
}
```

### 2. 배송 상태 변경

#### 엔드포인트
```
PATCH /api/shippings/{shippingId}/status
```

#### 경로 파라미터
| 파라미터       | 타입 | 필수 | 설명    |
|------------|------|------|-------|
| shippingId | Long | O | 배송 ID |

#### 요청 본문
```json
{
  "status": "DELIVERED",
  "deliveredAt": "2025-10-31T12:30:00"
}
```

#### 요청 필드
| 필드 | 타입     | 필수 | 설명     | 제약사항                           |
|------|--------|----|--------|--------------------------------|
| status | String | O  | 배송 상태  | PENDING, IN_TRANSIT, DELIVERED |
| deliveredAt| String | X  | 실제 도착일 | DELIVERED 상태일 경우 필수            |

#### 응답 (200 OK)
```json
{
  "shippingId": 1001,
  "orderId": 5001,
  "status": "DELIVERED",
  "deliveredAt": "2025-10-31T12:30:00",
  "updatedAt": "2025-10-31T12:31:00"
}
```

#### 응답 필드
| 필드 | 타입 | 설명         |
|------|------|------------|
| shippingId | Long | 배송 ID      |
| orderId | Long | 주문 ID      |
| status | String | 배송 상태      |
| deliveredAt | String | 실제 도착일     |
| updatedAt | String | 수정일        |

---

### 3. 사용자의 배송 목록 조회
**사용자의 배송 목록을 조회합니다.**

#### 엔드포인트
```
GET /api/users/{userId}/shippings
```

#### 경로 파라미터
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| userId | Long | O | 사용자 ID |

#### 쿼리 파라미터
| 파라미터    | 타입 | 필수 | 설명                                      | 기본값    |
|---------|------|------|-----------------------------------------|--------|
| status  | String | X | 배송 상태 필터 (PENDING,IN_TRANSIT,DELIVERED) | IN_TRANSIT |
| carrier | String | X | 택배사 필터                                  | CJ대한통운 |
| from    | String | X | 조회 시작일 (ISO) — createdAt 기준             | -      |
| to      | String | X | 조회 종료일 (ISO) — createdAt 기준             | -      |
| page    | Integer | X | 페이지 번호 (0부터 시작)                         | 0      |
| size    | Integer | X | 페이지 크기                                  | 20     |

#### 응답 (200 OK)
```json
{
  "userId": 1,
  "items": [
    {
      "shippingId": 1001,
      "orderId": 5001,
      "carrier": "CJ대한통운",
      "trackingNumber": "123456789012",
      "status": "DELIVERED",
      "shippingStartAt": "2025-10-25T10:00:00",
      "estimatedArrivalAt": "2025-10-28T18:00:00",
      "deliveredAt": "2025-10-28T17:50:00",
      "isDelayed": false,
      "createdAt": "2025-10-25T09:50:00",
      "updatedAt": "2025-10-28T17:50:00"
    },
    {
      "shippingId": 1003,
      "orderId": 5005,
      "carrier": "한진택배",
      "trackingNumber": "999999999999",
      "status": "IN_TRANSIT",
      "shippingStartAt": "2025-10-29T14:00:00",
      "estimatedArrivalAt": "2025-11-01T18:00:00",
      "deliveredAt": null,
      "isDelayed": false,
      "createdAt": "2025-10-29T13:50:00",
      "updatedAt": "2025-10-29T14:00:00"
    }
  ],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 2,
    "totalPages": 1
  },
  "summary": {
    "totalCount": 2,
    "pendingCount": 0,
    "inTransitCount": 1,
    "deliveredCount": 1
  }
}
```

### 비즈니스 규칙

| 로직 | 설명 |
|------|------|
| 자동 상태 전환 | 도착 예정일 < 현재일 → `DELIVERED`로 자동 전환 |
| 예외 처리 | 도착 예정일이 이미 지난 경우 생성 불가 |
| 중복 방지 | 주문별 배송 1건 제한, 송장번호 중복 불가 |
| 상태 전이 제약 | `PENDING → IN_TRANSIT → DELIVERED` 순서만 허용 |
| 지연 처리 | `estimatedArrivalAt`보다 늦게 도착 시 `isDelayed = true` 표시 |

---

## 주요 에러 코드 목록
| 에러 코드 | HTTP 상태 | 설명         |
|----------|----------|------------|
| SHIPPING_NOT_FOUND | 404 | 배송 정보를 찾을 수 없음 |
| ORDER_NOT_FOUND | 404 | 주문을 찾을 수 없음 |
| INVALID_ESTIMATED_DATE | 400 | 유효하지 않은 도착 예정일 |
| INVALID_QUANTITY | 400 | 유효하지 않은 수량 |
| DUPLICATE_TRACKING_NUMBER | 400 | 중복된 송장번호 |
| SHIPPING_ALREADY_EXISTS | 409 | 이미 배송 정보 존재 |
| INVALID_STATUS_TRANSITION | 400 | 잘못된 상태 전이 |
| ALREADY_DELIVERED | 410 | 이미 배송 완료됨 |