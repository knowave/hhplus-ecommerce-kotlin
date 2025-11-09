package com.hhplus.ecommerce.infrastructure.product

import com.hhplus.ecommerce.domain.product.ProductJpaRepository
import com.hhplus.ecommerce.domain.product.entity.Product
import com.hhplus.ecommerce.domain.product.entity.ProductCategory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ProductDataInitializer {

    @Bean
    fun productDataInitializerRunner(productRepository: ProductJpaRepository) = CommandLineRunner {
        if (productRepository.count() == 0L) {
            productRepository.saveAll<Product>(
                listOf<Product>(
                    Product(
                        name = "노트북 ABC",
                        description = "고성능 노트북으로 멀티태스킹에 최적화되어 있습니다.",
                        price = 1500000L,
                        stock = 50,
                        category = ProductCategory.ELECTRONICS,
                        specifications = mapOf("cpu" to "Intel i7", "ram" to "16GB", "storage" to "512GB SSD"),
                        salesCount = 150,
                    ),
                    Product(
                        name = "스마트폰 XYZ",
                        description = "최신 플래그십 스마트폰",
                        price = 1200000L,
                        stock = 100,
                        category = ProductCategory.ELECTRONICS,
                        specifications = mapOf("display" to "6.5inch OLED", "camera" to "108MP", "battery" to "5000mAh"),
                        salesCount = 200,
                    ),
                    Product(
                        name = "무선 이어폰 XYZ",
                        description = "노이즈 캔슬링 기능이 탑재된 프리미엄 이어폰",
                        price = 150000L,
                        stock = 80,
                        category = ProductCategory.ELECTRONICS,
                        specifications = mapOf("battery" to "24 hours", "bluetooth" to "5.2", "anc" to "active"),
                        salesCount = 250,
                    ),
                    Product(
                        name = "운동화 ABC",
                        description = "편안한 착용감의 러닝화",
                        price = 89000L,
                        stock = 45,
                        category = ProductCategory.FASHION,
                        specifications = mapOf("size" to "230-290mm", "material" to "mesh"),
                        salesCount = 180,
                    ),
                    Product(
                        name = "청바지",
                        description = "슬림핏 청바지",
                        price = 79000L,
                        stock = 200,
                        category = ProductCategory.FASHION,
                        specifications = mapOf("fit" to "slim", "material" to "denim"),
                        salesCount = 120,
                    ),
                    Product(
                        name = "유기농 쌀 10kg",
                        description = "100% 국내산 유기농 쌀",
                        price = 45000L,
                        stock = 300,
                        category = ProductCategory.FOOD,
                        specifications = mapOf("origin" to "국내산", "weight" to "10kg", "organic" to "yes"),
                        salesCount = 90,
                    ),
                    Product(
                        name = "코틀린 인 액션",
                        description = "코틀린 프로그래밍 완벽 가이드",
                        price = 36000L,
                        stock = 150,
                        category = ProductCategory.BOOKS,
                        specifications = mapOf("pages" to "488", "publisher" to "에이콘", "language" to "한국어"),
                        salesCount = 75,
                    ),
                    Product(
                        name = "LED 스탠드",
                        description = "눈부심 없는 학습용 LED 스탠드",
                        price = 65000L,
                        stock = 80,
                        category = ProductCategory.HOME,
                        specifications = mapOf("color_temp" to "3000K-6500K", "brightness" to "1000lm", "power" to "12W"),
                        salesCount = 60,
                    ),
                    Product(
                        name = "요가 매트",
                        description = "미끄럼 방지 요가 매트",
                        price = 35000L,
                        stock = 120,
                        category = ProductCategory.SPORTS,
                        specifications = mapOf("thickness" to "10mm", "material" to "TPE", "size" to "183x61cm"),
                        salesCount = 95,
                    ),
                    Product(
                        name = "수분 크림",
                        description = "24시간 촉촉한 수분 크림",
                        price = 42000L,
                        stock = 90,
                        category = ProductCategory.BEAUTY,
                        specifications = mapOf("volume" to "50ml", "spf" to "30", "type" to "moisturizer"),
                        salesCount = 110,
                    ),
                    Product(
                        name = "립스틱",
                        description = "롱래스팅 매트 립스틱",
                        price = 28000L,
                        stock = 150,
                        category = ProductCategory.BEAUTY,
                        specifications = mapOf("color" to "coral pink", "finish" to "matte", "weight" to "3.5g"),
                        salesCount = 130,
                    )
                )
            )
        }
    }
}