# 성능 분석 및 병목 탐색 가이드

## 📋 목차

1. [개요](#개요)
2. [데이터 수집 계층](#데이터-수집-계층)
3. [모니터링 도구 설정](#모니터링-도구-설정)
4. [병목 탐색 절차](#병목-탐색-절차)
5. [성능 지표 분석](#성능-지표-분석)
6. [개선 방안 도출](#개선-방안-도출)

---

## 개요

부하 테스트를 통해 시스템의 병목 지점을 찾고 개선하기 위한 체계적인 가이드입니다.

### 핵심 원칙

1. **측정하지 않으면 개선할 수 없다** - 모든 것을 측정하라
2. **가설을 세우고 검증하라** - 추측하지 말고 데이터로 증명하라
3. **가장 큰 병목부터 해결하라** - 파레토 법칙 (80/20)

---

## 데이터 수집 계층

부하 테스트 시 다음 4개 레이어의 데이터를 수집합니다.

```
┌─────────────────────────────────────────┐
│  1. K6 테스트 결과 (클라이언트 관점)      │
│     - 응답 시간, TPS, 에러율             │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│  2. 애플리케이션 로그 (서버 관점)         │
│     - API 처리 시간, 예외, 트랜잭션       │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│  3. JVM 메트릭 (런타임 관점)             │
│     - GC, 힙 메모리, 스레드              │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│  4. 데이터베이스 메트릭 (DB 관점)         │
│     - 슬로우 쿼리, 락, CPU, 커넥션        │
└─────────────────────────────────────────┘
```

---

## 모니터링 도구 설정

### 1. MySQL 슬로우 쿼리 로그 활성화

#### 설정 방법

**my.cnf 또는 my.ini 수정:**

```ini
[mysqld]
# 슬로우 쿼리 로그 활성화
slow_query_log = 1
slow_query_log_file = /var/log/mysql/mysql-slow.log

# 기준 시간 (초) - 0.1초 이상 걸리는 쿼리 기록
long_query_time = 0.1

# 인덱스를 사용하지 않는 쿼리도 기록
log_queries_not_using_indexes = 1

# 로그에 실행 시간 기록
log_slow_extra = 1
```

**또는 런타임에 설정:**

```sql
-- 슬로우 쿼리 로그 활성화
SET GLOBAL slow_query_log = 'ON';
SET GLOBAL slow_query_log_file = '/var/log/mysql/mysql-slow.log';
SET GLOBAL long_query_time = 0.1;
SET GLOBAL log_queries_not_using_indexes = 'ON';
```

#### 로그 분석 도구

**pt-query-digest (권장)**

```bash
# 설치 (Percona Toolkit)
# macOS
brew install percona-toolkit

# Ubuntu
apt-get install percona-toolkit

# 분석
pt-query-digest /var/log/mysql/mysql-slow.log > slow_query_report.txt
```

**mysqldumpslow (기본 제공)**

```bash
# 가장 느린 쿼리 Top 10
mysqldumpslow -s t -t 10 /var/log/mysql/mysql-slow.log

# 가장 많이 실행된 쿼리 Top 10
mysqldumpslow -s c -t 10 /var/log/mysql/mysql-slow.log
```

---

### 2. MySQL 성능 스키마 활성화

MySQL 5.7+ 버전에서는 Performance Schema를 사용하여 실시간 성능 모니터링이 가능합니다.

```sql
-- Performance Schema 활성화 확인
SHOW VARIABLES LIKE 'performance_schema';

-- 이벤트 활성화
UPDATE performance_schema.setup_instruments
SET ENABLED = 'YES', TIMED = 'YES'
WHERE NAME LIKE 'statement/%';

UPDATE performance_schema.setup_instruments
SET ENABLED = 'YES', TIMED = 'YES'
WHERE NAME LIKE 'wait/lock/%';
```

---

### 3. Spring Boot Actuator 설정

애플리케이션의 내부 메트릭을 노출합니다.

#### build.gradle.kts

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")  // Prometheus 연동 시
}
```

#### application-load-test.yml

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus,loggers,threaddump,heapdump
  metrics:
    export:
      prometheus:
        enabled: true
  endpoint:
    health:
      show-details: always
    metrics:
      enabled: true
```

#### 메트릭 확인

```bash
# 헬스 체크
curl http://localhost:8080/api/actuator/health

# 메트릭 확인
curl http://localhost:8080/api/actuator/metrics

# 특정 메트릭 (JVM 메모리)
curl http://localhost:8080/api/actuator/metrics/jvm.memory.used

# 특정 메트릭 (HTTP 요청)
curl http://localhost:8080/api/actuator/metrics/http.server.requests

# 스레드 덤프
curl http://localhost:8080/api/actuator/threaddump > threaddump.json

# 힙 덤프 (대용량 주의!)
curl http://localhost:8080/api/actuator/heapdump -o heapdump.hprof
```

---

### 4. JVM GC 로그 활성화

GC 성능 분석을 위한 로그를 활성화합니다.

#### Gradle 실행 시 JVM 옵션 추가

```bash
./gradlew bootRun --args='--spring.profiles.active=load-test' \
  -Dorg.gradle.jvmargs="-Xlog:gc*:file=gc.log:time,uptime,level,tags:filecount=5,filesize=100m"
```

#### 또는 application-load-test.yml에서 설정

```yaml
spring:
  application:
    name: hhplus-ecommerce
  jvm:
    options: -Xlog:gc*:file=gc.log:time,uptime,level,tags
```

#### GC 로그 분석 도구

- **GCeasy** (온라인): https://gceasy.io/
- **GCViewer** (오프라인): https://github.com/chewiebug/GCViewer

---

## 병목 탐색 절차

### Step 1: K6 테스트 실행 및 결과 수집

```bash
# 1. 인덱스 제거
mysql -u root -p -D hhplus_ecommerce < scripts/load-test/drop-product-indexes.sql

# 2. 애플리케이션 실행 (부하 테스트 모드)
./gradlew bootRun --args='--spring.profiles.active=load-test'

# 3. K6 테스트 실행 (결과를 JSON으로 저장)
k6 run --out json=test-results.json k6/run-all.js
```

#### K6 결과에서 확인할 지표

```javascript
// test-results.json 분석
{
  "metric": "http_req_duration",
  "type": "Point",
  "data": {
    "time": "2025-12-22T10:30:00Z",
    "value": 1523.45,  // 응답 시간 (ms)
    "tags": {
      "name": "ProductRanking",
      "status": "200"
    }
  }
}
```

**주요 지표:**
- `http_req_duration`: 총 응답 시간
- `http_req_waiting`: 서버 처리 시간 (실제 병목)
- `http_req_blocked`: 커넥션 대기 시간
- `http_req_connecting`: TCP 연결 시간

---

### Step 2: 애플리케이션 로그 분석

부하 테스트 중 애플리케이션 로그를 수집합니다.

```bash
# 로그 파일 확인
tail -f logs/application.log | grep -E "(Duration|Exception|Lock|Timeout)"
```

#### 확인할 로그 패턴

1. **SQL 실행 시간**
   ```
   [Hibernate] select ... from product ... (execution time: 1523ms)
   ```

2. **트랜잭션 시간**
   ```
   [TransactionInterceptor] Transaction completed in 2145ms
   ```

3. **락 대기 예외**
   ```
   LockAcquisitionException: could not obtain lock
   DeadlockLoserDataAccessException: Deadlock found
   ```

4. **타임아웃 예외**
   ```
   QueryTimeoutException: Query timeout
   ```

---

### Step 3: MySQL 성능 분석

#### 3.1 슬로우 쿼리 확인

```bash
# 슬로우 쿼리 로그 분석
pt-query-digest /var/log/mysql/mysql-slow.log > slow_query_analysis.txt
```

**분석 결과 예시:**

```
# Query 1: 15.2 QPS, 0.95x concurrency, ID 0x1234ABCD
# Time range: 2025-12-22 10:30:00 to 10:31:00
# Attribute    pct   total     min     max     avg     95%  stddev  median
# ============ === ======= ======= ======= ======= ======= ======= =======
# Count         30     912
# Exec time     85  875.3s    0.1s    5.2s    0.96s    2.1s    0.5s    0.8s
# Lock time      5   1.23s    1ms    50ms     1ms     3ms     2ms     1ms
# Rows sent      0       0       0       0       0       0       0       0
# Rows examine  95  45.6M   50.0k   50.0k   50.0k   50.0k       0   50.0k

SELECT p.*
FROM product p
ORDER BY p.sales_count DESC
LIMIT 10\G
```

**문제점 식별:**
- `Rows examined`가 매우 크면 → Full Table Scan (인덱스 필요)
- `Exec time avg`가 크면 → 쿼리 최적화 필요
- `Lock time`이 크면 → 락 경합 문제

---

#### 3.2 실행 계획 분석 (EXPLAIN)

```sql
-- 인기 상품 조회 쿼리
EXPLAIN ANALYZE
SELECT p.*
FROM product p
ORDER BY p.sales_count DESC
LIMIT 10;
```

**출력 예시 (인덱스 없을 때):**

```
-> Limit: 10 row(s)  (cost=5021.5 rows=10) (actual time=1523.2..1523.3 rows=10 loops=1)
    -> Sort: p.sales_count DESC  (cost=5021.5 rows=50000) (actual time=1523.1..1523.2 rows=10 loops=1)
        -> Table scan on p  (cost=5021.5 rows=50000) (actual time=0.1..1234.5 rows=50000 loops=1)
```

**문제점:**
- `Table scan` → Full Table Scan 발생
- `actual time=1523ms` → 매우 느림

---

#### 3.3 락 정보 확인

```sql
-- 현재 실행 중인 트랜잭션 확인
SELECT * FROM information_schema.INNODB_TRX;

-- 락 대기 중인 트랜잭션
SELECT
    r.trx_id waiting_trx_id,
    r.trx_mysql_thread_id waiting_thread,
    r.trx_query waiting_query,
    b.trx_id blocking_trx_id,
    b.trx_mysql_thread_id blocking_thread,
    b.trx_query blocking_query
FROM information_schema.INNODB_LOCK_WAITS w
INNER JOIN information_schema.INNODB_TRX b ON b.trx_id = w.blocking_trx_id
INNER JOIN information_schema.INNODB_TRX r ON r.trx_id = w.requesting_trx_id;

-- 데드락 정보
SHOW ENGINE INNODB STATUS\G
```

**확인할 정보:**
- `trx_wait_started`: 대기 시작 시간
- `trx_rows_locked`: 잠긴 행 수
- `trx_rows_modified`: 수정된 행 수

---

#### 3.4 데이터베이스 리소스 확인

```sql
-- CPU 사용률이 높은 쿼리
SELECT
    DIGEST_TEXT,
    COUNT_STAR,
    AVG_TIMER_WAIT / 1000000000000 AS avg_sec,
    SUM_TIMER_WAIT / 1000000000000 AS total_sec
FROM performance_schema.events_statements_summary_by_digest
ORDER BY SUM_TIMER_WAIT DESC
LIMIT 10;

-- 커넥션 상태
SHOW PROCESSLIST;
SHOW STATUS LIKE 'Threads%';
SHOW VARIABLES LIKE 'max_connections';

-- 버퍼 풀 사용률
SHOW STATUS LIKE 'Innodb_buffer_pool%';
```

---

### Step 4: JVM 메트릭 분석

#### 4.1 GC 로그 분석

GCeasy에 gc.log를 업로드하여 분석합니다.

**확인할 지표:**
- GC Pause Time (STW 시간)
- GC Frequency (GC 빈도)
- Heap 사용률

**문제 패턴:**
- GC가 자주 발생 → 힙 크기 부족 또는 메모리 누수
- STW 시간이 김 → Old Gen GC 발생 (Full GC)

---

#### 4.2 스레드 덤프 분석

```bash
# 스레드 덤프 3회 수집 (5초 간격)
jstack <PID> > threaddump1.txt
sleep 5
jstack <PID> > threaddump2.txt
sleep 5
jstack <PID> > threaddump3.txt
```

**확인할 정보:**
- 대기 중인 스레드 (`WAITING`, `TIMED_WAITING`)
- 락을 보유한 스레드 (`RUNNABLE`, `BLOCKED`)
- 데드락 발생 여부

---

## 성능 지표 분석

### 병목 판단 기준표

| 지표 | 정상 범위 | 병목 의심 | 원인 추정 |
|---|---|---|---|
| **응답 시간 (p95)** | < 500ms | > 2000ms | DB 쿼리, 락 경합, GC |
| **TPS** | > 1000 | < 100 | 커넥션 풀, 스레드 풀 고갈 |
| **에러율** | < 1% | > 10% | 타임아웃, 리소스 부족 |
| **DB CPU** | < 70% | > 90% | 쿼리 최적화 필요 |
| **DB 커넥션** | < 80% | > 95% | 커넥션 풀 튜닝 |
| **JVM Heap** | < 70% | > 85% | 메모리 누수, 힙 크기 부족 |
| **GC Pause** | < 100ms | > 1000ms | Old Gen GC, 힙 튜닝 |
| **락 대기 시간** | < 10ms | > 1000ms | 트랜잭션 범위 축소 |

---

### 병목 유형별 증상

#### 1. 데이터베이스 병목

**증상:**
- 슬로우 쿼리 로그에 같은 쿼리가 반복 기록
- DB CPU 사용률 90% 이상
- 응답 시간이 일관되게 느림

**원인:**
- Full Table Scan (인덱스 부재)
- 비효율적인 JOIN
- 과도한 데이터 조회

**해결:**
- 인덱스 추가
- 쿼리 최적화
- 페이징 적용

---

#### 2. 락 경합 병목

**증상:**
- 특정 시점에만 응답 시간 급증
- `LockAcquisitionException` 발생
- DB 커넥션은 남는데 TPS가 낮음

**원인:**
- 트랜잭션 범위가 너무 큼
- 비관적 락 사용 시 대기 시간 증가
- 데드락 발생

**해결:**
- 트랜잭션 범위 최소화
- 낙관적 락 검토
- 락 순서 통일 (데드락 방지)

---

#### 3. 커넥션 풀 고갈

**증상:**
- `Unable to acquire JDBC Connection` 에러
- TPS가 일정 수준 이상 오르지 않음
- 응답 시간이 불규칙적으로 급증

**원인:**
- 커넥션 풀 크기 부족
- 트랜잭션이 커넥션을 오래 점유

**해결:**
- 커넥션 풀 크기 증가
- 트랜잭션 최적화
- 타임아웃 설정

---

#### 4. JVM 메모리 부족

**증상:**
- Old Gen GC 빈발 (Full GC)
- GC Pause Time이 1초 이상
- `OutOfMemoryError` 발생

**원인:**
- 힙 크기 부족
- 메모리 누수
- 과도한 객체 생성

**해결:**
- 힙 크기 증가 (`-Xmx`)
- 메모리 프로파일링 (힙 덤프 분석)
- 객체 재사용 (Connection Pool, Object Pool)

---

## 개선 방안 도출

### 우선순위 결정 방법

1. **영향도 × 빈도**가 가장 큰 병목부터 해결
2. **비용 대비 효과**가 큰 것부터 적용

```
우선순위 = (개선 효과 %) × (발생 빈도 %) / (개선 비용)
```

### 개선 체크리스트

#### 데이터베이스 최적화

- [ ] 슬로우 쿼리 Top 10 최적화
- [ ] Full Table Scan 쿼리에 인덱스 추가
- [ ] N+1 쿼리 문제 해결 (Batch Fetch, Join Fetch)
- [ ] 불필요한 데이터 조회 제거 (SELECT * → 필요한 컬럼만)
- [ ] 페이징 적용 (Offset Limit)

#### 트랜잭션 최적화

- [ ] 트랜잭션 범위 최소화
- [ ] 읽기 전용 트랜잭션 분리 (`@Transactional(readOnly = true)`)
- [ ] 낙관적 락 검토
- [ ] 데드락 방지 (락 순서 통일)

#### 애플리케이션 최적화

- [ ] 캐시 적용 (Redis, Local Cache)
- [ ] 비동기 처리 (Kafka, Spring @Async)
- [ ] 커넥션 풀 튜닝
- [ ] 스레드 풀 튜닝

#### 인프라 최적화

- [ ] DB 리소스 증설 (CPU, 메모리)
- [ ] 애플리케이션 서버 스케일 아웃
- [ ] 로드 밸런서 적용
- [ ] CDN 적용 (정적 리소스)

---

## 실습: 단계별 분석 예시

### 예시: 인기 상품 조회 병목 분석

#### 1. K6 결과 확인

```
http_req_duration..............: avg=1523ms p95=2100ms
http_req_failed................: 0%
```

→ 응답 시간이 매우 느림 (목표: p95 < 500ms)

#### 2. 애플리케이션 로그 확인

```
[Hibernate] SELECT * FROM product ORDER BY sales_count DESC LIMIT 10
[Hibernate] Execution time: 1512ms
```

→ 쿼리 실행 시간이 대부분을 차지

#### 3. MySQL EXPLAIN 분석

```sql
EXPLAIN ANALYZE
SELECT * FROM product ORDER BY sales_count DESC LIMIT 10;
```

```
-> Table scan on product (cost=5021, rows=50000) (actual time=1512ms)
```

→ Full Table Scan 발생 (인덱스 없음)

#### 4. 개선 방안

```sql
-- 인덱스 추가
CREATE INDEX idx_product_sales_count ON product (sales_count DESC);
```

#### 5. 개선 후 재측정

```
http_req_duration..............: avg=45ms p95=120ms
```

→ **30배 이상 개선!**

---

## 다음 단계

1. 실제 부하 테스트 실행
2. 수집된 데이터 분석
3. 병목 지점 식별
4. 개선 방안 적용
5. 재측정 및 비교

모든 분석 결과는 **성능 개선 보고서**로 정리하세요.

---

## 참고 자료

- [MySQL Performance Schema](https://dev.mysql.com/doc/refman/8.0/en/performance-schema.html)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [JVM GC 튜닝 가이드](https://docs.oracle.com/javase/8/docs/technotes/guides/vm/gctuning/)
- [Percona Toolkit](https://www.percona.com/software/database-tools/percona-toolkit)
