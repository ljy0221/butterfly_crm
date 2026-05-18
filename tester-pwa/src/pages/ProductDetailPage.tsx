import { useNavigate, useParams } from 'react-router-dom'
import { useEffect } from 'react'
import { findById } from '../data/catalog'
import { cartStore } from '../stores/cartStore'
import { EventTrigger } from '../butterfly/EventTrigger'

export default function ProductDetailPage() {
  const { productId } = useParams<{ productId: string }>()
  const navigate = useNavigate()
  const product = findById(productId ?? '')

  useEffect(() => {
    if (product) {
      EventTrigger.fireProductViewWithDataSilently(
        product.category, product.id, product.name, product.discountedPrice ?? product.price
      )
    }
  }, [product])

  if (!product) {
    return (
      <div style={{ padding: 32, textAlign: 'center' }}>
        <p>상품을 찾을 수 없습니다.</p>
        <button onClick={() => navigate('/')} style={{ marginTop: 16, color: '#6750A4' }}>홈으로</button>
      </div>
    )
  }

  const displayPrice = product.discountedPrice ?? product.price
  const discount = product.discountedPrice
    ? Math.round((1 - product.discountedPrice / product.price) * 100)
    : 0

  return (
    <div style={{ maxWidth: 480, margin: '0 auto', paddingBottom: 100 }}>
      <header style={{
        background: '#6750A4', color: '#fff', padding: '16px',
        display: 'flex', alignItems: 'center', gap: 12,
        position: 'sticky', top: 0, zIndex: 100,
      }}>
        <button onClick={() => navigate(-1)} style={{ color: '#fff', fontSize: 20 }}>←</button>
        <h1 style={{ fontSize: 18, fontWeight: 700, flex: 1 }}>상품 상세</h1>
        <button onClick={() => navigate('/cart')} style={{ color: '#fff', fontSize: 20 }}>🛒</button>
      </header>

      <div style={{
        background: '#f5f0ff', height: 260,
        display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 80,
      }}>👟</div>

      <div style={{ padding: 16 }}>
        <div style={{ fontSize: 12, color: '#888', marginBottom: 4 }}>{product.category}</div>
        <h2 style={{ fontSize: 20, fontWeight: 700 }}>{product.name}</h2>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 8 }}>
          <span style={{ color: '#f5a623' }}>⭐</span>
          <span style={{ fontSize: 14 }}>{product.rating} ({product.reviewCount}개 리뷰)</span>
        </div>

        <div style={{ marginTop: 16 }}>
          {discount > 0 && (
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
              <span style={{
                background: '#ff4757', color: '#fff', borderRadius: 4,
                padding: '2px 6px', fontSize: 12, fontWeight: 700,
              }}>{discount}%</span>
              <span style={{ color: '#999', textDecoration: 'line-through', fontSize: 14 }}>
                {product.price.toLocaleString()}원
              </span>
            </div>
          )}
          <div style={{ fontSize: 24, fontWeight: 700, color: '#6750A4' }}>
            {displayPrice.toLocaleString()}원
          </div>
        </div>

        <div style={{
          background: '#f5f5f5', borderRadius: 8, padding: 12, marginTop: 16,
          fontSize: 14, color: '#555', lineHeight: 1.6,
        }}>
          <p>상품 ID: {product.id}</p>
          <p>무료 배송 · 당일 출고</p>
          <p>30일 이내 무료 반품</p>
        </div>
      </div>

      <div style={{
        position: 'fixed', bottom: 0, left: '50%', transform: 'translateX(-50%)',
        width: '100%', maxWidth: 480, padding: '12px 16px',
        background: '#fff', borderTop: '1px solid #eee', display: 'flex', gap: 8,
      }}>
        <button
          onClick={() => EventTrigger.fireWishlistAddSilently(product.category, product.id)}
          style={{
            flex: 1, padding: '14px 0', borderRadius: 12,
            border: '2px solid #6750A4', color: '#6750A4', fontWeight: 700, fontSize: 15,
          }}
        >♡ 찜하기</button>
        <button
          onClick={() => {
            cartStore.addItem(product, 1)
            EventTrigger.fireAddToCartSilently(product.category, product.id, 1, displayPrice)
            navigate('/cart')
          }}
          style={{
            flex: 2, padding: '14px 0', borderRadius: 12,
            background: '#6750A4', color: '#fff', fontWeight: 700, fontSize: 15,
          }}
        >장바구니 담기</button>
      </div>
    </div>
  )
}
