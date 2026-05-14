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
 * ClickHouse {@link NamedParameterJdbcTemplate} 빈 등록 (S14P31B101-275).
 *
 * <p>{@code butterfly.clickhouse.enabled=true} 일 때만 활성. butterfly-api / worker 의 기본 MySQL
 * datasource 와 별도로 클러스터 분리된 ClickHouse 인스턴스에 접근.
 *
 * <p><b>중요</b>: ClickHouse 의 {@link DataSource} 를 type-level 빈으로 등록하지 않는다. Spring Boot 의
 * {@code DataSourceAutoConfiguration} 가 {@code @ConditionalOnMissingBean(DataSource.class)} 이라,
 * 우리가 DataSource 빈을 등록하는 순간 MySQL DataSource autoconfig 가 skip 되어 JPA 가 무너지기
 * 때문. 대신 {@link ClickHouseAccess} wrapper 로 감싸 type-pollution 을 피한다. ClickHouse 의
 * JdbcTemplate 도 wrapper 안에 보관 — 별도 NamedParameterJdbcTemplate 빈으로 등록하면 기존 MySQL
 * template 사용처에서 ambiguous 가 발생.
 *
 * <p>{@code butterfly-api} 는 application.yml, {@code butterfly-worker} 는 자체
 * application.properties 에 동일한 키를 mirror 한다 (worker 가 api 의 yml 을 안 읽음).
 */
@Configuration
@ConditionalOnProperty(name = "butterfly.clickhouse.enabled", havingValue = "true")
public class ClickHouseConfig {

    @Bean
    public ClickHouseAccess clickHouseAccess(
            @Value("${butterfly.clickhouse.url}") String url,
            @Value("${butterfly.clickhouse.username}") String username,
            @Value("${butterfly.clickhouse.password}") String password,
            @Value("${butterfly.clickhouse.max-pool-size:4}") int maxPoolSize) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.clickhouse.jdbc.ClickHouseDriver");
        config.setAutoCommit(true);
        config.setMaximumPoolSize(maxPoolSize);
        config.setPoolName("ClickHouseHikariPool");
        DataSource dataSource = new HikariDataSource(config);
        return new ClickHouseAccess(dataSource, new NamedParameterJdbcTemplate(dataSource));
    }

    /**
     * ClickHouse DataSource + NamedParameterJdbcTemplate 묶음.
     *
     * <p>Spring DI 의 type-based autowire 가 {@link DataSource} 나 {@link NamedParameterJdbcTemplate}
     * 으로 우리 ClickHouse 측을 안 잡도록 wrapper. 사용처는 본 record 를 inject 받아 내부 필드 사용.
     */
    public record ClickHouseAccess(DataSource dataSource, NamedParameterJdbcTemplate jdbcTemplate) {}
}
