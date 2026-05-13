import { useState } from "react";

interface Props {
  close: () => void;
}

interface Section {
  title: string;
  items: { q: string; a: string }[];
}

const SECTIONS: Section[] = [
  {
    title: "시작하기",
    items: [
      {
        q: "SDK를 어떻게 연동하나요?",
        a: "프로젝트 설정 → API 키 탭에서 API Key를 발급받으세요. Android SDK는 X-API-Key 헤더에 발급된 키를 담아 POST /api/v1/devices (디바이스 등록) → POST /api/v1/events (이벤트 수집) 순으로 호출합니다.",
      },
      {
        q: "API Key는 어디서 발급하나요?",
        a: "우측 상단 아바타 → API 키 관리, 또는 사이드바 설정 → API 키 탭에서 발급할 수 있습니다. OWNER 권한이 필요합니다. 발급된 키는 최초 1회만 표시되므로 안전한 곳에 복사해두세요.",
      },
      {
        q: "팀원은 어떻게 초대하나요?",
        a: "설정 → 멤버 탭에서 회사 인증번호를 공유하세요. 팀원이 회원가입 시 해당 번호를 입력하면 가입 신청이 접수되고, OWNER가 승인하면 프로젝트에 합류됩니다.",
      },
    ],
  },
  {
    title: "주요 기능",
    items: [
      {
        q: "세그먼트는 어떻게 만드나요?",
        a: "사이드바 세그먼트 → 세그먼트 만들기를 클릭하세요. 이벤트 발생 횟수, 마지막 발생 시점, 속성값 등을 AND/OR 조건으로 조합해 타겟 고객군을 정의할 수 있습니다. 세그먼트는 주기적으로 자동 재계산됩니다.",
      },
      {
        q: "캠페인 회차란 무엇인가요?",
        a: "캠페인 1건은 여러 번 실행될 수 있으며, 각 실행을 '회차'라고 합니다. 캠페인 상세 페이지에서 회차별 발송/전달/오픈/클릭 통계를 확인할 수 있습니다.",
      },
      {
        q: "채널별 발송 설정은 어떻게 하나요?",
        a: "채널 자격증명(FCM 서버 키 등)은 관리자에게 문의해주세요. 등록 후 캠페인 생성 시 해당 채널(앱 푸시 등)을 선택할 수 있습니다.",
      },
    ],
  },
  {
    title: "문제 해결",
    items: [
      {
        q: "이벤트가 대시보드에 반영되지 않아요",
        a: "1) API Key가 올바르게 설정되어 있는지 확인하세요. 2) SDK가 X-API-Key 헤더를 포함해 요청을 보내는지 확인하세요. 3) 이벤트 수집 후 대시보드 집계까지 최대 수 분이 소요될 수 있습니다.",
      },
      {
        q: "푸시 알림이 전달되지 않아요",
        a: "1) FCM 서버 키가 정상 등록되어 있는지 관리자에게 확인하세요. 2) 디바이스 등록(POST /api/v1/devices) 시 FCM 토큰이 정상적으로 전달됐는지 확인하세요. 3) 디바이스의 알림 권한 허용 여부를 확인하세요.",
      },
      {
        q: "API Key를 분실했어요",
        a: "API Key는 발급 시 1회만 표시됩니다. 분실 시 설정 → API 키 탭에서 재발급하세요. 재발급하면 이전 키는 즉시 무효화되므로 SDK 설정을 새 키로 교체해야 합니다.",
      },
    ],
  },
];

export default function HelpCenterModal({ close }: Props) {
  const [openItems, setOpenItems] = useState<Set<string>>(new Set());

  const toggle = (key: string) =>
    setOpenItems((prev) => {
      const next = new Set(prev);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });

  return (
    <div
      style={{
        background: "#fff",
        borderRadius: 14,
        width: 720,
        maxWidth: "90vw",
        maxHeight: "80vh",
        display: "flex",
        flexDirection: "column",
        boxShadow: "0 24px 64px -8px rgba(0,0,0,.25)",
        overflow: "hidden",
      }}
    >
      {/* Header */}
      <div
        style={{
          padding: "20px 24px 16px",
          borderBottom: "1px solid #f1f5f9",
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          flex: "none",
        }}
      >
        <div>
          <div style={{ fontSize: 16, fontWeight: 700, color: "#1f2937" }}>
            도움말 센터
          </div>
          <div style={{ fontSize: 12, color: "#94a3b8", marginTop: 2 }}>
            자주 묻는 질문과 사용 가이드
          </div>
        </div>
        <button
          onClick={close}
          style={{
            width: 28,
            height: 28,
            border: "1px solid #e2e8f0",
            borderRadius: 6,
            background: "#fff",
            cursor: "pointer",
            fontSize: 16,
            color: "#64748b",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
          }}
        >
          ×
        </button>
      </div>

      {/* Body */}
      <div style={{ overflowY: "auto", padding: "16px 24px 24px" }}>
        {SECTIONS.map((section) => (
          <div key={section.title} style={{ marginBottom: 20 }}>
            <div
              style={{
                fontSize: 11,
                fontWeight: 600,
                color: "#94a3b8",
                letterSpacing: "0.06em",
                textTransform: "uppercase",
                marginBottom: 8,
                paddingBottom: 6,
                borderBottom: "1px solid #f1f5f9",
              }}
            >
              {section.title}
            </div>

            {section.items.map((item) => {
              const key = `${section.title}::${item.q}`;
              const isOpen = openItems.has(key);
              return (
                <div
                  key={key}
                  style={{
                    borderRadius: 8,
                    border: "1px solid #f1f5f9",
                    marginBottom: 6,
                    overflow: "hidden",
                  }}
                >
                  <button
                    onClick={() => toggle(key)}
                    style={{
                      width: "100%",
                      display: "flex",
                      justifyContent: "space-between",
                      alignItems: "center",
                      padding: "11px 14px",
                      background: isOpen ? "#f8fafc" : "#fff",
                      border: 0,
                      cursor: "pointer",
                      fontFamily: "inherit",
                      fontSize: 13,
                      fontWeight: 500,
                      color: "#1f2937",
                      textAlign: "left",
                      gap: 8,
                    }}
                  >
                    <span style={{ flex: 1 }}>{item.q}</span>
                    <span
                      style={{
                        color: "#94a3b8",
                        fontSize: 16,
                        transition: "transform 180ms",
                        transform: isOpen ? "rotate(180deg)" : "none",
                        flex: "none",
                      }}
                    >
                      ›
                    </span>
                  </button>
                  {isOpen && (
                    <div
                      style={{
                        padding: "10px 14px 14px",
                        fontSize: 13,
                        color: "#475569",
                        lineHeight: 1.7,
                        background: "#f8fafc",
                        borderTop: "1px solid #f1f5f9",
                        whiteSpace: "pre-line",
                      }}
                    >
                      {item.a}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        ))}

        <div
          style={{
            marginTop: 8,
            padding: 14,
            background: "#f0f4ff",
            borderRadius: 8,
            fontSize: 12,
            color: "#4f63c4",
            lineHeight: 1.6,
          }}
        >
          추가 문의는 프로젝트 OWNER에게 연락하거나 관리자에게 문의해주세요.
        </div>
      </div>
    </div>
  );
}
