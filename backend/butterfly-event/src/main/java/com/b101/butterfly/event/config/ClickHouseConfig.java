package com.b101.butterfly.event.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
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
 * <p>운영 도입 시: PROD ClickHouse 인스턴스 URL/credentials 를 환경변수로 주입.
 */
@Configuration
@ConditionalOnProperty(name = "butterfly.clickhouse.enabled", havingValue = "true")
public class ClickHouseConfig {

    @Bean
    @ConfigurationProperties("butterfly.clickhouse")
    public ClickHouseProperties clickHouseProperties() {
        return new ClickHouseProperties();
    }

    @Bean("clickHouseDataSource")
    public DataSource clickHouseDataSource(ClickHouseProperties props) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(props.getUrl());
        config.setUsername(props.getUsername());
        config.setPassword(props.getPassword());
        config.setDriverClassName("com.clickhouse.jdbc.ClickHouseDriver");
        // ClickHouse 는 트랜잭션이 약해 autoCommit=true 가 안전. 동시 connection 도 적게 (segment build
        // 가 사실상 single connection 사용).
        config.setAutoCommit(true);
        config.setMaximumPoolSize(props.getMaxPoolSize());
        config.setPoolName("ClickHouseHikariPool");
        return new HikariDataSource(config);
    }

    @Bean("clickHouseJdbcTemplate")
    public NamedParameterJdbcTemplate clickHouseJdbcTemplate(DataSource clickHouseDataSource) {
        return new NamedParameterJdbcTemplate(clickHouseDataSource);
    }

    public static class ClickHouseProperties {
        private String url;
        private String username;
        private String password;
        private int maxPoolSize = 4;

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
    }
}
