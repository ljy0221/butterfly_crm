package com.b101.butterfly.segment.service.preview;

import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.json.ConditionTreeJsonModule;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * (projectId, conditionTree) → 결정론적 SHA-256 hex hash. 미리보기 캐시 키 산정용.
 *
 * <p>canonical 직렬화 보장:
 * <ul>
 *   <li>Map 엔트리 key 정렬 ({@code ORDER_MAP_ENTRIES_BY_KEYS}, {@code SORT_PROPERTIES_ALPHABETICALLY})
 *   <li>{@code @JsonInclude(NON_NULL)} 정책으로 null 처리 통일
 *   <li>record / sealed type은 declared 필드 순서 보장 (Jackson 기본)
 *   <li>projectId를 hash 입력에 포함 — 프로젝트 간 캐시 키 충돌 방지
 * </ul>
 */
@Component
public class ConditionTreeHasher {

    private static final ObjectMapper CANONICAL_MAPPER =
            JsonMapper.builder()
                    .addModule(new JavaTimeModule())
                    .addModule(new ConditionTreeJsonModule())
                    .defaultPropertyInclusion(
                            JsonInclude.Value.construct(
                                    JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                    .build();

    public String hash(Long projectId, GroupNode tree) {
        try {
            String treeJson = CANONICAL_MAPPER.writeValueAsString(tree);
            String input = projectId + "|" + treeJson;
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Failed to canonicalize condition tree", e);
        }
    }
}
