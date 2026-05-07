package com.b101.butterfly.reporting.report.service;

import com.b101.butterfly.reporting.aggregation.entity.ProjectStatsDaily;
import com.b101.butterfly.reporting.aggregation.repository.FailureStatsDailyRepository;
import com.b101.butterfly.reporting.aggregation.repository.ProjectStatsDailyRepository;
import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.report.dto.DashboardOverviewResponse;
import com.b101.butterfly.reporting.report.dto.FailureCountRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("DashboardOverviewService — 운영자 대시보드 종합")
class DashboardOverviewServiceTest {

    private ProjectStatsDailyRepository projectRepo;
    private FailureStatsDailyRepository failureRepo;
    private DashboardOverviewService service;

    @BeforeEach
    void setUp() {
        projectRepo = mock(ProjectStatsDailyRepository.class);
        failureRepo = mock(FailureStatsDailyRepository.class);
        service = new DashboardOverviewService(projectRepo, failureRepo);
    }

    /**
     * ProjectStatsDaily는 setter가 없고 native UPSERT로만 만들어진다 — 테스트에서만 reflection으로 채움.
     */
    private static ProjectStatsDaily projectStats(
            long projectId, LocalDate date,
            int activeUsers, int totalSent, int totalDelivered, int totalFailed,
            int totalOpen, int totalClick, int totalConvert,
            int activeCampaignCount,
            BigDecimal pushOpen, BigDecimal click, BigDecimal convert
    ) {
        ProjectStatsDaily s;
        try {
            // protected no-arg ctor — reflection으로 우회 (테스트 전용)
            var ctor = ProjectStatsDaily.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            s = ctor.newInstance();
            setField(s, "projectId", projectId);
            setField(s, "bucketDate", date);
            setField(s, "activeUserCount", activeUsers);
            setField(s, "newUserCount", 0);
            setField(s, "churnUserCount", 0);
            setField(s, "totalSent", totalSent);
            setField(s, "totalDelivered", totalDelivered);
            setField(s, "totalFailed", totalFailed);
            setField(s, "totalOpen", totalOpen);
            setField(s, "totalClick", totalClick);
            setField(s, "totalConvert", totalConvert);
            setField(s, "activeCampaignCount", activeCampaignCount);
            setField(s, "pushOpenRate", pushOpen);
            setField(s, "overallClickRate", click);
            setField(s, "overallConvertRate", convert);
            setField(s, "updatedAt", LocalDateTime.of(2026, 5, 7, 12, 0));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return s;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = ProjectStatsDaily.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    @DisplayName("정상 — summary + failure top N + 비율 계산")
    void normalOverview() {
        ProjectStatsDaily stats = projectStats(1L, LocalDate.of(2026, 5, 7),
                500, 1000, 800, 200, 400, 100, 25, 12,
                new BigDecimal("50.00"), new BigDecimal("10.00"), new BigDecimal("2.50"));
        given(projectRepo.findByProjectIdAndBucketDate(1L, LocalDate.of(2026, 5, 7)))
                .willReturn(Optional.of(stats));

        // 총 200건 실패: PUSH/TOKEN_INVALID 150 + EMAIL/INVALID_ADDRESS 50
        given(failureRepo.findTopByProjectAndDate(any(), any(), any(Pageable.class)))
                .willReturn(List.of(
                        new FailureCountRow(Channel.PUSH, "TOKEN_INVALID", 150L, 5, 100),
                        new FailureCountRow(Channel.EMAIL, "INVALID_ADDRESS", 50L, 2, 30)
                ));

        DashboardOverviewResponse res = service.getOverview(1L, LocalDate.of(2026, 5, 7), 5);

        assertThat(res.projectId()).isEqualTo(1L);
        assertThat(res.bucketDate()).isEqualTo(LocalDate.of(2026, 5, 7));

        // summary
        assertThat(res.summary().totalSent()).isEqualTo(1000);
        assertThat(res.summary().pushOpenRate()).isEqualByComparingTo("50.00");
        assertThat(res.summary().activeCampaignCount()).isEqualTo(12);

        // failure top — total=200 기준 비율
        assertThat(res.failuresTop()).hasSize(2);
        assertThat(res.failuresTop().get(0).errorCode()).isEqualTo("TOKEN_INVALID");
        assertThat(res.failuresTop().get(0).failedCount()).isEqualTo(150);
        assertThat(res.failuresTop().get(0).ratePercent()).isEqualByComparingTo("75.00"); // 150/200
        assertThat(res.failuresTop().get(1).errorCode()).isEqualTo("INVALID_ADDRESS");
        assertThat(res.failuresTop().get(1).ratePercent()).isEqualByComparingTo("25.00"); // 50/200

        assertThat(res.updatedAt()).isEqualTo(LocalDateTime.of(2026, 5, 7, 12, 0));
    }

    @Test
    @DisplayName("project_stats_daily 데이터 없음 — Summary.zero + 빈 failures")
    void noDataReturnsZero() {
        given(projectRepo.findByProjectIdAndBucketDate(any(), any())).willReturn(Optional.empty());
        given(failureRepo.findTopByProjectAndDate(any(), any(), any(Pageable.class)))
                .willReturn(List.of());

        DashboardOverviewResponse res = service.getOverview(1L, LocalDate.of(2026, 5, 7), 5);

        assertThat(res.summary().totalSent()).isZero();
        assertThat(res.summary().activeCampaignCount()).isZero();
        assertThat(res.summary().pushOpenRate()).isNull();
        assertThat(res.failuresTop()).isEmpty();
        assertThat(res.updatedAt()).isNull();
    }

    @Test
    @DisplayName("실패 0건 — 비율 분모 0이지만 빈 list라 ratePercent 계산도 안 됨")
    void zeroFailuresMeansEmptyList() {
        ProjectStatsDaily stats = projectStats(1L, LocalDate.of(2026, 5, 7),
                100, 50, 50, 0, 25, 5, 1, 3, null, null, null);
        given(projectRepo.findByProjectIdAndBucketDate(any(), any())).willReturn(Optional.of(stats));
        given(failureRepo.findTopByProjectAndDate(any(), any(), any(Pageable.class)))
                .willReturn(List.of());

        DashboardOverviewResponse res = service.getOverview(1L, LocalDate.of(2026, 5, 7), 5);

        assertThat(res.failuresTop()).isEmpty();
        assertThat(res.summary().totalFailed()).isZero();
    }

    @Test
    @DisplayName("실패 1건만 — ratePercent = 100%")
    void singleFailureGetsFullRate() {
        given(projectRepo.findByProjectIdAndBucketDate(any(), any())).willReturn(Optional.empty());
        given(failureRepo.findTopByProjectAndDate(any(), any(), any(Pageable.class)))
                .willReturn(List.of(
                        new FailureCountRow(Channel.SMS, "RATE_LIMITED", 30L, 1, 25)
                ));

        DashboardOverviewResponse res = service.getOverview(1L, LocalDate.of(2026, 5, 7), 5);

        assertThat(res.failuresTop()).hasSize(1);
        assertThat(res.failuresTop().get(0).ratePercent()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("ProjectStats의 비율은 그대로 노출 (서비스가 재계산 안 함)")
    void preservesPersistedRates() {
        ProjectStatsDaily stats = projectStats(1L, LocalDate.of(2026, 5, 7),
                100, 1000, 800, 0, 600, 100, 50, 5,
                new BigDecimal("75.00"),  // 600/800 = 75% (delivered 기준)
                new BigDecimal("16.67"),  // 100/600 (open 기준)
                new BigDecimal("5.00"));  // 50/1000
        given(projectRepo.findByProjectIdAndBucketDate(any(), any())).willReturn(Optional.of(stats));
        given(failureRepo.findTopByProjectAndDate(any(), any(), any(Pageable.class)))
                .willReturn(List.of());

        DashboardOverviewResponse res = service.getOverview(1L, LocalDate.of(2026, 5, 7), 5);

        // Repository에 적재된 값 그대로 — 서비스 레이어에서 변형하지 않음
        assertThat(res.summary().pushOpenRate()).isEqualByComparingTo("75.00");
        assertThat(res.summary().overallClickRate()).isEqualByComparingTo("16.67");
        assertThat(res.summary().overallConvertRate()).isEqualByComparingTo("5.00");
    }
}
