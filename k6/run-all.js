/**
 * 전체 부하 테스트 실행 스크립트
 *
 * 모든 시나리오를 순차적으로 실행.
 * 각 시나리오는 setup에서 자체적으로 테스트 데이터를 생성.
 *
 * 실행 방법:
 * k6 run k6/run-all.js
 *
 * 시나리오:
 * 1. 인기 상품 조회 (0-40초)
 * 2. 쿠폰 발급 (45-105초)
 * 3. 주문 및 결제 (110-150초)
 */

import { sleep } from 'k6';
import http from 'k6/http';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api';

const HEADERS = {
    'Content-Type': 'application/json',
};

// 커스텀 메트릭
const successfulRankingQueries = new Counter('ranking_successful_queries');
const successfulOrders = new Counter('order_successful_creates');
const successfulPayments = new Counter('payment_successful_processes');
const successfulCouponIssues = new Counter('coupon_successful_issues');
const rankingQueryTime = new Trend('ranking_query_time');
const orderTime = new Trend('order_create_time');
const paymentTime = new Trend('payment_process_time');

export const options = {
    scenarios: {
        // 1. 인기 상품 조회 테스트 (0-40초)
        product_ranking: {
            executor: 'constant-vus',
            exec: 'productRankingTest',
            vus: 50,
            duration: '30s',
            startTime: '0s',
            tags: { scenario: 'product_ranking' },
        },

        // 2. 쿠폰 발급 테스트 (45-105초)
        coupon_issue: {
            executor: 'ramping-vus',
            exec: 'couponIssueTest',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 100 },
                { duration: '30s', target: 100 },
                { duration: '10s', target: 0 },
            ],
            startTime: '45s',
            tags: { scenario: 'coupon_issue' },
        },

        // 3. 주문 및 결제 테스트 (110-150초)
        order_payment: {
            executor: 'constant-vus',
            exec: 'orderPaymentTest',
            vus: 100,
            duration: '30s',
            startTime: '110s',
            tags: { scenario: 'order_payment' },
        },
    },
    thresholds: {
        'http_req_duration{scenario:product_ranking}': ['p(95)<2000'],
        'http_req_duration{scenario:coupon_issue}': ['p(95)<3000'],
        'http_req_duration{scenario:order_payment}': ['p(95)<3000'],
    },
};

// ==================== 공통 함수 ====================

// 사용자 생성
function createUser(balance = 3000000) {
    const url = `${BASE_URL}/users`;
    const payload = JSON.stringify({ balance });

    const response = http.post(url, payload, { headers: HEADERS, tags: { name: 'CreateUser' } });

    if (response.status === 201) {
        try {
            return JSON.parse(response.body).id;
        } catch (e) {
            console.error(`[CreateUser] JSON 파싱 실패: ${e}`);
            return null;
        }
    }
    console.error(`[CreateUser] 실패 - Status: ${response.status}, Body: ${response.body}, URL: ${url}`);
    return null;
}

// 상품 목록 조회
function getProducts() {
    const url = `${BASE_URL}/products?page=0&size=10`;
    const response = http.get(url, { headers: HEADERS, tags: { name: 'GetProducts' } });

    if (response.status === 200) {
        try {
            return JSON.parse(response.body).products || [];
        } catch (e) {
            return [];
        }
    }
    return [];
}

// 사용 가능한 쿠폰 조회
function getAvailableCoupons() {
    const url = `${BASE_URL}/coupons/available`;
    const response = http.get(url, { headers: HEADERS, tags: { name: 'GetCoupons' } });

    if (response.status === 200) {
        try {
            return JSON.parse(response.body).coupons || [];
        } catch (e) {
            return [];
        }
    }
    return [];
}

// ==================== 테스트 데이터 준비 ====================

export function setup() {
    console.log('========================================');
    console.log('🚀 전체 부하 테스트 시작');
    console.log('========================================');
    console.log('1. 인기 상품 조회 (0-40초)');
    console.log('2. 쿠폰 발급 (45-105초)');
    console.log('3. 주문 및 결제 (110-150초)');
    console.log('========================================\n');

    console.log('📋 테스트 데이터 준비 시작...\n');

    // 사용자 생성 (100명)
    const users = [];
    for (let i = 0; i < 100; i++) {
        const userId = createUser();
        if (userId) users.push(userId);
        if ((i + 1) % 25 === 0) {
            console.log(`사용자 생성 진행: ${i + 1}/100`);
        }
    }
    console.log(`✅ 사용자 생성 완료: ${users.length}명\n`);

    // 상품 목록 조회
    const products = getProducts();
    const productIds = products.map((p) => p.id);
    console.log(`✅ 상품 조회 완료: ${productIds.length}개\n`);

    // 쿠폰 목록 조회
    const coupons = getAvailableCoupons();
    const couponId = coupons.length > 0 ? coupons[0].id : null;
    console.log(`✅ 쿠폰 조회 완료: ${coupons.length}개\n`);

    if (users.length === 0) {
        throw new Error('사용자 생성에 실패했습니다. 테스트를 중단합니다.');
    }

    if (productIds.length === 0) {
        throw new Error('상품이 없습니다. 상품을 먼저 생성해주세요.');
    }

    console.log('========================================');
    console.log('✅ 테스트 데이터 준비 완료');
    console.log('========================================\n');

    return { users, productIds, couponId };
}

// ==================== 인기 상품 조회 테스트 ====================

