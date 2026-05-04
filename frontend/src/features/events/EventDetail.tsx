import { Navigate, useNavigate, useParams } from "react-router-dom";
import {
  Button,
  Card,
  Icon,
  PageHeader,
  StatCard,
  Tag,
} from "@/components/common";
import { openModal } from "@/lib/ui-bus";
import { mapEventError } from "@/lib/event-errors";
import {
  useEventDetailQuery,
  useEventSampleQuery,
  useEventSchemaQuery,
} from "@/hooks/queries/event";
import { useProjectStore } from "@/stores/project";
import EditSchemaModal from "./EditSchemaModal";

/**
 * Sprint 7 — 이벤트 카테고리 상세 페이지.
 *
 * URL: /p/:projectId/events/:eventName
 *
 * 좌(1fr): 속성 스키마 테이블
 * 우(380px): 최근 수신 샘플 JSON / (사용 세그먼트 P1)
 */

function fmtNumber(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`;
  return n.toLocaleString();
}

function fmtDateTime(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
}

const TYPE_TONE: Record<string, "default" | "info" | "soft" | "success" | "warning"> = {
  string: "info",
  number: "soft",
  boolean: "success",
  datetime: "warning",
  object: "default",
};

export default function EventDetail() {
  const project = useProjectStore((s) => s.current);
  const role = project?.role ?? "VIEWER";
  const canEdit = role !== "VIEWER";
  const projectId = project?.projectId ?? null;
  const params = useParams<{ projectId: string; eventName: string }>();
  const navigate = useNavigate();
  const eventName = params.eventName ? decodeURIComponent(params.eventName) : null;

  // Hook 호출은 early return 이전에 — enabled 플래그로 분기
  const enabled = projectId != null && !!eventName;
  const { data: cat, isLoading, isError, error } = useEventDetailQuery(
    projectId,
    eventName,
    enabled,
  );
  const { data: schema } = useEventSchemaQuery(projectId, eventName, enabled);
  const { data: sample } = useEventSampleQuery(projectId, eventName, enabled);

  if (projectId == null) {
    return <Navigate to="/projects" replace />;
  }
  if (!eventName) {
    return <Navigate to={`/p/${projectId}/events`} replace />;
  }

  if (isLoading) {
    return (
      <div>
        <PageHeader
          title={<span style={{ fontFamily: "var(--font-mono)" }}>{eventName}</span>}
          subtitle="로딩 중..."
        />
        <div
          style={{
            padding: 32,
            textAlign: "center",
            color: "#94a3b8",
            fontSize: 13,
          }}
        >
          이벤트 카테고리를 불러오는 중...
        </div>
      </div>
    );
  }
  if (isError || !cat) {
    return (
      <div>
        <PageHeader
          title={<span style={{ fontFamily: "var(--font-mono)" }}>{eventName}</span>}
          subtitle="오류"
          back="이벤트 목록"
          onBack={() => navigate(`/p/${projectId}/events`)}
        />
        <Card style={{}} padding={24}>
          <div
            role="alert"
            style={{ color: "#b91c1c", fontSize: 13, marginBottom: 12 }}
          >
            {mapEventError(error).message}
          </div>
        </Card>
      </div>
    );
  }

  const onEditSchema = (): void => {
    if (!schema) return;
    openModal(({ close }) => (
      <EditSchemaModal
        projectId={projectId}
        categoryName={cat.categoryName}
        initial={schema}
        close={close}
      />
    ));
  };

  return (
    <div>
      <PageHeader
        back="이벤트 목록"
        onBack={() => navigate(`/p/${projectId}/events`)}
        title={
          <span style={{ fontFamily: "var(--font-mono)", fontSize: 22 }}>
            {cat.categoryName}
          </span>
        }
        subtitle={
          <span
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: 10,
              flexWrap: "wrap",
            }}
          >
            <Tag tone={cat.kind === "BUILTIN" ? "info" : "soft"} style={{}}>
              {cat.kind === "BUILTIN" ? "기본" : "커스텀"}
            </Tag>
            <span style={{ color: "#64748b" }}>{cat.displayName}</span>
            <span style={{ color: "#94a3b8", fontSize: 11 }}>
              · {fmtNumber(cat.eventCount)} 건 / 30일
            </span>
          </span>
        }
        actions={
          canEdit ? (
            <Button
              variant="primaryStrong"
              size="md"
              icon={Icon.edit(12)}
              onClick={onEditSchema}
              disabled={!schema}
              style={{}}
            >
              스키마 편집
            </Button>
          ) : null
        }
      />

      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(4, 1fr)",
          gap: 14,
          marginBottom: 20,
        }}
      >
        <StatCard
          label="30일 수집량"
          value={fmtNumber(cat.eventCount)}
          sub="이벤트 수 (mock)"
          style={{}}
        />
        <StatCard
          label="수집 성공률"
          value={`${cat.successRate.toFixed(1)}%`}
          sub="최근 24시간"
          style={{}}
        />
        <StatCard
          label="속성 수"
          value={String(schema?.length ?? 0)}
          sub={`필수 ${schema?.filter((p) => p.required).length ?? 0}개`}
          style={{}}
        />
        <StatCard
          label="마지막 수신"
          value={fmtDateTime(cat.lastCollectedAt)}
          sub="KST"
          style={{}}
        />
      </div>

      <div
        style={{
          display: "grid",
          gridTemplateColumns: "1fr 380px",
          gap: 14,
        }}
      >
        <Card
          title="속성 스키마"
          subtitle="이벤트 데이터 JSON 구조"
          action={
            canEdit ? (
              <Button
                variant="text"
                size="sm"
                icon={Icon.edit(12)}
                onClick={onEditSchema}
                disabled={!schema}
                style={{}}
              >
                편집
              </Button>
            ) : null
          }
          style={{}}
        >
          <div
            style={{
              border: "1px solid #e2e8f0",
              borderRadius: 8,
              overflow: "hidden",
            }}
          >
            <div
              style={{
                display: "grid",
                gridTemplateColumns: "1.2fr 0.7fr 0.5fr 1.3fr",
                padding: "10px 14px",
                background: "#f8fafc",
                fontSize: 11,
                color: "#64748b",
                fontWeight: 600,
                borderBottom: "1px solid #e2e8f0",
              }}
            >
              <div>속성 이름</div>
              <div>타입</div>
              <div>필수</div>
              <div>예시값</div>
            </div>
            {(schema ?? []).map((s, i, arr) => (
              <div
                key={s.key}
                style={{
                  display: "grid",
                  gridTemplateColumns: "1.2fr 0.7fr 0.5fr 1.3fr",
                  padding: "11px 14px",
                  alignItems: "center",
                  fontSize: 12,
                  borderBottom:
                    i < arr.length - 1 ? "1px solid #f1f5f9" : "none",
                  background: "#fff",
                }}
              >
                <div
                  style={{
                    fontFamily: "var(--font-mono)",
                    color: "#1f2937",
                    fontWeight: 500,
                  }}
                >
                  {s.key}
                </div>
                <div>
                  <Tag tone={TYPE_TONE[s.type] ?? "default"} style={{}}>
                    {s.type}
                  </Tag>
                </div>
                <div>
                  {s.required ? (
                    <span
                      style={{ color: "#16a34a", display: "inline-flex" }}
                    >
                      {Icon.check(13)}
                    </span>
                  ) : (
                    <span style={{ color: "#cbd5e1" }}>—</span>
                  )}
                </div>
                <div
                  style={{
                    fontFamily: "var(--font-mono)",
                    color: "#64748b",
                    overflow: "hidden",
                    textOverflow: "ellipsis",
                    whiteSpace: "nowrap",
                  }}
                >
                  {s.example ?? "—"}
                </div>
              </div>
            ))}
            {(schema?.length ?? 0) === 0 && (
              <div
                style={{
                  padding: 24,
                  textAlign: "center",
                  color: "#94a3b8",
                  fontSize: 13,
                }}
              >
                속성이 없습니다. [스키마 편집] 으로 속성을 추가하세요.
              </div>
            )}
          </div>
          <div
            style={{
              marginTop: 14,
              padding: 12,
              background: "#fffbeb",
              border: "1px solid #fde68a",
              borderRadius: 8,
              fontSize: 12,
              color: "#92400e",
              display: "flex",
              gap: 10,
              alignItems: "flex-start",
            }}
          >
            <span style={{ fontSize: 14 }}>⚠</span>
            <div>
              <div style={{ fontWeight: 600, marginBottom: 2 }}>
                스키마 변경 시 주의
              </div>
              속성 이름이나 타입을 바꾸면 이 이벤트를 사용하는 세그먼트의 예상
              모수가 재계산될 수 있습니다.
            </div>
          </div>
        </Card>

        <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          <Card
            title="최근 수신 샘플"
            subtitle="실시간 · 최근 1건"
            style={{}}
          >
            {sample ? (
              <pre
                style={{
                  margin: 0,
                  padding: 14,
                  background: "#0f172a",
                  color: "#e2e8f0",
                  borderRadius: 8,
                  fontFamily: "var(--font-mono)",
                  fontSize: 11,
                  lineHeight: 1.7,
                  overflow: "auto",
                  maxHeight: 360,
                }}
              >
                <span style={{ color: "#94a3b8" }}>
                  {/* prettier-ignore */}
                  {`// occurredAt: ${sample.occurredAt}`}
                </span>
                {"\n"}
                {JSON.stringify(sample.eventData, null, 2)}
              </pre>
            ) : (
              <div
                style={{
                  padding: 16,
                  textAlign: "center",
                  color: "#94a3b8",
                  fontSize: 12,
                }}
              >
                최근 수신된 이벤트가 없습니다.
              </div>
            )}
          </Card>
          <Card
            title="사용 중인 세그먼트"
            subtitle="이 이벤트 기반 (P1)"
            style={{}}
          >
            <div
              style={{
                padding: 12,
                color: "#94a3b8",
                fontSize: 12,
                textAlign: "center",
              }}
            >
              세그먼트-이벤트 링크는 후속 스프린트에서 제공됩니다.
            </div>
          </Card>
        </div>
      </div>
    </div>
  );
}
