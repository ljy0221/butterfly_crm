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
 * <p><b>패키지 매처 컨벤션</b>: 모든 모듈 식별은 {@code com.b101.butterfly.<module>..}
 * 모듈 prefix 형식으로 표기한다. {@code ..<module>..} 형식은 패키지 어디에든 모듈명이 들어가면
 * 매칭되어 false positive를 만든다 (예: {@code auth.adapter.in.campaign}이 campaign 코드로
 * 잘못 인식됨). port adapter처럼 다른 모듈을 위해 자기 모듈 안에서 어댑터를 두는 패턴은
 * 정상이므로 매처가 그것을 잡으면 안 된다.
 *
 * <p><b>모듈 명명 컨벤션</b>: P3 모듈 개명 후 도메인 모듈 7종은 다음과 같다 —
 * common, auth, tenant, event, segment, campaign, notification, reporting.
 * customer/delivery/analytics는 더 이상 사용하지 않는다.
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
                    noClasses().that().resideInAPackage("com.b101.butterfly.campaign..")
                            .should().dependOnClassesThat()
                            .resideInAnyPackage(
                                    "com.b101.butterfly.segment.repository..",
                                    "com.b101.butterfly.segment.entity..",
                                    "com.b101.butterfly.tenant.repository..",
                                    "com.b101.butterfly.tenant.entity..",
                                    "com.b101.butterfly.auth.repository..",
                                    "com.b101.butterfly.auth.entity.."
                            )
                            .as("캠페인 모듈은 다른 도메인의 repository·entity를 직접 import할 수 없다")
            );

    /** reporting은 campaign 내부 구현(domain·repository)에 직접 의존하지 못한다. */
    @ArchTest
    static final ArchRule reportingShouldNotDependOnCampaignInternals =
            FreezingArchRule.freeze(
                    noClasses().that().resideInAPackage("com.b101.butterfly.reporting..")
                            .should().dependOnClassesThat()
                            .resideInAnyPackage(
                                    "com.b101.butterfly.campaign.campaign.domain..",
                                    "com.b101.butterfly.campaign.campaign.repository..",
                                    "com.b101.butterfly.campaign.messagetemplate.domain..",
                                    "com.b101.butterfly.campaign.messagetemplate.repository.."
                            )
                            .as("리포팅 모듈은 캠페인 내부 도메인/리포지토리를 직접 import할 수 없다")
            );

    /** notification 모듈도 다른 도메인의 entity/repository에 직접 의존하지 못한다. */
    @ArchTest
    static final ArchRule notificationShouldNotDependOnInternalsOfOtherDomains =
            FreezingArchRule.freeze(
                    noClasses().that().resideInAPackage("com.b101.butterfly.notification..")
                            .should().dependOnClassesThat()
                            .resideInAnyPackage(
                                    "com.b101.butterfly.segment.repository..",
                                    "com.b101.butterfly.segment.entity..",
                                    "com.b101.butterfly.campaign.campaign.repository..",
                                    "com.b101.butterfly.campaign.messagetemplate.repository..",
                                    "com.b101.butterfly.tenant.repository..",
                                    "com.b101.butterfly.tenant.entity.."
                            )
                            .as("notification 모듈은 다른 도메인의 repository·entity를 직접 import할 수 없다")
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
