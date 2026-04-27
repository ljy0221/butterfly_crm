import React from "react";
import {
  Icon, Button, Tag, ChannelBadge, CampaignStatus, Card, StatCard,
  Input, Select, Avatar, Checkbox, Menu, SearchInput, PageHeader,
} from "../../components/common";
import { toast, openModal, confirmDialog } from "../../lib/ui-bus.js";
import { fmtNum } from "../../utils/fmt.js";
import { CRM_DATA } from "../../data/mock.js";
import MobilePreview from "./MobilePreview.jsx";

const STEPS = [
  { id: 1, title: "기본 정보", desc: "이름·유형·설명" },
  { id: 2, title: "발송 채널", desc: "FCM·이메일·SMS·웹훅" },
  { id: 3, title: "대상 세그먼트", desc: "타겟 선택·모수 확인" },
  { id: 4, title: "메시지 템플릿", desc: "내용·미리보기" },
  { id: 5, title: "발송 일정", desc: "즉시·예약·반복" },
  { id: 6, title: "확인 및 발송", desc: "최종 검토" },
];

function CampaignWizard({ onBack }) {
  const [step, setStep] = React.useState(1);
  const [data, setData] = React.useState({
    name: "여름 프리오더 사전 안내",
    type: "single",
    desc: "작년 여름 구매 이력이 있는 사용자 대상 프리오더 안내",
    channel: "FCM",
    segment: "여름 시즌 구매 예상",
    template: "장바구니 이탈 리마인더",
    schedule: "scheduled",
    date: "2026-04-25",
    time: "10:00",
  });

  const canNext = step < 6;
  const next = () => canNext && setStep(step + 1);
  const prev = () => step > 1 ? setStep(step - 1) : onBack();

  return (
    <div>
      <PageHeader
        back="캠페인" onBack={onBack}
        title="새 캠페인 만들기"
        subtitle={`${step} / 6 단계 · ${STEPS[step-1].title}`}
        actions={<>
          <Button variant="ghost" size="md">임시 저장</Button>
          <Button variant="secondary" size="md" onClick={prev}>{step === 1 ? "취소" : "이전 단계"}</Button>
          {step < 6 ? <Button variant="primaryStrong" size="md" onClick={next} iconRight={Icon.chevron(12)}>다음 단계</Button> : <Button variant="primaryStrong" size="md" icon={Icon.send(12)}>발송 시작</Button>}
        </>}
      />

      <div style={{ display: "grid", gridTemplateColumns: "240px 1fr", gap: 20 }}>
        {/* Stepper */}
        <div style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 16, alignSelf: "flex-start", position: "sticky", top: 0 }}>
          {STEPS.map((s, i) => {
            const state = step === s.id ? "active" : step > s.id ? "done" : "upcoming";
            return (
              <div key={s.id} onClick={() => step > s.id && setStep(s.id)} style={{
                display: "flex", gap: 12, padding: "10px 6px", cursor: step > s.id ? "pointer" : "default",
                borderRadius: 8, background: state === "active" ? "rgba(59,91,219,.06)" : "transparent",
                position: "relative",
              }}>
                {i < STEPS.length - 1 && <div style={{ position: "absolute", left: 18, top: 34, width: 1, height: 22, background: state === "done" ? "#3b5bdb" : "#e2e8f0" }} />}
                <div style={{
                  width: 24, height: 24, borderRadius: 999, flex: "none",
                  background: state === "done" ? "#3b5bdb" : state === "active" ? "#3b5bdb" : "#f1f5f9",
                  color: state === "upcoming" ? "#94a3b8" : "#fff",
                  display: "flex", alignItems: "center", justifyContent: "center",
                  fontSize: 11, fontWeight: 700,
                }}>
                  {state === "done" ? Icon.check(13) : s.id}
                </div>
                <div>
                  <div style={{ fontSize: 12, fontWeight: state === "active" ? 700 : 500, color: state === "upcoming" ? "#94a3b8" : "#1f2937" }}>{s.title}</div>
                  <div style={{ fontSize: 11, color: "#94a3b8", marginTop: 1 }}>{s.desc}</div>
                </div>
              </div>
            );
          })}
        </div>

        {/* Step body */}
        <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          {step === 1 && <Step1 data={data} setData={setData} />}
          {step === 2 && <Step2 data={data} setData={setData} />}
          {step === 3 && <Step3 data={data} setData={setData} />}
          {step === 4 && <Step4 data={data} setData={setData} />}
          {step === 5 && <Step5 data={data} setData={setData} />}
          {step === 6 && <Step6 data={data} />}
        </div>
      </div>
    </div>
  );
}

