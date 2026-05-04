package com.b101.butterfly.segment.port;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * EventAggReader 호출 시 segment가 함께 넘기는 평가 컨텍스트.
 *
 * <p>asOfDate는 Period.Relative 평가 기준일. build job이 자정을 걸쳐 도는 동안 LocalDate.now()를
 * 그대로 쓰면 결과 비결정성이 생기므로 호출 시점에 명시적으로 박는다.
 */
public record EventQueryContext(Long projectId, LocalDate asOfDate, ZoneId timeZone) {
}
