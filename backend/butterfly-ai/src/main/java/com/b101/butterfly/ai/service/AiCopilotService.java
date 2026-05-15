package com.b101.butterfly.ai.service;

import com.b101.butterfly.ai.dto.CampaignDraftRequest;
import com.b101.butterfly.ai.dto.CampaignDraftResponse;
import com.b101.butterfly.ai.dto.SegmentDraftRequest;
import com.b101.butterfly.ai.dto.SegmentDraftResponse;
import com.b101.butterfly.segment.domain.condition.ConditionNode;
import com.b101.butterfly.segment.domain.condition.EventNode;
import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.LogicOp;
import com.b101.butterfly.segment.domain.condition.Operator;
import com.b101.butterfly.segment.domain.condition.Period;
import com.b101.butterfly.segment.domain.condition.TagNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.template.NoOpTemplateRenderer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AiCopilotService {

    private static final NoOpTemplateRenderer NO_OP_TEMPLATE_RENDERER =
            new NoOpTemplateRenderer();
    private static final Pattern DAYS_PATTERN = Pattern.compile("(\\d{1,3})\\s*일");

    private final ObjectMapper objectMapper;
    @Nullable private final ChatClient chatClient;

    @Autowired
    public AiCopilotService(
            ObjectMapper objectMapper, ObjectProvider<ChatClient.Builder> chatClientBuilderProvider) {
        this(objectMapper, createChatClient(chatClientBuilderProvider));
    }

    AiCopilotService(ObjectMapper objectMapper, @Nullable ChatClient chatClient) {
        this.objectMapper = objectMapper;
        this.chatClient = chatClient;
    }

    public CampaignDraftResponse generateCampaignDraft(
            Long projectId, CampaignDraftRequest request) {
        AiGenerationResult<CampaignDraftResponse> result =
                generateWithAi(
                        campaignSystemPrompt(), userPayload(projectId, request), CampaignDraftResponse.class);
        return result.response().orElseGet(() -> fallbackCampaignDraft(request, result.reason()));
    }

    public SegmentDraftResponse generateSegmentDraft(Long projectId, SegmentDraftRequest request) {
        AiGenerationResult<SegmentDraftResponse> result =
                generateWithAi(
                        segmentSystemPrompt(), userPayload(projectId, request), SegmentDraftResponse.class);
        return result.response().orElseGet(() -> fallbackSegmentDraft(request, result.reason()));
    }

    private static ChatClient createChatClient(
            ObjectProvider<ChatClient.Builder> chatClientBuilderProvider) {
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        if (builder == null) {
            return null;
        }
        return builder.defaultTemplateRenderer(NO_OP_TEMPLATE_RENDERER).build();
    }

    private <T> AiGenerationResult<T> generateWithAi(
            String systemPrompt, String userPrompt, Class<T> type) {
        if (chatClient == null) {
            return AiGenerationResult.empty("Spring AI ChatClient 빈이 없어 로컬 fallback 초안을 반환했습니다.");
        }

        try {
            String content =
                    chatClient
                            .prompt()
                            .system(systemPrompt)
                            .user(userPrompt)
                            .templateRenderer(NO_OP_TEMPLATE_RENDERER)
                            .call()
                            .content();
            if (content == null || content.isBlank()) {
                return AiGenerationResult.empty("AI 모델 응답이 비어 있어 로컬 fallback 초안을 반환했습니다.");
            }
            return AiGenerationResult.ok(objectMapper.readValue(extractJson(content), type));
        } catch (JsonProcessingException ex) {
            log.warn("AI 응답 JSON 파싱에 실패했습니다. fallback 초안을 사용합니다. message={}", ex.getMessage());
            return AiGenerationResult.empty(
                    "AI 응답 JSON 파싱에 실패해 로컬 fallback 초안을 반환했습니다: " + ex.getOriginalMessage());
        } catch (RuntimeException ex) {
            log.warn("AI 모델 호출에 실패했습니다. fallback 초안을 사용합니다. message={}", ex.getMessage());
            return AiGenerationResult.empty(
                    "AI 모델 호출에 실패해 로컬 fallback 초안을 반환했습니다: "
                            + ex.getClass().getSimpleName()
                            + " - "
                            + sanitize(ex.getMessage()));
        }
    }

    private String userPayload(Long projectId, Object request) {
        try {
            return objectMapper.writeValueAsString(new AiRequestEnvelope(projectId, request));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("AI 요청 JSON 생성에 실패했습니다.", ex);
        }
    }

    private String extractJson(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end < start) {
            return content;
        }
        return content.substring(start, end + 1);
    }

    private CampaignDraftResponse fallbackCampaignDraft(CampaignDraftRequest request, String reason) {
        String goal = request.prompt();
        CampaignDraftRequest.CampaignDraftContext context = request.context();
        String channel = defaultText(context.channel(), "KAKAO");
        String campaignType = defaultText(context.campaignType(), "ONE_TIME");
        String segment = defaultText(context.segmentName(), "선택 세그먼트");
        String greetingVar = pickGreetingVariable(request.availableVariables());
        String body =
                greetingVar == null
                        ? goal + "\n지금 바로 확인해 보시고 필요한 혜택을 놓치지 마세요."
                        : "{{" + greetingVar + "}}님, " + goal
                                + "\n지금 바로 확인해 보시고 필요한 혜택을 놓치지 마세요.";

        return new CampaignDraftResponse(
                new CampaignDraftResponse.CampaignDraft(
                        limitText(segment + " 대상 " + shortGoal(goal) + " 캠페인", 50),
                        limitText(goal + " 목표로 " + segment + " 고객에게 발송할 캠페인 초안입니다.", 200),
                        campaignType,
                        channel),
                new CampaignDraftResponse.TemplateDraft(
                        limitText(shortGoal(goal) + " " + channel + " 템플릿", 255),
                        channel + " 채널에 맞춘 메시지 템플릿 초안입니다.",
                        channel,
                        limitText(segment + " 고객님을 위한 안내", 255),
                        body,
                        null,
                        greetingVar == null ? List.of() : List.of(greetingVar)),
                List.of(reason));
    }

    /**
     * fallback 본문에 쓸 호칭 변수를 고른다. availableVariables 가 비어 있으면 null
     * 을 반환해 변수 없는 안전한 문장을 만들도록 한다 (이전엔 user_tags 에 'name' 시드가
     * 없으면 "님, ..." 같은 어색한 잔재가 남았다). 'name' 이 있으면 우선 사용, 없으면
     * 첫 항목을 선택.
     */
    private String pickGreetingVariable(List<String> availableVariables) {
        if (availableVariables == null || availableVariables.isEmpty()) {
            return null;
        }
        return availableVariables.stream()
                .filter(v -> v != null && !v.isBlank())
                .filter(v -> v.equalsIgnoreCase("name"))
                .findFirst()
                .orElseGet(
                        () ->
                                availableVariables.stream()
                                        .filter(v -> v != null && !v.isBlank())
                                        .findFirst()
                                        .orElse(null));
    }

    private SegmentDraftResponse fallbackSegmentDraft(SegmentDraftRequest request, String reason) {
        int days = detectDays(request.prompt()).orElse(30);
        ConditionNode condition =
                pickEventHint(request)
                        .<ConditionNode>map(
                                event ->
                                        new EventNode(
                                                event.categoryId(),
                                                List.of(),
                                                new Period.Relative(days),
                                                1))
                        .orElseGet(() -> fallbackTagCondition(request));
        GroupNode conditionTree = new GroupNode(LogicOp.AND, List.of(condition));

        return new SegmentDraftResponse(
                limitText(shortGoal(request.prompt()) + " 세그먼트", 30),
                limitText(request.prompt() + " 조건을 바탕으로 생성한 세그먼트 초안입니다.", 100),
                conditionTree,
                List.of(
                        "명시된 기간이 없으면 최근 " + days + "일 기준으로 해석했습니다.",
                        reason));
    }

    /**
     * 이벤트 힌트가 매칭 안 됐을 때의 fallback tag 조건. 프로젝트의 실제 user_tags
     * 키 (request.availableTagKeys) 가 있으면 그 첫 항목을 사용해 매칭 0건 사고를
     * 방지한다. 키 후보가 없으면 임의 키로 노드를 만들지 말고 마지막 수단으로
     * "interest" 를 사용 (기존 동작 유지).
     */
    private ConditionNode fallbackTagCondition(SegmentDraftRequest request) {
        String key =
                request.availableTagKeys().stream()
                        .filter(k -> k != null && !k.isBlank())
                        .findFirst()
                        .orElse("interest");
        return new TagNode(
                key,
                Operator.CONTAINS,
                objectMapper.getNodeFactory().textNode(shortGoal(request.prompt())));
    }

    private Optional<SegmentDraftRequest.EventCategoryHint> pickEventHint(SegmentDraftRequest request) {
        String prompt = normalize(request.prompt());
        return request.availableEvents().stream()
                .filter(event -> event.categoryId() != null && event.categoryId() > 0)
                .filter(event -> eventMatches(prompt, event))
                .findFirst()
                .or(
                        () ->
                                request.availableEvents().stream()
                                        .filter(event -> event.categoryId() != null && event.categoryId() > 0)
                                        .findFirst());
    }

    private boolean eventMatches(String prompt, SegmentDraftRequest.EventCategoryHint event) {
        String haystack =
                normalize(
                        String.join(
                                " ",
                                defaultText(event.categoryName(), ""),
                                defaultText(event.displayName(), ""),
                                defaultText(event.description(), "")));
        if (haystack.isBlank()) {
            return false;
        }
        if ((prompt.contains("구매") || prompt.contains("결제") || prompt.contains("purchase"))
                && (haystack.contains("구매")
                        || haystack.contains("결제")
                        || haystack.contains("purchase")
                        || haystack.contains("order"))) {
            return true;
        }
        if ((prompt.contains("장바구니") || prompt.contains("cart"))
                && (haystack.contains("장바구니") || haystack.contains("cart"))) {
            return true;
        }
        return haystack.contains(prompt) || prompt.contains(haystack);
    }

    private Optional<Integer> detectDays(String prompt) {
        Matcher matcher = DAYS_PATTERN.matcher(prompt);
        if (!matcher.find()) {
            return Optional.empty();
        }
        int days = Integer.parseInt(matcher.group(1));
        return Optional.of(Math.max(1, days));
    }

    private String shortGoal(String text) {
        return limitText(defaultText(text, "신규 캠페인").replaceAll("\\s+", " "), 30);
    }

    private String limitText(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String normalize(String value) {
        return defaultText(value, "").toLowerCase();
    }

    private String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "상세 메시지 없음";
        }
        return message.replaceAll("sk-[A-Za-z0-9_-]+", "sk-***");
    }

    private String campaignSystemPrompt() {
        return """
                당신은 B2B CRM 캠페인 운영 Copilot입니다.
                입력 JSON을 참고해 캠페인과 메시지 템플릿 초안을 한국어로 작성하세요.
                과장 광고, 민감정보 추론, 법적 확정 표현은 피하세요.
                반드시 JSON 객체 하나만 반환하세요.
                반환 필드:
                campaign, template, notes.
                campaign 필드: campaignName, campaignDescription, campaignType, channel.
                template 필드: messageTemplateName, messageTemplateDescription, channel,
                messageTemplateTitle, messageTemplateBody, messageTemplateImageUrl, requiredVariables.
                requiredVariables에는 messageTemplateBody에 {{변수명}} 형태로 실제 등장하는 변수만 넣으세요.
                입력 JSON의 availableVariables가 비어 있지 않으면 messageTemplateBody에는 그 목록 안의 변수만 {{변수명}} 형태로 사용하세요.
                availableVariables 목록 밖의 변수가 필요하면 새 변수를 만들지 말고 notes에 필요한 변수 후보로 적으세요.
                campaignType은 ONE_TIME 또는 AUTOMATION, channel은 PUSH, EMAIL, SMS, WEBHOOK, KAKAO 중 하나입니다.
                """;
    }

    private String segmentSystemPrompt() {
        return """
                당신은 B2B CRM 세그먼트 빌더 Copilot입니다.
                입력 JSON의 자연어 요청과 사용 가능한 이벤트/필드를 참고해 조건 트리 초안을 만드세요.
                조건 트리 루트는 logic, conditions를 가진 group입니다.
                입력 JSON의 availableEvents에 categoryId가 제공되면 자연어 요청과 가장 가까운 이벤트를 골라 event 노드를 만드세요.
                예: 구매/결제 요청은 구매, purchase, order 의미의 availableEvents categoryId를 사용하세요.
                실제 categoryId를 모르면 event 노드를 만들지 말고 tag/user/device 노드를 사용하세요.
                event 노드는 type=event, categoryId, filters, period, minCount를 사용하세요.
                tag/user/device 노드는 type, key, op, value를 사용하세요.
                tag 노드의 key는 입력 JSON의 availableTagKeys 가 비어 있지 않으면 반드시 그 목록 안의 키만 사용하세요.
                availableTagKeys 목록 밖의 키를 만들지 마세요 — 실제 user_tags 와 매칭되지 않아 세그먼트 결과가 0건이 됩니다.
                availableTagKeys 가 비어 있고 적합한 키를 추정할 수 없으면 tag 노드 대신 event 노드 또는 user/device 노드를 시도하고, 그래도 어려우면 notes에 부족한 정보를 적으세요.
                op는 eq, neq, gt, lt, gte, lte, in, contains 중 하나입니다.
                period는 relative면 type=relative, days를 사용하세요.
                모호한 부분은 notes에 적으세요.
                반드시 JSON 객체 하나만 반환하세요.
                반환 필드: name, description, conditionTree, notes.
                """;
    }

    private record AiRequestEnvelope(Long projectId, Object request) {}

    private record AiGenerationResult<T>(Optional<T> response, String reason) {

        private static <T> AiGenerationResult<T> ok(T response) {
            return new AiGenerationResult<>(Optional.of(response), "");
        }

        private static <T> AiGenerationResult<T> empty(String reason) {
            return new AiGenerationResult<>(Optional.empty(), reason);
        }
    }
}
