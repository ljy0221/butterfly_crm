package com.b101.butterfly.testsupport;

import org.junit.jupiter.api.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 통합 테스트 클래스/베이스에 부여하는 메타 어노테이션.
 * JUnit Platform `@Tag("integration")`을 적용해 단위/통합 테스트 분리에 사용한다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("integration")
public @interface IntegrationTest {
}
