# Product API 문서

## 개요
상품 조회, 재고 확인, 인기 상품 통계 API를 제공합니다.

## 기본 경로
```
/api/products
```

---

## API 목록

### 1. 상품 목록 조회
**전체 상품 목록을 조회합니다. 필터링 및 정렬 옵션을 제공합니다.**

#### 엔드포인트
```
GET /api/products
```

#### 쿼리 파라미터
| 파라미터 | 타입 | 필수 | 설명 | 기본값 |
|---------|------|------|------|-------|
| category | String | X | 상품 카테고리 필터 | - |
| sort | String | X | 정렬 기준 (price, popularity, newest) | newest |
| page | Integer | X | 페이지 번호 (0부터 시작) | 0 |
| size | Integer | X | 페이지 크기 | 20 |

#### 응답 (200 OK)
```json
{
  "products": [
    {
      "productId": 1,
      "name": "노트북 ABC",
      "description": "고성능 노트북",
      "price": 1500000,
      "stock": 50,
      "category": "ELECTRONICS",
      "createdAt": "2025-10-01T00:00:00",
      "updatedAt": "2025-10-29T10:00:00"
    }
  ],
  "pagination": {
    "currentPage": 0,
    "totalPages": 5,
    "totalElements": 100,
    "size": 20,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

#### 응답 필드
| 필드 | 타입 | 설명 |
|------|------|------|
| products | Array | 상품 목록 |
| products[].productId | Long | 상품 ID |
| products[].name | String | 상품명 |
| products[].description | String | 상품 설명 |
| products[].price | Long | 상품 가격 |
| products[].stock | Integer | 재고 수량 |
| products[].category | String | 상품 카테고리 |
| pagination | Object | 페이지네이션 정보 |

#### 정렬 옵션
- `price`: 가격 낮은 순
- `popularity`: 인기도 높은 순 (판매량 기준)
- `newest`: 최신 등록 순 (기본값)

---

### 2. 상품 상세 조회
**특정 상품의 상세 정보를 조회합니다.**

#### 엔드포인트
```
GET /api/products/{productId}
```

#### 경로 파라미터
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| productId | Long | O | 상품 ID |

#### 응답 (200 OK)
```json
{
  "productId": 1,
  "name": "노트북 ABC",
  "description": "고성능 노트북으로 멀티태스킹에 최적화되어 있습니다.",
  "price": 1500000,
  "stock": 50,
  "category": "ELECTRONICS",
  "specifications": {
    "cpu": "Intel i7",
    "ram": "16GB",
    "storage": "512GB SSD"
  },
  "salesCount": 150,
  "createdAt": "2025-10-01T00:00:00",
  "updatedAt": "2025-10-29T10:00:00"
}
```

#### 응답 필드
| 필드 | 타입 | 설명 |
|------|------|------|
| productId | Long | 상품 ID |
| name | String | 상품명 |
| description | String | 상품 상세 설명 |
| price | Long | 상품 가격 |
| stock | Integer | 현재 재고 수량 |
| category | String | 상품 카테고리 |
| specifications | Object | 상품 스펙 (카테고리별 상이) |
| salesCount | Integer | 총 판매 수량 |
| createdAt | String | 등록 시간 |
| updatedAt | String | 마지막 업데이트 시간 |

#### 에러 응답
| 상태 코드 | 에러 코드 | 설명 |
|----------|----------|------|
| 404 | PRODUCT_NOT_FOUND | 상품을 찾을 수 없음 |

---

### 3. 인기 상품 조회 (Top 5)
**최근 3일간 판매량이 가장 많은 상위 5개 상품을 조회합니다.**

#### 엔드포인트
```
GET /api/products/top
```

#### 쿼리 파라미터
| 파라미터 | 타입 | 필수 | 설명 | 기본값 |
|---------|------|------|------|-------|
| days | Integer | X | 집계 기간 (일) | 3 |
| limit | Integer | X | 조회할 상품 수 | 5 |

#### 응답 (200 OK)
```json
{
  "period": {
    "days": 3,
    "startDate": "2025-10-26T00:00:00",
    "endDate": "2025-10-29T23:59:59"
  },
  "products": [
    {
      "rank": 1,
      "productId": 15,
      "name": "무선 이어폰 XYZ",
      "price": 150000,
      "category": "ELECTRONICS",
      "salesCount": 250,
      "revenue": 37500000,
      "stock": 80
    },
    {
      "rank": 2,
      "productId": 7,
      "name": "운동화 ABC",
      "price": 89000,
      "category": "FASHION",
      "salesCount": 180,
      "revenue": 16020000,
      "stock": 45
    }
  ]
}
```

#### 응답 필드
| 필드 | 타입 | 설명 |
|------|------|------|
| period | Object | 집계 기간 정보 |
| period.days | Integer | 집계 일수 |
| period.startDate | String | 집계 시작 일시 |
| period.endDate | String | 집계 종료 일시 |
| products | Array | 인기 상품 목록 |
| products[].rank | Integer | 순위 (1~5) |
| products[].productId | Long | 상품 ID |
| products[].name | String | 상품명 |
| products[].price | Long | 상품 가격 |
| products[].category | String | 카테고리 |
| products[].salesCount | Integer | 판매 수량 |
| products[].revenue | Long | 총 매출액 |
| products[].stock | Integer | 현재 재고 |

#### 비즈니스 규칙
- 최근 3일간 실제 결제 완료된 주문만 집계 (PAID 상태)
- 동일 판매량일 경우 매출액이 높은 순으로 정렬
- 판매량과 매출액이 모두 동일하면 productId가 낮은 순으로 정렬
- 집계 기간 동안 판매 이력이 없는 상품은 제외

---

### 4. 상품 재고 확인
**특정 상품의 실시간 재고를 확인합니다.**

#### 엔드포인트
```
GET /api/products/{productId}/stock
```

#### 경로 파라미터
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| productId | Long | O | 상품 ID |

#### 응답 (200 OK)
```json
{
  "productId": 1,
  "productName": "노트북 ABC",
  "stock": 50,
  "isAvailable": true,
  "lastUpdatedAt": "2025-10-29T10:30:00"
}
```

#### 응답 필드
| 필드 | 타입 | 설명 |
|------|------|------|
| productId | Long | 상품 ID |
| productName | String | 상품명 |
| stock | Integer | 현재 재고 수량 |
| isAvailable | Boolean | 구매 가능 여부 (stock > 0) |
| lastUpdatedAt | String | 재고 마지막 업데이트 시간 |

#### 에러 응답
| 상태 코드 | 에러 코드 | 설명 |
|----------|----------|------|
| 404 | PRODUCT_NOT_FOUND | 상품을 찾을 수 없음 |

---

## 공통 에러 응답 형식
```json
{
  "timestamp": "2025-10-29T10:30:00",
  "status": 404,
  "error": "Not Found",
  "code": "PRODUCT_NOT_FOUND",
  "message": "상품을 찾을 수 없습니다. (상품 ID: 999)",
  "path": "/api/products/999"
}
```

## 주요 에러 코드 목록
| 에러 코드 | HTTP 상태 | 설명 |
|----------|----------|------|
| PRODUCT_NOT_FOUND | 404 | 상품을 찾을 수 없음 |
| INVALID_CATEGORY | 400 | 유효하지 않은 카테고리 |
| INVALID_SORT_OPTION | 400 | 유효하지 않은 정렬 옵션 |

## 상품 카테고리 목록
- `ELECTRONICS`: 전자제품
- `FASHION`: 의류/패션
- `FOOD`: 식품
- `BOOKS`: 도서
- `HOME`: 생활용품
- `SPORTS`: 스포츠/레저
- `BEAUTY`: 화장품/미용