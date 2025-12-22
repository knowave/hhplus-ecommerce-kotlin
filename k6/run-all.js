/**
 * 전체 부하 테스트 실행 스크립트
 *
 * 모든 시나리오를 순차적으로 실행.
 * 각 시나리오 사이에 5초의 대기 시간을 둠.
 *
 * 실행 방법:
 * k6 run k6/run-all.js
 */

import { sleep } from 'k6';
import exec from 'k6/execution';

export const options = {
  scenarios: {
    // 1. 인기 상품 조회 테스트 (0-35초)
    product_ranking: {
      executor: 'constant-vus',
      exec: 'productRanking',
      vus: 100,
      duration: '30s',
      startTime: '0s',
      tags: { scenario: 'product_ranking' },
    },

    // 2. 쿠폰 발급 테스트 (40-90초)
    coupon_issue: {
      executor: 'ramping-vus',
      exec: 'couponIssue',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 100 },
        { duration: '30s', target: 100 },
        { duration: '10s', target: 0 },
      ],
      startTime: '40s',
      tags: { scenario: 'coupon_issue' },
    },

    // 3. 주문 생성 테스트 (95-130초)
    order_create: {
      executor: 'constant-vus',
      exec: 'orderCreate',
      vus: 100,
      duration: '30s',
      startTime: '95s',
      tags: { scenario: 'order_create' },
    },

    // 4. 결제 처리 테스트 (135-170초)
    payment_process: {
      executor: 'constant-vus',
      exec: 'paymentProcess',
      vus: 100,
      duration: '30s',
      startTime: '135s',
      tags: { scenario: 'payment_process' },
    },
  },
  thresholds: {
    'http_req_duration{scenario:product_ranking}': ['p(95)<2000'],
    'http_req_duration{scenario:coupon_issue}': ['p(95)<3000'],
    'http_req_duration{scenario:order_create}': ['p(95)<3000'],
    'http_req_duration{scenario:payment_process}': ['p(95)<3000'],
  },
};

import productRankingTest from './scenarios/product-ranking.js';
import couponIssueTest from './scenarios/coupon-issue.js';
import orderCreateTest from './scenarios/order-create.js';
import paymentProcessTest from './scenarios/payment-process.js';

export function productRanking() {
  productRankingTest();
}

export function couponIssue() {
  couponIssueTest();
}

export function orderCreate() {
  orderCreateTest();
}

export function paymentProcess() {
  paymentProcessTest();
}

// 테스트 시작 시 출력
export function setup() {
  console.log('========================================');
  console.log('🚀 부하 테스트 시작');
  console.log('========================================');
  console.log('1. 인기 상품 조회 (0-35초)');
  console.log('2. 쿠폰 발급 (40-90초)');
  console.log('3. 주문 생성 (95-130초)');
  console.log('4. 결제 처리 (135-170초)');
  console.log('========================================\n');
}

// 테스트 완료 시 출력
export function teardown(data) {
  console.log('\n========================================');
  console.log('✅ 모든 부하 테스트 완료');
  console.log('========================================\n');
}