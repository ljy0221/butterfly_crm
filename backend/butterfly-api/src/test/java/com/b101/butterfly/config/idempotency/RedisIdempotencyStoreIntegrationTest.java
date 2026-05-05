package com.b101.butterfly.config.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.b101.butterfly.testsupport.IntegrationTest;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * {@link RedisIdempotencyStore} Testcontainers 통합 테스트.
 *
 * <p>실제 Redis 7-alpine 컨테이너에 대해 SETNX lock + fingerprint match/mismatch + 응답 캐시 +
 * TTL 만료 시나리오를 검증한다. {@link #buildTemplate(GenericContainer)}는 Lettuce factory를 직접
 * 구성해 application.properties와 무관하게 동작한다.
 */
@IntegrationTest
@DisplayName("RedisIdempotencyStore — Testcontainers 통합")
class RedisIdempotencyStoreIntegrationTest {

    private static final int REDIS_PORT = 6379;

    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine")).withExposedPorts(REDIS_PORT);

    private static StringRedisTemplate redisTemplate;
    private static LettuceConnectionFactory connectionFactory;
    private RedisIdempotencyStore store;

    @BeforeAll
    static void startContainer() {
        REDIS.start();
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void stopContainer() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        REDIS.stop();
    }

    @BeforeEach
    void cleanRedis() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        store = new RedisIdempotencyStore(redisTemplate, new ObjectMapper());
    }

    @Test
    @DisplayName("tryReserve 1차 ACQUIRED → 2차 동일키 같은 fingerprint → FINGERPRINT_MATCH")
    void lockCollisionSameFingerprint() {
        IdempotencyStore.LockOutcome first = store.tryLockWithFingerprint(
                "key-int-1", "fp-A", Duration.ofMinutes(1));
        IdempotencyStore.LockOutcome second = store.tryLockWithFingerprint(
                "key-int-1", "fp-A", Duration.ofMinutes(1));

        assertThat(first).isEqualTo(IdempotencyStore.LockOutcome.ACQUIRED);
        assertThat(second).isEqualTo(IdempotencyStore.LockOutcome.FINGERPRINT_MATCH);
    }

    @Test
    @DisplayName("같은 키 다른 fingerprint → FINGERPRINT_MISMATCH")
    void lockCollisionDifferentFingerprint() {
        store.tryLockWithFingerprint("key-int-2", "fp-A", Duration.ofMinutes(1));

        IdempotencyStore.LockOutcome second = store.tryLockWithFingerprint(
                "key-int-2", "fp-B", Duration.ofMinutes(1));

        assertThat(second).isEqualTo(IdempotencyStore.LockOutcome.FINGERPRINT_MISMATCH);
    }

    @Test
    @DisplayName("release 호출 후 → 같은 키 재시도 ACQUIRED")
    void releaseAllowsReacquire() {
        store.tryLockWithFingerprint("key-int-3", "fp-A", Duration.ofMinutes(1));

        store.release("key-int-3");
        IdempotencyStore.LockOutcome retry = store.tryLockWithFingerprint(
                "key-int-3", "fp-A", Duration.ofMinutes(1));

        assertThat(retry).isEqualTo(IdempotencyStore.LockOutcome.ACQUIRED);
    }

    @Test
    @DisplayName("saveResponse → findResponse 동일 CachedResponse 반환")
    void saveAndFindResponse() {
        byte[] body = "{\"data\":\"ok\"}".getBytes();
        CachedResponse cached = new CachedResponse(200, "application/json", body, "fp-A");

        store.saveResponse("key-int-4", cached, Duration.ofMinutes(5));
        Optional<CachedResponse> found = store.findResponse("key-int-4");

        assertThat(found).isPresent();
        assertThat(found.get().status()).isEqualTo(200);
        assertThat(found.get().contentType()).isEqualTo("application/json");
        assertThat(found.get().body()).isEqualTo(body);
        assertThat(found.get().requestBodyHash()).isEqualTo("fp-A");
    }

    @Test
    @DisplayName("findResponse 미저장 키 → empty")
    void findResponseMissing() {
        Optional<CachedResponse> found = store.findResponse("never-saved");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("TTL 만료 후 findResponse → empty")
    void responseTtlExpiry() throws InterruptedException {
        CachedResponse cached = new CachedResponse(200, "application/json",
                "x".getBytes(), "fp-X");
        store.saveResponse("key-int-5", cached, Duration.ofSeconds(1));

        // Redis TTL 1초 + safety margin — flaky 방지를 위해 polling 대기.
        long deadline = System.currentTimeMillis() + 5_000L;
        boolean expired = false;
        while (System.currentTimeMillis() < deadline) {
            if (store.findResponse("key-int-5").isEmpty()) {
                expired = true;
                break;
            }
            Thread.sleep(200);
        }
        assertThat(expired).as("response should expire within 5s of saving with 1s TTL").isTrue();
    }
}
