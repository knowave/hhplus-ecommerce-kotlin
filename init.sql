-- ==============================
-- 이커머스 데이터베이스 초기화
-- ==============================

CREATE DATABASE IF NOT EXISTS hhplus CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ==============================
-- hhplus DB 사용
-- ==============================
USE hhplus;

CREATE TABLE IF NOT EXISTS coupon (
    id CHAR(36) PRIMARY KEY,
    code VARCHAR(255) NOT NULL,
    discount_type VARCHAR(50) NOT NULL,
    discount_value INT NOT NULL,
    min_order_amount INT,
    max_discount_amount INT,
    valid_from DATETIME,
    valid_until DATETIME,
    is_active BOOLEAN,
    issue_limit INT,
    issued_count INT,
    created_at DATETIME,
    updated_at DATETIME
);

CREATE TABLE IF NOT EXISTS product (
    id CHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price INT NOT NULL,
    stock INT NOT NULL,
    category VARCHAR(50),
    sales_count INT,
    created_at DATETIME,
    updated_at DATETIME
);

-- 문자 집합 설정
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 초기 쿠폰 데이터 삽입 (5개)
INSERT INTO coupon (id, code, discount_type, discount_value, min_order_amount, max_discount_amount, valid_from, valid_until, is_active, issue_limit, issued_count, created_at, updated_at) VALUES
(UUID(), 'WELCOME10', 'PERCENTAGE', 10, 0, 10000, NOW(), DATE_ADD(NOW(), INTERVAL 90 DAY), TRUE, 1000, 0, NOW(), NOW()),
(UUID(), 'SUMMER20', 'PERCENTAGE', 20, 10000, 50000, NOW(), DATE_ADD(NOW(), INTERVAL 90 DAY), TRUE, 500, 0, NOW(), NOW()),
(UUID(), 'NEWUSER5000', 'FIXED_AMOUNT', 5000, 20000, NULL, NOW(), DATE_ADD(NOW(), INTERVAL 60 DAY), TRUE, 100, 0, NOW(), NOW()),
(UUID(), 'FREESHIP', 'FIXED_AMOUNT', 3000, 0, 3000, NOW(), DATE_ADD(NOW(), INTERVAL 45 DAY), TRUE, 2000, 0, NOW(), NOW()),
(UUID(), 'VIP30', 'PERCENTAGE', 30, 50000, 100000, NOW(), DATE_ADD(NOW(), INTERVAL 180 DAY), TRUE, 50, 0, NOW(), NOW());

-- 초기 상품 데이터 삽입 (20개)
-- ELECTRONICS (5개)
INSERT INTO product (id, name, description, price, stock, category, sales_count, created_at, updated_at) VALUES
(UUID(), '노트북 Pro 15', '고성능 노트북 15인치', 1800000, 50, 'ELECTRONICS', 180, NOW(), NOW()),
(UUID(), '스마트폰 Galaxy S24', '최신 플래그십 스마트폰', 1300000, 100, 'ELECTRONICS', 250, NOW(), NOW()),
(UUID(), '태블릿 Pro 12.9', '업무용 태블릿', 900000, 40, 'ELECTRONICS', 90, NOW(), NOW()),
(UUID(), '무선 이어폰 Pro', '노이즈 캔슬링 이어폰', 280000, 200, 'ELECTRONICS', 320, NOW(), NOW()),
(UUID(), '스마트워치 Ultra', '건강 관리 워치', 450000, 80, 'ELECTRONICS', 140, NOW(), NOW());

-- FASHION (5개)
INSERT INTO product (id, name, description, price, stock, category, sales_count, created_at, updated_at) VALUES
(UUID(), '러닝화 Ultra', '가벼운 러닝화', 180000, 200, 'FASHION', 130, NOW(), NOW()),
(UUID(), '블랙 청바지 슬림', '슬림핏 청바지', 95000, 150, 'FASHION', 100, NOW(), NOW()),
(UUID(), '가죽 코트 프리미엄', '프리미엄 가죽코트', 380000, 30, 'FASHION', 60, NOW(), NOW()),
(UUID(), '화이트 티셔츠', '베이직 화이트 티셔츠', 35000, 500, 'FASHION', 1200, NOW(), NOW()),
(UUID(), '캐쉬미어 스웨터', '고급 캐쉬미어 스웨터', 250000, 25, 'FASHION', 45, NOW(), NOW());

-- FOOD (5개)
INSERT INTO product (id, name, description, price, stock, category, sales_count, created_at, updated_at) VALUES
(UUID(), '유기농 현미', '국내산 유기농 현미', 48000, 500, 'FOOD', 270, NOW(), NOW()),
(UUID(), '원두 커피 블렌드', '아라비카 원두 커피', 28000, 300, 'FOOD', 190, NOW(), NOW()),
(UUID(), '꿀 1kg', '순수 천연 꿀', 35000, 100, 'FOOD', 85, NOW(), NOW()),
(UUID(), '올리브유 500ml', '스페인산 올리브유', 42000, 80, 'FOOD', 55, NOW(), NOW()),
(UUID(), '초콜릿 세트', '벨기에산 프리미엄 초콜릿', 65000, 60, 'FOOD', 120, NOW(), NOW());

-- BOOKS (5개)
INSERT INTO product (id, name, description, price, stock, category, sales_count, created_at, updated_at) VALUES
(UUID(), '클린 코드', '소프트웨어 품질 개선 가이드', 45000, 200, 'BOOKS', 320, NOW(), NOW()),
(UUID(), '객체 지향의 사실과 오해', '객체 지향 설계의 기초', 40000, 150, 'BOOKS', 280, NOW(), NOW()),
(UUID(), '이펙티브 자바', '자바 프로그래밍의 핵심 가이드', 52000, 100, 'BOOKS', 210, NOW(), NOW()),
(UUID(), '마이크로 서비스 패턴', '마이크로서비스 아키텍처', 55000, 80, 'BOOKS', 95, NOW(), NOW()),
(UUID(), '스프링 인 액션', 'Spring 프레임워크 완벽 가이드', 50000, 120, 'BOOKS', 185, NOW(), NOW());
