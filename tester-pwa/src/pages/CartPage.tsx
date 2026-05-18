import { useNavigate } from 'react-router-dom'
import { useEffect } from 'react'
import { useCart } from '../stores/cartStore'
import { EventTrigger } from '../butterfly/EventTrigger'

export default function CartPage() {
  const navigate = useNavigate()
  const { items, removeItem, updateQuantity, totalPrice, totalCount } = useCart()

  useEffect(() => {
    EventTrigger.fireCartViewSilently(totalCount, totalPrice)
  }, [totalCount, totalPrice])

  return (
    <div style={{ maxWidth: 480, margin: '0 auto', paddingBottom: 100 }}>
      <header style={{
        background: '#6750A4', color: '#fff', padding: '16px',
        display: 'flex', alignItems: 'center', gap: 12,
        position: 'sticky', top: 0, zIndex: 100,
      }}>
        <button onClick={() => navigate(-1)} style={{ color: '#fff', fontSize: 20 }}>←</button>
        <h1 style={{ fontSize: 18, fontWeight: 700 }}>장바구니 ({totalCount})</h1>
      </header>

      {items.length === 0 ? (
        <div style={{ padding: 40, textAlign: 'center', color: '#666' }}>
          <div style={{ fontSize: 48, marginBottom: 16 }}>🛒</div>
          <p>장바구니가 비어있습니다.</p>
          <button
            onClick={() => navigate('/')}
            style={{ marginTop: 16, color: '#6750A4', fontWeight: 600 }}
          >쇼핑 계속하기</button>
        </div>
      ) : (
        <>
          <div style={{ padding: '8px 16px', display: 'flex', flexDirection: 'column', gap: 8 }}>
            {items.map(({ product, quantity }) => {
              const price = product.discountedPrice ?? product.price
              return (
                <div key={product.id} style={{
                  background: '#fff', borderRadius: 12, padding: 12,
                  boxShadow: '0 1px 4px rgba(0,0,0,0.08)', display: 'flex', gap: 12,
                }}>
                  <div style={{
                    width: 56, height: 56, background: '#f5f0ff', borderRadius: 8,
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    fontSize: 28, flexShrink: 0,
                  }}>👟</div>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontWeight: 600, fontSize: 14 }}>{product.name}</div>
                    <div style={{ fontSize: 13, color: '#6750A4', fontWeight: 700, marginTop: 4 }}>
                      {(price * quantity).toLocaleString()}원
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 8 }}>
                      <button
                        onClick={() => updateQuantity(product.id, quantity - 1)}
                        style={{ width: 28, height: 28, borderRadius: 14, background: '#f0f0f0', fontSize: 16 }}
                      >−</button>
                      <span style={{ fontSize: 14, fontWeight: 600 }}>{quantity}</span>
                      <button
                        onClick={() => updateQuantity(product.id, quantity + 1)}
                        style={{ width: 28, height: 28, borderRadius: 14, background: '#f0f0f0', fontSize: 16 }}
                      >+</button>
                      <button
                        onClick={() => removeItem(product.id)}
                        style={{ marginLeft: 'auto', color: '#999', fontSize: 12 }}
                      >삭제</button>
                    </div>
                  </div>
                </div>
              )
            })}
          </div>

          <div style={{ margin: '8px 16px', background: '#fff', borderRadius: 12, padding: 16 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
              <span style={{ color: '#666' }}>상품 금액</span>
              <span>{totalPrice.toLocaleString()}원</span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
              <span style={{ color: '#666' }}>배송비</span>
              <span style={{ color: '#4CAF50' }}>무료</span>
            </div>
            <div style={{ borderTop: '1px solid #eee', paddingTop: 8, display: 'flex', justifyContent: 'space-between' }}>
              <span style={{ fontWeight: 700 }}>총 결제금액</span>
              <span style={{ fontWeight: 700, fontSize: 18, color: '#6750A4' }}>
                {totalPrice.toLocaleString()}원
              </span>
            </div>
          </div>

          <div style={{
            position: 'fixed', bottom: 0, left: '50%', transform: 'translateX(-50%)',
            width: '100%', maxWidth: 480, padding: '12px 16px',
            background: '#fff', borderTop: '1px solid #eee',
          }}>
            <button
              onClick={() => navigate('/checkout')}
              style={{
                width: '100%', padding: '16px 0', borderRadius: 12,
                background: '#6750A4', color: '#fff', fontWeight: 700, fontSize: 16,
              }}
            >{totalPrice.toLocaleString()}원 결제하기</button>
          </div>
        </>
      )}
    </div>
  )
}
