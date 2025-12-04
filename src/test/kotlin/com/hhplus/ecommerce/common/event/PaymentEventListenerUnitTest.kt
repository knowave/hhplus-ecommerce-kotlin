package com.hhplus.ecommerce.common.event

import com.hhplus.ecommerce.application.payment.DataPlatformService
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.*
import java.util.UUID

class PaymentEventListenerUnitTest : DescribeSpec({
    lateinit var dataPlatformService: DataPlatformService
    lateinit var paymentEventListener: PaymentEventListener

    beforeEach {
        dataPlatformService = mockk(relaxed = true)
        paymentEventListener = PaymentEventListener(dataPlatformService)
    }

    describe("PaymentEventListener 단위 테스트") {
        context("결제 완료 이벤트 처리") {
            it("결제 완료 이벤트를 수신하면 DataPlatformService를 호출한다") {
                // given
                val paymentId = UUID.randomUUID()
                val orderId = UUID.randomUUID()
                val userId = UUID.randomUUID()
                val amount = 50000L

                val event = PaymentCompletedEvent(
                    paymentId = paymentId,
                    orderId = orderId,
                    userId = userId,
                    amount = amount
                )

                // when
                paymentEventListener.handlePaymentCompleted(event)

                // then
                verify(exactly = 1) { dataPlatformService.sendPaymentData(event) }
            }

            it("여러 이벤트를 순차적으로 처리한다") {
                // given
                val events = (1..5).map {
                    PaymentCompletedEvent(
                        paymentId = UUID.randomUUID(),
                        orderId = UUID.randomUUID(),
                        userId = UUID.randomUUID(),
                        amount = 10000L * it
                    )
                }

                // when
                events.forEach { event ->
                    paymentEventListener.handlePaymentCompleted(event)
                }

                // then
                verify(exactly = 5) { dataPlatformService.sendPaymentData(any()) }
            }

            it("DataPlatformService에서 예외가 발생해도 리스너는 예외를 catch한다") {
                // given
                val event = PaymentCompletedEvent(
                    paymentId = UUID.randomUUID(),
                    orderId = UUID.randomUUID(),
                    userId = UUID.randomUUID(),
                    amount = 50000L
                )

                every { dataPlatformService.sendPaymentData(any()) } throws RuntimeException("External API failed")

                // when - 예외가 발생해도 리스너 내부에서 catch 되어야 함
                // 실제로 PaymentEventListener에서 try-catch로 감싸고 있음
                paymentEventListener.handlePaymentCompleted(event)

                // then
                verify(exactly = 1) { dataPlatformService.sendPaymentData(event) }
            }
        }

        context("이벤트 데이터 전달 검증") {
            it("이벤트의 모든 필드가 DataPlatformService에 그대로 전달된다") {
                // given
                val paymentId = UUID.randomUUID()
                val orderId = UUID.randomUUID()
                val userId = UUID.randomUUID()
                val amount = 123456L

                val event = PaymentCompletedEvent(
                    paymentId = paymentId,
                    orderId = orderId,
                    userId = userId,
                    amount = amount
                )

                val capturedEvent = slot<PaymentCompletedEvent>()
                every { dataPlatformService.sendPaymentData(capture(capturedEvent)) } just Runs

                // when
                paymentEventListener.handlePaymentCompleted(event)

                // then
                capturedEvent.captured.paymentId shouldBe paymentId
                capturedEvent.captured.orderId shouldBe orderId
                capturedEvent.captured.userId shouldBe userId
                capturedEvent.captured.amount shouldBe amount
            }
        }
    }
})

private infix fun Any?.shouldBe(expected: Any?) {
    if (this != expected) {
        throw AssertionError("Expected $expected but was $this")
    }
}

