package com.b101.butterfly.segment.domain.condition;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * USER 조건 속성의 단일 원천.
 *
 * <p>프론트 메타데이터 응답과 SQL 컬럼 화이트리스트가 같은 목록을 사용한다.
 */
public final class UserConditionAttributes {

    private static final List<UserConditionAttribute> ATTRIBUTES =
            List.of(
                    new UserConditionAttribute(
                            "email",
                            "email",
                            "이메일",
                            "string",
                            List.of(Operator.EQ, Operator.NEQ, Operator.IN, Operator.CONTAINS)),
                    new UserConditionAttribute(
                            "phone",
                            "phone",
                            "전화번호",
                            "string",
                            List.of(Operator.EQ, Operator.NEQ, Operator.IN, Operator.CONTAINS)),
                    new UserConditionAttribute(
                            "birthDate",
                            "birth_date",
                            "생년월일",
                            "date",
                            List.of(Operator.EQ, Operator.NEQ, Operator.IN)),
                    new UserConditionAttribute(
                            "pushOptIn",
                            "push_opt_in",
                            "푸시 수신 동의",
                            "boolean",
                            List.of(Operator.EQ, Operator.NEQ)),
                    new UserConditionAttribute(
                            "signedUpAt",
                            "signed_up_at",
                            "가입일",
                            "date",
                            List.of(Operator.EQ, Operator.NEQ, Operator.IN)));

    private UserConditionAttributes() {}

    public static List<UserConditionAttribute> list() {
        return ATTRIBUTES;
    }

    public static Map<String, String> columnByKey() {
        return ATTRIBUTES.stream()
                .collect(Collectors.toUnmodifiableMap(
                        UserConditionAttribute::key,
                        UserConditionAttribute::column));
    }
}