export function productRankingTest(data) {
    const userId = data.users[__VU % data.users.length];
    const productId = data.productIds[Math.floor(Math.random() * data.productIds.length)];

    // 주문 생성 (랭킹 업데이트용)
    const orderUrl = `${BASE_URL}/orders`;
    const orderPayload = JSON.stringify({
        userId: userId,
        items: [{ productId: productId, quantity: Math.floor(Math.random() * 3) + 1 }],
    });

    const orderResponse = http.post(orderUrl, orderPayload, { headers: HEADERS, tags: { name: 'OrderCreate' } });

    if (orderResponse.status === 201) {
        try {
            const orderId = JSON.parse(orderResponse.body).orderId;
            successfulOrders.add(1);

            sleep(0.3);

            // 결제 처리
            const paymentUrl = `${BASE_URL}/payments/orders/${orderId}/payment`;
            const paymentPayload = JSON.stringify({ userId: userId });
            http.post(paymentUrl, paymentPayload, { headers: HEADERS, tags: { name: 'PaymentProcess' } });
        } catch (e) {}
    }

    sleep(0.5);

    // 인기 상품 조회
    const startTime = Date.now();
    const response = http.get(`${BASE_URL}/products/top?days=3&limit=5`, {
        headers: HEADERS,
        tags: { name: 'ProductRanking' },
    });
    const endTime = Date.now();

    rankingQueryTime.add(endTime - startTime);

    if (response.status === 200) {
        successfulRankingQueries.add(1);
    }

    sleep(Math.random() * 2 + 1);
}

// ==================== 쿠폰 발급 테스트 ====================

export function couponIssueTest(data) {
    if (!data.couponId) {
        console.warn('쿠폰이 없습니다. 스킵합니다.');
        sleep(1);
        return;
    }

    const userId = data.users[__VU % data.users.length];

    const url = `${BASE_URL}/coupons/${data.couponId}/issue`;
    const payload = JSON.stringify({ userId: userId });

    const response = http.post(url, payload, { headers: HEADERS, tags: { name: 'CouponIssue' } });

    if (response.status === 201) {
        successfulCouponIssues.add(1);
    }

    sleep(Math.random() * 0.5);
}

// ==================== 주문 및 결제 테스트 ====================

export function orderPaymentTest(data) {
    const userId = data.users[__VU % data.users.length];
    const productId = data.productIds[Math.floor(Math.random() * data.productIds.length)];

    // 1. 주문 생성
    const orderUrl = `${BASE_URL}/orders`;
    const orderPayload = JSON.stringify({
        userId: userId,
        items: [{ productId: productId, quantity: Math.floor(Math.random() * 2) + 1 }],
    });

    const startOrderTime = Date.now();
    const orderResponse = http.post(orderUrl, orderPayload, { headers: HEADERS, tags: { name: 'OrderCreate' } });
    const endOrderTime = Date.now();

    orderTime.add(endOrderTime - startOrderTime);

    if (orderResponse.status !== 201) {
        sleep(1);
        return;
    }

    let orderId;
    try {
        orderId = JSON.parse(orderResponse.body).orderId;
        successfulOrders.add(1);
    } catch (e) {
        sleep(1);
        return;
    }

    sleep(0.5);

    // 2. 결제 처리
    const paymentUrl = `${BASE_URL}/payments/orders/${orderId}/payment`;
    const paymentPayload = JSON.stringify({ userId: userId });

    const startPaymentTime = Date.now();
    const paymentResponse = http.post(paymentUrl, paymentPayload, {
        headers: HEADERS,
        tags: { name: 'PaymentProcess' },
    });
    const endPaymentTime = Date.now();

    paymentTime.add(endPaymentTime - startPaymentTime);

    if (paymentResponse.status === 200) {
        successfulPayments.add(1);
    }

    sleep(Math.random() * 2 + 1);
}

// ==================== 테스트 종료 ====================

export function teardown(data) {
    console.log('\n========================================');
    console.log('✅ 모든 부하 테스트 완료');
    console.log('========================================\n');
}

// 테스트 완료 후 요약 출력
export function handleSummary(data) {
    const summary = {
        '랭킹 조회 성공': data.metrics.ranking_successful_queries?.values.count || 0,
        '주문 생성 성공': data.metrics.order_successful_creates?.values.count || 0,
        '결제 처리 성공': data.metrics.payment_successful_processes?.values.count || 0,
        '쿠폰 발급 성공': data.metrics.coupon_successful_issues?.values.count || 0,
        '평균 랭킹 조회 시간 (ms)': data.metrics.ranking_query_time?.values.avg?.toFixed(2) || 'N/A',
        '평균 주문 시간 (ms)': data.metrics.order_create_time?.values.avg?.toFixed(2) || 'N/A',
        '평균 결제 시간 (ms)': data.metrics.payment_process_time?.values.avg?.toFixed(2) || 'N/A',
        'p95 응답 시간 (ms)': data.metrics.http_req_duration?.values['p(95)']?.toFixed(2) || 'N/A',
        'p99 응답 시간 (ms)': data.metrics.http_req_duration?.values['p(99)']?.toFixed(2) || 'N/A',
    };

    console.log('\n========== 전체 부하 테스트 결과 ==========');
    console.log(JSON.stringify(summary, null, 2));
    console.log('==========================================\n');

    return {
        stdout: JSON.stringify(data, null, 2),
    };
}
