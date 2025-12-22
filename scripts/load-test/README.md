# 부하 테스트용 SQL 스크립트

부하 테스트를 위해 Product 테이블의 인덱스를 제거하고 복구하는 스크립트입니다.

## 📁 파일 구성

- `drop-product-indexes.sql` - 인덱스 제거 (부하 테스트 시작 전)
- `restore-product-indexes.sql` - 인덱스 복구 (부하 테스트 완료 후)

## 🚀 사용 방법

### 1. 부하 테스트 시작 전 - 인덱스 제거

```bash
# MySQL에 접속하여 실행
mysql -u root -p -D hhplus_ecommerce < scripts/load-test/drop-product-indexes.sql

# 또는 MySQL 클라이언트에서
source scripts/load-test/drop-product-indexes.sql;
```

### 2. 부하 테스트 완료 후 - 인덱스 복구

```bash
# MySQL에 접속하여 실행
mysql -u root -p -D hhplus_ecommerce < scripts/load-test/restore-product-indexes.sql

# 또는 MySQL 클라이언트에서
source scripts/load-test/restore-product-indexes.sql;
```

## 📊 제거되는 인덱스 목록

| 인덱스 이름 | 컬럼 | 용도 |
|---|---|---|
| `idx_product_category` | `category` | 카테고리별 조회 |
| `idx_product_category_sales` | `category, sales_count DESC` | 카테고리별 인기 상품 |
| `idx_product_category_price` | `category, price` | 카테고리별 가격 검색 |
| `idx_product_stock` | `stock` | 재고 필터링 |
| `idx_product_sales_count` | `sales_count DESC` | 전체 인기 상품 조회 |

## ⚠️ 주의사항

1. **테스트 환경에서만 실행**
   - 운영 환경에서는 절대 실행하지 마세요!
   - 성능이 크게 저하됩니다.

2. **데이터베이스 백업**
   - 실행 전 데이터베이스를 백업하세요.

3. **인덱스 복구 필수**
   - 테스트 완료 후 반드시 인덱스를 복구하세요.
   - 복구하지 않으면 애플리케이션 성능이 저하됩니다.

## 🔍 인덱스 상태 확인

```sql
-- 현재 Product 테이블의 인덱스 확인
SELECT
    INDEX_NAME,
    COLUMN_NAME,
    SEQ_IN_INDEX
FROM
    INFORMATION_SCHEMA.STATISTICS
WHERE
    TABLE_SCHEMA = 'hhplus_ecommerce'
    AND TABLE_NAME = 'product'
ORDER BY
    INDEX_NAME, SEQ_IN_INDEX;
```

## 📈 기대 효과

인덱스 제거 시:
- Product 조회 쿼리가 Full Table Scan으로 실행됨
- 응답 시간이 10배 이상 증가 예상
- CPU 사용률 증가
- 인덱스의 중요성을 정량적으로 측정 가능