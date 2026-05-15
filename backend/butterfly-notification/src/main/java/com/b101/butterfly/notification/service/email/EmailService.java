package com.b101.butterfly.notification.service.email;

import com.b101.butterfly.notification.dto.request.DeliveryCommand;
import com.b101.butterfly.notification.dto.response.DeliveryResult;
import com.b101.butterfly.notification.exception.DeliveryErrorCode;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service("deliveryEmailService")
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@butterfly.com}")
    private String fromAddress;

    public DeliveryResult sendOne(DeliveryCommand.MessagePayload message,
                                  DeliveryCommand.DeliveryTarget target) {
        if (target.email() == null || target.email().isBlank()) {
            return DeliveryResult.permanentFailure(
                    DeliveryErrorCode.EMAIL_ADDRESS_MISSING.getCode(),
                    DeliveryErrorCode.EMAIL_ADDRESS_MISSING.getMessage());
        }

        // 개인화 변수 치환 (target.variables 비어있으면 동일 인스턴스 반환)
        DeliveryCommand.MessagePayload personalized = message.personalize(target.variables());

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(target.email());
            helper.setSubject(personalized.title());
            helper.setText(buildHtmlContent(personalized), true);

            mailSender.send(mimeMessage);

            log.debug("이메일 발송 성공: to={}, userId={}", target.email(), target.userId());
            return DeliveryResult.success();
        } catch (MessagingException e) {
            log.warn("이메일 발송 실패 (MessagingException): to={}, error={}",
                    target.email(), e.getMessage(), e);
            return mapEmailError(e);
        } catch (MailException e) {
            log.warn("이메일 발송 실패 (MailException): to={}, error={}",
                    target.email(), e.getMessage(), e);
            return DeliveryResult.transientFailure(
                    DeliveryErrorCode.EMAIL_SEND_FAILED.getCode(), e.getMessage());
        }
    }

    private String buildHtmlContent(DeliveryCommand.MessagePayload message) {
        StringBuilder html = new StringBuilder();
        html.append("<div style=\"font-family:sans-serif;max-width:600px;margin:0 auto;\">");

        if (message.imageUrl() != null && !message.imageUrl().isBlank()) {
            html.append("<img src=\"").append(message.imageUrl())
                    .append("\" style=\"max-width:100%;height:auto;\" alt=\"\" />");
        }

        html.append("<div style=\"padding:16px;\">")
                .append(message.body())
                .append("</div>");

        html.append("</div>");
        return html.toString();
    }

    private DeliveryResult mapEmailError(MessagingException e) {
        String errorMessage = e.getMessage();
        if (errorMessage != null && (errorMessage.contains("Invalid Addresses")
                || errorMessage.contains("550")
                || errorMessage.contains("553"))) {
            return DeliveryResult.permanentFailure(
                    DeliveryErrorCode.EMAIL_INVALID_ADDRESS.getCode(), errorMessage);
        }
        return DeliveryResult.transientFailure(
                DeliveryErrorCode.EMAIL_SEND_FAILED.getCode(), errorMessage);
    }
}
