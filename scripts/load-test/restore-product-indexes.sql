-- =====================================================
-- 부하 테스트 후 인덱스 복구 스크립트
-- =====================================================
-- 목적: Product 테이블의 인덱스를 원상 복구
-- 사용 시점: 부하 테스트 완료 후
-- 주의: JPA 엔티티에 정의된 인덱스와 동일하게 생성

-- 실행 방법:
-- mysql -u <user> -p -D <database> < restore-product-indexes.sql

USE hhplus_ecommerce;

-- 카테고리별 조회 인덱스
CREATE INDEX idx_product_category ON product (category);

-- 카테고리별 인기 상품 조회 인덱스 (복합 인덱스)
CREATE INDEX idx_product_category_sales ON product (category, sales_count DESC);

-- 카테고리별 가격 범위 검색 인덱스 (복합 인덱스)
CREATE INDEX idx_product_category_price ON product (category, price);

-- 재고 있는 상품 필터링 인덱스
CREATE INDEX idx_product_stock ON product (stock);

-- 전체 인기 상품 조회 인덱스 (카테고리 필터 없이)
CREATE INDEX idx_product_sales_count ON product (sales_count DESC);

-- 인덱스 복구 확인
SELECT
    TABLE_NAME,
    INDEX_NAME,
    COLUMN_NAME,
    SEQ_IN_INDEX,
    INDEX_TYPE,
    CARDINALITY
FROM
    INFORMATION_SCHEMA.STATISTICS
WHERE
    TABLE_SCHEMA = 'hhplus_ecommerce'
    AND TABLE_NAME = 'product'
ORDER BY
    INDEX_NAME, SEQ_IN_INDEX;

-- 인덱스 통계 업데이트 (선택사항, 성능 최적화)
ANALYZE TABLE product;

-- 완료 메시지
SELECT '✅ Product 테이블의 모든 인덱스가 복구되었습니다!' AS STATUS;
SELECT '📊 총 5개의 인덱스가 생성되었습니다. (PRIMARY KEY 제외)' AS INFO;