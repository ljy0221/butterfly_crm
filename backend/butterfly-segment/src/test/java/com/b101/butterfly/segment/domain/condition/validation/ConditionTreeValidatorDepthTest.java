package com.b101.butterfly.segment.domain.condition.validation;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.b101.butterfly.segment.domain.condition.ConditionNode;
import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.LogicOp;
import com.b101.butterfly.segment.domain.condition.Operator;
import com.b101.butterfly.segment.domain.condition.TagNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConditionTreeValidatorDepthTest {

    private final ConditionTreeValidator validator =
            new ConditionTreeValidator(
                    new StructureValidator(),
                    new TypeFieldValidator(),
                    new OperatorCompatibilityValidator(),
                    new PeriodValidator(),
                    new DepthGuard());

    private Logger depthLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        depthLogger = (Logger) LoggerFactory.getLogger(DepthGuard.class);
        appender = new ListAppender<>();
        appender.start();
        depthLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        depthLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    @DisplayName("depth 1~3은 경고 로그가 발생하지 않는다")
    void depthBelowThresholdNoWarn() {
        validator.validate(buildNestedTree(3));

        assertThat(warnCount()).isZero();
    }

    @Test
    @DisplayName("depth 4 이상은 경고 로그가 발생한다 (BusinessException은 없음)")
    void depthAtThresholdWarns() {
        validator.validate(buildNestedTree(4));

        assertThat(warnCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("depth 5는 경고 로그가 누적된다 (4와 5 두 번)")
    void depthAboveThresholdWarnsMultipleTimes() {
        validator.validate(buildNestedTree(5));

        assertThat(warnCount()).isGreaterThanOrEqualTo(2);
    }

    private long warnCount() {
        return appender.list.stream().filter(e -> e.getLevel() == Level.WARN).count();
    }

    /**
     * 깊이 N짜리 트리를 만든다. 루트(GroupNode) = depth 1, 안쪽 GroupNode가 N단까지 중첩.
     * 가장 안쪽 그룹은 단일 leaf TagNode를 갖는다.
     */
    private static GroupNode buildNestedTree(int targetDepth) {
        ConditionNode current =
                new TagNode("region", Operator.EQ, JsonNodeFactory.instance.textNode("서울"));
        for (int i = 0; i < targetDepth - 1; i++) {
            current = new GroupNode(LogicOp.AND, List.of(current));
        }
        if (current instanceof GroupNode root) {
            return root;
        }
        return new GroupNode(LogicOp.AND, List.of(current));
    }
}
