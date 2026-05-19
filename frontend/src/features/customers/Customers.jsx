import React from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  Avatar, Button, Card, Checkbox, Icon, Menu, PageHeader, SearchInput, StatCard, Tag,
} from "../../components/common";
import { toast, openModal } from "../../lib/ui-bus";
import { useUsersQuery } from "../../hooks/queries/user";
import AddToSegmentModal from "./AddToSegmentModal.jsx";

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

function Customers() {
  const navigate = useNavigate();
  const { projectId: projectIdParam } = useParams();
  const projectId = Number(projectIdParam);
  const [query, setQuery] = React.useState("");
  const [page, setPage] = React.useState(0);
  const [selected, setSelected] = React.useState(new Set());
  const pageSize = 20;

  const { data, isLoading, error } = useUsersQuery(
    Number.isFinite(projectId) ? projectId : null,
    { page, size: pageSize, search: query },
  );

  const customers = data?.content ?? [];
  const selectedOnPage = customers.filter((customer) => selected.has(customer.userId));
  const allSelected = customers.length > 0 && customers.every((customer) => selected.has(customer.userId));
  const pageEventCount = customers.reduce((sum, customer) => sum + customer.totalEvents, 0);
  const pagePushOptInCount = customers.filter((customer) => customer.pushOptIn).length;

  const changeQuery = (value) => {
    setQuery(value);
    setPage(0);
    setSelected(new Set());
  };

  const toggle = (userId) => {
    setSelected((prev) => {
      const next = new Set(prev);
      next.has(userId) ? next.delete(userId) : next.add(userId);
      return next;
    });
  };

  const toggleAll = () => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (allSelected) {
        customers.forEach((customer) => next.delete(customer.userId));
      } else {
        customers.forEach((customer) => next.add(customer.userId));
      }
      return next;
    });
  };

  const openSegmentModal = () => {
    openModal(({ close }) => (
      <AddToSegmentModal
        close={close}
        count={selectedOnPage.length}
        onDone={() => setSelected(new Set())}
      />
    ));
  };

  return (
    <div>
      <PageHeader
        title="고객"
        subtitle="DB에 저장된 프로젝트 고객을 조회합니다."
      />

      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 14, marginBottom: 20 }}>
        <StatCard label="전체 고객" value={formatCount(data?.totalElements ?? 0)} sub="users 테이블 기준" />
        <StatCard label="현재 페이지" value={`${formatCount(customers.length)}명`} sub={`${page + 1}/${Math.max(data?.totalPages ?? 1, 1)} 페이지`} />
        <StatCard label="푸시 동의" value={`${formatCount(pagePushOptInCount)}명`} sub="현재 페이지 기준" />
        <StatCard label="행동 이벤트" value={`${formatCount(pageEventCount)}건`} sub="현재 페이지 기준" />
      </div>

      <Card>
        <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 14 }}>
          <SearchInput
            placeholder="외부 ID, 이메일, 전화번호로 검색"
            value={query}
            onChange={changeQuery}
            width={360}
          />
          <div style={{ flex: 1 }} />
          <span style={{ fontSize: 12, color: "#64748b" }}>
            총 <b style={{ color: "#1f2937", fontVariantNumeric: "tabular-nums" }}>{formatCount(data?.totalElements ?? 0)}</b>명
            {selected.size > 0 && <span style={{ color: "#3b5bdb" }}> · {selected.size} 선택</span>}
          </span>
        </div>

        {selected.size > 0 && (
          <div style={{ padding: "10px 14px", background: "#eff3fe", border: "1px solid #bfd1fe", borderRadius: 8, marginBottom: 12, display: "flex", alignItems: "center", gap: 10 }}>
            <span style={{ fontSize: 12, color: "#1e40af", fontWeight: 600 }}>{selected.size}명 선택됨</span>
            <Button variant="secondary" size="sm" icon={Icon.plus(12)} onClick={openSegmentModal}>세그먼트에 추가</Button>
            <Button variant="ghost" size="sm" icon={Icon.send(12)} onClick={() => toast("선택 고객 기반 캠페인 생성은 세그먼트 연결 후 진행합니다.", "info")}>캠페인 만들기</Button>
            <div style={{ flex: 1 }} />
            <span onClick={() => setSelected(new Set())} style={{ fontSize: 11, color: "#64748b", cursor: "pointer" }}>선택 해제</span>
          </div>
        )}

        <div style={{ border: "1px solid #e2e8f0", borderRadius: 10, overflow: "hidden" }}>
          <div style={{ display: "grid", gridTemplateColumns: "40px 1.8fr 1.8fr .8fr 1fr 1fr 40px", padding: "12px 16px", background: "#f8fafc", borderBottom: "1px solid #e2e8f0", fontSize: 11, color: "#64748b", fontWeight: 600 }}>
            <div><Checkbox checked={allSelected} onChange={toggleAll} /></div>
            <div>고객</div>
            <div>이메일</div>
            <div>푸시</div>
            <div>이벤트</div>
            <div>최근 활동</div>
            <div></div>
          </div>

          {isLoading && (
            <div style={{ padding: 60, textAlign: "center", color: "#64748b", fontSize: 13 }}>고객을 불러오는 중입니다.</div>
          )}
          {error && (
            <div style={{ padding: 60, textAlign: "center", color: "#b91c1c", fontSize: 13 }}>
              고객 목록을 불러오지 못했습니다.
            </div>
          )}
          {!isLoading && !error && customers.length === 0 && (
            <div style={{ padding: 60, textAlign: "center", color: "#94a3b8", fontSize: 13 }}>검색 결과가 없습니다.</div>
          )}

          {!isLoading && !error && customers.map((customer, index) => (
            <div
              key={customer.userId}
              onClick={() => navigate(String(customer.userId))}
              style={{
                display: "grid",
                gridTemplateColumns: "40px 1.8fr 1.8fr .8fr 1fr 1fr 40px",
                padding: "14px 16px",
                alignItems: "center",
                fontSize: 13,
                cursor: "pointer",
                borderBottom: index < customers.length - 1 ? "1px solid #f1f5f9" : "none",
                background: selected.has(customer.userId) ? "#fafbfe" : "#fff",
                transition: "background 120ms",
              }}
              onMouseEnter={(event) => { event.currentTarget.style.background = selected.has(customer.userId) ? "#eff3fe" : "#f8fafc"; }}
              onMouseLeave={(event) => { event.currentTarget.style.background = selected.has(customer.userId) ? "#fafbfe" : "#fff"; }}
            >
              <div onClick={(event) => { event.stopPropagation(); toggle(customer.userId); }}>
                <Checkbox checked={selected.has(customer.userId)} onChange={() => toggle(customer.userId)} />
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 10, minWidth: 0 }}>
                <Avatar name={customer.externalId} size={28} />
                <div style={{ minWidth: 0 }}>
                  <div style={{ color: "#1f2937", fontWeight: 600, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{customer.externalId}</div>
                  <div style={{ fontSize: 11, color: "#94a3b8", fontFamily: "var(--font-mono)" }}>user_id={customer.userId}</div>
                </div>
              </div>
              <div style={{ color: "#64748b", fontSize: 12, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{customer.email ?? "-"}</div>
              <div>
                <Tag tone={customer.pushOptIn ? "success" : "default"} dot>{customer.pushOptIn ? "동의" : "미동의"}</Tag>
              </div>
              <div style={{ color: "#1f2937", fontVariantNumeric: "tabular-nums" }}>{formatCount(customer.totalEvents)}</div>
              <div style={{ color: "#64748b", fontSize: 12 }}>{formatDateTime(customer.lastSeenAt ?? customer.signedUpAt ?? customer.createdAt)}</div>
              <div onClick={(event) => event.stopPropagation()}>
                <Menu
                  trigger={<button style={{ width: 26, height: 26, border: 0, borderRadius: 6, background: "transparent", color: "#94a3b8", cursor: "pointer", display: "inline-flex", alignItems: "center", justifyContent: "center" }}>{Icon.dots(14)}</button>}
                  items={[
                    { label: "상세 보기", icon: Icon.edit(12), onClick: () => navigate(String(customer.userId)) },
                    { label: "세그먼트에 추가", icon: Icon.plus(12), onClick: () => openModal(({ close }) => <AddToSegmentModal close={close} count={1} onDone={() => {}} />) },
                  ]}
                />
              </div>
            </div>
          ))}
        </div>

        <div style={{ display: "flex", alignItems: "center", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
          <Button
            variant="secondary"
            size="sm"
            disabled={page <= 0}
            onClick={() => setPage((value) => Math.max(value - 1, 0))}
          >
            이전
          </Button>
          <span style={{ fontSize: 12, color: "#64748b" }}>
            {page + 1} / {Math.max(data?.totalPages ?? 1, 1)}
          </span>
          <Button
            variant="secondary"
            size="sm"
            disabled={!data || page + 1 >= data.totalPages}
            onClick={() => setPage((value) => value + 1)}
          >
            다음
          </Button>
        </div>
      </Card>
    </div>
  );
}

export default Customers;
