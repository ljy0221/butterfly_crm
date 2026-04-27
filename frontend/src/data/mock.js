// data.js — mock fixtures for a fashion/shopping e-commerce tenant "Studio SEL"

const PROJECT = {
  brand: "Studio SEL",
  projectName: "스튜디오 셀 · 운영 프로젝트",
  env: "Production",
  plan: "Growth",
  owner: "김나연",
};

const USER = {
  name: "박지우",
  email: "jiwoo.park@studiosel.co.kr",
  role: "Editor",
  initials: "PJ",
};
const NAV = [
  { key: "대시보드", label: "대시보드", icon: "dashboard" },
  { key: "이벤트", label: "이벤트", icon: "events" },
  { key: "고객", label: "고객", icon: "customers" },
  { key: "세그먼트", label: "세그먼트", icon: "segments" },
  { key: "캠페인", label: "캠페인", icon: "campaigns" },
  { key: "자동화", label: "자동화 플로우", icon: "journey" },
  { key: "템플릿", label: "메시지 템플릿", icon: "templates" },
  { key: "분석", label: "분석", icon: "analytics" },
  { key: "설정", label: "설정", icon: "settings" },
];

const EVENT_CATEGORIES = [
  { name: "page_view", type: "기본", volume: 4823910, status: "정상", rate: "99.2%", last: "방금", props: 4 },
  { name: "login", type: "기본", volume: 324819, status: "정상", rate: "98.7%", last: "방금", props: 3 },
  { name: "signup", type: "기본", volume: 18204, status: "정상", rate: "97.1%", last: "12초 전", props: 5 },
  { name: "add_to_cart", type: "기본", volume: 892104, status: "정상", rate: "96.4%", last: "방금", props: 7 },
  { name: "purchase", type: "기본", volume: 124820, status: "정상", rate: "99.8%", last: "방금", props: 9 },
  { name: "product_view", type: "커스텀", volume: 2184091, status: "정상", rate: "98.1%", last: "방금", props: 6 },
  { name: "wishlist_add", type: "커스텀", volume: 48210, status: "경고", rate: "84.2%", last: "3분 전", props: 4 },
  { name: "coupon_issued", type: "커스텀", volume: 12048, status: "경고", rate: "76.5%", last: "1시간 전", props: 5 },
  { name: "checkout_start", type: "커스텀", volume: 182910, status: "정상", rate: "95.8%", last: "방금", props: 6 },
  { name: "review_submit", type: "커스텀", volume: 4821, status: "오류", rate: "48.2%", last: "6시간 전", props: 4 },
];

// Schema for one event (purchase) — used in Event detail
const PURCHASE_SCHEMA = [
  { key: "order_id", type: "string", required: true, example: "SO-2026-041829" },
  { key: "total_amount", type: "number", required: true, example: "184,000" },
  { key: "currency", type: "string", required: true, example: "KRW" },
  { key: "items", type: "array", required: true, example: "[{ sku, qty, price }]" },
  { key: "coupon_code", type: "string", required: false, example: "SPRING15" },
  { key: "payment_method", type: "string", required: true, example: "card" },
  { key: "shipping_fee", type: "number", required: false, example: "0" },
  { key: "is_first_order", type: "boolean", required: false, example: "true" },
  { key: "referrer", type: "string", required: false, example: "instagram_ad" },
];

const CUSTOMERS = [
  { id: "u_284911", name: "이서연", email: "seoyeon.lee@gmail.com", last: "12분 전", events: 248, ltv: "1,284,000", tier: "VIP" },
  { id: "u_284910", name: "정민재", email: "m.j.jung@naver.com", last: "34분 전", events: 89, ltv: "482,000", tier: "Regular" },
  { id: "u_284906", name: "최유진", email: "yujin.choi@kakao.com", last: "2시간 전", events: 412, ltv: "2,104,000", tier: "VIP" },
  { id: "u_284881", name: "한도윤", email: "doyoon.h@studiosel.co.kr", last: "어제", events: 62, ltv: "298,000", tier: "Regular" },
  { id: "u_284877", name: "배소희", email: "sohee.bae@gmail.com", last: "2일 전", events: 18, ltv: "48,000", tier: "New" },
  { id: "u_284812", name: "오지훈", email: "jihoon.oh@hanmail.net", last: "5일 전", events: 142, ltv: "820,000", tier: "Regular" },
  { id: "u_284801", name: "문채아", email: "chaea.moon@kakao.com", last: "8일 전", events: 204, ltv: "1,018,000", tier: "VIP" },
  { id: "u_284793", name: "노건우", email: "gunwoo.no@gmail.com", last: "14일 전", events: 38, ltv: "182,000", tier: "Dormant" },
];

