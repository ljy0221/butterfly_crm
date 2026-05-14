package com.b101.butterfly.event.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

/**
 * ClickHouse DataSource + NamedParameterJdbcTemplate 빈 등록 (S14P31B101-275).
 *
 * <p>{@code butterfly.clickhouse.enabled=true} 일 때만 활성. butterfly-api / worker 의 기본 MySQL
 * datasource 와 별도로 클러스터 분리된 ClickHouse 인스턴스에 접근.
 *
 * <p>{@code butterfly-api} 는 application.yml, {@code butterfly-worker} 는 자체
 * application.properties 에 동일한 키를 mirror 하고 있어야 한다 (worker 가 api 의 yml 을 읽지 않음).
 */
@Configuration
@ConditionalOnProperty(name = "butterfly.clickhouse.enabled", havingValue = "true")
public class ClickHouseConfig {

    @Bean("clickHouseDataSource")
    public DataSource clickHouseDataSource(
            @Value("${butterfly.clickhouse.url}") String url,
            @Value("${butterfly.clickhouse.username}") String username,
            @Value("${butterfly.clickhouse.password}") String password,
            @Value("${butterfly.clickhouse.max-pool-size:4}") int maxPoolSize) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.clickhouse.jdbc.ClickHouseDriver");
        // ClickHouse 는 트랜잭션이 약해 autoCommit=true 가 안전. 동시 connection 도 적게 (segment build
        // 가 사실상 single connection 사용).
        config.setAutoCommit(true);
        config.setMaximumPoolSize(maxPoolSize);
        config.setPoolName("ClickHouseHikariPool");
        return new HikariDataSource(config);
    }

    @Bean("clickHouseJdbcTemplate")
    public NamedParameterJdbcTemplate clickHouseJdbcTemplate(DataSource clickHouseDataSource) {
        return new NamedParameterJdbcTemplate(clickHouseDataSource);
    }
}