function Step1({ data, setData }) {
  return (
    <Card title="기본 정보" subtitle="캠페인 관리를 위한 기본 속성">
      <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
        <div>
          <div style={{ fontSize: 12, fontWeight: 500, color: "#334155", marginBottom: 6 }}>캠페인 이름 <span style={{ color: "#ef4444" }}>*</span></div>
          <input value={data.name} onChange={e => setData({...data, name: e.target.value})} style={{ width: "100%", height: 40, padding: "0 14px", fontFamily: "inherit", fontSize: 14, background: "#fff", border: "1px solid #e2e8f0", borderRadius: 8, outline: "none", boxSizing: "border-box" }} />
        </div>
        <div>
          <div style={{ fontSize: 12, fontWeight: 500, color: "#334155", marginBottom: 8 }}>캠페인 유형</div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
            {[
              { k: "single", title: "단발 캠페인", desc: "일회성 발송. 예약·즉시 발송 가능." },
              { k: "automation", title: "자동화 캠페인", desc: "이벤트 트리거 기반 반복 발송. 저니와 연동." },
            ].map(t => (
              <label key={t.k} style={{
                display: "flex", gap: 10, padding: 14, border: `1.5px solid ${data.type === t.k ? "#3b5bdb" : "#e2e8f0"}`,
                borderRadius: 8, cursor: "pointer", background: data.type === t.k ? "rgba(59,91,219,.04)" : "#fff",
              }}>
                <input type="radio" checked={data.type === t.k} onChange={() => setData({...data, type: t.k})} style={{ marginTop: 2 }} />
                <div>
                  <div style={{ fontSize: 13, fontWeight: 600, color: "#1f2937" }}>{t.title}</div>
                  <div style={{ fontSize: 11, color: "#64748b", marginTop: 2 }}>{t.desc}</div>
                </div>
              </label>
            ))}
          </div>
        </div>
        <div>
          <div style={{ fontSize: 12, fontWeight: 500, color: "#334155", marginBottom: 6 }}>설명</div>
          <textarea value={data.desc} onChange={e => setData({...data, desc: e.target.value})} rows={3} style={{ width: "100%", padding: "10px 14px", fontFamily: "inherit", fontSize: 13, background: "#fff", border: "1px solid #e2e8f0", borderRadius: 8, outline: "none", boxSizing: "border-box", resize: "vertical" }} />
        </div>
      </div>
    </Card>
  );
}

function Step2({ data, setData }) {
  const channels = [
    { k: "FCM", title: "FCM 푸시", desc: "앱 사용자 대상 푸시 알림", price: "무료", status: "P0" },
    { k: "이메일", title: "이메일", desc: "HTML 템플릿 기반 대량 메일", price: "이메일 5원/건", status: "P0" },
    { k: "SMS", title: "SMS", desc: "단문 메시지", price: "SMS 12원/건", status: "P1" },
    { k: "웹훅", title: "웹훅", desc: "외부 시스템 연동", price: "무료", status: "P1" },
    { k: "카카오 알림톡", title: "카카오 알림톡", desc: "카카오 비즈톡 기반", price: "8원/건", status: "P1" },
  ];
  return (
    <Card title="발송 채널" subtitle="한 캠페인에 여러 채널을 선택할 수 있습니다.">
      <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
        {channels.map(c => (
          <label key={c.k} style={{
            display: "flex", alignItems: "center", gap: 14, padding: 14,
            border: `1.5px solid ${data.channel === c.k ? "#3b5bdb" : "#e2e8f0"}`, borderRadius: 8,
            background: data.channel === c.k ? "rgba(59,91,219,.04)" : "#fff", cursor: "pointer",
          }}>
            <input type="radio" checked={data.channel === c.k} onChange={() => setData({...data, channel: c.k})} />
            <div style={{ width: 36, height: 36, borderRadius: 8, background: "#eff3fe", color: "#3b5bdb", display: "flex", alignItems: "center", justifyContent: "center" }}>
              <ChannelBadge channel={c.k} />
            </div>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 13, fontWeight: 600, color: "#1f2937", display: "flex", alignItems: "center", gap: 8 }}>
                {c.title}
                <Tag tone={c.status === "P0" ? "success" : "default"}>{c.status === "P0" ? "사용 가능" : "준비중"}</Tag>
              </div>
              <div style={{ fontSize: 11, color: "#64748b", marginTop: 2 }}>{c.desc}</div>
            </div>
            <div style={{ fontSize: 12, color: "#334155", fontWeight: 500 }}>{c.price}</div>
          </label>
        ))}
      </div>
    </Card>
  );
}

