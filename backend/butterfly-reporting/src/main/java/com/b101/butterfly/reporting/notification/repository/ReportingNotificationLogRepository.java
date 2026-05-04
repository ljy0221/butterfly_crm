package com.b101.butterfly.reporting.notification.repository;

import com.b101.butterfly.reporting.notification.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 발송 로그 저장소. 멱등성 키로 기존 row 존재 여부를 빠르게 확인할 수 있도록
 * {@link #findByMessageKey(String)}을 노출한다 — 컨슈머는 이 키로 dedup을 수행한다.
 */
public interface ReportingNotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    Optional<NotificationLog> findByMessageKey(String messageKey);
}
