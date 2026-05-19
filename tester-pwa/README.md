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
| `VITE_API_KEY` | 이벤트/디바이스 등록용 Public API Key | `pk_live_...` |
| `VITE_FIREBASE_*` | Firebase Web Messaging 설정 | `.env.local.example` 참조 |
| `VITE_FIREBASE_VAPID_KEY` | Firebase Web Push 인증서 키 | Firebase Console 발급값 |

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

## 데스크톱 백그라운드 푸시 알림

- 데스크톱 Chrome/Edge 기준, Firebase Web Messaging 환경변수가 설정되어 있고 사용자가 알림 권한을 허용하면 FCM Web Push 토큰을 발급해 `/api/v1/devices`에 등록
- 앱이 열려 있을 때는 foreground 메시지를 in-app 토스트로 표시
- 앱이 닫혀 있을 때는 `firebase-messaging-sw.js`가 background 메시지를 브라우저 알림으로 표시
- Firebase 설정이 없거나 권한이 없으면 기존 foreground polling 토스트를 fallback으로 사용

## 한계사항

- `localStorage` 기반 deviceId: incognito 모드 종료 시 초기화
- iOS 푸시: PWA 설치(홈 화면 추가) + iOS 16.4 이상 필요
- 배포 시 HTTPS 필수 (Service Worker 요구사항)
