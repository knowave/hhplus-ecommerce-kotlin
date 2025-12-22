/**
 * 인기 상품 조회 부하 테스트
 *
 * 목적:
 * - 인덱스 없는 Product 테이블에서 Full Table Scan 부하 측정
 * - salesCount DESC 정렬 쿼리의 성능 측정
 *
 * 시나리오:
 * - 100명의 사용자가 동시에 인기 상품 Top 10 조회
 * - 30초 동안 지속적으로 요청
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';
import { BASE_URL, HEADERS, logResponse } from '../config/config.js';

// 커스텀 메트릭
const errorRate = new Rate('product_ranking_errors');

export const options = {
  scenarios: {
    product_ranking_load: {
      executor: 'constant-vus',
      vus: 100,              // 100명 동시 사용자
      duration: '30s',       // 30초 동안 실행
    },
  },
  thresholds: {
    'http_req_duration': ['p(95)<2000', 'p(99)<5000'],  // 95%는 2초, 99%는 5초 이내
    'http_req_failed': ['rate<0.05'],                    // 실패율 5% 이하
    'product_ranking_errors': ['rate<0.05'],
  },
};

export default function () {
  // 인기 상품 Top 10 조회
  const url = `${BASE_URL}/products/ranking/top`;

  const params = {
    headers: HEADERS,
    tags: { name: 'ProductRanking' },
  };

  const response = http.get(url, params);

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

  // 실패 메트릭 기록
  errorRate.add(!success);

  // 로깅
  if (__ITER % 10 === 0) {  // 10번에 1번씩 로깅
    logResponse(response, 'ProductRanking');
  }

  // Think Time (사용자가 결과를 보는 시간 시뮬레이션)
  sleep(Math.random() * 2 + 1);  // 1-3초
}

// 테스트 완료 후 요약 출력
export function handleSummary(data) {
  return {
    stdout: JSON.stringify(data, null, 2),
  };
}