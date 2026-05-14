package com.b101.butterfly.campaign.lifecycle.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b101.butterfly.campaign.config.CampaignJpaAutoConfig;
import com.b101.butterfly.campaign.lifecycle.domain.CampaignExecution;
import com.b101.butterfly.campaign.lifecycle.domain.CampaignExecutionRepository;
import com.b101.butterfly.campaign.lifecycle.domain.enums.CampaignExecutionStatus;
import com.b101.butterfly.testsupport.containers.AbstractMySqlContainerTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import jakarta.persistence.OptimisticLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * CampaignExecution {@code @Version} 낙관적 락 통합 테스트 — 실제 MySQL 8 Testcontainer 대상.
 *
 * <p>Flyway 없이 {@code ddl-auto=create}로 campaign_executions 테이블을 직접 생성한다.
 * V6 migration이 추가한 {@code version} 컬럼은 Hibernate가 {@code @Version Long version}
 * 매핑을 통해 스키마에 포함시킨다.
 *
 * <p>트랜잭션 분리 — {@code @Transactional}을 테스트 메서드에 걸면 하나의 영속성 컨텍스트를
 * 공유하므로 낙관적 락 충돌을 시뮬레이션할 수 없다. 각 시나리오는 {@link TransactionTemplate}을
 * 사용해 별도 트랜잭션을 명시적으로 시작·커밋한다.
 */
