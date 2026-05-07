import { useNavigate } from "react-router-dom";
import { Card, Icon, PageHeader, Tag } from "@/components/common";
import { useProjectStore } from "@/stores/project";

/**
 * 분석 도메인 허브 페이지.
 *
 * 백엔드에 머지된 4개 리포트 진입점을 카드로 노출:
 *  - 대시보드 (API 57): 모든 권한
 *  - 캠페인 단계 펀널 (S14P31B101-90): OWNER
 *  - 세그먼트 × 채널 매트릭스 (S14P31B101-92): OWNER
 *  - 감사 로그 CSV 내보내기 (S14P31B101-91): 관리자 페이지로 안내
 *
 * 권한이 부족한 카드는 비활성 상태로 표시 + 안내 텍스트.
 */

interface AnalyticsCard {
  key: string;
  title: string;
  description: string;
  icon: () => React.ReactNode;
  to?: string;
  /** OWNER 전용이면 'OWNER', 외부 안내면 'ADMIN' */
  requires?: "OWNER" | "ADMIN";
  status?: "ready" | "soon";
}

const CARDS: AnalyticsCard[] = [
  {
    key: "dashboard",
    title: "대시보드",
    description: "기간 KPI · 발송 추이 · 채널 비중 · 최근 캠페인",
    icon: () => Icon.dashboard(20),
    to: "../dashboard",
    status: "ready",
  },
  {
    key: "funnel",
    title: "캠페인 단계 펀널",
    description: "발송 → 전달 → 오픈 → 클릭 → 전환 5단계 이탈 추적",
    icon: () => Icon.analytics(20),
    to: "funnel",
    requires: "OWNER",
    status: "ready",
  },
  {
    key: "matrix",
    title: "세그먼트 × 채널 매트릭스",
    description: "세그먼트별 · 채널별 성과 비교, 상하위 조합 하이라이트",
    icon: () => Icon.segments(20),
    to: "segment-channel",
    requires: "OWNER",
    status: "ready",
  },
  {
    key: "audit",
    title: "감사 로그 내보내기",
    description: "CSV 다운로드는 관리자 페이지에서 가능합니다.",
    icon: () => Icon.copy(20),
    requires: "ADMIN",
    status: "ready",
  },
];

export default function AnalyticsHome() {
  const navigate = useNavigate();
  const project = useProjectStore((s) => s.current);
  const role = project?.role ?? "VIEWER";

  return (
    <div>
      <PageHeader
        title="분석"
        subtitle="대시보드 · 펀널 · 매트릭스 · 감사 로그 진입점"
      />
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))",
          gap: 14,
        }}
      >
        {CARDS.map((card) => {
          const allowed =
            !card.requires ||
            (card.requires === "OWNER" && role === "OWNER") ||
            card.requires === "ADMIN"; // ADMIN 카드는 진입 자체는 외부 페이지 안내
          const clickable = card.status === "ready" && card.to && allowed;
          return (
            <Card
              key={card.key}
              style={{
                cursor: clickable ? "pointer" : "default",
                opacity: allowed ? 1 : 0.55,
              }}
              onClick={() => {
                if (clickable && card.to) navigate(card.to);
              }}
            >
              <div
                style={{
                  display: "flex",
                  alignItems: "flex-start",
                  gap: 12,
                  padding: "4px 0",
                }}
              >
                <div
                  style={{
                    width: 40,
                    height: 40,
                    borderRadius: 10,
                    background: "#eef2ff",
                    color: "#3730a3",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    flexShrink: 0,
                  }}
                >
                  {card.icon()}
                </div>
                <div style={{ minWidth: 0, flex: 1 }}>
                  <div
                    style={{
                      display: "flex",
                      alignItems: "center",
                      gap: 6,
                      marginBottom: 4,
                    }}
                  >
                    <h3
                      style={{
                        margin: 0,
                        fontSize: 15,
                        fontWeight: 600,
                        color: "#1f2937",
                      }}
                    >
                      {card.title}
                    </h3>
                    {card.requires === "OWNER" && (
                      <Tag tone={role === "OWNER" ? "info" : "warning"}>
                        OWNER
                      </Tag>
                    )}
                    {card.requires === "ADMIN" && <Tag tone="warning">관리자</Tag>}
                  </div>
                  <p
                    style={{
                      margin: 0,
                      fontSize: 12,
                      color: "#64748b",
                      lineHeight: 1.5,
                    }}
                  >
                    {card.description}
                  </p>
                  {!allowed && card.requires === "OWNER" && (
                    <p
                      style={{
                        margin: "8px 0 0",
                        fontSize: 11,
                        color: "#ea580c",
                      }}
                    >
                      OWNER 권한이 필요합니다 (현재: {role})
                    </p>
                  )}
                </div>
              </div>
            </Card>
          );
        })}
      </div>
    </div>
  );
}
