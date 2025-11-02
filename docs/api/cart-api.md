# Cart API 문서

## 개요
장바구니 관리 API를 제공합니다. 상품 추가, 수량 변경, 삭제 기능을 지원합니다.

## 기본 경로
```
/api/carts
```

---

## API 목록

### 1. 장바구니 조회
**사용자의 현재 장바구니를 조회합니다.**

#### 엔드포인트
```
GET /api/carts/{userId}
```

#### 경로 파라미터
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| userId | Long | O | 사용자 ID |

#### 응답 (200 OK)
```json
{
  "userId": 1,
  "items": [
    {
      "cartItemId": 1,
      "productId": 15,
      "productName": "무선 이어폰 XYZ",
      "price": 150000,
      "quantity": 2,
      "subtotal": 300000,
      "stock": 80,
      "isAvailable": true,
      "addedAt": "2025-10-28T14:30:00"
    },
    {
      "cartItemId": 2,
      "productId": 7,
      "productName": "운동화 ABC",
      "price": 89000,
      "quantity": 1,
      "subtotal": 89000,
      "stock": 0,
      "isAvailable": false,
      "addedAt": "2025-10-29T09:15:00"
    }
  ],
  "summary": {
    "totalItems": 2,
    "totalQuantity": 3,
    "totalAmount": 389000,
    "availableAmount": 300000,
    "unavailableCount": 1
  }
}
```

#### 응답 필드
| 필드 | 타입 | 설명 |
|------|------|------|
| userId | Long | 사용자 ID |
| items | Array | 장바구니 아이템 목록 |
| items[].cartItemId | Long | 장바구니 아이템 ID |
| items[].productId | Long | 상품 ID |
| items[].productName | String | 상품명 |
| items[].price | Long | 상품 단가 |
| items[].quantity | Integer | 수량 |
| items[].subtotal | Long | 소계 (가격 × 수량) |
| items[].stock | Integer | 현재 재고 |
| items[].isAvailable | Boolean | 구매 가능 여부 |
| items[].addedAt | String | 장바구니 추가 시간 |
| summary | Object | 장바구니 요약 정보 |
| summary.totalItems | Integer | 총 아이템 종류 수 |
| summary.totalQuantity | Integer | 총 수량 |
| summary.totalAmount | Long | 총 금액 |
| summary.availableAmount | Long | 구매 가능한 상품 금액 |
| summary.unavailableCount | Integer | 품절 상품 수 |

#### 에러 응답
| 상태 코드 | 에러 코드 | 설명 |
|----------|----------|------|
| 404 | USER_NOT_FOUND | 사용자를 찾을 수 없음 |

---

### 2. 장바구니에 상품 추가
**장바구니에 상품을 추가하거나 기존 상품의 수량을 증가시킵니다.**

#### 엔드포인트
```
POST /api/carts/{userId}/items
```

#### 경로 파라미터
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| userId | Long | O | 사용자 ID |

#### 요청 본문
```json
{
  "productId": 15,
  "quantity": 2
}
```

#### 요청 필드
| 필드 | 타입 | 필수 | 설명 | 제약사항 |
|------|------|------|------|---------|
| productId | Long | O | 상품 ID | 존재하는 상품이어야 함 |
| quantity | Integer | O | 수량 | 1 이상, 100 이하 |

#### 응답 (200 OK)
```json
{
  "cartItemId": 1,
  "productId": 15,
  "productName": "무선 이어폰 XYZ",
  "price": 150000,
  "quantity": 2,
  "subtotal": 300000,
  "addedAt": "2025-10-29T10:30:00"
}
```

#### 응답 필드
| 필드 | 타입 | 설명 |
|------|------|------|
| cartItemId | Long | 장바구니 아이템 ID |
| productId | Long | 상품 ID |
| productName | String | 상품명 |
| price | Long | 상품 단가 |
| quantity | Integer | 수량 |
| subtotal | Long | 소계 |
| addedAt | String | 추가/업데이트 시간 |

#### 비즈니스 규칙
- 동일한 상품을 추가하면 기존 수량에 합산됩니다
- 총 수량은 재고를 초과할 수 없습니다
- 한 상품당 최대 100개까지 담을 수 있습니다
- 품절 상품은 추가할 수 없습니다

