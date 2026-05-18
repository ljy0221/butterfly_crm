import { useState } from "react";
import { Card, Button, Tag } from "@/components/common";
import {
  useCredentialQuery,
  useSaveCredentialMutation,
  useDeleteCredentialMutation,
} from "@/hooks/queries/notification";
import { useAuthStore } from "@/stores/auth";
import { confirmDialog, toast } from "@/lib/ui-bus";

export default function CredentialsTab({ projectId }: { projectId: number }) {
  const me = useAuthStore((s) => s.member);
  const isOwner = me?.role === "OWNER";

  const credentialQ = useCredentialQuery(projectId, "PUSH");
  const saveMut = useSaveCredentialMutation(projectId);
  const deleteMut = useDeleteCredentialMutation(projectId);

  const isRegistered = !!credentialQ.data && !credentialQ.isError;
  const [jsonText, setJsonText] = useState("");
  const [jsonError, setJsonError] = useState<string | null>(null);

  const validateJson = (text: string): boolean => {
    if (!text.trim()) {
      setJsonError("서비스 계정 JSON을 입력해주세요.");
      return false;
    }
    try {
      const parsed = JSON.parse(text);
      if (parsed.type !== "service_account") {
        setJsonError("Firebase 서비스 계정 JSON이 아닙니다. type: \"service_account\" 필드를 확인해주세요.");
        return false;
      }
      setJsonError(null);
      return true;
    } catch {
      setJsonError("유효한 JSON 형식이 아닙니다.");
      return false;
    }
  };

  const handleSave = async () => {
    if (!validateJson(jsonText)) return;

    const ok = await confirmDialog({
      title: isRegistered ? "FCM 인증정보를 교체할까요?" : "FCM 인증정보를 등록할까요?",
      message: isRegistered
        ? "기존 서비스 계정 JSON이 새 JSON으로 교체됩니다."
        : "Firebase 서비스 계정 JSON을 등록합니다. 이후 PUSH 캠페인 발송이 가능해집니다.",
      confirmText: isRegistered ? "교체" : "등록",
    });
    if (!ok) return;

    try {
      await saveMut.mutateAsync({ channel: "PUSH", req: { credentialData: jsonText } });
      toast("FCM 인증정보가 저장되었습니다.", "success");
      setJsonText("");
    } catch {
      toast("저장에 실패했습니다. JSON 내용을 확인해주세요.", "error");
    }
  };

  const handleDelete = async () => {
    const ok = await confirmDialog({
      title: "FCM 인증정보를 삭제할까요?",
      message: "삭제 후 PUSH 캠페인 발송이 불가능해집니다.",
      confirmText: "삭제",
      tone: "danger",
    });
    if (!ok) return;

    try {
      await deleteMut.mutateAsync("PUSH");
      toast("FCM 인증정보가 삭제되었습니다.", "success");
      setJsonText("");
    } catch {
      toast("삭제에 실패했습니다.", "error");
    }
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <Card title="앱 푸시 (FCM)">
        <div style={{ fontSize: 13, color: "#64748b", lineHeight: 1.6, marginBottom: 20 }}>
          Firebase Cloud Messaging 서비스 계정 JSON을 등록하면 PUSH 채널 캠페인을 발송할 수 있습니다.
          <br />
          Firebase Console → 프로젝트 설정 → 서비스 계정 탭 →{" "}
          <strong style={{ color: "#1f2937" }}>새 비공개 키 생성</strong>에서 JSON을 다운로드하세요.
        </div>

        {/* 현재 상태 */}
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "120px 1fr",
            rowGap: 12,
            alignItems: "center",
            fontSize: 13,
            marginBottom: 20,
          }}
        >
          <div style={{ color: "#64748b" }}>현재 상태</div>
          <div>
            {credentialQ.isLoading ? (
              <div style={{ width: 60, height: 22, borderRadius: 6, background: "#f1f5f9" }} />
            ) : isRegistered ? (
              <Tag tone="success" dot>등록됨</Tag>
            ) : (
              <Tag tone="default" dot>미등록</Tag>
            )}
          </div>

          <div style={{ color: "#64748b" }}>채널</div>
          <div style={{ color: "#1f2937", fontWeight: 500 }}>PUSH (Firebase FCM)</div>
        </div>

        {/* JSON 입력 */}
        {isOwner && (
          <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
            <div style={{ fontSize: 13, fontWeight: 600, color: "#374151" }}>
              {isRegistered ? "서비스 계정 JSON 교체" : "서비스 계정 JSON 등록"}
            </div>
            <textarea
              value={jsonText}
              onChange={(e) => {
                setJsonText(e.target.value);
                if (jsonError) setJsonError(null);
              }}
              placeholder={`{\n  "type": "service_account",\n  "project_id": "your-project",\n  ...\n}`}
              spellCheck={false}
              style={{
                width: "100%",
                minHeight: 180,
                padding: "12px 14px",
                fontSize: 12,
                fontFamily: "var(--font-mono, ui-monospace, SFMono-Regular, Menlo, monospace)",
                lineHeight: 1.6,
                border: jsonError ? "1px solid #fca5a5" : "1px solid #e2e8f0",
                borderRadius: 8,
                background: jsonError ? "#fff7f7" : "#f8fafc",
                color: "#1f2937",
                resize: "vertical",
                outline: "none",
                boxSizing: "border-box",
              }}
            />
            {jsonError && (
              <div style={{ fontSize: 12, color: "#dc2626" }}>{jsonError}</div>
            )}

            {/* 버튼 영역 */}
            <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginTop: 4 }}>
              <Button
                variant="primaryStrong"
                size="md"
                onClick={handleSave}
                disabled={saveMut.isPending || !jsonText.trim()}
              >
                {saveMut.isPending ? "저장 중..." : isRegistered ? "교체" : "등록"}
              </Button>

              {isRegistered && (
                <>
                  <Button
                    variant="danger"
                    size="md"
                    onClick={handleDelete}
                    disabled={deleteMut.isPending}
                  >
                    {deleteMut.isPending ? "삭제 중..." : "삭제"}
                  </Button>
                </>
              )}
            </div>
          </div>
        )}

        {!isOwner && (
          <div
            style={{
              fontSize: 12,
              color: "#94a3b8",
              padding: "10px 12px",
              background: "#f8fafc",
              border: "1px solid #e2e8f0",
              borderRadius: 8,
            }}
          >
            채널 인증정보 관리는 OWNER만 수행할 수 있습니다.
          </div>
        )}
      </Card>

      {/* 안내 */}
      <Card title="서비스 계정 JSON 발급 방법">
        <ol
          style={{
            fontSize: 13,
            color: "#475569",
            lineHeight: 2,
            paddingLeft: 20,
            margin: 0,
          }}
        >
          <li>
            <a
              href="https://console.firebase.google.com"
              target="_blank"
              rel="noreferrer"
              style={{ color: "#3b5bdb" }}
            >
              Firebase Console
            </a>{" "}
            접속 → 해당 프로젝트 선택
          </li>
          <li>좌측 상단 ⚙️ → <strong>프로젝트 설정</strong></li>
          <li><strong>서비스 계정</strong> 탭 클릭</li>
          <li><strong>새 비공개 키 생성</strong> 버튼 클릭 → JSON 다운로드</li>
          <li>다운로드한 JSON 파일의 내용을 위 입력창에 붙여넣기</li>
        </ol>

        <div
          style={{
            marginTop: 16,
            padding: "10px 14px",
            background: "#fffbeb",
            border: "1px solid #fde68a",
            borderRadius: 8,
            fontSize: 12,
            color: "#92400e",
          }}
        >
          서비스 계정 JSON에는 민감한 비밀키가 포함됩니다. 파일을 공유하거나 코드 저장소에 커밋하지 마세요.
        </div>
      </Card>
    </div>
  );
}