function Step3({ data, setData }) {
  const { SEGMENTS } = CRM_DATA;
  return (
    <Card title="대상 세그먼트" subtitle="기존 세그먼트를 선택하거나 새로 만듭니다.">
      <div style={{ display: "flex", gap: 8, marginBottom: 12 }}>
        <div style={{ flex: 1, display: "flex", alignItems: "center", gap: 8, height: 34, padding: "0 12px", border: "1px solid #e2e8f0", borderRadius: 8, background: "#fff" }}>
          <span style={{ color: "#94a3b8" }}>{Icon.search(13)}</span>
          <input placeholder="세그먼트 검색" style={{ flex: 1, border: 0, outline: 0, background: "transparent", fontFamily: "inherit", fontSize: 12 }} />
        </div>
        <Button variant="secondary" size="md" icon={Icon.plus(12)}>세그먼트 만들기</Button>
      </div>
      <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
        {SEGMENTS.slice(0, 5).map(s => (
          <label key={s.name} style={{
            display: "flex", alignItems: "center", gap: 12, padding: 12,
            border: `1.5px solid ${data.segment === s.name ? "#3b5bdb" : "#e2e8f0"}`, borderRadius: 8,
            background: data.segment === s.name ? "rgba(59,91,219,.04)" : "#fff", cursor: "pointer",
          }}>
            <input type="radio" checked={data.segment === s.name} onChange={() => setData({...data, segment: s.name})} />
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 13, fontWeight: 600, color: "#1f2937" }}>{s.name}</div>
              <div style={{ fontSize: 11, color: "#64748b", marginTop: 2 }}>{s.desc}</div>
            </div>
            <div style={{ textAlign: "right" }}>
              <div style={{ fontSize: 16, fontWeight: 700, color: "#1f2937", fontFamily: "var(--font-display)", fontVariantNumeric: "tabular-nums" }}>{fmtNum(s.reach)}</div>
              <div style={{ fontSize: 10, color: "#94a3b8" }}>예상 모수</div>
            </div>
          </label>
        ))}
      </div>
    </Card>
  );
}

function Step4({ data, setData }) {
  const { TEMPLATES } = CRM_DATA;
  const filtered = TEMPLATES.filter(t => t.channel === data.channel || data.channel === "FCM");
  return (
    <div style={{ display: "grid", gridTemplateColumns: "1fr 320px", gap: 14 }}>
      <Card title="메시지 템플릿" subtitle={`${data.channel} 채널 대응 템플릿`} action={<Button variant="text" size="sm" icon={Icon.plus(12)}>새 템플릿</Button>}>
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {filtered.map(t => (
            <label key={t.id} style={{
              display: "flex", gap: 12, padding: 12,
              border: `1.5px solid ${data.template === t.name ? "#3b5bdb" : "#e2e8f0"}`, borderRadius: 8,
              background: data.template === t.name ? "rgba(59,91,219,.04)" : "#fff", cursor: "pointer",
            }}>
              <input type="radio" checked={data.template === t.name} onChange={() => setData({...data, template: t.name})} />
              <div style={{ flex: 1 }}>
                <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                  <span style={{ fontSize: 13, fontWeight: 600, color: "#1f2937" }}>{t.name}</span>
                  <ChannelBadge channel={t.channel} />
                </div>
                <div style={{ fontSize: 11, color: "#64748b", marginTop: 4 }}>{t.desc}</div>
                <div style={{ fontSize: 10, color: "#94a3b8", marginTop: 4 }}>{t.creator} · {t.created}</div>
              </div>
            </label>
          ))}
        </div>
      </Card>
      <div>
        <MobilePreview title="[스튜디오 셀] 🍃 여름 프리오더 오픈" body="{{name}}님, 기다리시던 시즌 컬렉션이 프리오더로 오픈되었어요. 회원 전용 15% 쿠폰도 함께 드립니다." />
      </div>
    </div>
  );
}

