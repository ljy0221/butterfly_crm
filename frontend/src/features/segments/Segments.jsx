import React from "react";
import {
  Icon, Button, Tag, ChannelBadge, CampaignStatus, Card, StatCard,
  Input, Select, Avatar, Checkbox, Menu, SearchInput, PageHeader,
} from "../../components/common";
import { toast, openModal, confirmDialog } from "../../lib/ui-bus.js";
import { fmtNum } from "../../utils/fmt.js";
import { CRM_DATA } from "../../data/mock.js";

function Segments({ onNavigate }) {
  const onOpenBuilder = (s) => onNavigate(s ? "세그먼트-상세:" + s.name : "세그먼트-신규");
  const { SEGMENTS } = CRM_DATA;
  const [favs, setFavs] = React.useState(Object.fromEntries(SEGMENTS.map(s => [s.name, s.fav])));

  return (
    <div>
      <PageHeader
        title="세그먼트"
        subtitle="이벤트와 속성을 조합해 타겟 그룹을 정의합니다."
        actions={<>
          <Button variant="secondary" size="md" icon={Icon.copy(12)}>가져오기</Button>
          <Button variant="primaryStrong" size="md" icon={Icon.plus(12)} onClick={() => onOpenBuilder()}>세그먼트 만들기</Button>
        </>}
      />

      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 14, marginBottom: 20 }}>
        <StatCard label="전체 세그먼트" value="24" sub="활성 18 · 초안 6" />
        <StatCard label="캠페인에 연결됨" value="13" sub="운영 중" />
        <StatCard label="총 타겟 인원 (중복 제거)" value="162,841" delta="4.2%" trend="up" />
        <StatCard label="평균 재계산 시간" value="1.8s" sub="최근 24시간" />
      </div>

      <Card>
        <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 14 }}>
          <div style={{ flex: 1, display: "flex", alignItems: "center", gap: 8, height: 32, padding: "0 12px", border: "1px solid #e2e8f0", borderRadius: 8, background: "#fff", maxWidth: 320 }}>
            <span style={{ color: "#94a3b8" }}>{Icon.search(13)}</span>
            <input placeholder="세그먼트명으로 검색" style={{ flex: 1, border: 0, outline: 0, background: "transparent", fontFamily: "inherit", fontSize: 12 }} />
          </div>
          <Button variant="ghost" size="sm" icon={Icon.filter(12)}>생성자</Button>
          <Button variant="ghost" size="sm" icon={Icon.calendar(12)}>생성일</Button>
          <div style={{ flex: 1 }} />
          <Select value="recent" onChange={()=>{}} style={{ width: 140 }}>
            <option value="recent">최근 사용순</option>
            <option value="reach">예상 모수순</option>
            <option value="created">생성일순</option>
          </Select>
        </div>

        <div style={{ border: "1px solid #e2e8f0", borderRadius: 10, overflow: "hidden" }}>
          <div style={{ display: "grid", gridTemplateColumns: "30px 2.4fr 1fr 0.9fr 0.9fr 0.9fr 40px", padding: "12px 16px", background: "#f8fafc", borderBottom: "1px solid #e2e8f0", fontSize: 11, color: "#64748b", fontWeight: 600 }}>
            <div></div><div>세그먼트</div><div>생성자</div><div>예상 모수</div><div>연결 캠페인</div><div>최종 수정</div><div></div>
          </div>
          {SEGMENTS.map((s, i) => (
            <div key={s.name} onClick={() => onOpenBuilder(s)} style={{
              display: "grid", gridTemplateColumns: "30px 2.4fr 1fr 0.9fr 0.9fr 0.9fr 40px",
              padding: "14px 16px", alignItems: "center", fontSize: 13, cursor: "pointer",
              borderBottom: i < SEGMENTS.length - 1 ? "1px solid #f1f5f9" : "none",
              transition: "background 120ms",
            }}
              onMouseEnter={e => e.currentTarget.style.background = "#f8fafc"}
              onMouseLeave={e => e.currentTarget.style.background = "#fff"}
            >
              <div onClick={e => { e.stopPropagation(); setFavs({ ...favs, [s.name]: !favs[s.name] }); }} style={{ cursor: "pointer" }}>
                {Icon.star(favs[s.name], 14)}
              </div>
              <div>
                <div style={{ color: "#1f2937", fontWeight: 500 }}>{s.name}</div>
                <div style={{ fontSize: 11, color: "#94a3b8", marginTop: 2, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{s.desc}</div>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                <Avatar name={s.creator} size={22} />
                <span style={{ fontSize: 12, color: "#64748b" }}>{s.creator}</span>
              </div>
              <div style={{ color: "#1f2937", fontVariantNumeric: "tabular-nums", fontWeight: 500 }}>{fmtNum(s.reach)}</div>
              <div><Tag tone={s.campaigns > 0 ? "info" : "default"}>{s.campaigns}개</Tag></div>
              <div style={{ color: "#64748b", fontSize: 12 }}>{s.created}</div>
              <div style={{ color: "#cbd5e1", textAlign: "right" }}>{Icon.chevron(12)}</div>
            </div>
          ))}
        </div>
      </Card>
    </div>
  );
}

export default Segments;
