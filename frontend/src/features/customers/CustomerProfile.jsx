import React from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Button, Icon } from "../../components/common";
import { useUserProfileQuery } from "../../hooks/queries/user";

const METRIC_COLORS = ["#5564f2", "#7c5cff", "#45a557", "#e79c1f"];

function formatDateTime(iso) {
  if (!iso) return "-";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  const pad = (value) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function formatCount(value) {
  return Number(value ?? 0).toLocaleString();
}

function stringifyEventData(eventData) {
  if (!eventData) return "이벤트 속성 없음";
  if (typeof eventData === "string") return eventData;
  return JSON.stringify(eventData);
}

function CustomerProfile() {
  const navigate = useNavigate();
  const { projectId: projectIdParam, customerId } = useParams();
  const projectId = Number(projectIdParam);
  const userId = Number(customerId);
  const [activeTab, setActiveTab] = React.useState("all");

  const { data, isLoading, error } = useUserProfileQuery(
    Number.isFinite(projectId) ? projectId : null,
    Number.isFinite(userId) ? userId : null,
  );

  const recentEvents = data?.recentEvents ?? [];
  const tabs = React.useMemo(() => {
    const categories = Array.from(new Set(recentEvents.map((event) => event.eventCategory).filter(Boolean)));
    return [
      { key: "all", label: "전체" },
      ...categories.slice(0, 5).map((category) => ({ key: category, label: category })),
    ];
  }, [recentEvents]);
  const filteredEvents = activeTab === "all"
    ? recentEvents
    : recentEvents.filter((event) => event.eventCategory === activeTab);

  const displayName = data?.externalId ?? `user_id=${customerId}`;
  const initial = displayName.slice(0, 1).toUpperCase();
  const attributes = data ? [
    ["외부 ID", data.externalId],
    ["이메일", data.email ?? "-"],
    ["전화번호", data.phone ?? "-"],
    ["내부 user_id", String(data.userId)],
    ["가입일", formatDateTime(data.signedUpAt)],
    ["푸시", data.pushOptIn ? "동의" : "미동의"],
    ["플랫폼", data.device?.platform ?? "-"],
    ["OS", data.device?.osVersion ?? "-"],
    ["앱 버전", data.device?.appVersion ?? "-"],
    ["토큰", data.device ? (data.device.tokenValid ? "유효" : "무효") : "-"],
    ["최근 디바이스 활동", formatDateTime(data.device?.lastActiveAt ?? null)],
  ] : [];

  const metrics = data ? [
    ["총 이벤트", `${formatCount(data.summary.totalEvents)}건`],
    ["최근 활동", formatDateTime(data.summary.lastSeenAt)],
    ["등록 디바이스", data.device ? data.device.platform : "없음"],
    ["가입일", formatDateTime(data.signedUpAt)],
  ] : [];

  return (
    <div className="customer-profile-page">
      <style>{`
        .customer-profile-page {
          display: flex;
          flex-direction: column;
          gap: 24px;
          color: #1f2937;
        }

        .customer-profile-header,
        .customer-profile-hero,
        .customer-profile-card,
        .customer-profile-metric {
          background: #fff;
          border: 1px solid rgba(226, 232, 240, 0.72);
          box-shadow: 0 18px 42px rgba(15, 23, 42, 0.06);
        }

        .customer-profile-header {
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 16px;
          min-height: 86px;
          padding: 16px 28px;
          border-radius: 20px;
        }

        .customer-profile-header h1 {
          margin: 0;
          font-family: var(--font-display);
          font-size: 25px;
          line-height: 1.2;
          letter-spacing: 0;
        }

        .customer-profile-header p {
          margin: 6px 0 0;
          color: #7b8493;
          font-size: 14px;
        }

        .customer-profile-hero {
          display: flex;
          align-items: center;
          gap: 22px;
          min-height: 138px;
          padding: 26px 34px;
          border-radius: 20px;
        }

        .customer-profile-avatar {
          width: 90px;
          height: 90px;
          flex: none;
          border-radius: 50%;
          display: grid;
          place-items: center;
          background: #5662dc;
          color: #fff;
          font-family: var(--font-display);
          font-size: 32px;
          font-weight: 700;
        }

        .customer-profile-name h2 {
          margin: 0;
          font-family: var(--font-display);
          font-size: 30px;
          line-height: 1.2;
          letter-spacing: 0;
        }

        .customer-profile-meta {
          margin-top: 4px;
          color: #7b8493;
          font-size: 15px;
        }

        .customer-profile-badges {
          display: flex;
          align-items: center;
          gap: 14px;
          margin-left: auto;
          padding-right: 12%;
          flex-wrap: wrap;
        }

        .customer-profile-chip {
          display: inline-flex;
          align-items: center;
          height: 28px;
          padding: 0 12px;
          border-radius: 999px;
          font-size: 13px;
          font-weight: 700;
          white-space: nowrap;
        }

        .customer-profile-chip.success {
          background: #eaf7ee;
          color: #3a974d;
        }

        .customer-profile-chip.info {
          background: #eef3ff;
          color: #5564f2;
        }

        .customer-profile-chip.muted {
          background: #f1f5f9;
          color: #64748b;
        }

        .customer-profile-metrics {
          display: grid;
          grid-template-columns: repeat(4, minmax(0, 1fr));
          gap: 28px;
        }

        .customer-profile-metric {
          min-height: 96px;
          padding: 20px 32px;
          border-top: 4px solid var(--metric-color);
          border-radius: 14px;
        }

        .customer-profile-metric-label {
          color: #7b8493;
          font-size: 13px;
        }

        .customer-profile-metric-value {
          margin-top: 2px;
          font-family: var(--font-display);
          font-size: 27px;
          line-height: 1.15;
          font-weight: 700;
          font-variant-numeric: tabular-nums;
        }

        .customer-profile-content {
          display: grid;
          grid-template-columns: 468px minmax(0, 1fr);
          gap: 20px;
        }

        .customer-profile-card {
          border-radius: 16px;
          padding: 24px 26px;
        }

        .customer-profile-card h3 {
          margin: 0 0 14px;
          font-family: var(--font-display);
          font-size: 18px;
          letter-spacing: 0;
        }

        .customer-profile-attributes {
          display: flex;
          flex-direction: column;
        }

        .customer-profile-attribute {
          display: grid;
          grid-template-columns: 126px minmax(0, 1fr);
          gap: 0;
          padding: 13px 0;
          border-bottom: 1px solid #e8ebf0;
          font-size: 15px;
        }

        .customer-profile-attribute:last-child {
          border-bottom: 0;
        }

        .customer-profile-attribute span:first-child {
          color: #7b8493;
        }

        .customer-profile-attribute span:last-child {
          min-width: 0;
          color: #2b3340;
          overflow-wrap: anywhere;
        }

        .customer-profile-tabs {
          display: flex;
          align-items: center;
          gap: 14px;
          margin-bottom: 16px;
        }

        .customer-profile-tab {
          min-width: 62px;
          height: 36px;
          border: 0;
          border-radius: 999px;
          background: transparent;
          color: #7b8493;
          cursor: pointer;
          font-size: 14px;
          font-weight: 700;
        }

        .customer-profile-tab.active {
          background: #5662dc;
          color: #fff;
        }

        .customer-profile-timeline {
          position: relative;
          display: flex;
          flex-direction: column;
          gap: 0;
          padding-left: 26px;
        }

        .customer-profile-timeline::before {
          content: "";
          position: absolute;
          left: 5px;
          top: 9px;
          bottom: 9px;
          width: 2px;
          background: #e4e8ef;
        }

        .customer-profile-event {
          position: relative;
          display: grid;
          grid-template-columns: 132px 164px minmax(0, 1fr);
          align-items: start;
          min-height: 58px;
          color: #2b3340;
          font-size: 15px;
        }

        .customer-profile-event::before {
          content: "";
          position: absolute;
          left: -26px;
          top: 7px;
          width: 13px;
          height: 13px;
          border-radius: 50%;
          background: #5662dc;
        }

        .customer-profile-event-time {
          color: #7b8493;
          font-size: 14px;
        }

        .customer-profile-event-name {
          color: #4f60e8;
          font-weight: 700;
          font-variant-numeric: tabular-nums;
          overflow-wrap: anywhere;
        }

        .customer-profile-event-detail {
          min-width: 0;
          color: #2b3340;
          overflow-wrap: anywhere;
        }

        .customer-profile-empty,
        .customer-profile-state {
          padding: 48px;
          text-align: center;
          color: #64748b;
          font-size: 14px;
        }

        .customer-profile-state.error {
          color: #b91c1c;
        }

        @media (max-width: 1180px) {
          .customer-profile-metrics {
            grid-template-columns: repeat(2, minmax(0, 1fr));
          }

          .customer-profile-content {
            grid-template-columns: 1fr;
          }

          .customer-profile-badges {
            padding-right: 0;
            margin-left: 0;
          }
        }

        @media (max-width: 720px) {
          .customer-profile-header,
          .customer-profile-hero {
            align-items: flex-start;
            flex-direction: column;
          }

          .customer-profile-metrics {
            grid-template-columns: 1fr;
            gap: 14px;
          }

          .customer-profile-event {
            grid-template-columns: 96px minmax(0, 1fr);
            row-gap: 2px;
          }

          .customer-profile-event-detail {
            grid-column: 2;
          }

          .customer-profile-attribute {
            grid-template-columns: 96px minmax(0, 1fr);
          }
        }
      `}</style>

      <section className="customer-profile-header">
        <div>
          <h1>고객 프로필</h1>
          <p>DB에 저장된 고객 속성과 행동 이력을 확인합니다</p>
        </div>
        <Button variant="primaryStrong" size="lg" icon={Icon.search(15)} onClick={() => navigate(`/p/${projectId}/customers`)}>
          고객 검색
        </Button>
      </section>

      {isLoading && <div className="customer-profile-state">고객 프로필을 불러오는 중입니다.</div>}
      {error && <div className="customer-profile-state error">고객 프로필을 불러오지 못했습니다.</div>}
      {!isLoading && !error && data && (
        <>
          <section className="customer-profile-hero">
            <div className="customer-profile-avatar" aria-hidden="true">
              {initial}
            </div>
            <div>
              <div className="customer-profile-name">
                <h2>{displayName}</h2>
              </div>
              <div className="customer-profile-meta">
                user_id={data.userId} · 마지막 활동 {formatDateTime(data.summary.lastSeenAt)}
              </div>
            </div>
            <div className="customer-profile-badges">
              <span className={`customer-profile-chip ${data.pushOptIn ? "success" : "muted"}`}>
                {data.pushOptIn ? "푸시 동의" : "푸시 미동의"}
              </span>
              <span className={`customer-profile-chip ${data.device ? "info" : "muted"}`}>
                {data.device ? `${data.device.platform} 디바이스` : "디바이스 없음"}
              </span>
            </div>
          </section>

          <section className="customer-profile-metrics">
            {metrics.map(([label, value], index) => (
              <div
                key={label}
                className="customer-profile-metric"
                style={{ "--metric-color": METRIC_COLORS[index] }}
              >
                <div className="customer-profile-metric-label">{label}</div>
                <div className="customer-profile-metric-value">{value}</div>
              </div>
            ))}
          </section>

          <section className="customer-profile-content">
            <article className="customer-profile-card">
              <h3>고객 속성</h3>
              <div className="customer-profile-attributes">
                {attributes.map(([label, value]) => (
                  <div key={label} className="customer-profile-attribute">
                    <span>{label}</span>
                    <span>{value}</span>
                  </div>
                ))}
              </div>
            </article>

            <article className="customer-profile-card">
              <h3>행동 이력</h3>
              <div className="customer-profile-tabs" role="tablist" aria-label="행동 이력 필터">
                {tabs.map((tab) => (
                  <button
                    key={tab.key}
                    type="button"
                    className={`customer-profile-tab${activeTab === tab.key ? " active" : ""}`}
                    onClick={() => setActiveTab(tab.key)}
                  >
                    {tab.label}
                  </button>
                ))}
              </div>
              {filteredEvents.length === 0 ? (
                <div className="customer-profile-empty">표시할 행동 이력이 없습니다.</div>
              ) : (
                <div className="customer-profile-timeline">
                  {filteredEvents.map((event) => (
                    <div key={event.eventId} className="customer-profile-event">
                      <div className="customer-profile-event-time">{formatDateTime(event.occurredAt)}</div>
                      <div className="customer-profile-event-name">{event.eventCategory}</div>
                      <div className="customer-profile-event-detail">{stringifyEventData(event.eventData)}</div>
                    </div>
                  ))}
                </div>
              )}
            </article>
          </section>
        </>
      )}
    </div>
  );
}

export default CustomerProfile;
