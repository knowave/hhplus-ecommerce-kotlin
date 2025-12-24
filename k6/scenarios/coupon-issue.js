/**
 * 선착순 쿠폰 발급 부하 테스트
 *
 * 목적:
 * - 비관적 락(Pessimistic Lock) 기반 동시성 제어 성능 측정
 * - 100명이 한정된 쿠폰을 선착순으로 받을 때 DB Lock 경합 측정
 *
 * 시나리오:
 * - setup에서 100명의 사용자 생성 (API 호출)
 * - 사용 가능한 쿠폰 목록 조회
 * - 100명의 사용자가 동시에 쿠폰 발급 요청
 * - 10초에 걸쳐 100명까지 증가 → 30초 동안 유지
 */

import { check, sleep } from 'k6';
import http from 'k6/http';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api';

const HEADERS = {
    'Content-Type': 'application/json',
};

// 커스텀 메트릭
const successfulIssues = new Counter('coupon_successful_issues');
const failedIssues = new Counter('coupon_failed_issues');
const issueErrorRate = new Rate('coupon_issue_errors');
const lockWaitTime = new Trend('lock_wait_time');

export const options = {
    scenarios: {
        coupon_issue_spike: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 100 }, // 10초에 걸쳐 100명까지 증가
                { duration: '30s', target: 100 }, // 30초 동안 100명 유지
                { duration: '10s', target: 0 }, // 10초에 걸쳐 0명으로 감소
            ],
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<3000'],
        http_req_failed: ['rate<0.6'], // 한정 수량이므로 실패율 높을 수 있음
        coupon_issue_errors: ['rate<0.1'],
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

// 사용 가능한 쿠폰 목록 조회
function getAvailableCoupons() {
    const url = `${BASE_URL}/coupons/available`;
    const response = http.get(url, { headers: HEADERS, tags: { name: 'GetCoupons' } });

    if (response.status === 200) {
        try {
            const body = JSON.parse(response.body);
            return body.coupons || [];
        } catch (e) {
            console.error('Failed to parse coupons response:', e);
            return [];
        }
    }
    console.error(`Failed to get coupons: ${response.status} - ${response.body}`);
    return [];
}

// 쿠폰 발급 요청
function issueCoupon(couponId, userId) {
    const url = `${BASE_URL}/coupons/${couponId}/issue`;

    const payload = JSON.stringify({
        userId: userId,
    });

    const startTime = Date.now();
    const response = http.post(url, payload, { headers: HEADERS, tags: { name: 'CouponIssue' } });
    const endTime = Date.now();

    lockWaitTime.add(endTime - startTime);

    return response;
}

// 테스트 데이터 준비 (setup)
export function setup() {
    console.log('========================================');
    console.log('🎫 쿠폰 발급 테스트 데이터 준비 시작');
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

    // 사용 가능한 쿠폰 목록 조회
    const coupons = getAvailableCoupons();
    console.log(`✅ 쿠폰 조회 완료: ${coupons.length}개`);

    if (users.length === 0) {
        throw new Error('사용자 생성에 실패했습니다. 테스트를 중단합니다.');
    }

    if (coupons.length === 0) {
        throw new Error('사용 가능한 쿠폰이 없습니다. 쿠폰을 먼저 생성해주세요.');
    }

    // 첫 번째 쿠폰 사용 (또는 랜덤 선택)
    const targetCouponId = coupons[0].id;
    console.log(`🎯 테스트 대상 쿠폰: ${targetCouponId}`);
    console.log(`   - 이름: ${coupons[0].name}`);
    console.log(`   - 할인율: ${coupons[0].discountRate}%`);
    console.log(`   - 남은 수량: ${coupons[0].remainingQuantity}`);

    console.log('========================================');
    console.log('✅ 테스트 데이터 준비 완료');
    console.log('========================================\n');

    return { users, couponId: targetCouponId };
}

// 메인 테스트 함수
export default function (data) {
    const userId = data.users[__VU % data.users.length];

    // 쿠폰 발급 요청
    const response = issueCoupon(data.couponId, userId);

    // 응답 검증
    check(response, {
        'status is 201 (success)': (r) => r.status === 201,
        'status is 409 (sold out)': (r) => r.status === 409,
        'status is 400 (already issued)': (r) => r.status === 400,
    });

    // 메트릭 기록
    if (response.status === 201) {
        successfulIssues.add(1);
    } else if (response.status === 409 || response.status === 400) {
        failedIssues.add(1); // 품절/중복은 정상적인 실패
    } else {
        issueErrorRate.add(1);
        console.error(`Unexpected error: ${response.status} - ${response.body}`);
    }

    // Think Time
    sleep(Math.random() * 0.5); // 0-0.5초 (빠른 요청 시뮬레이션)
}

// 테스트 완료 후 요약 출력
export function handleSummary(data) {
    const summary = {
        '총 쿠폰 발급 시도': data.metrics.http_reqs?.values.count || 0,
        '성공한 발급': data.metrics.coupon_successful_issues?.values.count || 0,
        '실패한 발급 (품절/중복)': data.metrics.coupon_failed_issues?.values.count || 0,
        '평균 응답 시간 (ms)': data.metrics.http_req_duration?.values.avg?.toFixed(2) || 'N/A',
        'p95 응답 시간 (ms)': data.metrics.http_req_duration?.values['p(95)']?.toFixed(2) || 'N/A',
        'p99 응답 시간 (ms)': data.metrics.http_req_duration?.values['p(99)']?.toFixed(2) || 'N/A',
    };

    console.log('\n========== 쿠폰 발급 테스트 결과 ==========');
    console.log(JSON.stringify(summary, null, 2));
    console.log('==========================================\n');

    return {
        stdout: JSON.stringify(data, null, 2),
    };
}
