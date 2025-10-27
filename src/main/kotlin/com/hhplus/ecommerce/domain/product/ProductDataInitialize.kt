package com.hhplus.ecommerce.domain.product

import com.hhplus.ecommerce.domain.product.entity.Product
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.math.BigDecimal
import java.util.UUID

@Configuration
class ProductDataInitialize {

    @Bean
    fun productDataInitializer(productRepository: ProductRepository) = CommandLineRunner {
        if (productRepository.count() === 0L) {
            productRepository.saveAll(
                listOf(
                    Product(
                        id = UUID.randomUUID().toString(),
                        name = "MacBook Pro 16",
                        description = "Apple M3 Max 칩, 36GB 통합 메모리, 512GB SSD",
                        price = BigDecimal("3690000"),
                        stock = 15,
                        category = "전자기기"
                    ),
                    Product(
                        id = UUID.randomUUID().toString(),
                        name = "iPhone 15 Pro",
                        description = "128GB, 티타늄 블랙, A17 Pro 칩",
                        price = BigDecimal("1550000"),
                        stock = 50,
                        category = "전자기기"
                    ),
                    Product(
                        id = UUID.randomUUID().toString(),
                        name = "AirPods Pro 2세대",
                        description = "액티브 노이즈 캔슬링, USB-C 충전 케이스",
                        price = BigDecimal("359000"),
                        stock = 100,
                        category = "전자기기"
                    ),
                    Product(
                        id = UUID.randomUUID().toString(),
                        name = "나이키 에어맥스",
                        description = "운동화, 사이즈 270mm, 블랙 컬러",
                        price = BigDecimal("189000"),
                        stock = 30,
                        category = "의류"
                    ),
                    Product(
                        id = UUID.randomUUID().toString(),
                        name = "삼성 갤럭시 탭 S9",
                        description = "11인치, 128GB, Wi-Fi 모델",
                        price = BigDecimal("799000"),
                        stock = 25,
                        category = "전자기기"
                    ),
                    Product(
                        id = UUID.randomUUID().toString(),
                        name = "다이슨 무선청소기 V15",
                        description = "레이저 감지 기술, 60분 사용 가능",
                        price = BigDecimal("890000"),
                        stock = 12,
                        category = "가전제품"
                    ),
                    Product(
                        id = UUID.randomUUID().toString(),
                        name = "LG 그램 노트북 17",
                        description = "17인치, Intel i7, 16GB RAM, 512GB SSD",
                        price = BigDecimal("2190000"),
                        stock = 20,
                        category = "전자기기"
                    ),
                    Product(
                        id = UUID.randomUUID().toString(),
                        name = "소니 WH-1000XM5",
                        description = "무선 노이즈 캔슬링 헤드폰, 30시간 재생",
                        price = BigDecimal("449000"),
                        stock = 40,
                        category = "전자기기"
                    ),
                    Product(
                        id = UUID.randomUUID().toString(),
                        name = "아디다스 트레이닝 세트",
                        description = "상하의 세트, M 사이즈, 네이비 컬러",
                        price = BigDecimal("129000"),
                        stock = 60,
                        category = "의류"
                    ),
                    Product(
                        id = UUID.randomUUID().toString(),
                        name = "쿠쿠 전기압력밥솥",
                        description = "10인용, IH 열판, 음성 안내 기능",
                        price = BigDecimal("520000"),
                        stock = 18,
                        category = "가전제품"
                    )
                )
            )
        }
    }
}