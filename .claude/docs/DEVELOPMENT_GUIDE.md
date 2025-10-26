## 개발 진행 순서
1. `application.yml` 파일을 정의.
   2. default API = `/api`
   3. jpa configuration
   4. jwt token 정의 (access token)
2. 각 도메인에 대한 entity 정의 id, createdAt, updatedAt 과 같은 중복되는 column은 `CustomBaseEntity` 를 생성해서 상속받아서 entity를 생성.
```sql
-- 상품 정보
Table products {
  id varchar [pk]
  name varchar
  description text
  price decimal(10,2)
  stock int
  category varchar
  created_at timestamp

  indexes {
    (category)
    (created_at)
  }
}

-- 주문 정보
Table orders {
  id varchar [pk]
  user_id varchar [ref: > users.id]
  total_amount decimal(10,2)
  discount_amount decimal(10,2)
  final_amount decimal(10,2)
  status varchar // PENDING, PAID, CANCELLED
  created_at timestamp
  paid_at timestamp

  indexes {
    (user_id, status)
    (created_at)
  }
}

-- 주문 상세
Table order_items {
  id varchar [pk]
  order_id varchar [ref: > orders.id]
  product_id varchar [ref: > products.id]
  quantity int
  unit_price decimal(10,2)
  subtotal decimal(10,2)

  indexes {
    (order_id)
    (product_id)
  }
}

-- 쿠폰 마스터
Table coupons {
  id varchar [pk]
  name varchar
  discount_rate int // 10, 20, 30 (%)
  total_quantity int
  issued_quantity int
  start_date datetime
  end_date datetime

  indexes {
    (start_date, end_date)
  }
}

-- 사용자 쿠폰
Table user_coupons {
  id varchar [pk]
  user_id varchar [ref: > users.id]
  coupon_id varchar [ref: > coupons.id]
  status varchar // AVAILABLE, USED, EXPIRED
  issued_at timestamp
  used_at timestamp
  expires_at timestamp

  indexes {
    (user_id, status)
    (coupon_id)
    (expires_at)
  }
}

-- 데이터 전송 로그 (Outbox Pattern)
Table data_transmissions {
  id varchar [pk]
  order_id varchar [ref: > orders.id]
  payload json
  status varchar // PENDING, SUCCESS, FAILED
  attempts int
  created_at timestamp
  sent_at timestamp

  indexes {
    (status, created_at)
    (order_id)
  }
} 
```
3. Mock 서버를 구현. 아래는 예시 JSON
```json
{
  "products": [
    {
      "id": "P001",
      "name": "노트북",
      "price": 890000,
      "stock": 10,
      "category": "전자제품"
    },
    {
      "id": "P002",
      "name": "키보드",
      "price": 120000,
      "stock": 50,
      "category": "주변기기"
    }
  ],
  "orders": [],
  "coupons": [
    {
      "id": "COUPON_10",
      "name": "10% 할인쿠폰",
      "discountRate": 10,
      "totalQuantity": 100,
      "issuedQuantity": 0
    }
  ],
  "users": [
    {
      "id": "user1",
      "balance": 1000000
    }
  ]
}
```
