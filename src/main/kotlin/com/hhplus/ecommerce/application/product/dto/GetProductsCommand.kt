package com.hhplus.ecommerce.application.product.dto

/**
 * 상품 목록 조회 요청 DTO
 *
 * @property category 카테고리 필터 (optional)
 * @property sortBy 정렬 기준 (price, popularity, newest)
 * @property orderBy 정렬 방향 (asc, desc)
 * @property page 페이지 번호 (0부터 시작)
 * @property size 페이지 크기
 */
data class GetProductsCommand(
    val category: String? = null,
    val sortBy: SortBy = SortBy.NEWEST,
    val orderBy: OrderBy = OrderBy.DESC,
    val page: Int = 0,
    val size: Int = 20
) {
    init {
        require(page >= 0) { "page number must be greater than or equal to 0." }
        require(size > 0) { "page size should be greater than 0." }
        require(size <= 100) { "page size should be 100 or less." }
    }

    /**
     * 정렬 기준
     */
    enum class SortBy {
        PRICE,          // 가격순
        POPULARITY,     // 인기순 (판매량순)
        NEWEST;         // 최신순 (생성일순)

        companion object {
            /**
             * 문자열을 SortBy로 변환
             * 매칭되지 않는 경우 기본값(NEWEST) 반환
             */
            fun fromString(value: String?): SortBy {
                return when (value?.lowercase()) {
                    "price" -> PRICE
                    "popularity" -> POPULARITY
                    "newest", null -> NEWEST
                    else -> NEWEST
                }
            }
        }
    }

    /**
     * 정렬 방향
     */
    enum class OrderBy {
        ASC,    // 오름차순
        DESC;   // 내림차순

        companion object {
            /**
             * 문자열을 OrderBy로 변환
             * 매칭되지 않는 경우 기본값(DESC) 반환
             */
            fun fromString(value: String?): OrderBy {
                return when (value?.lowercase()) {
                    "asc" -> ASC
                    "desc", null -> DESC
                    else -> DESC
                }
            }
        }
    }
}