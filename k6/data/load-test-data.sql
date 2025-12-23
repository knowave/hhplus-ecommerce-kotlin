-- ==========================================
-- K6 부하 테스트용 초기 데이터
-- ==========================================
-- Entity 클래스 기반으로 작성된 테스트 데이터
-- ==========================================

-- 기존 테스트 데이터 삭제 (재실행 시 중복 방지)
DELETE FROM user_coupon WHERE coupon_id = UNHEX(REPLACE('550e8400-e29b-41d4-a716-446655440001', '-', ''));
DELETE FROM coupon WHERE id = UNHEX(REPLACE('550e8400-e29b-41d4-a716-446655440001', '-', ''));
DELETE FROM product WHERE id IN (
    UNHEX(REPLACE('550e8400-e29b-41d4-a716-446655440011', '-', '')),
    UNHEX(REPLACE('550e8400-e29b-41d4-a716-446655440012', '-', '')),
    UNHEX(REPLACE('550e8400-e29b-41d4-a716-446655440013', '-', ''))
);

-- 테스트 사용자 100명 삭제 (550e8400-e29b-41d4-a716-4466554400XX 패턴)
DELETE FROM users WHERE id LIKE CONCAT(UNHEX(REPLACE('550e8400-e29b-41d4-a716-44665544', '-', '')), '%');

-- ==========================================
-- 1. 테스트 사용자 생성 (User Entity)
-- ==========================================
-- K6 부하 테스트용 100명의 사용자 생성
-- Entity: User(balance: Long)
-- BaseEntity: id, createdAt, updatedAt

-- 재귀 CTE를 사용하여 100명의 사용자 일괄 생성
INSERT INTO users (id, balance, created_at, updated_at)
WITH RECURSIVE numbers AS (
    SELECT 0 AS n
    UNION ALL
    SELECT n + 1 FROM numbers WHERE n < 99
)
SELECT
    UNHEX(REPLACE(CONCAT('550e8400-e29b-41d4-a716-4466554400', LPAD(n, 2, '0')), '-', '')) AS id,
    1000000 AS balance,
    NOW() AS created_at,
    NOW() AS updated_at
FROM numbers;

-- ==========================================
-- 2. 테스트 쿠폰 생성 (Coupon Entity)
-- ==========================================
-- K6 config.js의 COUPON_ID와 일치
-- Entity: Coupon(name, description, discountRate, totalQuantity, issuedQuantity, startDate, endDate, validityDays)
-- BaseEntity: id, createdAt, updatedAt
INSERT INTO coupon (
    id,
    name,
    description,
    discount_rate,
    total_quantity,
    issued_quantity,
    start_date,
    end_date,
    validity_days,
    created_at,
    updated_at
)
VALUES (
    UNHEX(REPLACE('550e8400-e29b-41d4-a716-446655440001', '-', '')),
    '부하테스트 쿠폰 - DB 비관적 락',
    'Redis/Kafka 없이 순수 DB 비관적 락(PESSIMISTIC_WRITE)으로 동시성 제어 테스트. load-test 프로파일 전용.',
    10,                         -- 10% 할인
    10000,                      -- 총 발급 가능 수량 (10,000개)
    0,                          -- 초기 발급 수량 0
    '2025-12-01 00:00:00',     -- 발급 시작일
    '2025-12-31 23:59:59',     -- 발급 종료일
    30,                         -- 유효 기간 30일
    NOW(),
    NOW()
);

