export interface Category {
  id: string
  name: string
  icon: string
}

export interface Product {
  id: string
  name: string
  category: string
  price: number
  discountedPrice: number | null
  rating: number
  reviewCount: number
}

export const categories: Category[] = [
  { id: 'running_shoes', name: '운동화', icon: '👟' },
  { id: 'shirt',         name: '셔츠',   icon: '👔' },
  { id: 'electronics',   name: '가전',   icon: '📺' },
  { id: 'food',          name: '식품',   icon: '🍎' },
  { id: 'book',          name: '도서',   icon: '📚' },
  { id: 'cosmetic',      name: '화장품', icon: '💄' },
  { id: 'bag',           name: '가방',   icon: '👜' },
  { id: 'watch',         name: '시계',   icon: '⌚' },
  { id: 'eyewear',       name: '안경',   icon: '🕶️' },
  { id: 'chair',         name: '의자',   icon: '🪑' },
  { id: 'toy',           name: '완구',   icon: '🧸' },
  { id: 'furniture',     name: '가구',   icon: '🛋️' },
]

function makeProducts(
  category: string,
  prefix: string,
  items: Array<[string, number, number | null]>
): Product[] {
  return items.map(([name, price, discountedPrice], idx) => ({
    id: `sku-${prefix}-${idx + 1}`,
    name,
    category,
    price,
    discountedPrice,
    rating: 4.5,
    reviewCount: 100,
  }))
}

export const products: Product[] = [
  ...makeProducts('운동화', 'shoe', [
    ['데일리 러닝화', 89000, 57850],
    ['프로 러닝화', 159000, 119000],
    ['스니커즈', 69000, 49000],
    ['워킹화', 99000, 79000],
    ['등산화', 149000, null],
  ]),
  ...makeProducts('셔츠', 'shirt', [
    ['화이트 셔츠', 59000, 39000],
    ['스트라이프 셔츠', 69000, null],
    ['옥스포드 셔츠', 79000, 49000],
    ['린넨 셔츠', 89000, null],
    ['플란넬 셔츠', 99000, 69000],
  ]),
  ...makeProducts('가전', 'elec', [
    ['무선 이어폰', 199000, 149000],
    ['블루투스 스피커', 89000, null],
    ['스마트 워치', 299000, 229000],
    ['키보드', 129000, 89000],
    ['마우스', 59000, null],
  ]),
  ...makeProducts('식품', 'food', [
    ['프리미엄 한우', 89000, 69000],
    ['유기농 사과', 19000, null],
    ['아메리카노 원두', 29000, 22000],
    ['샐러드 박스', 39000, null],
    ['그래놀라', 15000, 12000],
  ]),
  ...makeProducts('도서', 'book', [
    ['Clean Code', 30000, null],
    ['DDD Quickly', 25000, 19000],
    ['Effective Kotlin', 40000, null],
    ['Designing Data-Intensive', 50000, 39000],
    ['System Design Interview', 35000, null],
  ]),
  ...makeProducts('화장품', 'cosm', [
    ['립스틱 레드', 32000, null],
    ['파운데이션', 45000, 35000],
    ['아이섀도우 팔레트', 58000, null],
    ['스킨케어 세트', 120000, 89000],
    ['향수 50ml', 150000, null],
  ]),
  ...makeProducts('가방', 'bag', [
    ['크로스백', 89000, 59000],
    ['백팩', 120000, null],
    ['토트백', 150000, 119000],
    ['클러치', 79000, null],
    ['여행가방', 250000, 199000],
  ]),
  ...makeProducts('시계', 'watch', [
    ['클래식 시계', 250000, 199000],
    ['스포츠 시계', 180000, null],
    ['디지털 시계', 99000, 79000],
    ['미니멀 시계', 150000, null],
    ['자동 시계', 450000, 379000],
  ]),
  ...makeProducts('안경', 'eye', [
    ['선글라스 블랙', 120000, 89000],
    ['뿔테 안경', 150000, null],
    ['메탈 안경', 180000, 139000],
    ['반테 안경', 130000, null],
    ['스포츠 선글라스', 99000, 79000],
  ]),
  ...makeProducts('의자', 'chair', [
    ['오피스 체어', 250000, 199000],
    ['게이밍 체어', 450000, null],
    ['좌식 의자', 89000, 69000],
    ['스툴', 59000, null],
    ['리클라이너', 650000, 499000],
  ]),
  ...makeProducts('완구', 'toy', [
    ['레고 시티 세트', 120000, 89000],
    ['보드게임', 45000, null],
    ['퍼즐 1000pcs', 35000, 29000],
    ['RC 자동차', 89000, null],
    ['인형', 29000, 19000],
  ]),
  ...makeProducts('가구', 'furn', [
    ['원목 책상', 350000, 279000],
    ['3단 책장', 189000, null],
    ['침대 프레임', 450000, 379000],
    ['소파 2인용', 650000, null],
    ['커피 테이블', 120000, 89000],
  ]),
]

export function productsByCategory(categoryName: string): Product[] {
  return products.filter((p) => p.category === categoryName)
}

export function findById(productId: string): Product | undefined {
  return products.find((p) => p.id === productId)
}

export function popularProducts(limit = 6): Product[] {
  return products.slice(0, limit)
}

export function search(query: string): Product[] {
  const q = query.toLowerCase()
  return products.filter(
    (p) => p.name.toLowerCase().includes(q) || p.category.toLowerCase().includes(q)
  )
}
