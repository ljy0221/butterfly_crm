import React from "react";
import {
  Icon, Button, Tag, ChannelBadge, CampaignStatus, Card, StatCard,
  Input, Select, Avatar, Checkbox, Menu, SearchInput, PageHeader,
} from "../../components/common";
import { toast, openModal, confirmDialog } from "../../lib/ui-bus.js";
import { fmtNum } from "../../utils/fmt.js";
import { CRM_DATA } from "../../data/mock.js";
import MobilePreview from "../campaigns/MobilePreview.jsx";

function Templates() {
  const { TEMPLATES } = CRM_DATA;
  const [sel, setSel] = React.useState(TEMPLATES[0]);
  const [title, setTitle] = React.useState("{{name}}님, 장바구니에 담아두신 상품이 있어요");
  const [body, setBody] = React.useState("안녕하세요, {{name}}님\n\n장바구니에 담아두신 [{{product_name}}] 외 {{item_count}}개 상품이 기다리고 있어요.\n오늘만 15% 추가 할인 쿠폰도 함께 드립니다.\n\n지금 확인하기 →");

  return (
    <div>
      <PageHeader
        title="메시지 템플릿"
        subtitle="채널별 메시지 양식을 관리합니다. 변수 치환으로 개인화된 메시지를 보낼 수 있습니다."
        actions={<>
          <Button variant="secondary" size="md">미리보기</Button>
          <Button variant="primaryStrong" size="md" icon={Icon.plus(12)}>새 템플릿</Button>
        </>}
      />

      <div style={{ display: "grid", gridTemplateColumns: "280px 1fr 340px", gap: 14 }}>
        {/* List */}
        <Card padding={0}>
          <div style={{ padding: "14px 16px", borderBottom: "1px solid #f1f5f9" }}>
            <div style={{ display: "flex", alignItems: "center", gap: 8, height: 30, padding: "0 10px", background: "#f5f7fb", borderRadius: 6 }}>
              <span style={{ color: "#94a3b8" }}>{Icon.search(12)}</span>
              <input placeholder="템플릿 검색" style={{ flex: 1, border: 0, outline: 0, background: "transparent", fontFamily: "inherit", fontSize: 12 }} />
            </div>
          </div>
          <div style={{ maxHeight: 580, overflowY: "auto" }}>
            {TEMPLATES.map((t, i) => (
              <div key={t.id} onClick={() => setSel(t)} style={{
                padding: "14px 16px", borderBottom: i < TEMPLATES.length - 1 ? "1px solid #f1f5f9" : "none",
                cursor: "pointer",
                background: sel.id === t.id ? "rgba(59,91,219,.06)" : "#fff",
                borderLeft: sel.id === t.id ? "3px solid #3b5bdb" : "3px solid transparent",
              }}>
                <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 4 }}>
                  <span style={{ fontSize: 13, fontWeight: 600, color: "#1f2937" }}>{t.name}</span>
                </div>
                <div style={{ fontSize: 11, color: "#64748b", marginBottom: 6 }}>{t.desc}</div>
                <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                  <ChannelBadge channel={t.channel} />
                  <span style={{ fontSize: 10, color: "#94a3b8", marginLeft: "auto" }}>{t.created}</span>
                </div>
              </div>
            ))}
          </div>
        </Card>

        {/* Editor */}
        <Card>
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 14 }}>
            <div>
              <div style={{ fontSize: 15, fontWeight: 700, color: "#1f2937", fontFamily: "var(--font-display)" }}>{sel.name}</div>
              <div style={{ fontSize: 11, color: "#94a3b8", marginTop: 2 }}>{sel.creator} · 마지막 수정 2시간 전</div>
            </div>
            <div style={{ display: "flex", gap: 6 }}>
              <Button variant="ghost" size="sm" icon={Icon.copy(12)}>복제</Button>
              <Button variant="danger" size="sm" icon={Icon.trash(12)}>삭제</Button>
            </div>
          </div>

          <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
            <Input label="템플릿 이름" defaultValue={sel.name} />
            <div>
              <div style={{ fontSize: 12, fontWeight: 500, color: "#334155", marginBottom: 6 }}>메시지 제목</div>
              <input value={title} onChange={e => setTitle(e.target.value)} style={{ width: "100%", height: 38, padding: "0 14px", fontFamily: "inherit", fontSize: 13, background: "#fff", border: "1px solid #e2e8f0", borderRadius: 8, outline: "none", boxSizing: "border-box" }} />
            </div>
            <div>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 6 }}>
                <div style={{ fontSize: 12, fontWeight: 500, color: "#334155" }}>메시지 본문</div>
                <div style={{ display: "flex", gap: 4 }}>
                  {["{{name}}", "{{product_name}}", "{{item_count}}", "{{coupon_code}}"].map(v => (
                    <button key={v} onClick={() => setBody(body + " " + v)} style={{ fontSize: 10, fontFamily: "var(--font-mono)", padding: "3px 8px", border: "1px solid #e2e8f0", borderRadius: 4, background: "#fff", color: "#3b5bdb", cursor: "pointer", fontWeight: 600 }}>+ {v}</button>
                  ))}
                </div>
              </div>
              <textarea value={body} onChange={e => setBody(e.target.value)} rows={8} style={{ width: "100%", padding: "12px 14px", fontFamily: "inherit", fontSize: 13, lineHeight: 1.6, background: "#fff", border: "1px solid #e2e8f0", borderRadius: 8, outline: "none", boxSizing: "border-box", resize: "vertical" }} />
              <div style={{ marginTop: 6, fontSize: 11, color: "#94a3b8", display: "flex", justifyContent: "space-between" }}>
                <span>{body.length} / 1000자</span>
                <span>개인화 변수 3개 사용됨</span>
              </div>
            </div>
            <div>
              <div style={{ fontSize: 12, fontWeight: 500, color: "#334155", marginBottom: 6 }}>이미지 (선택)</div>
              <div style={{ height: 96, border: "1.5px dashed #cbd5e1", borderRadius: 8, display: "flex", alignItems: "center", justifyContent: "center", background: "#f8fafc", fontSize: 12, color: "#64748b", flexDirection: "column", gap: 6 }}>
                <div style={{ color: "#94a3b8" }}>{Icon.plus(18)}</div>
                <div>이미지 추가 · 최대 512×512 · 자동 압축됩니다</div>
              </div>
            </div>
          </div>
        </Card>

        {/* Preview */}
        <div style={{ position: "sticky", top: 0 }}>
          <MobilePreview title={title.replace("{{name}}", "이서연")} body={body.replace("{{name}}", "이서연").replace("{{product_name}}", "린넨 블레이저").replace("{{item_count}}", "2")} />
        </div>
      </div>
    </div>
  );
}

export default Templates;
