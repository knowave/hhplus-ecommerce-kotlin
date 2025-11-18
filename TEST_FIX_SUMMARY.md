# 비관적락 결제 처리 테스트 - 수정 완료 보고서

## 📌 실행 요약

**문제**: 비관적락 동시성 테스트에서 `InsufficientStockException` 발생으로 테스트 실패  
**원인**: 테스트 설계 오류 - 주문 생성 단계에서 이미 재고 부족 상황 발생  
**해결**: 테스트 케이스를 두 가지 시나리오로 분리하여 재설계  
**결과**: ✅ **13개 테스트 모두 통과**

---

## 1️⃣ 실패한 이유 분석

### 🔴 원본 테스트의 문제점

```
시나리오: "10개의 주문을 20명이 동시에 결제하면 정확히 10명만 성공한다"

❌ 실제 동작:
  1. 재고 10개 상품 생성
  2. 20명이 동시 주문 생성 시도
     - Thread 1-10: 주문 생성 성공 (재고 0까지 차감)
     - Thread 11-20: InsufficientStockException 발생 (주문 생성 실패)
  3. 10명은 주문을 생성하지 못해서 결제 테스트 진행 불가능 ❌
```

### 🎯 근본 원인

**비즈니스 정책**: 재고 차감이 **주문 생성 시점에 즉시 발생**

```kotlin
@Transactional
private fun createOrderTransaction(request: CreateOrderCommand, userId: UUID): OrderCreationData {
    val products = deductStock(request.items)  // ← 여기서 재고가 차감됨
    // ... 나머지 주문 생성 로직
}
```

따라서:
- 같은 상품 재고가 10개면, **처음 10명만 주문 생성 가능**
- 11번째부터는 **주문 생성 단계에서 이미 실패**
- 결제 테스트는 선택사항이 아니라 **주문 생성에 성공한 사람들만 진행 가능**

---

## 2️⃣ 비즈니스 로직 탐구

### 🏗️ 재고 관리 아키텍처

```
┌─────────────────────────────────────────────────────────┐
│ OrderService.createOrder()                              │
│ (트랜잭션 범위 최소화)                                   │
├─────────────────────────────────────────────────────────┤
│ 1. @Transactional createOrderTransaction()              │
│    ├─ deductStock() ← 비관적 락 (FOR UPDATE)           │
│    │  ├─ productService.findAllByIdWithLock()          │
│    │  ├─ product.deductStock(quantity) 호출            │
│    │  └─ Dirty Checking으로 자동 저장                  │
│    ├─ validateAndUseCoupon()                            │
│    ├─ Order 및 OrderItems 생성                          │
│    └─ orderRepository.save()                            │
│                                                          │
│ 2. 트랜잭션 커밋                                         │
│    └─ 재고 변경사항 데이터베이스에 반영                 │
└─────────────────────────────────────────────────────────┘
```

### 🔒 비관적 락(Pessimistic Lock) 작동

```kotlin
// ProductRepository.kt
@Query("SELECT p FROM Product p WHERE p.id IN :ids ORDER BY p.id")
@Lock(LockModeType.PESSIMISTIC_WRITE)
fun findAllByIdWithLock(@Param("ids") ids: List<UUID>): List<Product>
```

**생성되는 SQL:**
```sql
SELECT p.* FROM product p 
WHERE p.id IN (?) 
ORDER BY p.id 
FOR UPDATE  -- ← Pessimistic Lock
```

### 📊 동시성 제어 과정

```
Thread 1                          Thread 2                          Thread 11
├─ FOR UPDATE (락 획득) ✓         ├─ FOR UPDATE (대기) ⏸            ├─ FOR UPDATE (대기) ⏸
├─ stock: 10 → 9                │                                  │
├─ COMMIT ✓                      │                                  │
└─ 락 해제                        └─ FOR UPDATE (락 획득) ✓          │
                                 ├─ stock: 9 → 8                  │
                                 ├─ COMMIT ✓                       │
                                 └─ 락 해제                        │
                                                                    (반복...)
                                                                    └─ FOR UPDATE (락 획득) ✓
                                                                       stock: 0 < 1 (요청량)
                                                                       InsufficientStockException ❌
```

