package com.b101.butterfly.delivery.controller;

import com.b101.butterfly.delivery.dto.request.DeliveryCommand;
import com.b101.butterfly.delivery.dto.response.DeliveryResult;
import com.b101.butterfly.delivery.service.email.EmailService;
import com.b101.butterfly.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Email 발송 테스트용 컨트롤러. 운영 배포 시 제거할 것.
 */
@RestController
@RequestMapping("/api/test/email")
@RequiredArgsConstructor
public class EmailTestController {

    private final EmailService emailService;

    public record TestEmailRequest(
            String to,
            String title,
            String body,
            String imageUrl,
            String clickAction
    ) {}

    @PostMapping("/send")
    public ApiResponse<DeliveryResult> testSend(@RequestBody TestEmailRequest request) {
        DeliveryCommand.MessagePayload message = new DeliveryCommand.MessagePayload(
                request.title(), request.body(), request.imageUrl(), request.clickAction(), null);

        DeliveryCommand.DeliveryTarget target = new DeliveryCommand.DeliveryTarget(
                0L, null, null, request.to(), null);

        return ApiResponse.ok(emailService.sendOne(message, target));
    }
}
