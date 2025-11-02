# Payment API 문서

## 개요
주문 결제 처리 및 데이터 전송 관리 API를 제공합니다. 잔액 기반 결제와 외부 시스템 연동을 지원합니다.

## 기본 경로
```
/api/payments
```

---

## API 목록

### 1. 결제 처리
**PENDING 상태의 주문을 결제합니다.**

#### 엔드포인트
```
POST /api/orders/{orderId}/payment
```

#### 경로 파라미터
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| orderId | Long | O | 주문 ID |

#### 요청 본문
```json
{
  "userId": 1
}
```

#### 요청 필드
| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| userId | Long | O | 사용자 ID |

#### 응답 (200 OK)
```json
{
  "paymentId": 5001,
  "orderId": 1001,
  "orderNumber": "ORD-20251029-1001",
  "userId": 1,
  "amount": 350100,
  "paymentStatus": "SUCCESS",
  "orderStatus": "PAID",
  "balance": {
    "previousBalance": 500000,
    "paidAmount": 350100,
    "remainingBalance": 149900
  },
  "dataTransmission": {
    "transmissionId": 7001,
    "status": "PENDING",
    "scheduledAt": "2025-10-29T10:31:00"
  },
  "paidAt": "2025-10-29T10:30:00"
}
```

#### 응답 필드
| 필드 | 타입 | 설명 |
|------|------|------|
| paymentId | Long | 결제 ID |
| orderId | Long | 주문 ID |
| orderNumber | String | 주문 번호 |
| userId | Long | 사용자 ID |
| amount | Long | 결제 금액 |
| paymentStatus | String | 결제 상태 (SUCCESS, FAILED) |
| orderStatus | String | 주문 상태 (PAID, CANCELLED) |
| balance | Object | 잔액 정보 |
| balance.previousBalance | Long | 결제 전 잔액 |
| balance.paidAmount | Long | 결제 금액 |
| balance.remainingBalance | Long | 결제 후 잔액 |
| dataTransmission | Object | 데이터 전송 정보 |
| dataTransmission.transmissionId | Long | 전송 ID |
| dataTransmission.status | String | 전송 상태 |
| dataTransmission.scheduledAt | String | 전송 예정 시간 |
| paidAt | String | 결제 완료 시간 |

#### 비즈니스 규칙
**결제 프로세스:**
1. **주문 상태 확인** - PENDING 상태인지 검증
2. **사용자 잔액 확인** - 결제 금액 이상의 잔액이 있는지 검증
3. **잔액 차감** - 사용자 잔액에서 결제 금액 차감
4. **주문 상태 변경** - PENDING → PAID
5. **데이터 전송 레코드 생성** - Outbox 패턴으로 전송 예약
6. **결제 이력 기록** - Payment 엔티티 생성

**결제 성공 조건:**
- 주문이 PENDING 상태여야 함
- 사용자 잔액이 결제 금액 이상이어야 함
- 이미 결제된 주문은 중복 결제 불가

**결제 실패 시 보상 처리:**
1. **재고 복원** - 차감된 재고 복원
2. **쿠폰 복원** - 사용된 쿠폰 AVAILABLE로 복원
3. **주문 취소** - 주문 상태 CANCELLED로 변경

#### 에러 응답
| 상태 코드 | 에러 코드 | 설명 |
|----------|----------|------|
| 400 | INSUFFICIENT_BALANCE | 잔액 부족 |
| 400 | INVALID_ORDER_STATUS | 유효하지 않은 주문 상태 |
| 400 | ALREADY_PAID | 이미 결제된 주문 |
| 404 | ORDER_NOT_FOUND | 주문을 찾을 수 없음 |
| 403 | FORBIDDEN | 다른 사용자의 주문 |

---

### 2. 결제 정보 조회
**특정 결제의 상세 정보를 조회합니다.**

#### 엔드포인트
```
GET /api/payments/{paymentId}
```

#### 경로 파라미터
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| paymentId | Long | O | 결제 ID |

#### 쿼리 파라미터
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| userId | Long | O | 사용자 ID (권한 확인용) |

#### 응답 (200 OK)
```json
{
  "paymentId": 5001,
  "orderId": 1001,
  "orderNumber": "ORD-20251029-1001",
  "userId": 1,
  "amount": 350100,
  "paymentStatus": "SUCCESS",
  "paidAt": "2025-10-29T10:30:00",
  "dataTransmission": {
    "transmissionId": 7001,
    "status": "SUCCESS",
    "sentAt": "2025-10-29T10:31:00",
    "attempts": 1
  }
}
```

#### 에러 응답
| 상태 코드 | 에러 코드 | 설명 |
|----------|----------|------|
| 404 | PAYMENT_NOT_FOUND | 결제 정보를 찾을 수 없음 |
| 403 | FORBIDDEN | 다른 사용자의 결제 |

---

### 3. 주문별 결제 내역 조회
**특정 주문의 결제 내역을 조회합니다.**

#### 엔드포인트
```
GET /api/orders/{orderId}/payment
```

#### 경로 파라미터
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| orderId | Long | O | 주문 ID |

#### 쿼리 파라미터
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| userId | Long | O | 사용자 ID (권한 확인용) |

#### 응답 (200 OK)
```json
{
  "orderId": 1001,
  "orderNumber": "ORD-20251029-1001",
  "payment": {
    "paymentId": 5001,
    "amount": 350100,
    "status": "SUCCESS",
    "paidAt": "2025-10-29T10:30:00"
  }
}
```

