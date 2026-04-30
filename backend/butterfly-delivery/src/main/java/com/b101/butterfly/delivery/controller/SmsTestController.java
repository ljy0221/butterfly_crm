package com.b101.butterfly.delivery.controller;

import com.b101.butterfly.delivery.dto.request.DeliveryCommand;
import com.b101.butterfly.delivery.dto.response.DeliveryResult;
import com.b101.butterfly.delivery.service.sms.SmsService;
import com.b101.butterfly.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SMS 발송 테스트용 컨트롤러. 운영 배포 시 제거할 것.
 */
@RestController
@RequestMapping("/api/test/sms")
@RequiredArgsConstructor
public class SmsTestController {

    private final SmsService smsService;

    public record TestSmsRequest(
            String apiKey,
            String apiSecret,
            String senderNumber,
            String to,
            String title,
            String body,
            String clickAction
    ) {}

    @PostMapping("/send")
    public ApiResponse<DeliveryResult> testSend(@RequestBody TestSmsRequest request) {
        DeliveryCommand.SmsCredential credential = new DeliveryCommand.SmsCredential(
                request.apiKey(), request.apiSecret(), request.senderNumber());

        DeliveryCommand.MessagePayload message = new DeliveryCommand.MessagePayload(
                request.title(), request.body(), null, request.clickAction(), null);

        DeliveryCommand.DeliveryTarget target = new DeliveryCommand.DeliveryTarget(
                0L, null, null, null, request.to());

        return ApiResponse.ok(smsService.sendOne(credential, message, target));
    }
}