#### 에러 응답
| 상태 코드 | 에러 코드 | 설명 |
|----------|----------|------|
| 400 | INVALID_QUANTITY | 유효하지 않은 수량 |
| 400 | EXCEED_STOCK | 재고 부족 |
| 400 | EXCEED_MAX_QUANTITY | 최대 수량 초과 (100개) |
| 404 | PRODUCT_NOT_FOUND | 상품을 찾을 수 없음 |
| 404 | USER_NOT_FOUND | 사용자를 찾을 수 없음 |

---

### 3. 장바구니 아이템 수량 변경
**장바구니 아이템의 수량을 변경합니다.**

#### 엔드포인트
```
PATCH /api/carts/{userId}/items/{cartItemId}
```

#### 경로 파라미터
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| userId | Long | O | 사용자 ID |
| cartItemId | Long | O | 장바구니 아이템 ID |

#### 요청 본문
```json
{
  "quantity": 3
}
```

#### 요청 필드
| 필드 | 타입 | 필수 | 설명 | 제약사항 |
|------|------|------|------|---------|
| quantity | Integer | O | 변경할 수량 | 1 이상, 100 이하 |

#### 응답 (200 OK)
```json
{
  "cartItemId": 1,
  "productId": 15,
  "productName": "무선 이어폰 XYZ",
  "price": 150000,
  "quantity": 3,
  "subtotal": 450000,
  "updatedAt": "2025-10-29T10:35:00"
}
```

#### 비즈니스 규칙
- 변경할 수량은 현재 재고 이하여야 합니다
- 수량을 0으로 변경하면 아이템이 삭제됩니다

#### 에러 응답
| 상태 코드 | 에러 코드 | 설명 |
|----------|----------|------|
| 400 | INVALID_QUANTITY | 유효하지 않은 수량 |
| 400 | EXCEED_STOCK | 재고 부족 |
| 404 | CART_ITEM_NOT_FOUND | 장바구니 아이템을 찾을 수 없음 |
| 403 | FORBIDDEN | 다른 사용자의 장바구니 아이템 |

---

### 4. 장바구니 아이템 삭제
**장바구니에서 특정 아이템을 삭제합니다.**

#### 엔드포인트
```
DELETE /api/carts/{userId}/items/{cartItemId}
```

#### 경로 파라미터
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| userId | Long | O | 사용자 ID |
| cartItemId | Long | O | 장바구니 아이템 ID |

#### 응답 (204 No Content)
내용 없음

#### 에러 응답
| 상태 코드 | 에러 코드 | 설명 |
|----------|----------|------|
| 404 | CART_ITEM_NOT_FOUND | 장바구니 아이템을 찾을 수 없음 |
| 403 | FORBIDDEN | 다른 사용자의 장바구니 아이템 |

---

### 5. 장바구니 전체 비우기
**사용자의 장바구니를 전체 비웁니다.**

#### 엔드포인트
```
DELETE /api/carts/{userId}
```

#### 경로 파라미터
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| userId | Long | O | 사용자 ID |

#### 응답 (204 No Content)
내용 없음

#### 비즈니스 규칙
- 주문 완료 후 자동으로 장바구니가 비워집니다
- 사용자가 직접 전체 삭제를 요청할 수도 있습니다

#### 에러 응답
| 상태 코드 | 에러 코드 | 설명 |
|----------|----------|------|
| 404 | USER_NOT_FOUND | 사용자를 찾을 수 없음 |

---

## 공통 에러 응답 형식
```json
{
  "timestamp": "2025-10-29T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "code": "EXCEED_STOCK",
  "message": "재고가 부족합니다. (요청: 5개, 재고: 3개)",
  "path": "/api/carts/1/items"
}
```

## 주요 에러 코드 목록
| 에러 코드 | HTTP 상태 | 설명 |
|----------|----------|------|
| CART_ITEM_NOT_FOUND | 404 | 장바구니 아이템을 찾을 수 없음 |
| PRODUCT_NOT_FOUND | 404 | 상품을 찾을 수 없음 |
| USER_NOT_FOUND | 404 | 사용자를 찾을 수 없음 |
| INVALID_QUANTITY | 400 | 유효하지 않은 수량 |
| EXCEED_STOCK | 400 | 재고 부족 |
| EXCEED_MAX_QUANTITY | 400 | 최대 수량 초과 |
| FORBIDDEN | 403 | 권한 없음 (다른 사용자의 장바구니) |