#### 응답 (주문이 결제되지 않은 경우)
```json
{
  "orderId": 1001,
  "orderNumber": "ORD-20251029-1001",
  "payment": null
}
```

---

### 4. 데이터 전송 상태 조회
**특정 주문의 외부 시스템 전송 상태를 조회합니다.**

#### 엔드포인트
```
GET /api/data-transmissions/{transmissionId}
```

#### 경로 파라미터
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| transmissionId | Long | O | 전송 ID |

#### 응답 (200 OK)
```json
{
  "transmissionId": 7001,
  "orderId": 1001,
  "orderNumber": "ORD-20251029-1001",
  "status": "SUCCESS",
  "attempts": 1,
  "maxAttempts": 3,
  "createdAt": "2025-10-29T10:30:00",
  "sentAt": "2025-10-29T10:31:00",
  "nextRetryAt": null,
  "errorMessage": null
}
```

#### 응답 필드
| 필드 | 타입 | 설명 |
|------|------|------|
| transmissionId | Long | 전송 ID |
| orderId | Long | 주문 ID |
| orderNumber | String | 주문 번호 |
| status | String | 전송 상태 (PENDING, SUCCESS, FAILED) |
| attempts | Integer | 시도 횟수 |
| maxAttempts | Integer | 최대 시도 횟수 |
| createdAt | String | 레코드 생성 시간 |
| sentAt | String | 전송 성공 시간 (성공 시에만) |
| nextRetryAt | String | 다음 재시도 예정 시간 (실패 시) |
| errorMessage | String | 에러 메시지 (실패 시) |

#### 전송 상태
- `PENDING`: 전송 대기 중
- `SUCCESS`: 전송 성공
- `FAILED`: 전송 실패 (최대 재시도 횟수 초과)

---

### 5. 실패한 데이터 전송 재시도
**실패한 데이터 전송을 수동으로 재시도합니다. (관리자 전용)**

#### 엔드포인트
```
POST /api/data-transmissions/{transmissionId}/retry
```

#### 경로 파라미터
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| transmissionId | Long | O | 전송 ID |

#### 응답 (200 OK)
```json
{
  "transmissionId": 7001,
  "status": "SUCCESS",
  "retriedAt": "2025-10-29T11:00:00",
  "attempts": 2
}
```

#### 비즈니스 규칙
**데이터 전송 정책 (Outbox Pattern):**

1. **전송 레코드 생성**
   - 결제 완료 시 주문과 동일한 트랜잭션에서 생성
   - 초기 상태: PENDING

2. **재시도 정책**
   - 최대 재시도 횟수: 3회
   - 재시도 간격 (지수 백오프):
     - 1차 실패: 1분 후 재시도
     - 2차 실패: 5분 후 재시도
     - 3차 실패: 15분 후 재시도

3. **전송 처리**
   - 배치 작업 또는 스케줄러가 PENDING 상태 레코드 조회
   - 외부 시스템으로 주문 데이터 전송
   - 성공 시: status = SUCCESS, sent_at 기록
   - 실패 시: attempts 증가, next_retry_at 계산

4. **최종 실패 처리**
   - 3회 재시도 후에도 실패 시 status = FAILED
   - 관리자 알림 발송
   - 수동 재시도 필요

5. **중복 전송 방지**
   - order_id로 중복 체크
   - SUCCESS 상태는 재전송하지 않음
   - 외부 시스템에서 멱등성 보장 (order_id 기반)

#### 에러 응답
| 상태 코드 | 에러 코드 | 설명 |
|----------|----------|------|
| 400 | ALREADY_SUCCESS | 이미 전송 성공한 레코드 |
| 404 | TRANSMISSION_NOT_FOUND | 전송 레코드를 찾을 수 없음 |

---

## 공통 에러 응답 형식
```json
{
  "timestamp": "2025-10-29T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "code": "INSUFFICIENT_BALANCE",
  "message": "잔액이 부족합니다. (현재 잔액: 100,000원, 필요 금액: 350,100원)",
  "path": "/api/orders/1001/payment"
}
```

## 주요 에러 코드 목록
| 에러 코드 | HTTP 상태 | 설명 |
|----------|----------|------|
| PAYMENT_NOT_FOUND | 404 | 결제 정보를 찾을 수 없음 |
| ORDER_NOT_FOUND | 404 | 주문을 찾을 수 없음 |
| TRANSMISSION_NOT_FOUND | 404 | 전송 레코드를 찾을 수 없음 |
| INSUFFICIENT_BALANCE | 400 | 잔액 부족 |
| INVALID_ORDER_STATUS | 400 | 유효하지 않은 주문 상태 |
| ALREADY_PAID | 400 | 이미 결제된 주문 |
| ALREADY_SUCCESS | 400 | 이미 전송 성공 |
| FORBIDDEN | 403 | 권한 없음 |

## 결제 및 전송 프로세스
```
주문 생성 (PENDING)
    ↓
결제 처리 시도
    ↓
잔액 확인
    ├─ 부족 → 결제 실패 → 보상 처리 (재고/쿠폰 복원) → 주문 취소 (CANCELLED)
    └─ 충분 → 잔액 차감 → 주문 완료 (PAID) → 데이터 전송 레코드 생성 (PENDING)
                                                    ↓
                                              외부 시스템 전송
                                                    ↓
                                            ┌───────┴───────┐
                                          성공              실패
                                            ↓                ↓
                                        SUCCESS       재시도 (최대 3회)
                                                            ↓
                                                    3회 실패 시 FAILED
```