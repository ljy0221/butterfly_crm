package com.b101.butterfly.reporting.notification.service;

import com.b101.butterfly.reporting.notification.entity.NotificationLog;

/**
 * Ingest 처리 결과 — 신규 INSERT인지 멱등 hit인지 컨트롤러가 status code를 분기하기 위한 정보.
 */
public record NotificationLogIngestResult(NotificationLog log, boolean deduplicated) {
}
