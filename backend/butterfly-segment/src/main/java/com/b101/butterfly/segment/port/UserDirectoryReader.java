package com.b101.butterfly.segment.port;

import java.util.List;
import java.util.Map;

/**
 * 미리보기 SQL을 실행해 매칭된 user_id를 조회한다. event 모듈이 진짜 구현을 등록한다.
 *
 * <p>SQL은 {@code ConditionTreeToSqlConverter}가 생성하고, 본 reader는 placeholder 바인딩 후
 * PreparedStatement로 안전하게 실행한다.
 */
public interface UserDirectoryReader {

    List<Long> queryUserIds(String sql, Map<String, Object> params);
}
