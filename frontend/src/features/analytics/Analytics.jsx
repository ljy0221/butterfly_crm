import React from "react";
import {
  Icon, Button, Tag, ChannelBadge, CampaignStatus, Card, StatCard,
  Input, Select, Avatar, Checkbox, Menu, SearchInput, PageHeader,
} from "../../components/common";
import { toast, openModal, confirmDialog } from "../../lib/ui-bus.js";
import { fmtNum } from "../../utils/fmt.js";
import { CRM_DATA } from "../../data/mock.js";

function Analytics() {
  return (
    <div>
      <PageHeader
        title="분석"
        subtitle="캠페인 성과 · 템플릿 효율 · 실패 원인을 한눈에 파악합니다."
        actions={<>
          <Button variant="secondary" size="md" icon={Icon.calendar(12)}>최근 30일</Button>
          <Button variant="primaryStrong" size="md" icon={Icon.copy(12)}>리포트 내보내기</Button>
        </>}
      />

      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 14, marginBottom: 20 }}>
        <StatCard label="총 발송" value="5,218,402" delta="12.4%" trend="up" />
        <StatCard label="성공률" value="99.2%" delta="0.3%p" trend="up" />
        <StatCard label="평균 오픈률" value="38.4%" delta="2.1%p" trend="up" />
        <StatCard label="전환율 (오픈→클릭)" value="18.2%" delta="1.4%p" trend="up" />
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr", gap: 14, marginBottom: 20 }}>
        <Card title="깔때기" subtitle="발송 → 도달 → 오픈 → 클릭 → 전환">
          <div style={{ display: "flex", alignItems: "flex-end", gap: 14, padding: "10px 0 0" }}>
            {[
              { l: "발송", v: "5.21M", p: 100, c: "#3b5bdb" },
              { l: "도달", v: "5.17M", p: 99.2, c: "#4d6ee3" },
              { l: "오픈", v: "2.00M", p: 38.4, c: "#637fff" },
              { l: "클릭", v: "364K", p: 7.0, c: "#8599ff" },
              { l: "전환", v: "91K", p: 1.7, c: "#b3bdff" },
            ].map((s, i) => (
              <div key={s.l} style={{ flex: 1 }}>
                <div style={{ height: 160, background: "#f8fafc", borderRadius: 8, display: "flex", alignItems: "flex-end", padding: 6 }}>
                  <div style={{ width: "100%", height: `${Math.max(s.p, 4)}%`, background: s.c, borderRadius: 6, display: "flex", alignItems: "flex-start", justifyContent: "center", paddingTop: 8 }}>
                    <span style={{ fontSize: 11, color: "#fff", fontWeight: 700 }}>{s.p}%</span>
                  </div>
                </div>
                <div style={{ marginTop: 8, fontSize: 11, color: "#64748b", fontWeight: 500 }}>{s.l}</div>
                <div style={{ fontSize: 14, color: "#1f2937", fontWeight: 700, fontFamily: "var(--font-display)", fontVariantNumeric: "tabular-nums" }}>{s.v}</div>
              </div>
            ))}
          </div>
        </Card>

        <Card title="실패 원인 분포" subtitle="최근 30일">
          {[
            { label: "잘못된 FCM 토큰", pct: 58, count: "706건", color: "#ef4444" },
            { label: "사용자 알림 수신 거부", pct: 22, count: "268건", color: "#f59e0b" },
            { label: "FCM 자격증명 오류", pct: 12, count: "146건", color: "#8b5cf6" },
            { label: "기타 타임아웃", pct: 8, count: "98건", color: "#64748b" },
          ].map(r => (
            <div key={r.label} style={{ marginBottom: 14 }}>
              <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, marginBottom: 5 }}>
                <span style={{ color: "#1f2937" }}>{r.label}</span>
                <span style={{ color: "#64748b", fontVariantNumeric: "tabular-nums" }}>{r.count}</span>
              </div>
              <div style={{ height: 6, borderRadius: 3, background: "#f1f5f9" }}>
                <div style={{ height: "100%", width: `${r.pct}%`, background: r.color, borderRadius: 3 }} />
              </div>
            </div>
          ))}
        </Card>
      </div>

      <Card title="캠페인별 성과" subtitle="최근 발송 · 오픈률 순">
        <div style={{ border: "1px solid #e2e8f0", borderRadius: 10, overflow: "hidden" }}>
          <div style={{ display: "grid", gridTemplateColumns: "2.4fr 1fr 1fr 1fr 1fr 1.4fr", padding: "12px 16px", background: "#f8fafc", borderBottom: "1px solid #e2e8f0", fontSize: 11, color: "#64748b", fontWeight: 600 }}>
            <div>캠페인</div><div>채널</div><div>발송</div><div>오픈률</div><div>클릭률</div><div>기여 매출</div>
          </div>
          {CRM_DATA.CAMPAIGNS.filter(c=>c.status==="success").map((c, i, a) => (
            <div key={c.id} style={{ display: "grid", gridTemplateColumns: "2.4fr 1fr 1fr 1fr 1fr 1.4fr", padding: "14px 16px", alignItems: "center", fontSize: 13, borderBottom: i < a.length - 1 ? "1px solid #f1f5f9" : "none" }}>
              <div style={{ color: "#1f2937", fontWeight: 500 }}>{c.name}</div>
              <div><ChannelBadge channel={c.channel} /></div>
              <div style={{ color: "#1f2937", fontVariantNumeric: "tabular-nums" }}>{fmtNum(c.sent)}</div>
              <div style={{ color: "#16a34a", fontVariantNumeric: "tabular-nums", fontWeight: 600 }}>{c.openRate}</div>
              <div style={{ color: "#1f2937", fontVariantNumeric: "tabular-nums" }}>{(parseFloat(c.openRate)*0.3).toFixed(1)}%</div>
              <div style={{ color: "#1f2937", fontVariantNumeric: "tabular-nums", fontWeight: 500 }}>₩{fmtNum(Math.floor(c.sent*400))}</div>
            </div>
          ))}
        </div>
      </Card>
    </div>
  );
}

export default Analytics;
