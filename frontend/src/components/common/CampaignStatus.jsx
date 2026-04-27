import React from "react";
import Tag from "./Tag.jsx";

const STATUS_MAP = {
  draft:   { tone: "default", txt: "초안" },
  ready:   { tone: "info",    txt: "발송 대기" },
  send:    { tone: "warning", txt: "발송 중" },
  success: { tone: "success", txt: "발송 완료" },
  fail:    { tone: "error",   txt: "발송 실패" },
  running: { tone: "success", txt: "운영 중" },
};

export default function CampaignStatus({ status }) {
  const m = STATUS_MAP[status] || { tone: "default", txt: status };
  return <Tag tone={m.tone} dot>{m.txt}</Tag>;
}
