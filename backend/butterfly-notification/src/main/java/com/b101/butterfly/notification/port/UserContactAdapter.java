package com.b101.butterfly.notification.port;

import com.b101.butterfly.notification.dto.request.DeliveryCommand;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@link UserContactPort} 실제 구현체 — DB에서 채널별 연락처를 조회한다.
 *
 * <p>{@link StubUserContactAdapter}는 {@code @ConditionalOnMissingBean}으로 등록되므로
 * 본 빈이 존재하면 자동 비활성화된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserContactAdapter implements UserContactPort {

    private static final String PLACEHOLDER_EXTERNAL_ID = "__anonymous__";

    private static final String PUSH_SQL =
            "SELECT u.user_id, d.device_id, d.push_token, u.email, u.phone,"
                    + " u.birth_date, u.signed_up_at "
                    + "FROM users u "
                    + "JOIN devices d ON d.user_id = u.user_id AND d.project_id = u.project_id "
                    + "WHERE u.project_id = :projectId "
                    + "  AND u.user_id IN (:userIds) "
                    + "  AND u.external_id <> :placeholder "
                    + "  AND u.push_opt_in = true "
                    + "  AND d.token_valid = true";

    private static final String EMAIL_SQL =
            "SELECT user_id, NULL AS device_id, NULL AS push_token, email, phone,"
                    + " birth_date, signed_up_at "
                    + "FROM users "
                    + "WHERE project_id = :projectId "
                    + "  AND user_id IN (:userIds) "
                    + "  AND external_id <> :placeholder "
                    + "  AND email IS NOT NULL";

    private static final String SMS_SQL =
            "SELECT user_id, NULL AS device_id, NULL AS push_token, email, phone,"
                    + " birth_date, signed_up_at "
                    + "FROM users "
                    + "WHERE project_id = :projectId "
                    + "  AND user_id IN (:userIds) "
                    + "  AND external_id <> :placeholder "
                    + "  AND phone IS NOT NULL";

    private static final String DEFAULT_SQL =
            "SELECT user_id, NULL AS device_id, NULL AS push_token, email, phone,"
                    + " birth_date, signed_up_at "
                    + "FROM users "
                    + "WHERE project_id = :projectId "
                    + "  AND user_id IN (:userIds) "
                    + "  AND external_id <> :placeholder";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public List<UserContactInfo> getContactsByUserIds(Long projectId, List<Long> userIds,
                                                      DeliveryCommand.Channel channel) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }

        String sql = switch (channel) {
            case PUSH -> PUSH_SQL;
            case EMAIL -> EMAIL_SQL;
            case SMS -> SMS_SQL;
            case WEBHOOK, KAKAO -> DEFAULT_SQL;
        };

        Map<String, Object> params = new HashMap<>();
        params.put("projectId", projectId);
        params.put("userIds", userIds);
        params.put("placeholder", PLACEHOLDER_EXTERNAL_ID);

        List<UserContactInfo> result = jdbcTemplate.query(sql, params, (rs, rowNum) -> new UserContactInfo(
                rs.getLong("user_id"),
                rs.getObject("device_id") != null ? rs.getLong("device_id") : null,
                rs.getString("push_token"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getString("birth_date"),
                rs.getString("signed_up_at")
        ));
        log.debug("UserContactAdapter 조회 결과: projectId={}, channel={}, 요청={}, 응답={}건, nullEmail={}건, nullPhone={}건, nullBirthDate={}건, nullSignedUpAt={}건",
                projectId, channel, userIds.size(), result.size(),
                result.stream().filter(c -> c.email() == null).count(),
                result.stream().filter(c -> c.phoneNumber() == null).count(),
                result.stream().filter(c -> c.birthDate() == null).count(),
                result.stream().filter(c -> c.signedUpAt() == null).count());
        return result;
    }
}
