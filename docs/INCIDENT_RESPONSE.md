# 장애 대응 매뉴얼 (Incident Response Manual)

## 📋 목차

1. [장애 등급 정의](#장애-등급-정의)
2. [장애 대응 프로세스](#장애-대응-프로세스)
3. [장애 시나리오별 대응 방안](#장애-시나리오별-대응-방안)
4. [긴급 조치 커맨드](#긴급-조치-커맨드)
5. [모니터링 알람 설정](#모니터링-알람-설정)
6. [사후 분석 (Post-Mortem)](#사후-분석-post-mortem)

---

## 장애 등급 정의

### Level 1 (Critical) - 서비스 중단

**정의:**
- 서비스 전체 또는 핵심 기능 사용 불가
- 데이터 손실 발생 또는 우려

**예시:**
- 애플리케이션 서버 다운
- 데이터베이스 다운
- 결제 시스템 장애

**대응 시간:**
- 즉시 대응 (5분 이내)
- 복구 목표 시간: 30분 이내

**대응 인력:**
- 전체 On-Call 팀 소집
- 경영진 보고

---

### Level 2 (High) - 기능 장애

**정의:**
- 일부 핵심 기능 사용 불가
- 서비스는 동작하나 성능 저하

**예시:**
- 쿠폰 발급 실패
- 주문 생성 지연 (5초 이상)
- 결제 처리 실패율 10% 이상

**대응 시간:**
- 15분 이내 대응 시작
- 복구 목표 시간: 2시간 이내

**대응 인력:**
- Backend 팀 On-Call
- 팀 리더 보고

---

### Level 3 (Medium) - 성능 저하

**정의:**
- 서비스는 동작하나 성능이 느림
- 사용자 경험 저하

**예시:**
- 응답 시간 2초 이상
- 에러율 5% 이상
- 데이터베이스 CPU 90% 이상

**대응 시간:**
- 1시간 이내 대응 시작
- 복구 목표 시간: 4시간 이내

**대응 인력:**
- 해당 담당자

---

### Level 4 (Low) - 경미한 이슈

**정의:**
- 서비스 정상 동작
- 일부 로그 에러 또는 경고

**예시:**
- 일부 사용자의 간헐적 에러
- 로그 WARNING 수준

**대응 시간:**
- 익일 대응 가능

---

## 장애 대응 프로세스

```
┌─────────────┐
│ 1. 장애 감지 │ → 모니터링 알람, 사용자 신고
└─────────────┘
       ↓
┌─────────────┐
│ 2. 장애 확인 │ → 로그, 메트릭 확인
└─────────────┘
       ↓
┌─────────────┐
│ 3. 긴급 조치 │ → 서비스 복구 (임시 조치)
└─────────────┘
       ↓
┌─────────────┐
│ 4. 원인 분석 │ → 로그 분석, 재현
└─────────────┘
       ↓
┌─────────────┐
│ 5. 근본 해결 │ → 패치 배포, 설정 변경
└─────────────┘
       ↓
┌─────────────┐
│ 6. 사후 분석 │ → Post-Mortem 작성
└─────────────┘
```

### 1. 장애 감지

**감지 경로:**
- 모니터링 시스템 알람 (Grafana, Datadog 등)
- 헬스 체크 실패
- 사용자 신고 (고객센터)
- 로그 모니터링

**즉시 확인 사항:**
- 영향 범위 (전체 사용자? 일부 사용자?)
- 장애 시작 시간
- 장애 등급 판단

---

### 2. 장애 확인

**체크리스트:**

```bash
# 1. 애플리케이션 상태 확인
curl http://localhost:8080/api/actuator/health

# 2. 로그 확인 (최근 100줄)
tail -n 100 logs/application.log

# 3. 데이터베이스 연결 확인
mysql -u root -p -e "SELECT 1"

# 4. 프로세스 확인
ps aux | grep java

# 5. 리소스 사용률 확인
top
htop
```

---

### 3. 긴급 조치

**우선순위:**
1. **서비스 복구** (임시 조치라도 먼저 복구)
2. 원인 분석
3. 근본 해결

**롤백 기준:**
- 5분 이내 원인 파악 불가 시 → 즉시 롤백
- 데이터 정합성 문제 발생 시 → 즉시 롤백

---

## 장애 시나리오별 대응 방안

### 시나리오 1: 데이터베이스 커넥션 풀 고갈

#### 증상

```
Unable to acquire JDBC Connection
HikariPool-1 - Connection is not available, request timed out after 30000ms
```

#### 원인

1. 커넥션 풀 크기 부족
2. 커넥션 누수 (반환 안됨)
3. 트랜잭션이 오래 걸림
4. DB 성능 저하

#### 즉시 확인

```bash
# 1. 현재 커넥션 수 확인
curl http://localhost:8080/api/actuator/metrics/hikaricp.connections.active

# 2. DB 프로세스 리스트 확인
mysql -u root -p -e "SHOW PROCESSLIST"
```

#### 긴급 조치

**옵션 1: 커넥션 풀 크기 증가 (임시 조치)**

```yaml
# application.yml 수정
spring:
  datasource:
    hikari:
      maximum-pool-size: 50  # 기존: 20 → 50으로 증가
      connection-timeout: 5000  # 5초
```

```bash
# 재시작
./gradlew bootRun
```

**옵션 2: 오래 실행되는 쿼리 강제 종료**

```sql
-- 오래 실행 중인 쿼리 확인 (10초 이상)
SELECT
    ID,
    USER,
    HOST,
    DB,
    COMMAND,
    TIME,
    STATE,
    INFO
FROM INFORMATION_SCHEMA.PROCESSLIST
WHERE TIME > 10
AND COMMAND != 'Sleep'
ORDER BY TIME DESC;

-- 해당 프로세스 강제 종료
KILL <PROCESS_ID>;
```

#### 근본 해결

1. 슬로우 쿼리 최적화
2. 트랜잭션 범위 축소
3. 커넥션 풀 적정 크기 계산

```
적정 풀 크기 = (CPU 코어 수) × 2 + (디스크 수)
예) CPU 8코어, SSD 1개 = 8 × 2 + 1 = 17
```

---

### 시나리오 2: 선착순 쿠폰 발급 실패

#### 증상

```
CouponOutOfStockException: Sold out
실제 발급 수량: 53개 (기대: 50개)
```

#### 원인

1. 동시성 제어 실패 (Race Condition)
2. 락 타임아웃
3. 데드락

#### 즉시 확인

```sql
-- 1. 실제 발급 수량 확인
SELECT
    id,
    total_quantity,
    issued_quantity
FROM coupon
WHERE id = '550e8400-e29b-41d4-a716-446655440001';

-- 2. 중복 발급 확인
SELECT
    user_id,
    COUNT(*) as count
FROM user_coupon
WHERE coupon_id = '550e8400-e29b-41d4-a716-446655440001'
GROUP BY user_id
HAVING count > 1;

-- 3. 락 대기 확인
SELECT * FROM information_schema.INNODB_TRX;
```

#### 긴급 조치

**옵션 1: 초과 발급된 쿠폰 무효화**

```sql
-- 마지막 3개 쿠폰 무효화 (발급 순서대로)
UPDATE user_coupon
SET status = 'EXPIRED'
WHERE coupon_id = '550e8400-e29b-41d4-a716-446655440001'
ORDER BY issued_at DESC
LIMIT 3;
```

**옵션 2: 쿠폰 수량 조정 (보상)**

```sql
-- 총 수량 증가 (초과 발급된 만큼)
UPDATE coupon
SET total_quantity = 53
WHERE id = '550e8400-e29b-41d4-a716-446655440001';
```

#### 근본 해결

**코드 수정: 락 타임아웃 증가**

```kotlin
@DistributedLock(
    key = "'coupon:issue:' + #couponId",
    waitTimeMs = 5000,  // 3초 → 5초로 증가
    leaseTimeMs = 15000,  // 10초 → 15초로 증가
)
```

**또는: 비동기 큐 방식으로 전환**

```kotlin
// Redis Queue + 스케줄러 방식 사용
override fun requestCouponIssuance(...)
```

---

### 시나리오 3: 주문 생성 타임아웃

#### 증상

```
QueryTimeoutException: Query timeout after 30000ms
TransactionTimedOutException: Transaction timed out
```

#### 원인

1. 재고 락 대기 시간 초과
2. 데드락
3. 트랜잭션 범위가 너무 큼

#### 즉시 확인

```bash
# 1. 스레드 덤프 확인
jstack <PID> | grep -A 20 "BLOCKED"

# 2. 데드락 확인
mysql -u root -p -e "SHOW ENGINE INNODB STATUS\G" | grep -A 50 "LATEST DETECTED DEADLOCK"
```

#### 긴급 조치

**옵션 1: 타임아웃 시간 증가 (임시)**

```yaml
spring:
  datasource:
    hikari:
      connection-timeout: 60000  # 30초 → 60초
  jpa:
    properties:
      javax.persistence.query.timeout: 60000  # 30초 → 60초
```

**옵션 2: 애플리케이션 재시작**

```bash
# Graceful Shutdown
./gradlew bootRun --stop

# 재시작
./gradlew bootRun --args='--spring.profiles.active=prod'
```

#### 근본 해결

**데드락 방지: 락 순서 통일**

```kotlin
// Before: 상품 ID 순서가 랜덤
val products = productService.findAllByIdWithLock(productIds)

// After: 상품 ID를 정렬하여 항상 같은 순서로 락 획득
val sortedProductIds = productIds.sorted()
val products = productService.findAllByIdWithLock(sortedProductIds)
```

---

### 시나리오 4: 데이터베이스 슬레이브 지연 (Replication Lag)

#### 증상

```
방금 주문한 상품이 주문 목록에 안보임
결제 완료 후에도 주문 상태가 PENDING
```

#### 원인

- Master-Slave 복제 지연
- 읽기 요청이 Slave로 분산되어 최신 데이터가 반영 안됨

#### 즉시 확인

```sql
-- Slave에서 실행
SHOW SLAVE STATUS\G

-- 중요 지표:
-- Seconds_Behind_Master: 0이면 정상, 1 이상이면 지연
```

#### 긴급 조치

**옵션 1: 읽기 요청을 Master로 전환 (임시)**

```kotlin
// Before: Slave에서 읽기
@Transactional(readOnly = true)
fun getOrders(...) { ... }

// After: Master에서 읽기 (readOnly = false)
@Transactional
fun getOrders(...) { ... }
```

**옵션 2: Replication 재시작**

```sql
-- Slave에서 실행
STOP SLAVE;
START SLAVE;
```

#### 근본 해결

1. **Slave 리소스 증설** (CPU, 메모리, 디스크)
2. **Read/Write 분리 최적화**
   - 최신 데이터가 필요한 경우: Master
   - 통계, 목록 조회: Slave

---

### 시나리오 5: 메모리 부족 (OutOfMemoryError)

#### 증상

```
java.lang.OutOfMemoryError: Java heap space
java.lang.OutOfMemoryError: GC overhead limit exceeded
```

#### 원인

1. 힙 크기 부족
2. 메모리 누수
3. 과도한 객체 생성

#### 즉시 확인

```bash
# 1. 힙 덤프 생성
jmap -dump:format=b,file=heap_dump.hprof <PID>

# 2. 힙 사용률 확인
jstat -gc <PID> 1000 10

# 3. 메모리 사용량 확인
free -h
ps aux --sort=-%mem | head
```

#### 긴급 조치

**옵션 1: 애플리케이션 재시작**

```bash
./gradlew bootRun --stop
./gradlew bootRun --args='--spring.profiles.active=prod -Xmx4g -Xms4g'
```

**옵션 2: 힙 크기 증가**

```bash
# -Xmx (최대 힙), -Xms (초기 힙)
./gradlew bootRun -Dorg.gradle.jvmargs="-Xmx4g -Xms4g -XX:+HeapDumpOnOutOfMemoryError"
```

#### 근본 해결

**힙 덤프 분석 (Eclipse MAT)**

```bash
# Eclipse MAT 다운로드
# https://www.eclipse.org/mat/

# 힙 덤프 열기
# File → Open Heap Dump → heap_dump.hprof

# Leak Suspects Report 확인
# → 메모리 누수 의심 객체 식별
```

**메모리 누수 패턴:**
- `ArrayList`, `HashMap` 등 컬렉션이 계속 증가
- 정적 변수에 객체 누적
- 캐시가 무한정 증가

---

## 긴급 조치 커맨드

### 애플리케이션 관리

```bash
# 프로세스 확인
ps aux | grep java

# 프로세스 강제 종료
kill -9 <PID>

# Graceful Shutdown
kill -15 <PID>

# 재시작
./gradlew bootRun --args='--spring.profiles.active=prod'

# 특정 포트 사용 프로세스 확인
lsof -i :8080
netstat -tulpn | grep 8080
```

---

### 데이터베이스 관리

```sql
-- 현재 실행 중인 쿼리 확인
SHOW PROCESSLIST;

-- 오래 실행되는 쿼리 강제 종료
KILL <PROCESS_ID>;

-- 락 정보 확인
SELECT * FROM information_schema.INNODB_TRX;
SELECT * FROM information_schema.INNODB_LOCKS;

-- 데드락 정보
SHOW ENGINE INNODB STATUS\G

-- 커넥션 수 확인
SHOW STATUS LIKE 'Threads_connected';
SHOW VARIABLES LIKE 'max_connections';

-- 슬로우 쿼리 확인
SELECT * FROM mysql.slow_log ORDER BY start_time DESC LIMIT 10;
```

---

### 캐시 관리 (Redis)

```bash
# Redis 연결 확인
redis-cli ping

# 특정 키 삭제
redis-cli DEL "coupon:issue:550e8400-e29b-41d4-a716-446655440001"

# 패턴으로 키 삭제
redis-cli --scan --pattern "coupon:*" | xargs redis-cli DEL

# 전체 캐시 삭제 (주의!)
redis-cli FLUSHALL

# 메모리 사용량 확인
redis-cli INFO memory
```

---

### 로그 확인

```bash
# 실시간 로그 확인
tail -f logs/application.log

# 에러 로그만 필터링
tail -f logs/application.log | grep -i error

# 특정 시간대 로그
grep "2025-12-22 10:30" logs/application.log

# 로그 파일 크기 확인
du -sh logs/
```

---

## 모니터링 알람 설정

### Grafana 알람 규칙 (예시)

#### 1. 응답 시간 알람

```yaml
alert: HighResponseTime
expr: histogram_quantile(0.95, http_request_duration_seconds) > 2
for: 5m
labels:
  severity: warning
annotations:
  summary: "API 응답 시간이 2초를 초과했습니다"
  description: "p95 응답 시간: {{ $value }}초"
```

#### 2. 에러율 알람

```yaml
alert: HighErrorRate
expr: rate(http_requests_total{status=~"5.."}[5m]) / rate(http_requests_total[5m]) > 0.05
for: 5m
labels:
  severity: critical
annotations:
  summary: "에러율이 5%를 초과했습니다"
  description: "현재 에러율: {{ $value | humanizePercentage }}"
```

#### 3. 데이터베이스 커넥션 알람

```yaml
alert: HighDBConnectionUsage
expr: hikaricp_connections_active / hikaricp_connections_max > 0.9
for: 5m
labels:
  severity: warning
annotations:
  summary: "DB 커넥션 사용률이 90%를 초과했습니다"
```

#### 4. JVM 힙 메모리 알람

```yaml
alert: HighHeapUsage
expr: jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} > 0.85
for: 10m
labels:
  severity: warning
annotations:
  summary: "JVM 힙 메모리 사용률이 85%를 초과했습니다"
```

---

## 사후 분석 (Post-Mortem)

장애 복구 후 반드시 사후 분석 문서를 작성합니다.

### Post-Mortem 템플릿

```markdown
# Post-Mortem: [장애 제목]

## 기본 정보
- **발생 일시**: 2025-12-22 10:30:00 ~ 11:15:00 (45분)
- **장애 등급**: Level 2 (High)
- **영향 범위**: 쿠폰 발급 기능 (사용자 약 1,000명)
- **담당자**: Backend 팀

## 타임라인
| 시각 | 내용 |
|------|------|
| 10:30 | 모니터링 알람 발생 (에러율 10% 초과) |
| 10:32 | On-Call 엔지니어 장애 확인 시작 |
| 10:35 | 원인 파악: 쿠폰 재고 수량 초과 발급 |
| 10:40 | 긴급 조치: 초과 발급 쿠폰 무효화 |
| 10:45 | 근본 원인 파악: 동시성 제어 실패 |
| 11:00 | 패치 배포: 락 타임아웃 증가 |
| 11:15 | 정상화 확인 및 모니터링 강화 |

## 장애 원인 (Root Cause)
Redis 분산락의 Lease Time (10초)이 너무 짧아서, 트랜잭션이 완료되기 전에
락이 해제되어 다른 요청이 동일한 쿠폰을 발급할 수 있었음.

## 영향 (Impact)
- 50개 제한 쿠폰이 53개 발급됨 (3개 초과)
- 약 1,000명의 사용자가 쿠폰 발급 실패
- 일부 사용자의 중복 발급 발생

## 해결 방안 (Resolution)
### 즉시 조치
1. 초과 발급된 3개 쿠폰 무효화
2. 락 타임아웃 10초 → 15초로 증가

### 근본 해결
1. 락 획득 후 트랜잭션 시간 모니터링 추가
2. 쿠폰 발급 시 이중 검증 로직 추가
3. 부하 테스트 강화

## 재발 방지 (Prevention)
### 단기
- [ ] 락 타임아웃 모니터링 알람 추가
- [ ] 쿠폰 재고 수량 검증 로직 강화
- [ ] 부하 테스트 자동화

### 장기
- [ ] 비동기 큐 방식으로 쿠폰 발급 로직 전환
- [ ] Saga 패턴 적용 (보상 트랜잭션)
- [ ] Chaos Engineering 도입

## 교훈 (Lessons Learned)
1. 동시성 제어 시 락 타임아웃은 충분히 길게 설정해야 함
2. 부하 테스트에서 동시성 시나리오를 더 강화해야 함
3. 알람 임계값을 더 엄격하게 설정해야 함

## 참고 자료
- [관련 로그](링크)
- [모니터링 대시보드](링크)
- [패치 PR](링크)
```

---

## 비상 연락망

| 역할 | 이름 | 전화번호 | 이메일 |
|------|------|---------|--------|
| On-Call (Backend) | 홍길동 | 010-XXXX-XXXX | hong@example.com |
| DB 관리자 | 김철수 | 010-YYYY-YYYY | kim@example.com |
| 팀 리더 | 이영희 | 010-ZZZZ-ZZZZ | lee@example.com |
| 인프라 담당 | 박민수 | 010-AAAA-AAAA | park@example.com |

---

## 체크리스트

### 장애 발생 시 즉시 확인

- [ ] 헬스 체크 상태 확인
- [ ] 로그 에러 메시지 확인
- [ ] 데이터베이스 연결 상태 확인
- [ ] 리소스 사용률 (CPU, 메모리, 디스크) 확인
- [ ] 네트워크 연결 상태 확인

### 긴급 조치 실행 전

- [ ] 장애 등급 판단
- [ ] 영향 범위 파악
- [ ] 롤백 가능 여부 확인
- [ ] 데이터 백업 상태 확인

### 사후 조치

- [ ] Post-Mortem 문서 작성
- [ ] 재발 방지 방안 수립
- [ ] 모니터링 알람 업데이트
- [ ] 팀 회고 미팅 개최

---

## 부록: 유용한 스크립트

### DB 헬스 체크 스크립트

```bash
#!/bin/bash
# db-health-check.sh

mysql -u root -p -e "
SELECT
    'Threads Connected' AS metric,
    VARIABLE_VALUE AS value
FROM performance_schema.global_status
WHERE VARIABLE_NAME = 'Threads_connected'
UNION ALL
SELECT
    'Max Connections',
    @@max_connections
UNION ALL
SELECT
    'Slow Queries',
    VARIABLE_VALUE
FROM performance_schema.global_status
WHERE VARIABLE_NAME = 'Slow_queries';
"
```

### 애플리케이션 헬스 체크 스크립트

```bash
#!/bin/bash
# app-health-check.sh

API_URL="http://localhost:8080/api/actuator/health"

response=$(curl -s -o /dev/null -w "%{http_code}" $API_URL)

if [ $response -eq 200 ]; then
    echo "✅ Application is healthy"
    exit 0
else
    echo "❌ Application is unhealthy (HTTP $response)"
    exit 1
fi
```

---

**마지막 업데이트**: 2025-12-22
**담당자**: Backend 팀
**검토 주기**: 분기별