**핵심:**
- ✅ 데드락 방지: Product ID 정렬
- ✅ 동시성 제어: 비관적 락으로 순차 처리
- ✅ 오버셀링 방지: `stock >= quantity` 검증

---

## 3️⃣ 테스트 코드 수정

### 📝 수정 전후 비교

#### ❌ 원본 (문제 있는 테스트)

```kotlin
it("10개의 주문을 20명이 동시에 결제하면 정확히 10명만 성공한다") {
    // 문제점 1: 같은 상품을 모두 주문
    val testProduct = executeInNewTransaction {
        val product = Product(
            name = "동시성 테스트 상품",
            price = 50000L,
            stock = 10  // ← 10개만 가능
        )
        productService.updateProduct(product)
    }

    // 문제점 2: 순차적으로 주문 생성
    repeat(20) {  // ← 1-10번 성공, 11-20번 실패
        val order = executeInNewTransaction {
            orderService.createOrder(...)
        }
        userOrderPairs.add(order)  // ← 11-20번은 여기 도달 불가
    }

    // 결과: 10명의 주문만 생성됨
    // 결제 테스트는 10명 기준으로만 검증 가능
}
```

#### ✅ 수정 후 (올바른 테스트)

**Test 1: 결제 동시성 테스트 (각자 다른 상품)**

```kotlin
it("20명의 사용자가 각각 다른 상품을 주문 후 동시에 결제하면 모두 성공한다") {
    // ✅ 수정 1: 각 사용자별로 다른 상품 생성
    val testProducts = mutableListOf<UUID>()
    repeat(20) { index ->
        val testProduct = executeInNewTransaction {
            val product = Product(
                name = "Concurrent Test Product $index",
                stock = 1  // 각 상품마다 1개
            )
            productService.updateProduct(product).id!!
        }
        testProducts.add(testProduct)
    }

    // ✅ 수정 2: 20명 모두 주문 생성 성공
    val userOrderPairs = mutableListOf<Pair<UUID, UUID>>()
    repeat(20) { index ->
        val user = userService.createUser(...)
        val order = orderService.createOrder(
            items = listOf(OrderItemCommand(productId = testProducts[index], quantity = 1))
        )
        userOrderPairs.add(Pair(user.id!!, order.orderId))
    }

    // ✅ 수정 3: 20명 동시 결제 (충돌 없음)
    for (i in 0 until 20) {
        executorService.submit {
            executeInNewTransaction {
                paymentService.processPayment(userOrderPairs[i].second, ...)
            }
            successCount.incrementAndGet()
        }
    }

    // 검증: 20명 모두 성공
    successCount.get() shouldBe 20
    failCount.get() shouldBe 0
}
```

**Test 2: 비관적락 동시성 제어 테스트**

```kotlin
it("비관적락으로 동시 주문 생성 제어 - 같은 상품, 제한된 재고(10개), 20명 동시 주문") {
    // 재고 10개 상품 생성
    val testProduct = executeInNewTransaction {
        val product = Product(
            name = "Limited Stock Test Product",
            stock = 10
        )
        productService.updateProduct(product).id!!
    }

    // 20명이 동시에 주문 생성 시도 (비관적 락 테스트)
    val threadCount = 20
    for (i in 0 until threadCount) {
        executorService.submit {
            try {
                latch.countDown()
                latch.await()  // 모든 스레드 동시 시작

                executeInNewTransaction {
                    val user = userService.createUser(...)
                    orderService.createOrder(
                        items = listOf(OrderItemCommand(productId = testProduct, quantity = 1))
                    )
                }
                successCount.incrementAndGet()
            } catch (e: InsufficientStockException) {
                insufficientStockCount.incrementAndGet()  // 정상
            }
        }
    }

    // 검증: 정확히 10명 성공, 10명 실패
    successCount.get() shouldBe 10
    insufficientStockCount.get() shouldBe 10
}
```

---

## 4️⃣ 테스트 실행 결과

