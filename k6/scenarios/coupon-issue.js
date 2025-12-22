/**
 * 선착순 쿠폰 발급 부하 테스트
 *
 * 목적:
 * - Redis 분산락 없이 DB 비관적락만으로 동시성 제어 시 성능 측정
 * - 100명이 50개 쿠폰을 선착순으로 받을 때 락 경합 측정
 *
 * 시나리오:
 * - 100명의 사용자가 동시에 쿠폰 발급 요청
 * - 쿠폰 수량: 50개 (절반만 성공)
 * - 10초에 걸쳐 100명까지 증가 → 30초 동안 유지
 */

import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { BASE_URL, HEADERS, TEST_DATA, getRandomUserId, logResponse } from '../config/config.js';

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
        { duration: '10s', target: 100 },  // 10초에 걸쳐 100명까지 증가
        { duration: '30s', target: 100 },  // 30초 동안 100명 유지
        { duration: '10s', target: 0 },    // 10초에 걸쳐 0명으로 감소
      ],
    },
  },
  thresholds: {
    'http_req_duration': ['p(95)<3000'],  // 95%는 3초 이내
    'http_req_failed': ['rate<0.6'],      // 실패율 60% 이하 (50개만 성공하므로)
    'coupon_issue_errors': ['rate<0.1'],  // 에러율 10% 이하 (품절은 정상)
  },
};

export default function () {
  const userId = getRandomUserId();

  // 쿠폰 발급 요청
  const url = `${BASE_URL}/coupons/${TEST_DATA.COUPON_ID}/issue`;

  const payload = JSON.stringify({
    userId: userId,
  });

  const params = {
    headers: HEADERS,
    tags: { name: 'CouponIssue' },
  };

  const startTime = Date.now();
  const response = http.post(url, payload, params);
  const endTime = Date.now();

  // 락 대기 시간 측정 (응답 시간으로 추정)
  lockWaitTime.add(endTime - startTime);

  // 응답 검증
  const isSuccess = check(response, {
    'status is 201 (success)': (r) => r.status === 201,
    'status is 409 (sold out)': (r) => r.status === 409,
    'status is 400 (already issued)': (r) => r.status === 400,
  });

  // 메트릭 기록
  if (response.status === 201) {
    successfulIssues.add(1);
  } else if (response.status === 409 || response.status === 400) {
    failedIssues.add(1);
  } else {
    // 예상치 못한 에러
    issueErrorRate.add(1);
    console.error(`Unexpected error: ${response.status} - ${response.body}`);
  }

  // 로깅
  if (__ITER % 10 === 0) {
    logResponse(response, 'CouponIssue');
  }
}

// 테스트 완료 후 요약 출력
export function handleSummary(data) {
  const summary = {
    '총 쿠폰 발급 시도': data.metrics.http_reqs.values.count,
    '성공한 발급': data.metrics.coupon_successful_issues ? data.metrics.coupon_successful_issues.values.count : 0,
    '실패한 발급 (품절/중복)': data.metrics.coupon_failed_issues ? data.metrics.coupon_failed_issues.values.count : 0,
    '평균 응답 시간 (ms)': data.metrics.http_req_duration.values.avg.toFixed(2),
    'p95 응답 시간 (ms)': data.metrics.http_req_duration.values['p(95)'].toFixed(2),
    'p99 응답 시간 (ms)': data.metrics.http_req_duration.values['p(99)'].toFixed(2),
  };

  console.log('\n========== 쿠폰 발급 테스트 결과 ==========');
  console.log(JSON.stringify(summary, null, 2));
  console.log('==========================================\n');

  return {
    stdout: JSON.stringify(data, null, 2),
  };
}