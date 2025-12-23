/**
 * K6 부하 테스트 공통 설정
 */

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api';

// 테스트 데이터
export const TEST_DATA = {
  // 쿠폰 (k6/data/load-test-data.sql과 일치)
  COUPON_ID: '550e8400-e29b-41d4-a716-446655440001',

  // 상품 (k6/data/load-test-data.sql과 일치)
  PRODUCT_IDS: [
    '550e8400-e29b-41d4-a716-446655440011',
    '550e8400-e29b-41d4-a716-446655440012',
    '550e8400-e29b-41d4-a716-446655440013',
  ],

  // 테스트 사용자 UUID 패턴 (100명: ...00 ~ ...99)
  USER_ID_PREFIX: '550e8400-e29b-41d4-a716-44665544',
  TOTAL_USERS: 100,
};

// 공통 헤더
export const HEADERS = {
  'Content-Type': 'application/json',
};

// Threshold 설정 (성능 기준)
export const THRESHOLDS = {
  // 95%의 요청이 2초 이내에 완료
  http_req_duration: ['p(95)<2000'],
  // 실패율 10% 이하
  http_req_failed: ['rate<0.1'],
};

// 로깅 헬퍼
export function logResponse(response, scenario) {
  console.log(
    `[${scenario}] Status: ${response.status}, Duration: ${response.timings.duration}ms`
  );
  if (response.status >= 400) {
    console.error(`[${scenario}] Error: ${response.body}`);
  }
}

// 랜덤 테스트 사용자 ID 반환 (100명 중 랜덤 선택)
export function getRandomTestUserId() {
  const userIndex = Math.floor(Math.random() * TEST_DATA.TOTAL_USERS);
  const paddedIndex = String(userIndex).padStart(2, '0');
  return `${TEST_DATA.USER_ID_PREFIX}00${paddedIndex}`;
}

// 특정 VU에 고정된 사용자 ID 반환 (VU당 1명씩 할당)
export function getVuUserId() {
  const vuIndex = (__VU - 1) % TEST_DATA.TOTAL_USERS;
  const paddedIndex = String(vuIndex).padStart(2, '0');
  return `${TEST_DATA.USER_ID_PREFIX}00${paddedIndex}`;
}

// 랜덤 상품 ID 선택
export function getRandomProductId() {
  const index = Math.floor(Math.random() * TEST_DATA.PRODUCT_IDS.length);
  return TEST_DATA.PRODUCT_IDS[index];
}