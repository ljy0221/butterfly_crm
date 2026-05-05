package com.b101.butterfly.campaign.lifecycle.domain.enums;

import java.util.Set;
import lombok.Getter;

/**
 * 캠페인 라이프사이클 상태.
 *
 * <p>각 상태는 자신이 전이 가능한 다음 상태들을 직접 알고 있다.
 * 컨트롤러/서비스에서 if-else 분기 없이 도메인이 자기 자신의 전이 규칙을 검증한다.
 *
 * <p>전이 매트릭스:
 * <pre>
 *   DRAFT  → ACTIVE
 *   ACTIVE → PAUSED, ENDED
 *   PAUSED → ACTIVE, ENDED
 *   ENDED  → (terminal)
 * </pre>
 */
@Getter
public enum CampaignStatus {

    DRAFT {
        @Override
        public Set<CampaignStatus> nextAllowed() {
            return Set.of(ACTIVE);
        }
    },
    ACTIVE {
        @Override
        public Set<CampaignStatus> nextAllowed() {
            return Set.of(PAUSED, ENDED);
        }
    },
    PAUSED {
        @Override
        public Set<CampaignStatus> nextAllowed() {
            return Set.of(ACTIVE, ENDED);
        }
    },
    ENDED {
        @Override
        public Set<CampaignStatus> nextAllowed() {
            return Set.of();
        }
    };

    public abstract Set<CampaignStatus> nextAllowed();

    public boolean canTransitionTo(CampaignStatus target) {
        return target != null && nextAllowed().contains(target);
    }
}
