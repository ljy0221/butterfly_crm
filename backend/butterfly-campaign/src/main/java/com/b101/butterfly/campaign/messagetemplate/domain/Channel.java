package com.b101.butterfly.campaign.messagetemplate.domain;

import lombok.Getter;

@Getter
public enum Channel {
    PUSH,
    EMAIL,
    SMS,
    WEBHOOK,
    KAKAO,
}
