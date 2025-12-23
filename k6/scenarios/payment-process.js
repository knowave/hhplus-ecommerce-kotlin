/**
 * 결제 처리 부하 테스트
 *
 * 목적:
 * - Redis 분산락 없이 DB 비관적락으로 잔액 차감 시 성능 측정
 * - 100명이 동시에 결제 처리 시 User 테이블 락 경합 측정
 *
 * 시나리오:
 * - 먼저 주문을 생성하고, 생성된 주문에 대해 결제 처리
 * - 100명의 사용자가 30초 동안 지속적으로 주문 생성 → 결제
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import {
  BASE_URL,
  HEADERS,
  TEST_DATA,
  getVuUserId,
  getRandomProductId,
  logResponse,
} from '../config/config.js';

// 커스텀 메트릭
const successfulPayments = new Counter('payment_successful_processes');
const failedPayments = new Counter('payment_failed_processes');
const paymentErrorRate = new Rate('payment_process_errors');
const paymentTime = new Trend('payment_process_time');

export const options = {
  scenarios: {
    payment_process_load: {
      executor: 'constant-vus',
      vus: 100,              // 100명 동시 사용자
      duration: '30s',       // 30초 동안 실행
    },
  },
  thresholds: {
    'http_req_duration': ['p(95)<3000', 'p(99)<5000'],  // 95%는 3초, 99%는 5초 이내
    'http_req_failed': ['rate<0.3'],                     // 실패율 30% 이하
    'payment_process_errors': ['rate<0.1'],
  },
};

// 주문 생성 함수
function createOrder(userId) {
  const url = `${BASE_URL}/orders`;

  const payload = JSON.stringify({
    userId: userId,
    items: [
      {
        productId: getRandomProductId(),
        quantity: 1,
      },
    ],
  });

  const params = {
    headers: HEADERS,
    tags: { name: 'OrderCreate_ForPayment' },
  };

  const response = http.post(url, payload, params);

  if (response.status === 201) {
    try {
      const body = JSON.parse(response.body);
      return body.orderId;
    } catch (e) {
      console.error('Failed to parse order response:', e);
      return null;
    }
  }

  return null;
}

// 결제 처리 함수
function processPayment(orderId, userId) {
  const url = `${BASE_URL}/orders/${orderId}/payment`;

  const payload = JSON.stringify({
    userId: userId,
  });

  const params = {
    headers: HEADERS,
    tags: { name: 'PaymentProcess' },
  };

  const startTime = Date.now();
  const response = http.post(url, payload, params);
  const endTime = Date.now();

  paymentTime.add(endTime - startTime);

  return response;
}

export default function () {
  const userId = getVuUserId();

  // 1. 주문 생성
  const orderId = createOrder(userId);

  if (!orderId) {
    console.warn('Failed to create order, skipping payment');
    sleep(1);
    return;
  }

  // 잠깐 대기 (주문 생성 후 결제까지의 Think Time)
  sleep(0.5);

  // 2. 결제 처리
  const response = processPayment(orderId, userId);

  // 응답 검증
  const isSuccess = check(response, {
    'status is 200 (success)': (r) => r.status === 200,
    'status is 400 (invalid request)': (r) => r.status === 400,
    'status is 409 (already paid)': (r) => r.status === 409,
    'status is 422 (insufficient balance)': (r) => r.status === 422,
    'has paymentId': (r) => {
      if (r.status === 200) {
        try {
          const body = JSON.parse(r.body);
          return body.paymentId !== undefined;
        } catch (e) {
          return false;
        }
      }
      return true;
    },
  });

  // 메트릭 기록
  if (response.status === 200) {
    successfulPayments.add(1);
  } else if (
    response.status === 400 ||
    response.status === 409 ||
    response.status === 422
  ) {
    failedPayments.add(1);
  } else {
    paymentErrorRate.add(1);
    console.error(`Unexpected error: ${response.status} - ${response.body}`);
  }

  // 로깅
  if (__ITER % 10 === 0) {
    logResponse(response, 'PaymentProcess');
  }

  // Think Time
  sleep(Math.random() * 2 + 1);  // 1-3초
}

// 테스트 완료 후 요약 출력
export function handleSummary(data) {
  const summary = {
    '총 결제 시도': data.metrics.http_reqs.values.count,
    '성공한 결제': data.metrics.payment_successful_processes
      ? data.metrics.payment_successful_processes.values.count
      : 0,
    '실패한 결제': data.metrics.payment_failed_processes
      ? data.metrics.payment_failed_processes.values.count
      : 0,
    '평균 결제 시간 (ms)': data.metrics.payment_process_time
      ? data.metrics.payment_process_time.values.avg.toFixed(2)
      : 'N/A',
    'p95 응답 시간 (ms)': data.metrics.http_req_duration.values['p(95)'].toFixed(2),
    'p99 응답 시간 (ms)': data.metrics.http_req_duration.values['p(99)'].toFixed(2),
  };

  console.log('\n========== 결제 처리 테스트 결과 ==========');
  console.log(JSON.stringify(summary, null, 2));
  console.log('=========================================\n');

  return {
    stdout: JSON.stringify(data, null, 2),
  };
}