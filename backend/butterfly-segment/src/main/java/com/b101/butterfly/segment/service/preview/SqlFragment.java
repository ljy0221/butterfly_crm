package com.b101.butterfly.segment.service.preview;

import java.util.Map;

/**
 * ConditionTree → SQL 변환 결과. SQL 문자열과 named parameter 맵.
 */
public record SqlFragment(String sql, Map<String, Object> params) {
}
