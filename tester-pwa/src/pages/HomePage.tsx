import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { categories, popularProducts } from '../data/catalog'
import { EventTrigger } from '../butterfly/EventTrigger'

export default function HomePage() {
  const navigate = useNavigate()

  useEffect(() => {
    EventTrigger.fireAppOpenSilently()
  }, [])

  return (
    <div style={{ maxWidth: 480, margin: '0 auto', paddingBottom: 80 }}>
      <header style={{
        background: '#6750A4',
        color: '#fff',
        padding: '16px',
        position: 'sticky',
        top: 0,
        zIndex: 100,
      }}>
        <h1 style={{ fontSize: 20, fontWeight: 700 }}>Butterfly Shop</h1>
        <p style={{ fontSize: 12, opacity: 0.8, marginTop: 2 }}>QA 테스터 앱</p>
      </header>

      <section style={{ padding: '16px' }}>
        <h2 style={{ fontSize: 16, fontWeight: 700, marginBottom: 12 }}>카테고리</h2>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 8 }}>
          {categories.map((cat) => (
            <button
              key={cat.id}
              onClick={() => {
                EventTrigger.fireCategoryViewSilently(cat.name)
                navigate(`/category/${cat.id}`)
              }}
              style={{
                background: '#fff',
                borderRadius: 12,
                padding: '12px 4px',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: 4,
                boxShadow: '0 1px 4px rgba(0,0,0,0.08)',
                fontSize: 12,
              }}
            >
              <span style={{ fontSize: 24 }}>{cat.icon}</span>
              <span>{cat.name}</span>
            </button>
          ))}
        </div>
      </section>

      <section style={{ padding: '0 16px' }}>
        <h2 style={{ fontSize: 16, fontWeight: 700, marginBottom: 12 }}>인기 상품</h2>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {popularProducts(6).map((product) => (
            <button
              key={product.id}
              onClick={() => {
                EventTrigger.fireProductClickSilently(product.category, product.id, product.discountedPrice ?? product.price)
                navigate(`/product/${product.id}`)
              }}
              style={{
                background: '#fff',
                borderRadius: 12,
                padding: 12,
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                boxShadow: '0 1px 4px rgba(0,0,0,0.08)',
                textAlign: 'left',
              }}
            >
              <div style={{
                width: 56, height: 56, background: '#f0ece8', borderRadius: 8,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontSize: 28, flexShrink: 0,
              }}>👟</div>
              <div style={{ flex: 1 }}>
                <div style={{ fontWeight: 600, fontSize: 14 }}>{product.name}</div>
                <div style={{ fontSize: 12, color: '#666', marginTop: 2 }}>{product.category}</div>
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
            </button>
          ))}
        </div>
      </section>
    </div>
  )
}
