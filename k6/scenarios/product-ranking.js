/**
 * 인기 상품 조회 부하 테스트
 *
 * 목적:
 * - DB 기반 인기 상품 랭킹 조회 성능 측정
 * - 주문 후 랭킹 조회 플로우 테스트
 *
 * 시나리오:
 * - setup에서 50명의 사용자 생성 (API 호출)
 * - 상품 목록 조회 후 랜덤 상품 주문 및 결제
 * - 인기 상품 Top 조회
 * - 50명의 사용자가 랜덤 상품을 주문하고 조회
 */

import { check, sleep } from 'k6';
import http from 'k6/http';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api';

const HEADERS = {
    'Content-Type': 'application/json',
};

// 커스텀 메트릭
const successfulRankingQueries = new Counter('ranking_successful_queries');
const successfulOrders = new Counter('ranking_order_creates');
const errorRate = new Rate('product_ranking_errors');
const rankingQueryTime = new Trend('ranking_query_time');

export const options = {
    scenarios: {
        product_ranking_load: {
            executor: 'constant-vus',
            vus: 50, // 50명 동시 사용자
            duration: '30s', // 30초 동안 실행
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<2000', 'p(99)<5000'],
        http_req_failed: ['rate<0.1'],
        product_ranking_errors: ['rate<0.05'],
    },
};

// 사용자 생성
function createUser() {
    const url = `${BASE_URL}/users`;
    const payload = JSON.stringify({
        balance: 3000000, // 300만원
    });

    const response = http.post(url, payload, { headers: HEADERS, tags: { name: 'CreateUser' } });

    if (response.status === 201) {
        try {
            const body = JSON.parse(response.body);
            return body.id;
        } catch (e) {
            console.error('Failed to parse user response:', e);
            return null;
        }
    }
    console.error(`Failed to create user: ${response.status} - ${response.body}`);
    return null;
}

// 상품 목록 조회
function getProducts() {
    const url = `${BASE_URL}/products?page=0&size=10`;
    const response = http.get(url, { headers: HEADERS, tags: { name: 'GetProducts' } });

    if (response.status === 200) {
        try {
            const body = JSON.parse(response.body);
            return body.products || [];
        } catch (e) {
            console.error('Failed to parse products response:', e);
            return [];
        }
    }
    console.error(`Failed to get products: ${response.status} - ${response.body}`);
    return [];
}

// 주문 생성
function createOrder(userId, productId) {
    const url = `${BASE_URL}/orders`;

    const payload = JSON.stringify({
        userId: userId,
        items: [
            {
                productId: productId,
                quantity: Math.floor(Math.random() * 3) + 1, // 1-3개
            },
        ],
    });

    const response = http.post(url, payload, { headers: HEADERS, tags: { name: 'OrderCreate' } });

    if (response.status === 201) {
        try {
            const body = JSON.parse(response.body);
            return body.orderId;
        } catch (e) {
            return null;
        }
    }
    return null;
}

// 결제 처리
function processPayment(orderId, userId) {
    const url = `${BASE_URL}/orders/${orderId}/payment`;

    const payload = JSON.stringify({
        userId: userId,
    });

    const response = http.post(url, payload, { headers: HEADERS, tags: { name: 'PaymentProcess' } });

    return response.status === 200;
}

// 인기 상품 Top 조회
function getTopProducts(days = 3, limit = 5) {
    const url = `${BASE_URL}/products/top?days=${days}&limit=${limit}`;

    const startTime = Date.now();
    const response = http.get(url, { headers: HEADERS, tags: { name: 'ProductRanking' } });
    const endTime = Date.now();

    rankingQueryTime.add(endTime - startTime);

    return response;
}

// 테스트 데이터 준비 (setup)
export function setup() {
    console.log('========================================');
    console.log('📊 인기 상품 조회 테스트 데이터 준비 시작');
    console.log('========================================');

    // 50명의 사용자 생성
    const users = [];
    for (let i = 0; i < 50; i++) {
        const userId = createUser();
        if (userId) {
            users.push(userId);
        }
        if ((i + 1) % 10 === 0) {
            console.log(`사용자 생성 진행: ${i + 1}/50`);
        }
    }
    console.log(`✅ 사용자 생성 완료: ${users.length}명`);

    // 상품 목록 조회
    const products = getProducts();
    const productIds = products.map((p) => p.id);
    console.log(`✅ 상품 조회 완료: ${productIds.length}개`);

    if (users.length === 0) {
        throw new Error('사용자 생성에 실패했습니다. 테스트를 중단합니다.');
    }

    if (productIds.length === 0) {
        throw new Error('상품이 없습니다. 상품을 먼저 생성해주세요.');
    }

    console.log('========================================');
    console.log('✅ 테스트 데이터 준비 완료');
    console.log('========================================\n');

    return { users, productIds };
}

// 메인 테스트 함수
export default function (data) {
    const userId = data.users[__VU % data.users.length];
    const productId = data.productIds[Math.floor(Math.random() * data.productIds.length)];

    // 1. 상품 주문 및 결제 (랭킹 업데이트를 위해)
    const orderId = createOrder(userId, productId);

    if (orderId) {
        successfulOrders.add(1);
        sleep(0.3);
        processPayment(orderId, userId);
    }

    sleep(0.5);

    // 2. 인기 상품 Top 조회
    const response = getTopProducts(3, 5);

    // 응답 검증
    const success = check(response, {
        'status is 200': (r) => r.status === 200,
        'response time < 2000ms': (r) => r.timings.duration < 2000,
        'has rankings': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.rankings && body.rankings.length > 0;
            } catch (e) {
                return false;
            }
        },
    });

    if (success) {
        successfulRankingQueries.add(1);
    } else {
        errorRate.add(1);
    }

    // Think Time
    sleep(Math.random() * 2 + 1); // 1-3초
}

// 테스트 완료 후 요약 출력
export function handleSummary(data) {
    const summary = {
        '총 랭킹 조회': data.metrics.ranking_successful_queries?.values.count || 0,
        '총 주문 생성': data.metrics.ranking_order_creates?.values.count || 0,
        '평균 랭킹 조회 시간 (ms)': data.metrics.ranking_query_time?.values.avg?.toFixed(2) || 'N/A',
        'p95 응답 시간 (ms)': data.metrics.http_req_duration?.values['p(95)']?.toFixed(2) || 'N/A',
        'p99 응답 시간 (ms)': data.metrics.http_req_duration?.values['p(99)']?.toFixed(2) || 'N/A',
        에러율: `${((data.metrics.product_ranking_errors?.values.rate || 0) * 100).toFixed(2)}%`,
    };

    console.log('\n========== 인기 상품 조회 테스트 결과 ==========');
    console.log(JSON.stringify(summary, null, 2));
    console.log('===============================================\n');

    return {
        stdout: JSON.stringify(data, null, 2),
    };
}
