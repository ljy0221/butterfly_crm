package com.b101.butterfly.event.adapter.in.segment;

import com.b101.butterfly.segment.port.UserDirectoryReader;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class UserDirectoryReaderAdapter implements UserDirectoryReader {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public List<Long> queryUserIds(String sql, Map<String, Object> params) {
        return jdbcTemplate.queryForList(sql, params, Long.class);
    }
}
