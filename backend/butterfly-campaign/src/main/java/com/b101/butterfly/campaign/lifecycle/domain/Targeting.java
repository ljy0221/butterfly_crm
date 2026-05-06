package com.b101.butterfly.campaign.lifecycle.domain;

/**
 * 캠페인 발송 대상 사양 (Sealed VO).
 *
 * <ul>
 *   <li>{@link All} — 프로젝트 전체 사용자</li>
 *   <li>{@link Segment} — 단일 세그먼트의 사용자</li>
 * </ul>
 */
public sealed interface Targeting
        permits Targeting.All, Targeting.Segment {

    /** 발송 대상 segmentId — All의 경우 null. */
    Long segmentId();

    /** 전체 발송. */
    record All() implements Targeting {
        @Override
        public Long segmentId() {
            return null;
        }
    }

    /** 세그먼트 발송. */
    record Segment(Long segmentId) implements Targeting {
    }
}
