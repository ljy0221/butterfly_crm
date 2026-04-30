package com.b101.butterfly.campaign.messagetemplate.port;

import java.util.List;
import java.util.Map;

public interface MemberInformationPort {

    Map<Long, String> getMemberNames(List<Long> memberIds);
}
