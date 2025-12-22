-- =====================================================
-- 부하 테스트용 인덱스 제거 스크립트
-- =====================================================
-- 목적: Product 테이블의 모든 인덱스를 제거하여 Full Table Scan 유도
-- 사용 시점: 부하 테스트 시작 전
-- 주의: 반드시 테스트 환경에서만 실행할 것!

-- 실행 방법:
-- mysql -u <user> -p -D <database> < drop-product-indexes.sql

USE hhplus_ecommerce;

-- Product 테이블의 인덱스 확인
SELECT
    TABLE_NAME,
    INDEX_NAME,
    COLUMN_NAME,
    SEQ_IN_INDEX,
    INDEX_TYPE
FROM
    INFORMATION_SCHEMA.STATISTICS
WHERE
    TABLE_SCHEMA = 'hhplus_ecommerce'
    AND TABLE_NAME = 'product'
    AND INDEX_NAME != 'PRIMARY'
ORDER BY
    INDEX_NAME, SEQ_IN_INDEX;

-- 카테고리 인덱스 제거
DROP INDEX IF EXISTS idx_product_category ON product;

-- 카테고리별 판매량 복합 인덱스 제거
DROP INDEX IF EXISTS idx_product_category_sales ON product;

-- 카테고리별 가격 복합 인덱스 제거
DROP INDEX IF EXISTS idx_product_category_price ON product;

-- 재고 인덱스 제거
DROP INDEX IF EXISTS idx_product_stock ON product;

-- 판매량 인덱스 제거 (인기 상품 조회용)
DROP INDEX IF EXISTS idx_product_sales_count ON product;

-- 인덱스 제거 확인
SELECT
    TABLE_NAME,
    INDEX_NAME,
    COLUMN_NAME
FROM
    INFORMATION_SCHEMA.STATISTICS
WHERE
    TABLE_SCHEMA = 'hhplus_ecommerce'
    AND TABLE_NAME = 'product'
ORDER BY
    INDEX_NAME;

-- 완료 메시지
SELECT '✅ Product 테이블의 모든 인덱스가 제거되었습니다. (PRIMARY KEY 제외)' AS STATUS;
SELECT '⚠️  이제 Product 조회 시 Full Table Scan이 발생합니다!' AS WARNING;