@SpringBootTest(
        classes = {
                DataSourceAutoConfiguration.class,
                JdbcTemplateAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class,
                TransactionAutoConfiguration.class,
                CampaignJpaAutoConfig.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ImportAutoConfiguration
@DisplayName("CampaignExecution @Version OptimisticLock 통합 테스트 (MySQL Testcontainer)")
class CampaignExecutionOptimisticLockIntegrationTest extends AbstractMySqlContainerTest {

    @DynamicPropertySource
    static void overrideJpa(DynamicPropertyRegistry registry) {
        // Flyway 비활성화 — Hibernate가 엔티티에서 스키마를 직접 생성한다.
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "false");
    }

    @Autowired
    private CampaignExecutionRepository repository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // -------------------------------------------------------------------------
    // 헬퍼 — 별도 트랜잭션에서 SEND 상태 execution 1건 INSERT 후 ID 반환
    // -------------------------------------------------------------------------

    private Long insertSendingExecution() {
        return transactionTemplate.execute(status -> {
            CampaignExecution e = CampaignExecution.ready(100L, 1, 50);
            e.markAsSent();
            repository.save(e);
            return e.getCampaignExecutionId();
        });
    }

    // =========================================================================
    // 시나리오 1 — 동시 markAsSuccess: 두 번째 트랜잭션 OptimisticLockException
    // =========================================================================

    @Nested
    @DisplayName("시나리오 1 — 동시 markAsSuccess")
    class ConcurrentMarkAsSuccess {

        @Test
        @DisplayName("T1 커밋 후 T2가 stale version으로 markAsSuccess 시도 → OptimisticLockingFailureException")
        void second_markAsSuccess_fails_with_optimistic_lock() {
            Long id = insertSendingExecution();

            // 두 트랜잭션이 동일 행을 독립적으로 load (각자 영속성 컨텍스트)
            CampaignExecution snapshot1 = transactionTemplate.execute(
                    s -> repository.findById(id).orElseThrow());
            CampaignExecution snapshot2 = transactionTemplate.execute(
                    s -> repository.findById(id).orElseThrow());

            assertThat(snapshot1.getVersion()).isEqualTo(snapshot2.getVersion())
                    .as("두 snapshot 모두 동일한 version 값으로 로드되어야 한다");

            // T1: markAsSuccess → commit (version: 0 → 1)
            transactionTemplate.execute(status -> {
                CampaignExecution attached = em.merge(snapshot1);
                attached.markAsSuccess();
                return null;
            });

            // T2: 동일 stale snapshot으로 markAsSuccess 재시도
            // TransactionTemplate을 직접 사용할 때는 JPA예외가 Spring 래퍼로 변환되지 않을 수 있으므로
            // jakarta.persistence.OptimisticLockException 또는 Spring의 OptimisticLockingFailureException 허용
            assertThatThrownBy(() ->
                    transactionTemplate.execute(status -> {
                        CampaignExecution attached = em.merge(snapshot2);
                        attached.markAsSuccess();
                        return null;
                    })
            ).satisfiesAnyOf(
                    e -> assertThat(e).isInstanceOf(OptimisticLockingFailureException.class),
                    e -> assertThat(e).isInstanceOf(OptimisticLockException.class),
                    e -> assertThat(e).hasCauseInstanceOf(OptimisticLockException.class)
            ).as("stale version으로 update 시 낙관적 락 예외가 발생해야 한다");

            // DB 최종 상태 — T1이 이긴 상태여야 함
            CampaignExecution final_ = transactionTemplate.execute(
                    s -> repository.findById(id).orElseThrow());
            assertThat(final_.getCampaignExecutionStatus()).isEqualTo(CampaignExecutionStatus.SUCCESS);
            assertThat(final_.getVersion()).isEqualTo(1L);
        }
    }

    // =========================================================================
    // 시나리오 2 — markAsSuccess vs markAsFailed 동시 경쟁
    // =========================================================================

    @Nested
    @DisplayName("시나리오 2 — markAsSuccess vs markAsFailed 동시 경쟁")
    class ConcurrentSuccessVsFailed {

        @Test
        @DisplayName("T1(SUCCESS) 커밋 후 T2(FAIL) stale update → OptimisticLockingFailureException")
        void success_beats_fail_second_throws_optimistic_lock() {
            Long id = insertSendingExecution();

            CampaignExecution snapshotForSuccess = transactionTemplate.execute(
                    s -> repository.findById(id).orElseThrow());
            CampaignExecution snapshotForFail = transactionTemplate.execute(
                    s -> repository.findById(id).orElseThrow());

            // T1: SUCCESS로 커밋
            transactionTemplate.execute(status -> {
                CampaignExecution attached = em.merge(snapshotForSuccess);
                attached.markAsSuccess();
                return null;
            });

            // T2: FAIL로 시도 — stale version → 낙관적 락 예외
            assertThatThrownBy(() ->
                    transactionTemplate.execute(status -> {
                        CampaignExecution attached = em.merge(snapshotForFail);
                        attached.markAsFailed();
                        return null;
                    })
            ).satisfiesAnyOf(
                    e -> assertThat(e).isInstanceOf(OptimisticLockingFailureException.class),
                    e -> assertThat(e).isInstanceOf(OptimisticLockException.class),
                    e -> assertThat(e).hasCauseInstanceOf(OptimisticLockException.class)
            );

            // 최종 상태는 먼저 커밋한 T1(SUCCESS) 기준으로 고정
            CampaignExecution final_ = transactionTemplate.execute(
                    s -> repository.findById(id).orElseThrow());
            assertThat(final_.getCampaignExecutionStatus()).isEqualTo(CampaignExecutionStatus.SUCCESS);
            assertThat(final_.getVersion()).isEqualTo(1L);
        }
    }

    // =========================================================================
    // 시나리오 3 — version 단조 증가 검증 (native query로 직접 확인)
    // =========================================================================

    @Nested
    @DisplayName("시나리오 3 — version 단조 증가")
    class VersionIncrement {

        @Test
        @DisplayName("INSERT 시 version=0, update 1회 → version=1, update 2회 → version=2")
        void version_increments_monotonically_across_transactions() {
            // INSERT — version=0
            Long id = transactionTemplate.execute(status -> {
                CampaignExecution e = CampaignExecution.ready(200L, 1, 10);
                repository.save(e);
                return e.getCampaignExecutionId();
            });

            Long versionAfterInsert = queryVersion(id);
            assertThat(versionAfterInsert).isEqualTo(0L);

            // update 1회: markAsSent → version=1
            transactionTemplate.execute(status -> {
                CampaignExecution e = repository.findById(id).orElseThrow();
                e.markAsSent();
                return null;
            });
            assertThat(queryVersion(id)).isEqualTo(1L);

            // update 2회: markAsSuccess → version=2
            transactionTemplate.execute(status -> {
                CampaignExecution e = repository.findById(id).orElseThrow();
                e.markAsSuccess();
                return null;
            });
            assertThat(queryVersion(id)).isEqualTo(2L);
        }

        private Long queryVersion(Long id) {
            return jdbcTemplate.queryForObject(
                    "SELECT version FROM campaign_executions WHERE campaign_execution_id = ?",
                    Long.class, id);
        }
    }

    // =========================================================================
    // 부가 sanity — ready() 팩토리 → save → load → version=0
    // =========================================================================

    @Nested
    @DisplayName("부가 sanity — 신규 save 시 version=0")
    class InitialVersionSanity {

        @Test
        @DisplayName("CampaignExecution.ready() save 후 reload 시 version은 0")
        void newly_saved_execution_has_version_zero() {
            Long id = transactionTemplate.execute(status -> {
                CampaignExecution e = CampaignExecution.ready(300L, 1, 0);
                repository.save(e);
                return e.getCampaignExecutionId();
            });

            CampaignExecution loaded = transactionTemplate.execute(
                    s -> repository.findById(id).orElseThrow());

            assertThat(loaded.getVersion()).isEqualTo(0L);
            assertThat(loaded.getCampaignExecutionStatus()).isEqualTo(CampaignExecutionStatus.READY);
        }

        @Test
        @DisplayName("markAsSent save 후 reload 시 version은 1로 증가")
        void after_markAsSent_version_is_one() {
            Long id = transactionTemplate.execute(status -> {
                CampaignExecution e = CampaignExecution.ready(400L, 2, 5);
                repository.save(e);
                return e.getCampaignExecutionId();
            });

            transactionTemplate.execute(status -> {
                CampaignExecution e = repository.findById(id).orElseThrow();
                e.markAsSent();
                return null;
            });

            CampaignExecution loaded = transactionTemplate.execute(
                    s -> repository.findById(id).orElseThrow());
            assertThat(loaded.getVersion()).isEqualTo(1L);
            assertThat(loaded.getCampaignExecutionStatus()).isEqualTo(CampaignExecutionStatus.SEND);
        }
    }
}
