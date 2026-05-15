package com.example.notificationtest.catalog

import com.example.notificationtest.R

/**
 * QA 빌드 전용 — 상품 카테고리 12개 + 카테고리당 5개 상품 = 총 60개 상품.
 * 이벤트 종류는 백엔드 event_categories 마스터(10개)와 이름 매칭.
 */
object ProductCatalog {

    data class Category(
        val id: String,
        val name: String,
        val icon: String
    )

    data class Product(
        val id: String,
        val name: String,
        val category: String,
        val price: Int,
        val discountedPrice: Int?,
        val imageRes: Int = R.drawable.product_running_shoes,
        val rating: Double = 4.5,
        val reviewCount: Int = 100
    )

    val categories: List<Category> = listOf(
        Category("running_shoes", "운동화", "👟"),
        Category("shirt",         "셔츠",   "👔"),
        Category("electronics",   "가전",   "📺"),
        Category("food",          "식품",   "🍎"),
        Category("book",          "도서",   "📚"),
        Category("cosmetic",      "화장품", "💄"),
        Category("bag",           "가방",   "👜"),
        Category("watch",         "시계",   "⌚"),
        Category("eyewear",       "안경",   "🕶"),
        Category("chair",         "의자",   "🪑"),
        Category("toy",           "완구",   "🧸"),
        Category("furniture",     "가구",   "🛋")
    )

    val products: List<Product> = buildList {
        addAll(makeProducts("운동화", "shoe", listOf(
            Triple("데일리 러닝화", 89000, 57850),
            Triple("프로 러닝화",   159000, 119000),
            Triple("스니커즈",      69000, 49000),
            Triple("워킹화",        99000, 79000),
            Triple("등산화",        149000, null)
        )))
        addAll(makeProducts("셔츠", "shirt", listOf(
            Triple("화이트 셔츠",     59000, 39000),
            Triple("스트라이프 셔츠", 69000, null),
            Triple("옥스포드 셔츠",   79000, 49000),
            Triple("린넨 셔츠",       89000, null),
            Triple("플란넬 셔츠",     99000, 69000)
        )))
        addAll(makeProducts("가전", "elec", listOf(
            Triple("무선 이어폰",     199000, 149000),
            Triple("블루투스 스피커", 89000, null),
            Triple("스마트 워치",     299000, 229000),
            Triple("키보드",          129000, 89000),
            Triple("마우스",          59000, null)
        )))
        addAll(makeProducts("식품", "food", listOf(
            Triple("프리미엄 한우",   89000, 69000),
            Triple("유기농 사과",     19000, null),
            Triple("아메리카노 원두", 29000, 22000),
            Triple("샐러드 박스",     39000, null),
            Triple("그래놀라",        15000, 12000)
        )))
        addAll(makeProducts("도서", "book", listOf(
            Triple("Clean Code",              30000, null),
            Triple("DDD Quickly",             25000, 19000),
            Triple("Effective Kotlin",        40000, null),
            Triple("Designing Data-Intensive", 50000, 39000),
            Triple("System Design Interview", 35000, null)
        )))
        addAll(makeProducts("화장품", "cosm", listOf(
            Triple("립스틱 레드",        32000, null),
            Triple("파운데이션",         45000, 35000),
            Triple("아이섀도우 팔레트",  58000, null),
            Triple("스킨케어 세트",      120000, 89000),
            Triple("향수 50ml",          150000, null)
        )))
        addAll(makeProducts("가방", "bag", listOf(
            Triple("크로스백",  89000, 59000),
            Triple("백팩",      120000, null),
            Triple("토트백",    150000, 119000),
            Triple("클러치",    79000, null),
            Triple("여행가방",  250000, 199000)
        )))
        addAll(makeProducts("시계", "watch", listOf(
            Triple("클래식 시계", 250000, 199000),
            Triple("스포츠 시계", 180000, null),
            Triple("디지털 시계", 99000, 79000),
            Triple("미니멀 시계", 150000, null),
            Triple("자동 시계",   450000, 379000)
        )))
        addAll(makeProducts("안경", "eye", listOf(
            Triple("선글라스 블랙",   120000, 89000),
            Triple("뿔테 안경",       150000, null),
            Triple("메탈 안경",       180000, 139000),
            Triple("반테 안경",       130000, null),
            Triple("스포츠 선글라스", 99000, 79000)
        )))
        addAll(makeProducts("의자", "chair", listOf(
            Triple("오피스 체어",  250000, 199000),
            Triple("게이밍 체어",  450000, null),
            Triple("좌식 의자",    89000, 69000),
            Triple("스툴",         59000, null),
            Triple("리클라이너",   650000, 499000)
        )))
        addAll(makeProducts("완구", "toy", listOf(
            Triple("레고 시티 세트", 120000, 89000),
            Triple("보드게임",        45000, null),
            Triple("퍼즐 1000pcs",    35000, 29000),
            Triple("RC 자동차",       89000, null),
            Triple("인형",            29000, 19000)
        )))
        addAll(makeProducts("가구", "furn", listOf(
            Triple("원목 책상",     350000, 279000),
            Triple("3단 책장",      189000, null),
            Triple("침대 프레임",   450000, 379000),
            Triple("소파 2인용",    650000, null),
            Triple("커피 테이블",   120000, 89000)
        )))
    }

    private fun makeProducts(
        category: String,
        prefix: String,
        items: List<Triple<String, Int, Int?>>
    ): List<Product> = items.mapIndexed { idx, (name, price, discount) ->
        Product(
            id = "sku-$prefix-${idx + 1}",
            name = name,
            category = category,
            price = price,
            discountedPrice = discount
        )
    }

    fun productsByCategory(categoryName: String): List<Product> =
        products.filter { it.category == categoryName }

    fun findById(productId: String): Product? =
        products.firstOrNull { it.id == productId }

    fun popularProducts(limit: Int = 6): List<Product> =
        products.take(limit)

    fun search(query: String): List<Product> = products.filter {
        it.name.contains(query, ignoreCase = true) ||
                it.category.contains(query, ignoreCase = true)
    }
}
