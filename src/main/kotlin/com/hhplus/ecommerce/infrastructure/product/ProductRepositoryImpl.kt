package com.hhplus.ecommerce.infrastructure.product

import com.hhplus.ecommerce.model.product.Product
import org.springframework.stereotype.Repository

@Repository
class ProductRepositoryImpl : ProductRepository {

    // Mock 데이터 저장소 (추후 JPA로 대체)
    private val products: MutableMap<Long, Product> = mutableMapOf(
        // ELECTRONICS
        1L to Product(
            id = 1L,
            name = "노트북 ABC",
            description = "고성능 노트북으로 멀티태스킹에 최적화되어 있습니다.",
            price = 1500000L,
            stock = 50,
            category = ProductCategory.ELECTRONICS,
            specifications = mapOf("cpu" to "Intel i7", "ram" to "16GB", "storage" to "512GB SSD"),
            salesCount = 150,
            createdAt = "2025-10-01T00:00:00",
            updatedAt = "2025-10-29T10:00:00"
        ),
        2L to Product(
            id = 2L,
            name = "스마트폰 XYZ",
            description = "최신 플래그십 스마트폰",
            price = 1200000L,
            stock = 100,
            category = ProductCategory.ELECTRONICS,
            specifications = mapOf("display" to "6.5inch OLED", "camera" to "108MP", "battery" to "5000mAh"),
            salesCount = 200,
            createdAt = "2025-10-05T00:00:00",
            updatedAt = "2025-10-28T15:00:00"
        ),
        15L to Product(
            id = 15L,
            name = "무선 이어폰 XYZ",
            description = "노이즈 캔슬링 기능이 탑재된 프리미엄 이어폰",
            price = 150000L,
            stock = 80,
            category = ProductCategory.ELECTRONICS,
            specifications = mapOf("battery" to "24 hours", "bluetooth" to "5.2", "anc" to "active"),
            salesCount = 250,
            createdAt = "2025-10-10T00:00:00",
            updatedAt = "2025-10-29T12:00:00"
        ),

        // FASHION
        7L to Product(
            id = 7L,
            name = "운동화 ABC",
            description = "편안한 착용감의 러닝화",
            price = 89000L,
            stock = 45,
            category = ProductCategory.FASHION,
            specifications = mapOf("size" to "230-290mm", "material" to "mesh"),
            salesCount = 180,
            createdAt = "2025-10-08T00:00:00",
            updatedAt = "2025-10-27T09:00:00"
        ),
        3L to Product(
            id = 3L,
            name = "청바지",
            description = "슬림핏 청바지",
            price = 79000L,
            stock = 200,
            category = ProductCategory.FASHION,
            specifications = mapOf("fit" to "slim", "material" to "denim"),
            salesCount = 120,
            createdAt = "2025-10-03T00:00:00",
            updatedAt = "2025-10-26T10:00:00"
        ),

        // FOOD
        4L to Product(
            id = 4L,
            name = "유기농 쌀 10kg",
            description = "100% 국내산 유기농 쌀",
            price = 45000L,
            stock = 300,
            category = ProductCategory.FOOD,
            specifications = mapOf("origin" to "국내산", "weight" to "10kg", "organic" to "yes"),
            salesCount = 90,
            createdAt = "2025-10-02T00:00:00",
            updatedAt = "2025-10-25T14:00:00"
        ),

        // BOOKS
        5L to Product(
            id = 5L,
            name = "코틀린 인 액션",
            description = "코틀린 프로그래밍 완벽 가이드",
            price = 36000L,
            stock = 150,
            category = ProductCategory.BOOKS,
            specifications = mapOf("pages" to "488", "publisher" to "에이콘", "language" to "한국어"),
            salesCount = 75,
            createdAt = "2025-09-20T00:00:00",
            updatedAt = "2025-10-24T11:00:00"
        ),

        // HOME
        6L to Product(
            id = 6L,
            name = "LED 스탠드",
            description = "눈부심 없는 학습용 LED 스탠드",
            price = 65000L,
            stock = 80,
            category = ProductCategory.HOME,
            specifications = mapOf("color_temp" to "3000K-6500K", "brightness" to "1000lm", "power" to "12W"),
            salesCount = 60,
            createdAt = "2025-10-07T00:00:00",
            updatedAt = "2025-10-23T16:00:00"
        ),

        // SPORTS
        8L to Product(
            id = 8L,
            name = "요가 매트",
            description = "미끄럼 방지 요가 매트",
            price = 35000L,
            stock = 120,
            category = ProductCategory.SPORTS,
            specifications = mapOf("thickness" to "10mm", "material" to "TPE", "size" to "183x61cm"),
            salesCount = 95,
            createdAt = "2025-10-12T00:00:00",
            updatedAt = "2025-10-22T13:00:00"
        ),

        // BEAUTY
        9L to Product(
            id = 9L,
            name = "수분 크림",
            description = "24시간 촉촉한 수분 크림",
            price = 42000L,
            stock = 90,
            category = ProductCategory.BEAUTY,
            specifications = mapOf("volume" to "50ml", "spf" to "30", "type" to "moisturizer"),
            salesCount = 110,
            createdAt = "2025-10-15T00:00:00",
            updatedAt = "2025-10-21T10:00:00"
        ),
        10L to Product(
            id = 10L,
            name = "립스틱",
            description = "롱래스팅 매트 립스틱",
            price = 28000L,
            stock = 150,
            category = ProductCategory.BEAUTY,
            specifications = mapOf("color" to "coral pink", "finish" to "matte", "weight" to "3.5g"),
            salesCount = 130,
            createdAt = "2025-10-18T00:00:00",
            updatedAt = "2025-10-20T09:00:00"
        )
    )

    override fun findById(productId: Long): Product? {
        return products[productId]
    }

    override fun findAll(): List<Product> {
        return products.values.toList()
    }

    override fun findByCategory(category: ProductCategory): List<Product> {
        return products.values.filter { it.category == category }
    }

    override fun save(product: Product): Product {
        products[product.id] = product
        return product
    }
}