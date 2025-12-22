/**
 * K6 부하 테스트 공통 설정
 */

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api';

// 테스트 사용자 설정
export const TEST_USERS = {
  START_USER_ID: 1,
  TOTAL_USERS: 100,
};

// 테스트 데이터
export const TEST_DATA = {
  COUPON_ID: '550e8400-e29b-41d4-a716-446655440001', // 테스트용 쿠폰 ID (UUID)
  PRODUCT_IDS: [
    '550e8400-e29b-41d4-a716-446655440011',
    '550e8400-e29b-41d4-a716-446655440012',
    '550e8400-e29b-41d4-a716-446655440013',
  ],
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

// 랜덤 사용자 ID 생성
export function getRandomUserId() {
  return (
    Math.floor(Math.random() * TEST_USERS.TOTAL_USERS) + TEST_USERS.START_USER_ID
  ).toString();
}

// 랜덤 상품 ID 선택
export function getRandomProductId() {
  const index = Math.floor(Math.random() * TEST_DATA.PRODUCT_IDS.length);
  return TEST_DATA.PRODUCT_IDS[index];
}