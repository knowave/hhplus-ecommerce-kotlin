# User API 문서

## 개요
사용자 관리 및 잔액 관리 API를 제공합니다.

## 기본 경로
```
/api/users
```

---

## API 목록

### 1. 사용자 잔액 조회
**사용자의 현재 잔액을 조회합니다.**

#### 엔드포인트
```
GET /api/users/{userId}/balance
```

#### 경로 파라미터
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| userId | Long | O | 사용자 ID |

#### 응답 (200 OK)
```json
{
  "userId": 1,
  "balance": 50000,
  "currency": "KRW",
  "lastUpdatedAt": "2025-10-29T10:30:00"
}
```

#### 응답 필드
| 필드 | 타입 | 설명 |
|------|------|------|
| userId | Long | 사용자 ID |
| balance | Long | 현재 잔액 (원 단위) |
| currency | String | 통화 단위 |
| lastUpdatedAt | String | 마지막 업데이트 시간 (ISO 8601) |

#### 에러 응답
| 상태 코드 | 에러 코드 | 설명 |
|----------|----------|------|
| 404 | USER_NOT_FOUND | 사용자를 찾을 수 없음 |

---

### 2. 사용자 잔액 충전
**사용자의 잔액을 충전합니다.**

#### 엔드포인트
```
POST /api/users/{userId}/balance/charge
```

#### 경로 파라미터
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| userId | Long | O | 사용자 ID |

#### 요청 본문
```json
{
  "amount": 10000
}
```

#### 요청 필드
| 필드 | 타입 | 필수 | 설명 | 제약사항 |
|------|------|------|------|---------|
| amount | Long | O | 충전할 금액 | 양수, 최소 1,000원, 최대 1,000,000원 |

#### 응답 (200 OK)
```json
{
  "userId": 1,
  "previousBalance": 50000,
  "chargedAmount": 10000,
  "currentBalance": 60000,
  "chargedAt": "2025-10-29T10:35:00"
}
```

#### 응답 필드
| 필드 | 타입 | 설명 |
|------|------|------|
| userId | Long | 사용자 ID |
| previousBalance | Long | 충전 전 잔액 |
| chargedAmount | Long | 충전 금액 |
| currentBalance | Long | 충전 후 잔액 |
| chargedAt | String | 충전 시간 (ISO 8601) |

#### 비즈니스 규칙
- 충전 금액은 1,000원 단위로만 가능
- 한 번에 최소 1,000원 ~ 최대 1,000,000원까지 충전 가능
- 충전 후 총 잔액은 10,000,000원을 초과할 수 없음

#### 에러 응답
| 상태 코드 | 에러 코드 | 설명 |
|----------|----------|------|
| 400 | INVALID_AMOUNT | 충전 금액이 유효하지 않음 |
| 400 | BALANCE_LIMIT_EXCEEDED | 최대 잔액 한도 초과 |
| 404 | USER_NOT_FOUND | 사용자를 찾을 수 없음 |

---

### 3. 사용자 정보 조회
**사용자의 기본 정보를 조회합니다.**

#### 엔드포인트
```
GET /api/users/{userId}
```

#### 경로 파라미터
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| userId | Long | O | 사용자 ID |

#### 응답 (200 OK)
```json
{
  "userId": 1,
  "email": "user@example.com",
  "name": "홍길동",
  "balance": 50000,
  "createdAt": "2025-01-01T00:00:00",
  "updatedAt": "2025-10-29T10:30:00"
}
```

#### 응답 필드
| 필드 | 타입 | 설명 |
|------|------|------|
| userId | Long | 사용자 ID |
| email | String | 이메일 주소 |
| name | String | 사용자 이름 |
| balance | Long | 현재 잔액 |
| createdAt | String | 계정 생성 시간 |
| updatedAt | String | 마지막 업데이트 시간 |

#### 에러 응답
| 상태 코드 | 에러 코드 | 설명 |
|----------|----------|------|
| 404 | USER_NOT_FOUND | 사용자를 찾을 수 없음 |

---

## 공통 에러 응답 형식
모든 에러 응답은 다음 형식을 따릅니다:

```json
{
  "timestamp": "2025-10-29T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "code": "INVALID_AMOUNT",
  "message": "충전 금액은 1,000원 이상이어야 합니다.",
  "path": "/api/users/1/balance/charge"
}
```

## 주요 에러 코드 목록
| 에러 코드 | HTTP 상태 | 설명 |
|----------|----------|------|
| USER_NOT_FOUND | 404 | 사용자를 찾을 수 없음 |
| INVALID_AMOUNT | 400 | 유효하지 않은 금액 |
| BALANCE_LIMIT_EXCEEDED | 400 | 잔액 한도 초과 |
| INSUFFICIENT_BALANCE | 400 | 잔액 부족 |