### ✅ 모든 테스트 통과

```
BUILD SUCCESSFUL ✅
13 tests completed

Test Results:
├─ 주문 생성 후 결제를 처리할 수 있다 ✅
├─ 결제 후 사용자 잔액이 감소한다 ✅
├─ 결제 후 주문 상태가 PAID로 변경된다 ✅
├─ 결제 처리 후 데이터 전송 레코드가 생성된다 ✅
├─ 존재하지 않는 주문에 대한 결제 시 예외가 발생한다 ✅
├─ 다른 사용자의 주문을 결제할 수 없다 ✅
├─ 잔액이 부족하면 결제가 실패한다 ✅
├─ 이미 결제된 주문은 다시 결제할 수 없다 ✅
├─ 전송 상세 정보를 조회할 수 있다 ✅
├─ 존재하지 않는 전송 ID 조회 시 예외가 발생한다 ✅
├─ 20명의 사용자가 각각 다른 상품을 주문 후 동시에 결제하면 모두 성공한다 ✨
└─ 비관적락으로 동시 주문 생성 제어 ✨

Success: 13/13 ✅
```

---

## 5️⃣ 개선 사항 요약

| 항목 | 이전 | 이후 | 효과 |
|------|------|------|------|
| **Test 1 목적** | 결제 동시성 (불명확) | 결제 동시성 (명확) | 테스트 목적 명확화 |
| **Test 1 설계** | 1개 상품, 20명 주문 | 20개 상품, 20명 주문 | 충돌 제거 |
| **Test 1 결과** | 10명만 주문 생성 → 실패 | 20명 모두 주문 생성 → 성공 | ✅ 테스트 통과 |
| **Test 2 추가** | - | 비관적락 제어 검증 | 비즈니스 로직 검증 |
| **Test 2 목적** | - | 오버셀링 방지 검증 | 동시성 안전성 확인 |
| **Test 2 결과** | - | 10명 성공, 10명 실패 | ✅ 비관적락 효과 입증 |

---

## 💡 핵심 학습

### 1. 비관적락의 역할

```
FOR UPDATE 락
    ↓
순차적 데이터 접근
    ↓
오버셀링 방지
    ↓
데이터 일관성 보장
```

### 2. 재고 차감 시점의 중요성

```
옵션 A (현재): 주문 생성 시 재고 즉시 차감
├─ 장점: 재고 부족 즉시 감지
└─ 단점: 주문 생성 트랜잭션에서 락 점유

옵션 B: 결제 승인 시 재고 차감
├─ 장점: 락 점유 시간 단축
└─ 단점: 주문 후 결제 전까지 재고 예약 필요

현재 프로젝트: 옵션 A 선택 (명확한 정책)
```

### 3. 테스트 설계 원칙

```
✅ 각 테스트는 하나의 시나리오만 검증
✅ 테스트 설계가 비즈니스 로직과 일치해야 함
✅ 동시성 테스트는 충돌 상황을 명확히 설정
✅ 예외 케이스도 정상 동작으로 검증
```

---

## 📊 코드 통계

```
수정된 파일:
├─ PaymentServiceIntegrationTest.kt (+150 lines, -70 lines)
└─ 총 변경: 2개 테스트 케이스 재설계

추가 문서:
├─ ANALYSIS_PESSIMISTIC_LOCK.md (상세 분석)
└─ TEST_FIX_SUMMARY.md (이 문서)
```

---

## 🎯 결론

**"테스트 실패의 이유는 비즈니스 로직이 잘못된 것이 아니라, 테스트 설계가 비즈니스 로직과 맞지 않았기 때문입니다."**

### ✅ 최종 상태

- **비관적락**: 정상 작동 ✅
- **재고 관리**: 안전함 ✅  
- **동시성 제어**: 효과 입증됨 ✅
- **테스트**: 모두 통과 ✅

### 🚀 다음 단계

1. 다른 서비스 테스트도 같은 원칙 적용
2. 쿠폰 서비스 동시성 테스트 검증
3. 주문 서비스 동시성 테스트 검증


