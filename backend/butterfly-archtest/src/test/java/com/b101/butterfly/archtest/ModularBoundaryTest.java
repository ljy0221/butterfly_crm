package com.b101.butterfly.archtest;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * 모듈러 모놀리스 경계 검증.
 *
 * <p>기존 위반은 {@link FreezingArchRule#freeze}로 baseline에 등록되어 빌드를 깨지 않고,
 * 신규 위반만 차단한다. baseline은 {@code src/test/resources/archunit_store/}에 저장된다.
 *
 * <p>패키지명 주의: P3(모듈 개명)이 머지되면 {@code customer→event},
 * {@code delivery→notification}, {@code analytics→reporting}으로 갱신할 것.
 */
@AnalyzeClasses(
        packages = "com.b101.butterfly",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ModularBoundaryTest {

    /** campaign이 다른 도메인의 repository/entity에 직접 의존하지 못한다. */
    @ArchTest
    static final ArchRule campaignShouldNotDependOnInternalsOfOtherDomains =
            FreezingArchRule.freeze(
                    noClasses().that().resideInAPackage("..campaign..")
                            .should().dependOnClassesThat()
                            .resideInAnyPackage(
                                    "..segment.repository..",
                                    "..segment.entity..",
                                    "..tenant.repository..",
                                    "..tenant.entity..",
                                    "..auth.repository..",
                                    "..auth.entity.."
                            )
                            .as("캠페인 모듈은 다른 도메인의 repository·entity를 직접 import할 수 없다")
            );

    /** analytics(→reporting)는 campaign 내부 구현(domain·repository)에 직접 의존하지 못한다. */
    @ArchTest
    static final ArchRule reportingShouldNotDependOnCampaignInternals =
            FreezingArchRule.freeze(
                    noClasses().that().resideInAPackage("..analytics..")
                            .should().dependOnClassesThat()
                            .resideInAnyPackage(
                                    "..campaign..campaign.domain..",
                                    "..campaign..campaign.repository..",
                                    "..campaign..messagetemplate.domain..",
                                    "..campaign..messagetemplate.repository.."
                            )
                            .as("리포팅 모듈은 캠페인 내부 도메인/리포지토리를 직접 import할 수 없다")
            );

    /**
     * delivery(→notification)도 다른 도메인의 entity/repository에 직접 의존하지 못한다.
     *
     * <p>현재 delivery 모듈은 빈 골격이라 매칭 클래스가 0개이므로 {@code allowEmptyShould(true)}.
     * 코드가 채워지면 자동으로 검사 활성화.
     */
    @ArchTest
    static final ArchRule deliveryShouldNotDependOnInternalsOfOtherDomains =
            FreezingArchRule.freeze(
                    noClasses().that().resideInAPackage("..delivery..")
                            .should().dependOnClassesThat()
                            .resideInAnyPackage(
                                    "..segment.repository..",
                                    "..segment.entity..",
                                    "..campaign..campaign.repository..",
                                    "..campaign..messagetemplate.repository..",
                                    "..tenant.repository..",
                                    "..tenant.entity.."
                            )
                            .as("발송 모듈은 다른 도메인의 repository·entity를 직접 import할 수 없다")
                            .allowEmptyShould(true)
            );

    /** 도메인 슬라이스 사이에 순환 의존이 없어야 한다. */
    @ArchTest
    static final ArchRule slicesShouldBeFreeOfCycles =
            FreezingArchRule.freeze(
                    slices().matching("com.b101.butterfly.(*)..")
                            .should().beFreeOfCycles()
                            .as("도메인 슬라이스 간 순환 의존 금지")
            );
}
