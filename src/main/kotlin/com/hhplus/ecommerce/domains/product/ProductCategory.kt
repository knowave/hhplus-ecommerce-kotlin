package com.hhplus.ecommerce.domains.product

/**
 * 상품 카테고리
 */
enum class ProductCategory(val description: String) {
    ELECTRONICS("전자제품"),
    FASHION("의류/패션"),
    FOOD("식품"),
    BOOKS("도서"),
    HOME("생활용품"),
    SPORTS("스포츠/레저"),
    BEAUTY("화장품/미용")
}