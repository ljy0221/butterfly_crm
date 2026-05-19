import { Link, useParams } from "react-router-dom";
import { useUserProfileQuery } from "@/hooks/queries/user";

function formatDateTime(iso: string | null): string {
  if (!iso) return "-";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/**
 * 5/22 시연용 — 사용자 프로필 화면.
 * 세그먼트 빌더 미리보기 결과에서 사용자를 클릭해 진입한다.
 */
export default function UserProfile() {
  const params = useParams<{ projectId: string; userId: string }>();
  const projectId = Number(params.projectId);
  const userId = Number(params.userId);

  const { data, isLoading, error } = useUserProfileQuery(projectId, userId);

  if (isLoading) {
    return <div className="p-6 text-sm text-gray-500">로딩 중…</div>;
  }
  if (error) {
    // eslint-disable-next-line no-console
    console.error("[UserProfile] fetch failed", error);
    return (
      <div className="p-6 text-sm text-red-600">
        프로필을 불러오지 못했습니다.
      </div>
    );
  }
  if (!data) {
    return <div className="p-6 text-sm text-gray-500">데이터 없음</div>;
  }

  return (
    <div className="p-6 space-y-6">
      <Link
        to={`/p/${projectId}/segments`}
        className="text-sm text-gray-500 hover:text-gray-800"
      >
        ← 세그먼트로 돌아가기
      </Link>

      <header>
        <h1 className="text-2xl font-bold">{data.externalId}</h1>
        <div className="text-sm text-gray-500">
          가입일: {formatDateTime(data.signedUpAt)}
        </div>
      </header>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <section className="border rounded p-4">
          <h2 className="text-sm font-semibold mb-2">디바이스</h2>
          {data.device ? (
            <dl className="text-sm space-y-1">
              <div>
                <dt className="inline text-gray-500">플랫폼: </dt>
                <dd className="inline">{data.device.platform}</dd>
              </div>
              <div>
                <dt className="inline text-gray-500">OS: </dt>
                <dd className="inline">{data.device.osVersion}</dd>
              </div>
              <div>
                <dt className="inline text-gray-500">앱 버전: </dt>
                <dd className="inline">{data.device.appVersion}</dd>
              </div>
              <div>
                <dt className="inline text-gray-500">푸시 동의: </dt>
                <dd className="inline">{data.pushOptIn ? "✓" : "✗"}</dd>
              </div>
              <div>
                <dt className="inline text-gray-500">토큰 유효: </dt>
                <dd className="inline">{data.device.tokenValid ? "✓" : "✗"}</dd>
              </div>
            </dl>
          ) : (
            <div className="text-sm text-gray-400">등록된 디바이스 없음</div>
          )}
        </section>

        <section className="border rounded p-4">
          <h2 className="text-sm font-semibold mb-2">행동 요약</h2>
          <dl className="text-sm space-y-1">
            <div>
              <dt className="inline text-gray-500">총 이벤트: </dt>
              <dd className="inline">{data.summary.totalEvents.toLocaleString()}건</dd>
            </div>
            <div>
              <dt className="inline text-gray-500">최근 활동: </dt>
              <dd className="inline">{formatDateTime(data.summary.lastSeenAt)}</dd>
            </div>
          </dl>
        </section>
      </div>

      <section>
        <h2 className="text-sm font-semibold mb-2">
          행동 타임라인 (최근 {data.recentEvents.length}건)
        </h2>
        <ul className="border rounded divide-y">
          {data.recentEvents.length === 0 && (
            <li className="p-3 text-sm text-gray-400">이벤트 없음</li>
          )}
          {data.recentEvents.map((e) => (
            <li key={e.eventId} className="p-3 flex gap-4 text-sm">
              <span className="text-gray-500 w-44 shrink-0">{formatDateTime(e.occurredAt)}</span>
              <span className="font-medium w-32 shrink-0">{e.eventCategory}</span>
              <span
                className="text-gray-600 truncate"
                title={e.eventData ? JSON.stringify(e.eventData, null, 2) : ""}
              >
                {e.eventData ? JSON.stringify(e.eventData) : "—"}
              </span>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