-- ==========================================
-- 3. 테스트 상품 생성 (Product Entity)
-- ==========================================
-- K6 config.js의 PRODUCT_IDS와 일치
-- Entity: Product(name, description, price, stock, category, salesCount)
-- BaseEntity: id, createdAt, updatedAt
-- category: ProductCategory enum (ELECTRONICS, FASHION, FOOD, BOOKS, HOME, SPORTS, BEAUTY)
INSERT INTO product (
    id,
    name,
    description,
    price,
    stock,
    category,
    sales_count,
    created_at,
    updated_at
)
VALUES
    -- 상품 1: 전자제품
    (
        UNHEX(REPLACE('550e8400-e29b-41d4-a716-446655440011', '-', '')),
        '부하테스트 상품 1 - 노트북',
        '고성능 게이밍 노트북 (부하 테스트용)',
        1500000,                -- 1,500,000원
        10000,                  -- 재고 10,000개
        'ELECTRONICS',          -- ProductCategory.ELECTRONICS
        0,                      -- 초기 판매 수량 0
        NOW(),
        NOW()
    ),
    -- 상품 2: 패션
    (
        UNHEX(REPLACE('550e8400-e29b-41d4-a716-446655440012', '-', '')),
        '부하테스트 상품 2 - 겨울 코트',
        '프리미엄 울 코트 (부하 테스트용)',
        300000,                 -- 300,000원
        10000,                  -- 재고 10,000개
        'FASHION',              -- ProductCategory.FASHION
        0,                      -- 초기 판매 수량 0
        NOW(),
        NOW()
    ),
    -- 상품 3: 식품
    (
        UNHEX(REPLACE('550e8400-e29b-41d4-a716-446655440013', '-', '')),
        '부하테스트 상품 3 - 프리미엄 과일 세트',
        '신선한 제철 과일 모음 (부하 테스트용)',
        50000,                  -- 50,000원
        10000,                  -- 재고 10,000개
        'FOOD',                 -- ProductCategory.FOOD
        0,                      -- 초기 판매 수량 0
        NOW(),
        NOW()
    );

-- ==========================================
-- 4. 데이터 생성 검증
-- ==========================================
SELECT '========================================' AS '';
SELECT 'Load Test Data Initialized Successfully!' AS status;
SELECT '========================================' AS '';

-- 사용자 확인 (100명 생성 확인)
SELECT
    CONCAT('Total Users Created: ', COUNT(*), ' users (Expected: 100)') AS user_summary
FROM users
WHERE id >= UNHEX(REPLACE('550e8400-e29b-41d4-a716-446655440000', '-', ''))
  AND id <= UNHEX(REPLACE('550e8400-e29b-41d4-a716-446655440099', '-', ''));

-- 첫 3명과 마지막 1명의 사용자 샘플 출력
SELECT
    CONCAT('Sample User #',
        RIGHT(LPAD(HEX(SUBSTRING(id, 15, 2)), 2, '0'), 2),
        ': ', LOWER(CONCAT(
            HEX(SUBSTRING(id, 1, 4)), '-',
            HEX(SUBSTRING(id, 5, 2)), '-',
            HEX(SUBSTRING(id, 7, 2)), '-',
            HEX(SUBSTRING(id, 9, 2)), '-',
            HEX(SUBSTRING(id, 11, 6))
        )), ' | Balance: ', FORMAT(balance, 0), '원') AS user_sample
FROM users
WHERE id IN (
    UNHEX(REPLACE('550e8400-e29b-41d4-a716-446655440000', '-', '')),
    UNHEX(REPLACE('550e8400-e29b-41d4-a716-446655440001', '-', '')),
    UNHEX(REPLACE('550e8400-e29b-41d4-a716-446655440002', '-', '')),
    UNHEX(REPLACE('550e8400-e29b-41d4-a716-446655440099', '-', ''))
)
ORDER BY id;

-- 쿠폰 확인
SELECT
    CONCAT('Coupon: ', name, ' | Discount: ', discount_rate, '% | Stock: ', (total_quantity - issued_quantity), '/', total_quantity) AS coupon_info
FROM coupon
WHERE id = UNHEX(REPLACE('550e8400-e29b-41d4-a716-446655440001', '-', ''));

-- 상품 확인
SELECT
    CONCAT('Product: ', name, ' | Price: ', FORMAT(price, 0), '원 | Stock: ', FORMAT(stock, 0), '개 | Category: ', category) AS product_info
FROM product
WHERE id IN (
    UNHEX(REPLACE('550e8400-e29b-41d4-a716-446655440011', '-', '')),
    UNHEX(REPLACE('550e8400-e29b-41d4-a716-446655440012', '-', '')),
    UNHEX(REPLACE('550e8400-e29b-41d4-a716-446655440013', '-', ''))
)
ORDER BY name;

SELECT '========================================' AS '';
SELECT 'Ready for K6 Load Testing!' AS status;
SELECT '========================================' AS '';