function Step5({ data, setData }) {
  return (
    <Card title="발송 일정" subtitle="즉시·예약·반복 발송 중 선택하세요.">
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 10, marginBottom: 16 }}>
        {[
          { k: "now", title: "즉시 발송", desc: "저장과 동시에 발송을 시작합니다." },
          { k: "scheduled", title: "예약 발송", desc: "지정한 일자·시간에 1회 발송합니다." },
          { k: "recurring", title: "반복 발송", desc: "주기와 횟수를 지정하여 반복 발송합니다." },
        ].map(s => (
          <label key={s.k} style={{
            padding: 14, border: `1.5px solid ${data.schedule === s.k ? "#3b5bdb" : "#e2e8f0"}`, borderRadius: 8,
            cursor: "pointer", background: data.schedule === s.k ? "rgba(59,91,219,.04)" : "#fff",
          }}>
            <input type="radio" checked={data.schedule === s.k} onChange={() => setData({...data, schedule: s.k})} style={{ marginRight: 8 }} />
            <span style={{ fontSize: 13, fontWeight: 600, color: "#1f2937" }}>{s.title}</span>
            <div style={{ fontSize: 11, color: "#64748b", marginTop: 6 }}>{s.desc}</div>
          </label>
        ))}
      </div>
      {data.schedule === "scheduled" && (
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 12, padding: 14, background: "#f8fafc", borderRadius: 8 }}>
          <Input label="발송 일자" value={data.date} onChange={e => setData({...data, date: e.target.value})} />
          <Input label="발송 시간" value={data.time} onChange={e => setData({...data, time: e.target.value})} />
          <Select label="타임존" value="kr"><option value="kr">Asia/Seoul (KST)</option></Select>
        </div>
      )}
      {data.schedule === "recurring" && (
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 12, padding: 14, background: "#f8fafc", borderRadius: 8 }}>
          <Select label="반복 주기"><option>매일</option><option>매주</option><option>매월</option></Select>
          <Input label="시작 일시" value="2026-04-25 10:00" />
          <Input label="반복 횟수" value="12" />
        </div>
      )}
    </Card>
  );
}

function Step6({ data }) {
  const rows = [
    ["캠페인 이름", data.name],
    ["유형", data.type === "single" ? "단발 캠페인" : "자동화 캠페인"],
    ["발송 채널", data.channel],
    ["대상 세그먼트", `${data.segment} (예상 모수 8,291명)`],
    ["메시지 템플릿", data.template],
    ["발송 일정", data.schedule === "now" ? "즉시" : data.schedule === "scheduled" ? `${data.date} ${data.time} (KST)` : "반복 발송"],
  ];
  return (
    <div style={{ display: "grid", gridTemplateColumns: "1fr 320px", gap: 14 }}>
      <Card title="최종 확인" subtitle="아래 내용으로 발송됩니다.">
        <div style={{ border: "1px solid #e2e8f0", borderRadius: 8, overflow: "hidden" }}>
          {rows.map((r, i) => (
            <div key={r[0]} style={{ display: "grid", gridTemplateColumns: "160px 1fr 60px", padding: "14px 16px", alignItems: "center", fontSize: 13, borderBottom: i < rows.length - 1 ? "1px solid #f1f5f9" : "none", background: "#fff" }}>
              <div style={{ color: "#64748b", fontSize: 12, fontWeight: 500 }}>{r[0]}</div>
              <div style={{ color: "#1f2937" }}>{r[1]}</div>
              <div style={{ fontSize: 11, color: "#2563eb", fontWeight: 600, textAlign: "right", cursor: "pointer" }}>편집</div>
            </div>
          ))}
        </div>
        <div style={{ marginTop: 14, padding: 14, background: "rgba(59,91,219,.06)", borderRadius: 8, display: "flex", gap: 12, alignItems: "flex-start" }}>
          <div style={{ width: 32, height: 32, borderRadius: 8, background: "#3b5bdb", color: "#fff", display: "flex", alignItems: "center", justifyContent: "center", flex: "none" }}>{Icon.send(14)}</div>
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: 13, fontWeight: 600, color: "#1f2937" }}>예상 발송 비용</div>
            <div style={{ fontSize: 11, color: "#64748b", marginTop: 2 }}>FCM 발송 · 8,291건 × ₩0 = <b style={{ color: "#1f2937" }}>무료</b></div>
          </div>
        </div>
      </Card>
      <MobilePreview title="[스튜디오 셀] 🍃 여름 프리오더 오픈" body="{{name}}님, 기다리시던 시즌 컬렉션이 프리오더로 오픈되었어요. 회원 전용 15% 쿠폰도 함께 드립니다." />
    </div>
  );
}

export default CampaignWizard;
