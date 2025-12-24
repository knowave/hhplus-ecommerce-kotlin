/**
 * 주문 및 결제 통합 부하 테스트
 *
 * 목적:
 * - 주문 생성과 결제 처리를 하나의 플로우로 테스트
 * - 100명의 사용자가 동시에 주문하고 결제하는 시나리오
 *
 * 시나리오:
 * - setup에서 100명의 사용자 생성 (API 호출)
 * - 상품 목록 조회 후 랜덤 상품 선택
 * - 주문 생성 → 결제 처리 통합 플로우
 * - 30초 동안 지속적으로 실행
 */

import { sleep } from 'k6';
import http from 'k6/http';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api';

const HEADERS = {
    'Content-Type': 'application/json',
};

// 커스텀 메트릭
const successfulOrders = new Counter('order_successful_creates');
const failedOrders = new Counter('order_failed_creates');
const successfulPayments = new Counter('payment_successful_processes');
const failedPayments = new Counter('payment_failed_processes');
const orderPaymentErrorRate = new Rate('order_payment_errors');
const orderTime = new Trend('order_create_time');
const paymentTime = new Trend('payment_process_time');

export const options = {
    scenarios: {
        order_payment_load: {
            executor: 'constant-vus',
            vus: 100, // 100명 동시 사용자
            duration: '30s', // 30초 동안 실행
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<3000', 'p(99)<5000'],
        http_req_failed: ['rate<0.3'],
        order_payment_errors: ['rate<0.1'],
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
                quantity: Math.floor(Math.random() * 2) + 1, // 1-2개
            },
        ],
    });

    const startTime = Date.now();
    const response = http.post(url, payload, { headers: HEADERS, tags: { name: 'OrderCreate' } });
    const endTime = Date.now();

    orderTime.add(endTime - startTime);

    if (response.status === 201) {
        successfulOrders.add(1);
        try {
            const body = JSON.parse(response.body);
            return body.orderId;
        } catch (e) {
            console.error('Failed to parse order response:', e);
            return null;
        }
    }

    failedOrders.add(1);
    if (response.status !== 409) {
        // 재고 부족은 정상적인 실패
        console.error(`Failed to create order: ${response.status} - ${response.body}`);
    }
    return null;
}

// 결제 처리
function processPayment(orderId, userId) {
    const url = `${BASE_URL}/payments/orders/${orderId}/payment`;

    const payload = JSON.stringify({
        userId: userId,
    });

    const startTime = Date.now();
    const response = http.post(url, payload, { headers: HEADERS, tags: { name: 'PaymentProcess' } });
    const endTime = Date.now();

    paymentTime.add(endTime - startTime);

    if (response.status === 200) {
        successfulPayments.add(1);
        return true;
    }

    failedPayments.add(1);
    if (response.status !== 422) {
        // 잔액 부족은 정상적인 실패
        console.error(`Failed to process payment: ${response.status} - ${response.body}`);
    }
    return false;
}

// 테스트 데이터 준비 (setup)
export function setup() {
    console.log('========================================');
    console.log('🚀 주문 및 결제 테스트 데이터 준비 시작');
    console.log('========================================');

    // 100명의 사용자 생성
    const users = [];
    for (let i = 0; i < 100; i++) {
        const userId = createUser();
        if (userId) {
            users.push(userId);
        }
        if ((i + 1) % 20 === 0) {
            console.log(`사용자 생성 진행: ${i + 1}/100`);
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

    // 1. 주문 생성
    const orderId = createOrder(userId, productId);

    if (!orderId) {
        sleep(1);
        return;
    }

    // Think Time (주문 후 결제까지의 시간)
    sleep(0.5);

    // 2. 결제 처리
    const paymentSuccess = processPayment(orderId, userId);

    if (!paymentSuccess) {
        orderPaymentErrorRate.add(1);
    }

    // Think Time
    sleep(Math.random() * 2 + 1); // 1-3초
}

// 테스트 완료 후 요약 출력
export function handleSummary(data) {
    const summary = {
        '총 주문 시도': data.metrics.order_successful_creates
            ? data.metrics.order_successful_creates.values.count +
              (data.metrics.order_failed_creates?.values.count || 0)
            : 'N/A',
        '성공한 주문': data.metrics.order_successful_creates?.values.count || 0,
        '실패한 주문': data.metrics.order_failed_creates?.values.count || 0,
        '성공한 결제': data.metrics.payment_successful_processes?.values.count || 0,
        '실패한 결제': data.metrics.payment_failed_processes?.values.count || 0,
        '평균 주문 시간 (ms)': data.metrics.order_create_time?.values.avg?.toFixed(2) || 'N/A',
        '평균 결제 시간 (ms)': data.metrics.payment_process_time?.values.avg?.toFixed(2) || 'N/A',
        'p95 응답 시간 (ms)': data.metrics.http_req_duration?.values['p(95)']?.toFixed(2) || 'N/A',
        'p99 응답 시간 (ms)': data.metrics.http_req_duration?.values['p(99)']?.toFixed(2) || 'N/A',
    };

    console.log('\n========== 주문 및 결제 테스트 결과 ==========');
    console.log(JSON.stringify(summary, null, 2));
    console.log('=============================================\n');

    return {
        stdout: JSON.stringify(data, null, 2),
    };
}
