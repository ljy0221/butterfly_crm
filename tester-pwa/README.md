# Butterfly Tester PWA

Butterfly SDK QA 테스터용 Progressive Web App.
Android Studio/에뮬레이터 없이 브라우저만으로 테스트 가능.

## 빠른 시작

### Android Chrome (권장)
테스터에게 URL 전달 → Chrome으로 접속 → 즉시 사용 가능

### iOS Safari
[iOS 설치 가이드](/install-guide.html) 참조 (홈 화면 추가 필요, ~90초)

## 개발

```bash
cd tester-pwa
cp .env.local.example .env.local   # 환경변수 설정
npm install
npm run dev                         # http://localhost:5176
```

## 환경변수

| 변수 | 설명 | 예시 |
|------|------|------|
| `VITE_API_BASE_URL` | 백엔드 API URL | `http://localhost:8080` |
| `VITE_PROJECT_ID` | Butterfly 프로젝트 ID | `proj-xxxxx` |
| `VITE_USER_ID` | 테스트 유저 ID | `user-142` |

## 화면 구조

```
/ → /home            홈 (카테고리 그리드 + 인기 상품)
/category/:id        카테고리별 상품 목록
/product/:id         상품 상세 + 장바구니 담기
/cart                장바구니
/checkout            결제
```

## 이벤트 수집

Android EventTrigger.kt와 동일한 이벤트:
`app_open`, `category_view`, `product_click`, `product_view`,
`add_to_cart`, `cart_view`, `purchase`, `wishlist_add`, `login`, `search`

## 푸시 알림

- **단계1 (현재, D-4용)**: foreground in-app 토스트 (10초 polling)
- **단계2 (post-demo)**: FCM Web Push + firebase-messaging-sw.js

## 한계사항

- `localStorage` 기반 deviceId: incognito 모드 종료 시 초기화
- iOS 푸시: PWA 설치(홈 화면 추가) 필수
- 배포 시 HTTPS 필수 (Service Worker 요구사항)
