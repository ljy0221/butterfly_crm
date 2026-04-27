import React from "react";
import { Icon } from "./Icon.jsx";

const ICONS = {
  FCM: () => Icon.mobile(12),
  "앱 푸시": () => Icon.mobile(12),
  이메일: () => Icon.mail(12),
  SMS: () => Icon.sms(12),
  LMS: () => Icon.sms(12),
  웹훅: () => Icon.webhook(12),
  "카카오 알림톡": () => Icon.sms(12),
};

export default function ChannelBadge({ channel }) {
  const render = ICONS[channel] || (() => Icon.send(12));
  return (
    <span
      style={{
        display: "inline-flex", alignItems: "center", gap: 5,
        height: 22, padding: "0 8px", borderRadius: 6,
        fontSize: 11, fontWeight: 600,
        background: "#f8fafc", color: "#334155",
        border: "1px solid #e2e8f0",
      }}
    >
      <span style={{ color: "#64748b" }}>{render()}</span>
      {channel}
    </span>
  );
}
