# K6 부하 테스트 스크립트

순수 DB 환경(Redis, Kafka 비활성화, 인덱스 제거)에서의 성능을 측정하기 위한 K6 부하 테스트 스크립트입니다.

## 📁 디렉토리 구조

```
k6/
├── config/
│   └── config.js              # 공통 설정 (BASE_URL, 테스트 데이터 등)
├── scenarios/
│   ├── product-ranking.js     # 인기 상품 조회 테스트
│   ├── coupon-issue.js        # 선착순 쿠폰 발급 테스트
│   ├── order-create.js        # 주문 생성 테스트
│   └── payment-process.js     # 결제 처리 테스트
├── run-all.js                 # 전체 시나리오 순차 실행
└── README.md                  # 이 파일
```

## 🎯 테스트 시나리오

### 1. 인기 상품 조회 (product-ranking.js)

**목적**: 인덱스 없는 Full Table Scan 부하 측정

- **VU (Virtual Users)**: 100명
- **Duration**: 30초
- **측정 지표**:
  - 응답 시간 (p95 < 2초, p99 < 5초)
  - TPS (Transactions Per Second)
  - 실패율 (< 5%)

```bash
k6 run k6/scenarios/product-ranking.js
```

---

### 2. 선착순 쿠폰 발급 (coupon-issue.js)

**목적**: DB 비관적락만으로 동시성 제어 시 성능 측정

- **VU**: 0 → 100명 (10초에 걸쳐 증가) → 30초 유지
- **쿠폰 수량**: 50개
- **측정 지표**:
  - 락 대기 시간
  - 성공/실패 비율 (50:50 예상)
  - p95 응답 시간 (< 3초)

```bash
k6 run k6/scenarios/coupon-issue.js
```

---

### 3. 주문 생성 (order-create.js)

**목적**: 재고 차감 시 DB 비관적락 경합 측정

- **VU**: 100명
- **Duration**: 30초
- **주문 아이템**: 1-3개 랜덤
- **측정 지표**:
  - 트랜잭션 처리 시간
  - 재고 부족 실패율
  - p95 응답 시간 (< 3초)

```bash
k6 run k6/scenarios/order-create.js
```

---

### 4. 결제 처리 (payment-process.js)

**목적**: 사용자 잔액 차감 시 락 경합 측정

- **VU**: 100명
- **Duration**: 30초
- **프로세스**: 주문 생성 → 결제 처리
- **측정 지표**:
  - 결제 처리 시간
  - 잔액 부족 실패율
  - p95 응답 시간 (< 3초)

```bash
k6 run k6/scenarios/payment-process.js
```

---

## 🚀 실행 방법

### 사전 준비

1. **K6 설치**

```bash
# macOS
brew install k6

# Windows (Chocolatey)
choco install k6

# Linux
sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update
sudo apt-get install k6
```

2. **애플리케이션 실행 (부하 테스트 모드)**

```bash
# 인덱스 제거
mysql -u root -p -D hhplus_ecommerce < scripts/load-test/drop-product-indexes.sql

# 애플리케이션 실행 (load-test 프로파일)
./gradlew bootRun --args='--spring.profiles.active=load-test'
```

3. **테스트 데이터 준비**
   - 사용자 100명 생성 (ID: 1-100)
   - 상품 데이터 충분히 준비
   - 쿠폰 50개 생성
   - 사용자 잔액 충전

---

### 개별 시나리오 실행

```bash
# 1. 인기 상품 조회
k6 run k6/scenarios/product-ranking.js

# 2. 쿠폰 발급
k6 run k6/scenarios/coupon-issue.js

# 3. 주문 생성
k6 run k6/scenarios/order-create.js

# 4. 결제 처리
k6 run k6/scenarios/payment-process.js
```

---

### 전체 시나리오 실행

모든 시나리오를 순차적으로 실행합니다 (총 약 3분 소요).

```bash
k6 run k6/run-all.js
```

**실행 순서**:
1. 인기 상품 조회 (0-35초)
2. 쿠폰 발급 (40-90초)
3. 주문 생성 (95-130초)
4. 결제 처리 (135-170초)

---

## 📊 결과 분석

### K6 출력 지표

