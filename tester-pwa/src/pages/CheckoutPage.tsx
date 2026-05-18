import { useNavigate } from 'react-router-dom'
import { useCart } from '../stores/cartStore'
import { EventTrigger } from '../butterfly/EventTrigger'

export default function CheckoutPage() {
  const navigate = useNavigate()
  const { items, totalPrice, clear } = useCart()

  const handlePurchase = async () => {
    const orderId = `O-${Date.now()}`
    const purchaseItems = items.map(({ product, quantity }) => ({
      productId: product.id,
      productName: product.name,
      quantity,
      price: product.discountedPrice ?? product.price,
    }))
    await EventTrigger.firePurchaseSilently(orderId, purchaseItems, totalPrice)
    clear()
    alert(`주문 완료!\n주문번호: ${orderId}`)
    navigate('/')
  }

  return (
    <div style={{ maxWidth: 480, margin: '0 auto', paddingBottom: 100 }}>
      <header style={{
        background: '#6750A4', color: '#fff', padding: '16px',
        display: 'flex', alignItems: 'center', gap: 12,
        position: 'sticky', top: 0, zIndex: 100,
      }}>
        <button onClick={() => navigate(-1)} style={{ color: '#fff', fontSize: 20 }}>←</button>
        <h1 style={{ fontSize: 18, fontWeight: 700 }}>결제</h1>
      </header>

      <div style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 12 }}>
        <div style={{ background: '#fff', borderRadius: 12, padding: 16 }}>
          <h3 style={{ fontSize: 15, fontWeight: 700, marginBottom: 12 }}>배송 정보</h3>
          <div style={{ fontSize: 14, color: '#555', lineHeight: 1.8 }}>
            <p>수령인: QA 테스터</p>
            <p>주소: 서울시 강남구 테헤란로 212</p>
            <p>연락처: 010-0000-0000</p>
          </div>
        </div>

        <div style={{ background: '#fff', borderRadius: 12, padding: 16 }}>
          <h3 style={{ fontSize: 15, fontWeight: 700, marginBottom: 12 }}>주문 상품 ({items.length})</h3>
          {items.map(({ product, quantity }) => (
            <div key={product.id} style={{
              display: 'flex', justifyContent: 'space-between', fontSize: 14,
              padding: '6px 0', borderBottom: '1px solid #f0f0f0',
            }}>
              <span style={{ flex: 1, color: '#333' }}>{product.name} × {quantity}</span>
              <span style={{ fontWeight: 600, color: '#6750A4' }}>
                {((product.discountedPrice ?? product.price) * quantity).toLocaleString()}원
              </span>
            </div>
          ))}
        </div>

        <div style={{ background: '#fff', borderRadius: 12, padding: 16 }}>
          <h3 style={{ fontSize: 15, fontWeight: 700, marginBottom: 12 }}>결제 수단</h3>
          <div style={{
            border: '2px solid #6750A4', borderRadius: 8, padding: 12,
            display: 'flex', alignItems: 'center', gap: 8, fontSize: 14,
          }}>
            <span>💳</span>
            <span>신용카드 (QA 테스트용)</span>
          </div>
        </div>

        <div style={{ background: '#fff', borderRadius: 12, padding: 16 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 700, fontSize: 16 }}>
            <span>최종 결제 금액</span>
            <span style={{ color: '#6750A4' }}>{totalPrice.toLocaleString()}원</span>
          </div>
        </div>
      </div>

      <div style={{
        position: 'fixed', bottom: 0, left: '50%', transform: 'translateX(-50%)',
        width: '100%', maxWidth: 480, padding: '12px 16px',
        background: '#fff', borderTop: '1px solid #eee',
      }}>
        <button
          onClick={handlePurchase}
          style={{
            width: '100%', padding: '16px 0', borderRadius: 12,
            background: '#6750A4', color: '#fff', fontWeight: 700, fontSize: 16,
          }}
        >{totalPrice.toLocaleString()}원 결제 완료</button>
      </div>
    </div>
  )
}
