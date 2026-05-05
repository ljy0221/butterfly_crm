import { useState } from "react";
import { Button, Tag } from "@/components/common";
import {
  useApproveMutation,
  useRejectMutation,
} from "@/hooks/queries/application";
import { confirmDialog, toast } from "@/lib/ui-bus";
import { ApiError } from "@/api/types";
import type { ApplicationResponse } from "@/api/application";

interface Props {
  application: ApplicationResponse;
  onClose: () => void;
}

const STATUS_TONE: Record<string, string> = {
  PENDING: "warning",
  APPROVED: "success",
  REJECTED: "error",
};

const STATUS_LABEL: Record<string, string> = {
  PENDING: "대기",
  APPROVED: "승인",
  REJECTED: "반려",
};

export default function ApplicationDetailModal({ application, onClose }: Props) {
  const [reason, setReason] = useState("");
  const [reasonError, setReasonError] = useState<string | null>(null);

  const approve = useApproveMutation();
  const reject = useRejectMutation();

  const isPending = application.status === "PENDING";

  const onApprove = async () => {
    const ok = await confirmDialog({
      title: "승인하시겠습니까?",
      message: "Owner 초대 이메일이 발송됩니다.",
      confirmText: "승인",
    });
    if (!ok) return;
    try {
      await approve.mutateAsync(application.applicationId);
      toast("승인되었습니다", "success");
      onClose();
    } catch (err) {
      const msg =
        err instanceof ApiError ? err.message : "승인 처리 중 오류가 발생했습니다";
      toast(msg, "error");
    }
  };

  const onReject = async () => {
    if (!reason.trim()) {
      setReasonError("반려 사유를 입력해주세요");
      return;
    }
    setReasonError(null);
    try {
      await reject.mutateAsync({
        applicationId: application.applicationId,
        req: { rejectReason: reason },
      });
      toast("반려되었습니다", "success");
      onClose();
    } catch (err) {
      const msg =
        err instanceof ApiError ? err.message : "반려 처리 중 오류가 발생했습니다";
      toast(msg, "error");
    }
  };

  return (
    <div
      style={{
        background: "#fff",
        borderRadius: "var(--radius-lg)",
        padding: 24,
        width: 480,
        maxWidth: "90vw",
        maxHeight: "85vh",
        overflowY: "auto",
        boxShadow: "var(--shadow-3)",
      }}
    >
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          marginBottom: 16,
        }}
      >
        <h2 style={{ margin: 0, fontSize: 18 }}>
          신청서 #{application.applicationId}
        </h2>
        <Tag tone={STATUS_TONE[application.status]} dot style={{}}>
          {STATUS_LABEL[application.status]}
        </Tag>
      </div>

      <Section title="신청자 정보">
        <Row label="이름" value={application.applicantName} />
        <Row label="이메일" value={application.email} />
        <Row label="전화번호" value={application.phone} />
        <Row label="생년월일" value={application.birthDate} />
      </Section>

      <Section title="사업자 정보">
        <Row label="사업자명" value={application.businessName} />
        <Row label="사업자번호" value={application.businessNo} mono />
      </Section>

      {application.status === "REJECTED" && application.rejectReason && (
        <Section title="반려 사유">
          <div
            style={{
              padding: 10,
              background: "var(--state-error-bg)",
              borderRadius: "var(--radius-sm)",
              fontSize: 13,
              color: "var(--color-error)",
            }}
          >
            {application.rejectReason}
          </div>
        </Section>
      )}

      {isPending && (
        <>
          <div style={{ marginTop: 16 }}>
            <label
              style={{
                fontSize: 12,
                color: "var(--fg-secondary)",
                marginBottom: 4,
                display: "block",
              }}
            >
              반려 사유 (반려 시 필수)
            </label>
            <textarea
              value={reason}
              onChange={(e) => {
                setReason(e.target.value);
                if (reasonError) setReasonError(null);
              }}
              rows={3}
              maxLength={500}
              style={{
                width: "100%",
                fontFamily: "inherit",
                fontSize: 13,
                padding: 10,
                border: reasonError
                  ? "1px solid var(--color-error)"
                  : "1px solid var(--border-default)",
                borderRadius: "var(--radius-sm)",
                resize: "vertical",
              }}
            />
            <div
              style={{
                fontSize: 11,
                color: "var(--fg-faint)",
                textAlign: "right",
                marginTop: 2,
              }}
            >
              {reason.length}/500
            </div>
            {reasonError && (
              <div
                style={{
                  fontSize: 12,
                  color: "var(--color-error)",
                  marginTop: 4,
                }}
              >
                {reasonError}
              </div>
            )}
          </div>

          <div
            style={{
              display: "flex",
              gap: 8,
              justifyContent: "flex-end",
              marginTop: 20,
            }}
          >
            <Button
              variant="secondary"
              size="md"
              onClick={onReject}
              disabled={reject.isPending}
              style={{}}
            >
              {reject.isPending ? "처리 중..." : "반려"}
            </Button>
            <Button
              variant="primaryStrong"
              size="md"
              onClick={onApprove}
              disabled={approve.isPending}
              style={{}}
            >
              {approve.isPending ? "처리 중..." : "승인"}
            </Button>
          </div>
        </>
      )}

      {!isPending && (
        <div
          style={{
            display: "flex",
            justifyContent: "flex-end",
            marginTop: 20,
          }}
        >
          <Button variant="ghost" size="md" onClick={onClose} style={{}}>
            닫기
          </Button>
        </div>
      )}
    </div>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: 14 }}>
      <div
        style={{
          fontSize: 12,
          fontWeight: 600,
          color: "var(--fg-strong)",
          marginBottom: 8,
          paddingBottom: 4,
          borderBottom: "1px solid var(--border-default)",
        }}
      >
        {title}
      </div>
      {children}
    </div>
  );
}

function Row({
  label,
  value,
  mono,
}: {
  label: string;
  value: string | number | null;
  mono?: boolean;
}) {
  return (
    <div style={{ display: "flex", padding: "4px 0", fontSize: 13 }}>
      <div style={{ width: 80, color: "var(--fg-secondary)" }}>{label}</div>
      <div
        style={{
          flex: 1,
          fontFamily: mono ? "var(--font-mono)" : undefined,
        }}
      >
        {value ?? "-"}
      </div>
    </div>
  );
}
