package com.b101.butterfly.worker.campaign;

import com.b101.butterfly.notification.dto.request.DeliveryCommand;

/**
 * 캠페인 발송 크레딧 단가 정책.
 *
 * <p>MVP는 모든 채널을 성공 발송 1건 = 1 credit으로 둔다. 채널별 단가가 확정되면
 * 이 클래스만 바꾸고 기존 예약/정산 흐름은 그대로 유지한다.
 */
final class CampaignCreditPricePolicy {

    private static final long DEFAULT_UNIT_PRICE = 1L;

    private CampaignCreditPricePolicy() {
    }

    static long unitPriceOf(DeliveryCommand.Channel channel) {
        return DEFAULT_UNIT_PRICE;
    }
}
