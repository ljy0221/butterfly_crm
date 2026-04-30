package com.b101.butterfly.segment.service.preview;

/**
 * conditionTree에 EVENT 조건이 포함됐지만 event 모듈 미합류로 평가 불가능한 경우.
 * PreviewService가 캐치해 SEGMENT_501_EVENT_NOT_READY로 매핑한다.
 */
public class EventConditionNotReadyException extends RuntimeException {

    public EventConditionNotReadyException() {
        super("EVENT condition cannot be evaluated until event module is integrated");
    }
}