const SEGMENTS = [
  { name: "장바구니 이탈 고객", desc: "장바구니에 상품을 담았지만 24시간 내 구매하지 않은 사용자", creator: "박지우", created: "2026-04-20", reach: 18420, campaigns: 3, fav: true },
  { name: "VIP · 최근 60일 재구매 없음", desc: "누적 결제 50만원 이상 + 60일간 구매 기록 없음", creator: "김나연", created: "2026-04-18", reach: 2184, campaigns: 2, fav: true },
  { name: "신규 가입 7일 이내", desc: "가입 후 7일 이내 사용자 · 온보딩 대상", creator: "박지우", created: "2026-04-15", reach: 4821, campaigns: 4, fav: false },
  { name: "위시리스트 3회 이상", desc: "위시리스트에 3개 이상 상품 담은 활성 사용자", creator: "이현우", created: "2026-04-12", reach: 12084, campaigns: 1, fav: true },
  { name: "여름 시즌 구매 예상", desc: "작년 동기간 구매 이력 있는 사용자 재참여", creator: "김나연", created: "2026-04-10", reach: 8291, campaigns: 0, fav: false },
  { name: "앱 미설치 웹 사용자", desc: "웹에서 3회 이상 방문 + 앱 설치 이력 없음", creator: "박지우", created: "2026-04-08", reach: 34821, campaigns: 2, fav: false },
  { name: "휴면 복귀 대상", desc: "90일 이상 비활성 상태 · 재방문 유도", creator: "이현우", created: "2026-03-28", reach: 48210, campaigns: 1, fav: false },
];

const CAMPAIGNS = [
  { id: "cmp_4821", name: "장바구니 이탈 리타게팅 · 4월", segment: "장바구니 이탈 고객", channel: "FCM", status: "success", sent: 18420, openRate: "34.2%", date: "2026-04-23 14:00" },
  { id: "cmp_4819", name: "VIP 감사 쿠폰 안내", segment: "VIP · 최근 60일 재구매 없음", channel: "이메일", status: "success", sent: 2104, openRate: "52.8%", date: "2026-04-22 10:00" },
  { id: "cmp_4815", name: "신규 가입 환영 · D+1", segment: "신규 가입 7일 이내", channel: "FCM", status: "send", sent: 812, openRate: "—", date: "2026-04-24 09:30" },
  { id: "cmp_4810", name: "여름 프리오더 사전 안내", segment: "여름 시즌 구매 예상", channel: "이메일", status: "ready", sent: 0, openRate: "—", date: "2026-04-25 10:00" },
  { id: "cmp_4804", name: "위시리스트 재고 알림", segment: "위시리스트 3회 이상", channel: "FCM", status: "success", sent: 12084, openRate: "41.2%", date: "2026-04-21 18:00" },
  { id: "cmp_4798", name: "휴면 복귀 혜택 안내", segment: "휴면 복귀 대상", channel: "SMS", status: "fail", sent: 48210, openRate: "8.2%", date: "2026-04-19 11:00" },
  { id: "cmp_4789", name: "앱 설치 유도 · 웹 사용자", segment: "앱 미설치 웹 사용자", channel: "웹훅", status: "draft", sent: 0, openRate: "—", date: "—" },
  { id: "cmp_4782", name: "회원가입 1주년 인사", segment: "신규 가입 7일 이내", channel: "이메일", status: "success", sent: 4218, openRate: "38.1%", date: "2026-04-17 14:00" },
];

const TEMPLATES = [
  { id: "tpl_cart_1", name: "장바구니 이탈 리마인더", channel: "FCM", desc: "24시간 내 구매 안 한 고객에게 재방문 유도", creator: "박지우", created: "2026-04-20" },
  { id: "tpl_welcome", name: "신규 가입 환영", channel: "이메일", desc: "가입 완료 직후 발송되는 환영 메일", creator: "김나연", created: "2026-04-15" },
  { id: "tpl_vip_coupon", name: "VIP 전용 쿠폰", channel: "이메일", desc: "월 1회 VIP 등급 사용자 대상", creator: "박지우", created: "2026-04-12" },
  { id: "tpl_restock", name: "위시리스트 재입고 알림", channel: "FCM", desc: "위시리스트 등록 상품 재입고 시 발송", creator: "이현우", created: "2026-04-08" },
  { id: "tpl_dormant", name: "휴면 복귀 쿠폰", channel: "SMS", desc: "90일 비활성 고객에게 복귀 쿠폰 발송", creator: "김나연", created: "2026-04-02" },
];

const JOURNEYS = [
  { name: "장바구니 이탈 자동 리마인더", status: "running", enrolled: 18420, trigger: "add_to_cart", updated: "2시간 전" },
  { name: "신규 가입 환영 시리즈", status: "running", enrolled: 4821, trigger: "signup", updated: "어제" },
  { name: "VIP 구매 후 감사 메시지", status: "running", enrolled: 2104, trigger: "purchase", updated: "3일 전" },
  { name: "휴면 복귀 5단계 시나리오", status: "draft", enrolled: 0, trigger: "manual", updated: "1주일 전" },
];


export const CRM_DATA = {
  PROJECT, USER, NAV, EVENT_CATEGORIES, PURCHASE_SCHEMA,
  CUSTOMERS, SEGMENTS, CAMPAIGNS, TEMPLATES, JOURNEYS,
};
