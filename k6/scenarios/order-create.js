/**
 * 주문 생성 부하 테스트
 *
 * 목적:
 * - Redis 분산락 없이 DB 비관적락으로 재고 차감 시 성능 측정
 * - 100명이 동시에 주문 생성 시 트랜잭션 경합 및 락 대기 시간 측정
 *
 * 시나리오:
 * - 100명의 사용자가 동시에 주문 생성
 * - 각 주문은 1-3개의 상품 포함
 * - 30초 동안 지속적으로 주문 생성
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import {
  BASE_URL,
  HEADERS,
  TEST_DATA,
  getRandomUserId,
  getRandomProductId,
  logResponse,
} from '../config/config.js';

// 커스텀 메트릭
const successfulOrders = new Counter('order_successful_creates');
const failedOrders = new Counter('order_failed_creates');
const orderErrorRate = new Rate('order_create_errors');
const transactionTime = new Trend('order_transaction_time');

export const options = {
  scenarios: {
    order_create_load: {
      executor: 'constant-vus',
      vus: 100,              // 100명 동시 사용자
      duration: '30s',       // 30초 동안 실행
    },
  },
  thresholds: {
    'http_req_duration': ['p(95)<3000', 'p(99)<5000'],  // 95%는 3초, 99%는 5초 이내
    'http_req_failed': ['rate<0.3'],                     // 실패율 30% 이하
    'order_create_errors': ['rate<0.1'],
  },
};

// 랜덤 주문 아이템 생성
function generateOrderItems() {
  const itemCount = Math.floor(Math.random() * 3) + 1;  // 1-3개
  const items = [];

  for (let i = 0; i < itemCount; i++) {
    items.push({
      productId: getRandomProductId(),
      quantity: Math.floor(Math.random() * 3) + 1,  // 1-3개
    });
  }

  return items;
}

export default function () {
  const userId = getRandomUserId();

  // 주문 생성 요청
  const url = `${BASE_URL}/orders`;

  const payload = JSON.stringify({
    userId: userId,
    items: generateOrderItems(),
    // couponId: null,  // 쿠폰 미사용
  });

  const params = {
    headers: HEADERS,
    tags: { name: 'OrderCreate' },
  };

  const startTime = Date.now();
  const response = http.post(url, payload, params);
  const endTime = Date.now();

  // 트랜잭션 시간 측정
  transactionTime.add(endTime - startTime);

  // 응답 검증
  const isSuccess = check(response, {
    'status is 201 (success)': (r) => r.status === 201,
    'status is 400 (invalid request)': (r) => r.status === 400,
    'status is 409 (insufficient stock)': (r) => r.status === 409,
    'has orderId': (r) => {
      if (r.status === 201) {
        try {
          const body = JSON.parse(r.body);
          return body.orderId !== undefined;
        } catch (e) {
          return false;
        }
      }
      return true;
    },
  });

  // 메트릭 기록
  if (response.status === 201) {
    successfulOrders.add(1);
  } else if (response.status === 400 || response.status === 409) {
    failedOrders.add(1);
  } else {
    orderErrorRate.add(1);
    console.error(`Unexpected error: ${response.status} - ${response.body}`);
  }

  // 로깅
  if (__ITER % 10 === 0) {
    logResponse(response, 'OrderCreate');
  }

  // Think Time
  sleep(Math.random() * 2 + 1);  // 1-3초
}

// 테스트 완료 후 요약 출력
export function handleSummary(data) {
  const summary = {
    '총 주문 생성 시도': data.metrics.http_reqs.values.count,
    '성공한 주문': data.metrics.order_successful_creates
      ? data.metrics.order_successful_creates.values.count
      : 0,
    '실패한 주문': data.metrics.order_failed_creates
      ? data.metrics.order_failed_creates.values.count
      : 0,
    '평균 트랜잭션 시간 (ms)': data.metrics.order_transaction_time
      ? data.metrics.order_transaction_time.values.avg.toFixed(2)
      : 'N/A',
    'p95 응답 시간 (ms)': data.metrics.http_req_duration.values['p(95)'].toFixed(2),
    'p99 응답 시간 (ms)': data.metrics.http_req_duration.values['p(99)'].toFixed(2),
  };

  console.log('\n========== 주문 생성 테스트 결과 ==========');
  console.log(JSON.stringify(summary, null, 2));
  console.log('=========================================\n');

  return {
    stdout: JSON.stringify(data, null, 2),
  };
}