```
     execution: local
        script: k6/scenarios/product-ranking.js
        output: -

     scenarios: (100.00%) 1 scenario, 100 max VUs, 1m0s max duration (incl. graceful stop):
              * product_ranking_load: 100 looping VUs for 30s (gracefulStop: 30s)


     ✓ status is 200
     ✓ response time < 2000ms
     ✓ has rankings

     checks.........................: 100.00% ✓ 3000      ✗ 0
     data_received..................: 1.5 MB  50 kB/s
     data_sent......................: 300 kB  10 kB/s
     http_req_blocked...............: avg=1.2ms    min=0s   med=1ms   max=15ms  p(90)=2ms   p(95)=3ms
     http_req_connecting............: avg=500µs    min=0s   med=400µs max=5ms   p(90)=800µs p(95)=1ms
     http_req_duration..............: avg=150ms    min=50ms med=130ms max=800ms p(90)=250ms p(95)=400ms
       { expected_response:true }...: avg=150ms    min=50ms med=130ms max=800ms p(90)=250ms p(95)=400ms
     http_req_failed................: 0.00%   ✓ 0         ✗ 1000
     http_req_receiving.............: avg=50µs     min=20µs med=40µs  max=200µs p(90)=80µs  p(95)=100µs
     http_req_sending...............: avg=30µs     min=10µs med=25µs  max=100µs p(90)=50µs  p(95)=60µs
     http_req_tls_handshaking.......: avg=0s       min=0s   med=0s    max=0s    p(90)=0s    p(95)=0s
     http_req_waiting...............: avg=149ms    min=49ms med=129ms max=799ms p(90)=249ms p(95)=399ms
     http_reqs......................: 1000    33.33/s
     iteration_duration.............: avg=3s       min=1.5s med=2.8s  max=5s    p(90)=4s    p(95)=4.5s
     iterations.....................: 1000    33.33/s
     vus............................: 100     min=100     max=100
     vus_max........................: 100     min=100     max=100
```

### 주요 지표 설명

| 지표 | 설명 |
|---|---|
| `http_req_duration` | HTTP 요청 총 시간 (전송 + 대기 + 수신) |
| `http_req_waiting` | 서버 처리 시간 (응답 대기 시간) |
| `http_reqs` | 총 요청 수 및 RPS (Requests Per Second) |
| `http_req_failed` | 실패한 요청 비율 |
| `p(95), p(99)` | 95%, 99% 백분위수 응답 시간 |
| `checks` | 검증 통과율 |

---

## ⚙️ 환경 변수

BASE_URL을 환경 변수로 설정할 수 있습니다.

```bash
# 기본값: http://localhost:8080/api
k6 run k6/scenarios/product-ranking.js

# 커스텀 URL
BASE_URL=http://192.168.1.100:8080/api k6 run k6/scenarios/product-ranking.js
```

---

## 📝 테스트 데이터 설정

`k6/config/config.js` 파일에서 테스트 데이터를 수정할 수 있습니다.

```javascript
export const TEST_DATA = {
  COUPON_ID: '550e8400-e29b-41d4-a716-446655440001',  // 쿠폰 UUID
  PRODUCT_IDS: [
    '550e8400-e29b-41d4-a716-446655440011',          // 상품 UUIDs
    '550e8400-e29b-41d4-a716-446655440012',
    '550e8400-e29b-41d4-a716-446655440013',
  ],
};
```

---

## 🔍 문제 해결

### 1. 연결 거부 (Connection Refused)

```
WARN[0001] Request Failed error="Get \"http://localhost:8080/api/...\": dial tcp 127.0.0.1:8080: connect: connection refused"
```

**해결**: 애플리케이션이 실행 중인지 확인하세요.

```bash
# 애플리케이션 상태 확인
curl http://localhost:8080/api/products/ranking/top
```

---

### 2. 높은 실패율

**원인**:
- 재고 부족 (정상적인 실패)
- 쿠폰 품절 (정상적인 실패)
- 잔액 부족 (정상적인 실패)

**확인**: 로그에서 실패 사유를 확인하세요.

---

### 3. 느린 응답 시간

**예상 결과**:
- 인덱스 제거로 인한 Full Table Scan
- Redis 분산락 비활성화로 인한 DB 락 대기
- Kafka 비활성화로 인한 동기 처리

이것이 바로 측정하고자 하는 부하입니다!

---

## 🎯 기대 결과

### 최적화 제거 전 (운영 환경)
- 인기 상품 조회: ~50ms
- 쿠폰 발급: ~100ms
- 주문 생성: ~200ms
- 결제 처리: ~150ms

### 최적화 제거 후 (부하 테스트 환경)
- 인기 상품 조회: ~500ms 이상 (인덱스 없음)
- 쿠폰 발급: ~500ms 이상 (DB 락 대기)
- 주문 생성: ~1000ms 이상 (트랜잭션 경합)
- 결제 처리: ~800ms 이상 (User 락 경합)

---

## 📈 사후 작업

테스트 완료 후:

1. **인덱스 복구**

```bash
mysql -u root -p -D hhplus_ecommerce < scripts/load-test/restore-product-indexes.sql
```

2. **애플리케이션 재시작 (운영 모드)**

```bash
./gradlew bootRun
```

3. **결과 분석 및 보고서 작성**
   - 각 시나리오별 성능 지표 정리
   - 최적화 전/후 비교 차트 작성
   - 병목 지점 식별 및 개선 방안 도출

---

## 📚 참고 자료

- [K6 공식 문서](https://k6.io/docs/)
- [K6 Thresholds](https://k6.io/docs/using-k6/thresholds/)
- [K6 Scenarios](https://k6.io/docs/using-k6/scenarios/)
- [부하 테스트 계획서](../docs/LOAD_TEST_PLAN.md)