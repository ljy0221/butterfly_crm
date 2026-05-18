import { useNavigate, useParams } from 'react-router-dom'
import { categories, productsByCategory } from '../data/catalog'
import { EventTrigger } from '../butterfly/EventTrigger'

export default function CategoryListPage() {
  const { categoryId } = useParams<{ categoryId: string }>()
  const navigate = useNavigate()
  const cat = categories.find((c) => c.id === categoryId)
  const prods = productsByCategory(cat?.name ?? '')

  return (
    <div style={{ maxWidth: 480, margin: '0 auto', paddingBottom: 80 }}>
      <header style={{
        background: '#6750A4', color: '#fff', padding: '16px',
        display: 'flex', alignItems: 'center', gap: 12,
        position: 'sticky', top: 0, zIndex: 100,
      }}>
        <button onClick={() => navigate(-1)} style={{ color: '#fff', fontSize: 20 }}>←</button>
        <h1 style={{ fontSize: 18, fontWeight: 700 }}>{cat?.icon} {cat?.name ?? '카테고리'}</h1>
      </header>

      <div style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 8 }}>
        {prods.length === 0 ? (
          <p style={{ textAlign: 'center', color: '#666', marginTop: 40 }}>상품이 없습니다.</p>
        ) : (
          prods.map((product) => (
            <button
              key={product.id}
              onClick={() => {
                EventTrigger.fireProductClickSilently(product.category, product.id, product.discountedPrice ?? product.price)
                navigate(`/product/${product.id}`)
              }}
              style={{
                background: '#fff', borderRadius: 12, padding: 12,
                display: 'flex', alignItems: 'center', gap: 12,
                boxShadow: '0 1px 4px rgba(0,0,0,0.08)', textAlign: 'left',
              }}
            >
              <div style={{
                width: 64, height: 64, background: '#f5f0ff', borderRadius: 8,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontSize: 32, flexShrink: 0,
              }}>{cat?.icon}</div>
              <div style={{ flex: 1 }}>
                <div style={{ fontWeight: 600 }}>{product.name}</div>
                <div style={{ fontSize: 12, color: '#888', marginTop: 2 }}>
                  ⭐ {product.rating} ({product.reviewCount})
                </div>
                <div style={{ marginTop: 4 }}>
                  {product.discountedPrice ? (
                    <>
                      <span style={{ fontWeight: 700, color: '#6750A4' }}>
                        {product.discountedPrice.toLocaleString()}원
                      </span>
                      <span style={{ fontSize: 11, color: '#999', textDecoration: 'line-through', marginLeft: 4 }}>
                        {product.price.toLocaleString()}원
                      </span>
                    </>
                  ) : (
                    <span style={{ fontWeight: 700 }}>{product.price.toLocaleString()}원</span>
                  )}
                </div>
              </div>
              <span style={{ color: '#aaa', fontSize: 18 }}>›</span>
            </button>
          ))
        )}
      </div>
    </div>
  )
}
