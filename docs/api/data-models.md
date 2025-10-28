# 데이터 모델

이 문서는 이커머스 시스템의 데이터 모델을 정의합니다.

## 참조 문서
데이터베이스 스키마와 엔티티 관계에 대한 상세한 정보는 [DATABASE_SCHEMA.md](../DATABASE_SCHEMA.md)를 참고하세요.

---

## 비즈니스 도메인 모델

### 1. User (사용자)
사용자 계정 및 잔액 정보

**핵심 속성**:
- `id`: 사용자 고유 ID
- `balance`: 현재 잔액

**핵심 메서드**:
- `chargeBalance(amount)`: 잔액 충전
- `deductBalance(amount)`: 잔액 차감

**비즈니스 규칙**:
- 잔액은 0 이상이어야 함
- 차감 시 잔액 부족 검증 필요

---

### 2. Product (상품)
판매 상품 정보

**핵심 속성**:
- `id`: 상품 ID
- `name`: 상품명
- `price`: 가격
- `stock`: 재고 수량
- `category`: 카테고리

**핵심 메서드**:
- `decreaseStock(quantity)`: 재고 차감
- `increaseStock(quantity)`: 재고 복원

**비즈니스 규칙**:
- 재고는 0 이상이어야 함
- 재고 차감 시 충분한 재고 검증 필요
- 동시성 제어 필요 (낙관적 락)

---

### 3. Order (주문)
주문 정보 및 상태 관리

**핵심 속성**:
- `id`: 주문 ID
- `user`: 주문한 사용자
- `totalAmount`: 총 주문 금액
- `discountAmount`: 할인 금액
- `finalAmount`: 최종 결제 금액
- `status`: 주문 상태 (PENDING, PAID, CANCELLED)
- `items`: 주문 아이템 목록

**핵심 메서드**:
- `markAsPaid()`: 결제 완료 처리
- `cancel()`: 주문 취소
- `addItem(orderItem)`: 주문 아이템 추가

**비즈니스 규칙**:
- 주문은 최소 1개 이상의 아이템을 포함해야 함
- PENDING 상태에서만 결제 또는 취소 가능
- 금액 계산: `finalAmount = totalAmount - discountAmount`

---

### 4. OrderItem (주문 아이템)
주문에 포함된 개별 상품 정보

**핵심 속성**:
- `id`: 주문 아이템 ID
- `order`: 소속 주문
- `product`: 주문한 상품
- `quantity`: 주문 수량
- `unitPrice`: 주문 시점의 단가
- `subtotal`: 소계 (unitPrice × quantity)

**비즈니스 규칙**:
- 수량은 1 이상이어야 함
- `subtotal = unitPrice × quantity` 검증

---

### 5. Coupon (쿠폰 마스터)
쿠폰 발급 관리

**핵심 속성**:
- `id`: 쿠폰 ID
- `name`: 쿠폰명
- `discountRate`: 할인율 (%)
- `totalQuantity`: 총 발급 가능 수량
- `issuedQuantity`: 현재 발급된 수량
- `startDate`: 사용 시작일
- `endDate`: 사용 종료일
- `version`: 낙관적 락 버전

**핵심 메서드**:
- `canIssue()`: 발급 가능 여부 확인
- `issue()`: 쿠폰 발급 (issuedQuantity 증가)
- `isValid()`: 유효기간 검증

**비즈니스 규칙**:
- `issuedQuantity < totalQuantity` 조건 체크
- 동시성 제어 필요 (낙관적 락)
- 할인율은 1~100 사이

---

### 6. UserCoupon (사용자 쿠폰)
사용자별 쿠폰 보유 정보

**핵심 속성**:
- `id`: 사용자 쿠폰 ID
- `user`: 쿠폰 소유자
- `coupon`: 쿠폰 마스터 정보
- `status`: 쿠폰 상태 (AVAILABLE, USED, EXPIRED)
- `issuedAt`: 발급 일시
- `usedAt`: 사용 일시
- `expiresAt`: 만료 일시

**핵심 메서드**:
- `use()`: 쿠폰 사용 (AVAILABLE → USED)
- `restore()`: 쿠폰 복원 (USED → AVAILABLE or EXPIRED)
- `isExpired()`: 만료 여부 확인
- `checkExpired()`: 만료 상태 갱신

**비즈니스 규칙**:
- 1인 1매 제한 (user_id, coupon_id 조합 유니크)
- AVAILABLE 상태에서만 사용 가능
- 만료된 쿠폰은 사용 불가
- 발급 후 30일 자동 만료

---

### 7. DataTransmission (데이터 전송 이력)
외부 시스템 데이터 전송 관리 (Outbox Pattern)

**핵심 속성**:
- `id`: 전송 ID
- `order`: 전송할 주문
- `payload`: 전송 데이터 (JSON)
- `status`: 전송 상태 (PENDING, SUCCESS, FAILED)
- `attempts`: 재시도 횟수
- `sentAt`: 전송 완료 일시

**핵심 메서드**:
- `markAsSuccess()`: 전송 성공 처리
- `markAsFailed()`: 전송 실패 처리
- `canRetry()`: 재시도 가능 여부
- `retry()`: 재시도 처리

**비즈니스 규칙**:
- 주문이 PAID 상태로 변경될 때 생성
- 최대 3회 재시도
- 재시도 간격: 1분, 5분, 15분 (지수 백오프)
- 중복 전송 방지 (order_id 기반)

---

## 엔티티 관계

```
User (1) ──── (N) Order
User (1) ──── (N) UserCoupon
Order (1) ──── (N) OrderItem
Order (1) ──── (N) DataTransmission
Product (1) ──── (N) OrderItem
Coupon (1) ──── (N) UserCoupon
```

---

## 금액 계산 공식

### 주문 아이템 소계
```
subtotal = unitPrice × quantity
```

### 총 주문 금액
```
totalAmount = Σ(subtotal)
```

### 할인 금액
```
discountAmount = totalAmount × (discountRate ÷ 100) [소수점 내림]
```

### 최종 결제 금액
```
finalAmount = totalAmount - discountAmount
```

---

## 상태 전이

### Order 상태 전이
```
PENDING (주문 생성)
   ↓
   ├─→ PAID (결제 성공)
   └─→ CANCELLED (결제 실패 또는 취소)
```

### UserCoupon 상태 전이
```
AVAILABLE (발급)
   ↓
   ├─→ USED (사용)
   │     ↓
   │   AVAILABLE (복원, 만료 전)
   │     ↓
   └─→ EXPIRED (만료)
```

### DataTransmission 상태 전이
```
PENDING (생성)
   ↓
   ├─→ SUCCESS (전송 성공)
   └─→ FAILED (전송 실패)
         ↓
       PENDING (재시도, 최대 3회)
         ↓
       FAILED (최종 실패)
```

---

## 동시성 제어

### 재고 관리 (Product)
- 낙관적 락 사용 (향후 적용)
- 재고 차감 시 동시 접근 제어

### 쿠폰 발급 (Coupon)
- 낙관적 락 사용 (`@Version`)
- `issuedQuantity` 증가 시 버전 충돌 체크
- 선착순 발급 보장

---

## 참고
- 더 상세한 데이터베이스 스키마는 [DATABASE_SCHEMA.md](../DATABASE_SCHEMA.md) 참고
- 비즈니스 정책은 [BUSINESS_POLICIES.md](../../.claude/docs/BUSINESS_POLICIES.md) 참고
- API 명세는 [api-specification.md](./api-specification.md) 참고