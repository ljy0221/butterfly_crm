package com.b101.butterfly.ai.service;

import com.b101.butterfly.ai.dto.CampaignDraftRequest;
import com.b101.butterfly.ai.dto.CampaignDraftResponse;
import com.b101.butterfly.ai.dto.SegmentDraftRequest;
import com.b101.butterfly.ai.dto.SegmentDraftResponse;
import com.b101.butterfly.segment.domain.condition.EventNode;
import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.TagNode;
import com.b101.butterfly.segment.domain.condition.json.ConditionTreeJsonModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiCopilotServiceTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper().registerModule(new ConditionTreeJsonModule());
    private final AiCopilotService service =
            new AiCopilotService(
                    objectMapper, (org.springframework.ai.chat.client.ChatClient) null);

    @Test
    void campaignDraftUsesFallbackWhenChatClientIsMissing() {
        CampaignDraftResponse response =
                service.generateCampaignDraft(
                        1L,
                        new CampaignDraftRequest(
                                "repurchase coupon",
                                new CampaignDraftRequest.CampaignDraftContext(
                                        null, null, "ONE_TIME", "KAKAO", null, "recent buyers"),
                                List.of("name")));

        assertThat(response.campaign().campaignName()).contains("recent buyers");
        assertThat(response.template().messageTemplateBody()).contains("{{name}}");
        assertThat(response.template().requiredVariables()).contains("name");
        assertThat(response.notes()).isNotEmpty();
    }

    @Test
    void campaignDraftResponseAcceptsSingleStringNote() throws Exception {
        String json =
                """
                {
                  "campaign": {
                    "campaignName": "maintenance notice",
                    "campaignDescription": "notice",
                    "campaignType": "ONE_TIME",
                    "channel": "KAKAO"
                  },
                  "template": {
                    "messageTemplateName": "maintenance template",
                    "messageTemplateDescription": "notice template",
                    "channel": "KAKAO",
                    "messageTemplateTitle": "maintenance",
                    "messageTemplateBody": "{{name}} notice",
                    "requiredVariables": "name"
                  },
                  "notes": "use confirmed schedule only"
                }
                """;

        CampaignDraftResponse response = objectMapper.readValue(json, CampaignDraftResponse.class);

        assertThat(response.notes()).containsExactly("use confirmed schedule only");
        assertThat(response.template().requiredVariables()).containsExactly("name");
    }

    @Test
    void campaignDraftResponseDerivesRequiredVariablesFromTemplateBody() throws Exception {
        String json =
                """
                {
                  "campaign": {
                    "campaignName": "coupon",
                    "campaignDescription": "coupon",
                    "campaignType": "ONE_TIME",
                    "channel": "KAKAO"
                  },
                  "template": {
                    "messageTemplateName": "coupon template",
                    "messageTemplateDescription": "coupon template",
                    "channel": "KAKAO",
                    "messageTemplateTitle": "coupon",
                    "messageTemplateBody": "{{name}}님, {{coupon_code}}를 확인해 주세요.",
                    "requiredVariables": ["name", "unused_variable"]
                  },
                  "notes": []
                }
                """;

        CampaignDraftResponse response = objectMapper.readValue(json, CampaignDraftResponse.class);

        assertThat(response.template().requiredVariables())
                .containsExactly("name", "coupon_code");
    }

    @Test
    void segmentDraftUsesTagFallbackWhenChatClientIsMissing() {
        SegmentDraftResponse response =
                service.generateSegmentDraft(
                        1L,
                        new SegmentDraftRequest(
                                "cart add customers in last 14 days",
                                new SegmentDraftRequest.SegmentDraftContext(null, null, null),
                                List.of(),
                                List.of()));

        assertThat(response.conditionTree()).isInstanceOf(GroupNode.class);
        assertThat(response.conditionTree().conditions()).hasSize(1);
        assertThat(response.conditionTree().conditions().get(0))
                .isInstanceOfSatisfying(
                        TagNode.class,
                        tag -> assertThat(tag.key()).isEqualTo("interest"));
    }

    @Test
    void segmentDraftTagFallbackUsesFirstAvailableTagKey() {
        SegmentDraftResponse response =
                service.generateSegmentDraft(
                        1L,
                        new SegmentDraftRequest(
                                "vip customers",
                                new SegmentDraftRequest.SegmentDraftContext(null, null, null),
                                List.of(),
                                List.of("vip_grade", "city")));

        assertThat(response.conditionTree().conditions().get(0))
                .isInstanceOfSatisfying(
                        TagNode.class,
                        tag -> assertThat(tag.key()).isEqualTo("vip_grade"));
    }

    @Test
    void segmentDraftFallbackUsesMatchingEventCategoryWhenProvided() {
        SegmentDraftResponse response =
                service.generateSegmentDraft(
                        1L,
                        new SegmentDraftRequest(
                                "purchase customers in last 30 days",
                                new SegmentDraftRequest.SegmentDraftContext(null, null, null),
                                List.of(
                                        new SegmentDraftRequest.EventCategoryHint(
                                                12L, "purchase_completed", "Purchase", "order paid")),
                                List.of()));

        assertThat(response.conditionTree().conditions().get(0))
                .isInstanceOfSatisfying(
                        EventNode.class,
                        event -> assertThat(event.categoryId()).isEqualTo(12L));
    }

    @Test
    void campaignDraftFallbackUsesFirstAvailableVariableForGreeting() {
        CampaignDraftResponse response =
                service.generateCampaignDraft(
                        1L,
                        new CampaignDraftRequest(
                                "birthday coupon",
                                new CampaignDraftRequest.CampaignDraftContext(
                                        null, null, "ONE_TIME", "EMAIL", null, "vip"),
                                List.of("first_name", "email")));

        assertThat(response.template().messageTemplateBody()).contains("{{first_name}}");
        assertThat(response.template().requiredVariables()).containsExactly("first_name");
    }

    @Test
    void campaignDraftFallbackOmitsVariableWhenNoneAvailable() {
        CampaignDraftResponse response =
                service.generateCampaignDraft(
                        1L,
                        new CampaignDraftRequest(
                                "system notice",
                                new CampaignDraftRequest.CampaignDraftContext(
                                        null, null, "ONE_TIME", "PUSH", null, null),
                                List.of()));

        assertThat(response.template().messageTemplateBody()).doesNotContain("{{");
        assertThat(response.template().requiredVariables()).isEmpty();
    }

    @Test
    void springCanCreateServiceWithoutChatClientBuilderBean() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(
                    ObjectMapper.class,
                    () -> new ObjectMapper().registerModule(new ConditionTreeJsonModule()));
            context.registerBean(AiCopilotService.class);

            context.refresh();

            assertThat(context.getBean(AiCopilotService.class)).isNotNull();
        }
    }
}
